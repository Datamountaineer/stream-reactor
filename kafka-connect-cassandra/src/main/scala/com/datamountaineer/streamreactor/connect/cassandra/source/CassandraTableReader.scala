/**
  * Copyright 2015 Datamountaineer.
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
  **/

package com.datamountaineer.streamreactor.connect.cassandra.source

import java.text.SimpleDateFormat
import java.util.{Collections, Date}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.LinkedBlockingQueue

import com.datamountaineer.streamreactor.connect.cassandra.config.{CassandraConfigConstants, CassandraSetting}
import com.datamountaineer.streamreactor.connect.cassandra.utils.CassandraUtils
import com.datamountaineer.streamreactor.connect.cassandra.utils.CassandraResultSetWrapper.resultSetFutureToScala
import com.datamountaineer.streamreactor.connect.offsets.OffsetHandler
import com.datastax.driver.core._
import com.datastax.driver.core.utils.UUIDs
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.connect.source.{SourceRecord, SourceTaskContext}

import scala.collection.JavaConverters.{mapAsJavaMapConverter, seqAsJavaListConverter}
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

/**
  * Created by andrew@datamountaineer.com on 20/04/16.
  * stream-reactor
  */
class CassandraTableReader(private val session: Session,
                           private val setting: CassandraSetting,
                           private val context : SourceTaskContext,
                           var queue: LinkedBlockingQueue[SourceRecord]) extends StrictLogging {

  logger.info(s"Received setting:\n ${setting.toString()}")
  private val defaultTimestamp = "1900-01-01 00:00:00.0000000Z"
  private val dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'")
  private val timestampCol = setting.timestampColumn.getOrElse("")
  private val preparedStatement = getPreparedStatements(setting)
  private var tableOffset: Option[Date] = buildOffsetMap(context)
  private val partition = Collections.singletonMap(CassandraConfigConstants.ASSIGNED_TABLES, setting.table)
  private val querying = new AtomicBoolean(false)
  private val stop = new AtomicBoolean(false)
  private var lastPoll = 0.toLong

  /**
    * Build a map of table to offset.
    *
    * @param context SourceTaskContext for this task.
    * @return The last stored offset.
    * */
  private def buildOffsetMap(context: SourceTaskContext) : Option[Date] = {
    val offsetStorageKey = CassandraConfigConstants.ASSIGNED_TABLES
    val tables = List(setting.table)
    val recoveredOffsets = OffsetHandler.recoverOffsets(offsetStorageKey, tables.asJava, context)
    val offset = OffsetHandler.recoverOffset[String](recoveredOffsets,offsetStorageKey, setting.table, timestampCol)
    Some(dateFormatter.parse(offset.getOrElse(defaultTimestamp)))
  }

  /**
    * Build a preparedStatement for the given table.
    *
    * @param setting Cassandra settings for this task.
    * @return A map of table -> prepared statements..
    * */
  private def getPreparedStatements(setting: CassandraSetting) : PreparedStatement = {
    //if no columns set then select the whole table

    val selectStatement = if (setting.bulkImportMode) {
      s"SELECT * FROM ${setting.keySpace}.${setting.table}"
    } else {
      s"SELECT * " +
      s"FROM ${setting.keySpace}.${setting.table} " +
      s"WHERE ${setting.timestampColumn.get} > maxTimeuuid(?) AND ${setting.timestampColumn.get} <= minTimeuuid(?) " +
        (if (setting.allowFiltering.get) " ALLOW FILTERING" else "")
    }
    session.prepare(selectStatement)
  }

  /**
    * Fires cassandra queries for the rows in a loop incrementing the timestamp
    * with each loop. Row returned are put into the queue.
    * */
  def read() : Unit = {
    if (!stop.get()) {

      if (setting.bulkImportMode & !queue.isEmpty) {
        logger.info(s"Entries still pending drainage from the queue for ${setting.keySpace}.${setting.table}!" +
          s" Not submitting query till empty.")
      }

      val newPollTime = System.currentTimeMillis()

      if (querying.get()) {
        //checking we are querying here,don't issue another query for the table if we querying,
        // maintain incremental order
        logger.debug(s"Still querying for ${setting.keySpace}.${setting.table}. Current queue size in ${queue.size()}.")
      }
      //wait for next poll interval to expire
      else if (lastPoll + setting.pollInterval < newPollTime) {
        lastPoll = newPollTime
        query()
      }
    }
    else {
      logger.info(s"Told to stop for ${setting.keySpace}.${setting.table}.")
    }
  }

  private def query() = {
    //execute the query
    querying.set(true)
    //logger.info(s"Setting up new query for ${setting.table}.")

    //if the tableoffset has been set use it as the lower bound else get default (1900-01-01) for first query
    val lowerBound = if (tableOffset.isEmpty) dateFormatter.parse(defaultTimestamp) else tableOffset.get
    //set the upper bound to now
    val upperBound = new Date()

    //execute the query, gives us back a future resultset
    val frs = if (setting.bulkImportMode) {
      resultSetFutureToScala(fireQuery())
    } else {
      resultSetFutureToScala(bindAndFireQuery(lowerBound, upperBound))
    }

    //give futureresultset to the process method to extract records,once complete it will update the tableoffset timestamp
    process(frs)
  }

  /**
    * Bind and execute the preparedStatement and set querying to true.
    *
    * @param previous The previous timestamp to bind.
    * @param now The current timestamp on the db to bind.
    * @return a ResultSet.
    * */
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
    * */
  private def fireQuery() : ResultSetFuture = {
    //bind the offset and db time
    val bound = preparedStatement.bind()
    //execute the query
    logger.info(s"Query ${preparedStatement.getQueryString} executing.")
    session.executeAsync(bound)
  }

  /**
    * Iterate over the resultset, extract SinkRecords
    * and add to the queue.
    *
    * @param f Cassandra Future ResultSet to iterate over.
    * */
  private def process(f: Future[ResultSet]) = {
    //get the max offset per query
    var maxOffset : Option[Date] = None
    //on success start writing the row to the queue
    f.onSuccess({
      case rs:ResultSet =>
        logger.info(s"Querying returning results for ${setting.keySpace}.${setting.table}.")
        val iter = rs.iterator()
        var counter = 0

        while (iter.hasNext & !stop.get()) { //check if we have been told to stop
          val row = iter.next()
          Try({
            //if not bulk get the row timestamp column value to get the max
            if (!setting.bulkImportMode) {
              val rowOffset = extractTimestamp(row)
              maxOffset = if (maxOffset.isEmpty || rowOffset.after(maxOffset.get)) Some(rowOffset) else maxOffset
            }
            processRow(row)
            counter += 1
          }) match {
            case Failure(e) =>
              reset(tableOffset)
              throw new ConnectException(s"Error processing row ${row.toString} for table ${setting.table}.", e)
            case Success(s) => logger.debug(s"Processed row ${row.toString}")
          }
        }
        logger.info(s"Processed $counter rows for table ${setting.topic}.${setting.table}")
        reset(maxOffset) //set the as the new high watermark.
    })

    //On failure, rest and throw
    f.onFailure({
      case t:Throwable =>
        reset(tableOffset)
        throw new ConnectException(s"Error will querying ${setting.table}.", t)
    })
  }

  /**
    * Process a Cassandra row, convert it to a SourceRecord and put in queue
    *
    * @param row The Cassandra row to process.
    *
    * */
  private def processRow(row: Row) = {
    //convert the cassandra row to a struct
    val struct = CassandraUtils.convert(row)
    //get the offset for this value

    val rowOffset: Date = if (setting.bulkImportMode) tableOffset.get else extractTimestamp(row)
    val offset: String = dateFormatter.format(rowOffset)

    logger.info(s"Storing offset $offset")

    //create source record
    val record = new SourceRecord(partition, Map(timestampCol -> offset).asJava, setting.topic, struct.schema(), struct)

    //add to queue
    logger.debug(s"Attempting to put SourceRecord ${record.toString} on queue for ${setting.keySpace}.${setting.table}.")
    if (queue.offer(record)) {
      logger.debug(s"Successfully enqueued SourceRecord ${record.toString}.")
    } else {
      logger.error(s"Failed to put ${record.toString} on to the queue for ${setting.keySpace}.${setting.table}.")
    }
  }

  /**
    * Extract the CQL UUID timestamp and return a date
    *
    * @param row The row to extract the UUI from
    * @return A java.util.Date
    * */
  private def extractTimestamp(row: Row) : Date = {
    new Date(UUIDs.unixTimestamp(row.getUUID(setting.timestampColumn.get)))
  }

  /**
    * Set the offset for the table and set querying to false
    *
    * @param offset the date to set the offset to
    * */
  private def reset(offset : Option[Date]) = {
    //set the offset to the 'now' bind value
    logger.debug(s"Setting offset for ${setting.keySpace}.${setting.table} to $offset.")
    tableOffset = offset.orElse(tableOffset)
    //switch to not querying
    logger.debug(s"Setting querying for ${setting.keySpace}.${setting.table} to false.")
    querying.set(false)
  }

  /**
    * Closed down the driver session and cluster.
    * */
  def close(): Unit = {
    logger.info("Shutting down Queries.")
    stopQuerying()
    logger.info("All stopped.")
  }

  /**
    * Tell me to stop processing.
    * */
  def stopQuerying() : Unit = {
    stop.set(true)
    while (querying.get()) {
      logger.info(s"Waiting for querying to stop for ${setting.keySpace}.${setting.table}.")
    }
    logger.info(s"Querying stopped for ${setting.keySpace}.${setting.table}.")
  }

  /**
    * Is the reader in the middle of a query
    * */
  def isQuerying : Boolean = {
    querying.get()
  }
}

object CassandraTableReader {
  def apply(session: Session,
            setting: CassandraSetting,
            context: SourceTaskContext,
            queue: LinkedBlockingQueue[SourceRecord]): CassandraTableReader = {
    //return a reader
    new CassandraTableReader(session = session,
                             setting = setting,
                             context = context,
                             queue = queue)
  }
}
