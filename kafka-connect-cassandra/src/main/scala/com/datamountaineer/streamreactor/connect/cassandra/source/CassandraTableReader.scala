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

import com.datamountaineer.streamreactor.connect.cassandra.config.{CassandraConfigConstants, CassandraSourceSetting, TimestampType}
import com.datamountaineer.streamreactor.connect.cassandra.utils.CassandraResultSetWrapper.resultSetFutureToScala
import com.datamountaineer.streamreactor.connect.cassandra.utils.CassandraUtils
import com.datamountaineer.streamreactor.connect.offsets.OffsetHandler
import com.datastax.driver.core._
import com.datastax.driver.core.utils.UUIDs
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.connect.source.{SourceRecord, SourceTaskContext}

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.connect.data.Schema

/**
  * Created by andrew@datamountaineer.com on 20/04/16.
  * stream-reactor
  */
class CassandraTableReader(private val session: Session,
                           private val setting: CassandraSourceSetting,
                           private val context: SourceTaskContext,
                           var queue: LinkedBlockingQueue[SourceRecord]) extends StrictLogging {


  private val config = setting.routes
  private val cqlGenerator = new CqlGenerator(setting)
  
  private val dateFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS'Z'")
  private val primaryKeyCol = setting.primaryKeyColumn.getOrElse("")
  private val querying = new AtomicBoolean(false)
  private val stop = new AtomicBoolean(false)
  private var lastPoll = 0.toLong
  private val table = config.getSource
  private val topic = config.getTarget
  private val keySpace = setting.keySpace
  // TODO: need two different statements for token (with no offset and with an offset)
  // private val preparedStatementNoOffset ??
  private val preparedStatement = getPreparedStatements
  private var tableOffset: Option[String] = buildOffsetMap(context)
  private val sourcePartition = Collections.singletonMap(CassandraConfigConstants.ASSIGNED_TABLES, table)
  private val schemaName = s"$keySpace.$table".replace('-', '.')


  /**
    * Build a map of table to offset.
    *
    * @param context SourceTaskContext for this task.
    * @return The last stored offset.
    */
  private def buildOffsetMap(context: SourceTaskContext): Option[String] = {
    val offsetStorageKey = CassandraConfigConstants.ASSIGNED_TABLES
    val tables = List(topic)
    val recoveredOffsets = OffsetHandler.recoverOffsets(offsetStorageKey, tables, context)
    val offset = OffsetHandler.recoverOffset[String](recoveredOffsets, offsetStorageKey, table, primaryKeyCol)
    
    cqlGenerator.getDefaultOffsetValue(offset)
  }

  /**
    * Build a preparedStatement for the given table.
    *
    * @return the PreparedStatement
    */
  private def getPreparedStatements: PreparedStatement = {
    val selectStatement = cqlGenerator.getCqlStatement
    val statement = session.prepare(selectStatement)
    setting.consistencyLevel.foreach(statement.setConsistencyLevel)
    statement
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
      // have we passed the last poll and interval
      else if (lastPoll + setting.pollInterval < newPollTime) {
        lastPoll = newPollTime
        query()
      } else {
        val sleepFor = newPollTime - lastPoll
        Thread.sleep(sleepFor)
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
    } else if (cqlGenerator.isTokenBased()) {
      // TODO: token based 
      null
    } else {
      // time based key column
      val lowerBound = dateFormatter.parse(cqlGenerator.getDefaultOffsetValue(tableOffset).get)
      // set the upper bound to now
      val upperBound = new Date()
      resultSetFutureToScala(bindAndFireQuery(lowerBound, upperBound))
    }

    //give futureresultset to the process method to extract records,
    //once complete it will update the tableoffset
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
    var maxOffset: Option[String] = None
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
            // if not bulk get the row timestamp column value to get the max
            if (!setting.bulkImportMode) {
              maxOffset = if (cqlGenerator.isTokenBased()) {
                getTokenMaxOffsetForRow(maxOffset, row)
              } else {
                getTimebasedMaxOffsetForRow(maxOffset, row)
              }
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
        //set as the new high watermark.
        reset(maxOffset)
    })

    //On failure, rest and throw
    future.onFailure({
      case t: Throwable =>
        reset(tableOffset)
        throw new ConnectException(s"Error will querying $table.", t)
    })
  }
  
  private def getTokenMaxOffsetForRow(maxOffset: Option[String], row: Row): Option[String] = {
    null
  }
  
  private def getTimebasedMaxOffsetForRow(maxOffset: Option[String], row: Row): Option[String] = {
    val rowOffsetDate = extractTimestamp(row)
    if (maxOffset.isEmpty || rowOffsetDate.after(dateFormatter.parse(maxOffset.get))) Some(dateFormatter.format(rowOffsetDate)) else maxOffset
  }

  /**
    * Process a Cassandra row, convert it to a SourceRecord and put in queue
    *
    * @param row The Cassandra row to process.
    *
    */
  private def processRow(row: Row) = {
    // convert the cassandra row to a struct
    val ignoreList = config.getIgnoredField.toList
    val structColDefs = CassandraUtils.getStructColumns(row, ignoreList)
    val struct = CassandraUtils.convert(row, schemaName, structColDefs)

    // get the offset for this value
    val offset: String = if (cqlGenerator.isTokenBased()) {
      // token
      null
    } else {
      val rowOffset: Date = if (setting.bulkImportMode) dateFormatter.parse(tableOffset.get) else extractTimestamp(row)
      dateFormatter.format(rowOffset)
    }
    
    logger.debug(s"Storing offset $offset")

    // create source record
    val record = if (config.isWithUnwrap) {
      val structValue = structColDefs.map(d => d.getName).map(name => row.getObject(name)).mkString(",")
      new SourceRecord(sourcePartition, Map(primaryKeyCol -> offset), topic, Schema.STRING_SCHEMA, structValue)
    } else {
      new SourceRecord(sourcePartition, Map(primaryKeyCol -> offset), topic, struct.schema(), struct)
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
    Try(row.getTimestamp(setting.primaryKeyColumn.get)) match {
      case Success(s) => s
      case Failure(_) => new Date(UUIDs.unixTimestamp(row.getUUID(setting.primaryKeyColumn.get)))
    }
  }

  /**
    * Set the offset for the table and set querying to false
    *
    * @param offset the date to set the offset to
    */
  private def reset(offset: Option[String]) = {
    //set the offset to the 'now' bind value
    val table = config.getTarget
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
    val table = config.getTarget
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
