/*
 * Copyright 2017 Datamountaineer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.cassandra.source

import java.text.SimpleDateFormat
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.{Collections, Date}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Created by andrew@datamountaineer.com on 20/04/16.
  * stream-reactor
  */
class CassandraTableReader(private val session: Session,
                           private val setting: CassandraSourceSetting,
                           private val context: SourceTaskContext,
                           var queue: LinkedBlockingQueue[SourceRecord]) extends StrictLogging {

  private val defaultTimestamp = "1900-01-01 00:00:00.0000000Z"
  private val dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'")
  private val timestampCol = setting.timestampColumn.getOrElse("")
  private val querying = new AtomicBoolean(false)
  private val stop = new AtomicBoolean(false)
  private var lastPoll = 0.toLong
  private val table = setting.routes.getSource
  private val topic = setting.routes.getTarget
  private val keySpace = setting.keySpace
  private val selectColumns = getSelectColumns
  private val preparedStatement = getPreparedStatements
  private var tableOffset: Option[Date] = buildOffsetMap(context)
  private val sourcePartition = Collections.singletonMap(CassandraConfigConstants.ASSIGNED_TABLES, table)
  private val schemaName = s"$keySpace.$table".replace('-', '.')


  /**
    * Build a map of table to offset.
    *
    * @param context SourceTaskContext for this task.
    * @return The last stored offset.
    */
  private def buildOffsetMap(context: SourceTaskContext): Option[Date] = {
    val offsetStorageKey = CassandraConfigConstants.ASSIGNED_TABLES
    val tables = List(topic)
    val recoveredOffsets = OffsetHandler.recoverOffsets(offsetStorageKey, tables, context)
    val offset = OffsetHandler.recoverOffset[String](recoveredOffsets, offsetStorageKey, table, timestampCol)
    Some(dateFormatter.parse(offset.getOrElse(defaultTimestamp)))
  }

  /**
    * Build a preparedStatement for the given table.
    *
    * @return the PreparedStatement
    */
  private def getPreparedStatements: PreparedStatement = {
    // if we are in incremental mode
    // we need to have the time stamp column
    if (!setting.bulkImportMode) {
      if (!selectColumns.contains(timestampCol) && !selectColumns.contentEquals("*")) {
        val msg = s"the timestamp column ($timestampCol) must appear in the SELECT statement"
        logger.error(msg)
        throw new ConfigException(msg)
      }
    }

    // build the correct CQL statement based on the KCQL
    val selectStatement = if (setting.bulkImportMode) {
      s"SELECT $selectColumns FROM $keySpace.$table"
    } else {
      val predicate = setting.timestampColType match {
        case TimestampType.TIMEUUID => s"WHERE $timestampCol > maxTimeuuid(?) AND $timestampCol <= minTimeuuid(?) ALLOW FILTERING"
        case TimestampType.TIMESTAMP => s"WHERE $timestampCol > ? AND $timestampCol <= ? ALLOW FILTERING"
      }
      s"SELECT $selectColumns FROM $keySpace.$table $predicate"
    }

    val statement = session.prepare(selectStatement)
    setting.consistencyLevel.foreach(statement.setConsistencyLevel)
    statement
  }

  /**
    * get the columns for the SELECT statement
    *
    * @return the comma separated columns
    */
  private def getSelectColumns: String = {
    val faList = setting.routes.getFieldAlias.map(fa => fa.getField).toList

    //if no columns set then select the whole table
    val f = if (faList == null || faList.isEmpty) "*" else faList.mkString(",")
    logger.info(s"the fields to select are $f")
    f
  }

  /**
    * Fires Cassandra queries and increments the timestamp
    * Every Row returned from query is put into the queue for processing.
    */
  def read(): Unit = {
    if (!stop.get()) {
      val newPollTime = System.currentTimeMillis()

      // don't issue another query for the table if we are querying
      if (querying.get()) {
        logger.debug(s"Still querying for $keySpace.$table. Current queue size is ${queue.size()}.")
      }
      // wait for next poll interval to expire
      else if (lastPoll + setting.pollInterval < newPollTime) {
        lastPoll = newPollTime
        query()
      }
    }
    else {
      logger.info(s"Told to stop for $keySpace.$table.")
    }
  }

  private def query() = {
    // we are going to execute the query
    querying.set(true)

    // execute the query, gives us back a future result set
    val frs = if (setting.bulkImportMode) {
      resultSetFutureToScala(fireQuery())
    }
    else {
      // if the tableoffset has been set use it as the lower bound else get default (1900-01-01) for first query
      val lowerBound = if (tableOffset.isEmpty) dateFormatter.parse(defaultTimestamp) else tableOffset.get
      // set the upper bound to now
      val upperBound = new Date()
      resultSetFutureToScala(bindAndFireQuery(lowerBound, upperBound))
    }

    //give futureresultset to the process method to extract records,
    //once complete it will update the tableoffset timestamp
    process(frs)
  }

  /**
    * Bind and execute the preparedStatement and set querying to true.
    *
    * @param previous The previous timestamp to bind.
    * @param now      The current timestamp on the db to bind.
    * @return a ResultSet.
    */
  private def bindAndFireQuery(previous: Date, now: Date) = {
    //bind the offset and db time
    val formattedPrevious = dateFormatter.format(previous)
    val formattedNow = dateFormatter.format(now)
    val bound = preparedStatement.bind(previous, now)
    logger.info(s"Query ${preparedStatement.getQueryString} executing with bindings ($formattedPrevious, $formattedNow).")
    session.executeAsync(bound)
  }

  /**
    * Execute the preparedStatement and set querying to true.
    *
    * @return a ResultSet.
    */
  private def fireQuery(): ResultSetFuture = {
    //bind the offset and db time
    val bound = preparedStatement.bind()
    //execute the query
    logger.info(s"Query ${preparedStatement.getQueryString} executing.")
    session.executeAsync(bound)
  }

  /**
    * Iterate over the resultset, extract SourceRecords
    * and add them to the queue.
    *
    * @param future Cassandra Future ResultSet to iterate over.
    */
  private def process(future: Future[ResultSet]) = {
    //get the max offset per query
    var maxOffset: Option[Date] = None
    //on success start writing the row to the queue
    future.onSuccess({
      case rs: ResultSet =>
        logger.info(s"Processing results for $keySpace.$table.")
        val iter = rs.iterator()
        var counter = 0

        // process results unless told to stop
        while (iter.hasNext & !stop.get()) {
          val row = iter.next()
          Try({
            //if not bulk get the row timestamp column value to get the max
            if (!setting.bulkImportMode) {
              val rowOffset = extractTimestamp(row)
              maxOffset = if (maxOffset.isEmpty || rowOffset.after(maxOffset.get)) Some(rowOffset) else maxOffset
              logger.info(s"Max Offset is currently: ${maxOffset.get}")
            }
            processRow(row)
            counter += 1
          }) match {
            case Failure(e) =>
              reset(tableOffset)
              throw new ConnectException(s"Error processing row ${row.toString} for table $table.", e)
            case Success(s) => logger.debug(s"Processed row ${row.toString}")
          }
        }
        logger.info(s"Processed $counter rows for table $topic.$table")
        reset(maxOffset) //set as the new high watermark.
    })

    //On failure, rest and throw
    future.onFailure({
      case t: Throwable =>
        reset(tableOffset)
        throw new ConnectException(s"Error will querying $table.", t)
    })
  }

  /**
    * Process a Cassandra row, convert it to a SourceRecord and put in queue
    *
    * @param row The Cassandra row to process.
    *
    */
  private def processRow(row: Row) = {
    // convert the cassandra row to a struct
    val ignoreList = setting.routes.getIgnoredField.toList
    val structColDefs = CassandraUtils.getStructColumns(row, ignoreList)
    val struct = CassandraUtils.convert(row, schemaName, structColDefs)

    // get the offset for this value
    val rowOffset: Date = if (setting.bulkImportMode) tableOffset.get else extractTimestamp(row)
    val offset: String = dateFormatter.format(rowOffset)

    logger.debug(s"Storing offset $offset")

    // create source record
    val record = if (setting.routes.isWithUnwrap) {
      val structValue = structColDefs.map(d => d.getName).map(name => row.getObject(name)).mkString(",")
      new SourceRecord(sourcePartition, Map(timestampCol -> offset), topic, Schema.STRING_SCHEMA, structValue)
    } else {
      new SourceRecord(sourcePartition, Map(timestampCol -> offset), topic, struct.schema(), struct)
    }

    // add source record to queue
    logger.debug(s"Attempting to put SourceRecord ${record.toString} on queue for $keySpace.$table.")
    if (queue.offer(record)) {
      logger.debug(s"Successfully enqueued SourceRecord ${record.toString}.")
    } else {
      logger.error(s"Failed to put ${record.toString} on to the queue for $keySpace.$table.")
    }
  }

  /**
    * Extract the CQL UUID timestamp and return a date
    *
    * @param row The row to extract the timestamp from
    * @return A java.util.Date
    */
  private def extractTimestamp(row: Row): Date = {
    Try(row.getTimestamp(setting.timestampColumn.get)) match {
      case Success(s) => s
      case Failure(_) => new Date(UUIDs.unixTimestamp(row.getUUID(setting.timestampColumn.get)))
    }
  }

  /**
    * Set the offset for the table and set querying to false
    *
    * @param offset the date to set the offset to
    */
  private def reset(offset: Option[Date]) = {
    //set the offset to the 'now' bind value
    val table = setting.routes.getTarget
    logger.debug(s"Setting offset for $keySpace.$table to $offset.")
    tableOffset = offset.orElse(tableOffset)
    //switch to not querying
    logger.debug(s"Setting querying for $keySpace.$table to false.")
    querying.set(false)
  }

  /**
    * Closed down the driver session and cluster.
    */
  def close(): Unit = {
    logger.info("Shutting down Queries.")
    stopQuerying()
    logger.info("All stopped.")
  }

  /**
    * Tell me to stop processing.
    */
  def stopQuerying(): Unit = {
    val table = setting.routes.getTarget
    stop.set(true)
    while (querying.get()) {
      logger.info(s"Waiting for querying to stop for $keySpace.$table.")
    }
    logger.info(s"Querying stopped for $keySpace.$table.")
  }

  /**
    * Is the reader in the middle of a query
    */
  def isQuerying: Boolean = {
    querying.get()
  }
}

object CassandraTableReader {
  def apply(session: Session,
            setting: CassandraSourceSetting,
            context: SourceTaskContext,
            queue: LinkedBlockingQueue[SourceRecord]): CassandraTableReader = {
    //return a reader
    new CassandraTableReader(session = session,
      setting = setting,
      context = context,
      queue = queue)
  }
}
