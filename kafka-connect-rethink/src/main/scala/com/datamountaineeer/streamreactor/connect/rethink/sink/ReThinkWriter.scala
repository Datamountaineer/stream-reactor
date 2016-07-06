/**
  * Copyright 2016 Datamountaineer.
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

package com.datamountaineeer.streamreactor.connect.rethink.sink

import com.datamountaineeer.streamreactor.connect.rethink.config.{ReThinkSetting, ReThinkSettings, ReThinkSinkConfig}
import com.datamountaineer.streamreactor.connect.errors.ErrorHandler
import com.datamountaineer.streamreactor.connect.schemas.ConverterUtil
import com.rethinkdb.RethinkDB
import com.rethinkdb.net.Connection
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.kafka.connect.sink.{SinkRecord, SinkTaskContext}

import scala.collection.JavaConverters._
import scala.util.Failure

object ReThinkWriter extends StrictLogging {
  def apply(config: ReThinkSinkConfig) : ReThinkWriter = {
    val rethinkHost = config.getString(ReThinkSinkConfig.RETHINK_HOST)

    //set up the connection to the host
    val settings = ReThinkSettings(config)
    val port = config.getInt(ReThinkSinkConfig.RETHINK_PORT)
    lazy val r = RethinkDB.r
    lazy val conn: Connection = r.connection().hostname(rethinkHost).port(port).connect()
    new ReThinkWriter(r, conn = conn, setting = settings)
  }
}
/***
  * Handles writes to Rethink
  *
  */
class ReThinkWriter(rethink : RethinkDB, conn : Connection, setting: ReThinkSetting)
  extends StrictLogging with ConverterUtil with ErrorHandler {

  logger.info("Initialising ReThink writer")
  //initialize error tracker
  initialize(setting.maxRetries, setting.errorPolicy)
  //check tables exist or are marked for auto create
  ReThinkSinkConverter.checkAndCreateTables(rethink, setting, conn)

  /**
    * Write a list of SinkRecords
    * to rethink
    *
    * @param records A list of SinkRecords to write.
    * */
  def write(records: List[SinkRecord]) : Unit = {

    if (records.isEmpty) {
      logger.debug("No records received.")
    } else {
      logger.info(s"Received ${records.size} records.")
      if (!conn.isOpen) conn.reconnect()
      val grouped = records.groupBy(_.topic()).grouped(setting.batchSize)
      grouped.foreach(g => g.foreach {case (topic, entries) => writeRecords(topic, entries)})
    }
  }

  /**
    * Write a list of sink records to Rethink
    *
    * @param topic The source topic
    * @param records The list of sink records to write.
    * */
  private def writeRecords(topic: String, records: List[SinkRecord]) = {
    logger.info(s"Handling records for $topic")
    val table = setting.topicTableMap.get(topic).get
    val conflict  = setting.conflictPolicy.get(table).get
    val pks = setting.pks.get(topic).get

    val writes = records.map(r => {
      val extracted = convert(r, setting.fieldMap.get(r.topic()).get, setting.ignoreFields.get(r.topic()).get)
      val hm = ReThinkSinkConverter.convertToReThink(rethink, extracted, pks)

      val x : java.util.Map[String, Object] =
        rethink
        .db(setting.db)
        .table(table)
        .insert(hm)
        .optArg("conflict", conflict.toLowerCase)
        .optArg("return_changes", true)
        .run(conn)
      x
    })

    //handle errors
    writes.foreach(w => handleFailure(w.asScala.toMap))
  }

  /**
    * Handle any write failures. If errors > 0 handle error
    * according to the policy
    *
    * @param write The write result changes return from rethink
    * */
  private def handleFailure(write : Map[String, Object]) = {
    val errors = write.getOrElse("errors", 0).toString
    logger.info(s"Result of write: ${write.mkString(",")}")

    if (!errors.equals("0")) {
      val error = write.getOrElse("first_error","Unknown error")
      val message = s"Write error occurred. ${error.toString}"
      val failure = Failure({new Throwable(message)})
      handleTry(failure)
    }
  }

  def close() : Unit = conn.close(true)
}
