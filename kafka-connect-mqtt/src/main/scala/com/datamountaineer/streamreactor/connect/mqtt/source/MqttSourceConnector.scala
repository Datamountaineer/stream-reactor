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

package com.datamountaineer.streamreactor.connect.mqtt.source

import java.util

import com.datamountaineer.streamreactor.connect.mqtt.config.{MqttSourceConfig, MqttSourceSettings}
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.kafka.common.config.ConfigDef
import org.apache.kafka.connect.connector.Task
import org.apache.kafka.connect.source.SourceConnector
import org.apache.kafka.connect.util.ConnectorUtils

import scala.collection.JavaConverters._
import scala.collection.JavaConversions._
import scala.collection.mutable

class MqttSourceConnector extends SourceConnector with StrictLogging {
  private var configProps: util.Map[String, String] = _
  private val configDef = MqttSourceConfig.config

  /**
    * States which SinkTask class to use
    **/
  override def taskClass(): Class[_ <: Task] = classOf[MqttSourceTask]

  /**
    * Set the configuration for each work and determine the split
    *
    * @param maxTasks The max number of task workers be can spawn
    * @return a List of configuration properties per worker
    **/
  override def taskConfigs(maxTasks: Int): util.List[util.Map[String, String]] = {

    val settings = MqttSourceSettings(MqttSourceConfig(configProps))
    val partitions = Math.min(settings.kcql.length, maxTasks)
    settings.kcql.grouped(partitions)
      .zipWithIndex
      .map { case (p, index) => settings.copy(kcql = p, clientId = settings.clientId + index).asMap() }
      .toList.asJava
  }

  /**
    * Start the sink and set to configuration
    *
    * @param props A map of properties for the connector and worker
    **/
  override def start(props: util.Map[String, String]): Unit = {
    configProps = props
  }

  override def stop(): Unit = {}

  override def config(): ConfigDef = configDef

  override def version(): String = getClass.getPackage.getImplementationVersion
}
