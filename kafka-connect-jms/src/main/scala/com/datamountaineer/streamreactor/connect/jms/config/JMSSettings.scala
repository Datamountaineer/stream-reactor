/*
 *  Copyright 2017 Datamountaineer.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datamountaineer.streamreactor.connect.jms.config

import javax.jms.{ConnectionFactory, QueueConnectionFactory, TopicConnectionFactory}

import com.datamountaineer.connector.config.{Config, FormatType}
import com.datamountaineer.streamreactor.connect.converters.source.Converter
import com.datamountaineer.streamreactor.connect.errors.{ErrorPolicy, ErrorPolicyEnum, ThrowErrorPolicy}
import com.datamountaineer.streamreactor.connect.jms.config.DestinationSelector.DestinationSelector
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.kafka.common.config.ConfigException
import org.apache.kafka.common.config.types.Password

import scala.collection.JavaConversions._
import scala.util.{Failure, Success, Try}

case class JMSSettings(connectionURL: String,
                       connectionFactoryClass: Class[_ <: ConnectionFactory with QueueConnectionFactory with TopicConnectionFactory],
                       destinationSelector: DestinationSelector ,
                       settings: List[JMSSetting],
                       user: Option[String],
                       password: Option[Password],
                       errorPolicy: ErrorPolicy = new ThrowErrorPolicy,
                       retries: Int) {
  require(connectionURL != null && connectionURL.trim.length > 0, "Invalid connection URL")
  require(connectionFactoryClass != null, "Invalid class for connection factory")
}


object JMSSettings extends StrictLogging {

  /**
    * Creates an instance of JMSSettings from a JMSSinkConfig
    *
    * @param config : The map of all provided configurations
    * @return An instance of JmsSettings
    */
  def apply(config: JMSConfig, sink: Boolean) : JMSSettings = {
    val raw = config.getString(JMSConfig.KCQL)
    require(raw != null && !raw.isEmpty, s"No ${JMSConfig.KCQL} provided!")

    val kcql = raw.split(";").map(r => Config.parse(r))
    val errorPolicyE = ErrorPolicyEnum.withName(config.getString(JMSConfig.ERROR_POLICY).toUpperCase)
    val errorPolicy = ErrorPolicy(errorPolicyE)
    val nbrOfRetries = config.getInt(JMSConfig.NBR_OF_RETRIES)
    val clazz = config.getString(JMSConfig.CONNECTION_FACTORY)
    val connectionFactoryClass = Try(Class.forName(clazz)).getOrElse(throw new ConfigException("$clazz can not be loaded"))

    val destinationSelector = DestinationSelector.withName(config.getString(JMSConfig.DESTINATION_SELECTOR).toUpperCase)

    if (!connectionFactoryClass.isInstanceOf[Class[_ <: ConnectionFactory with QueueConnectionFactory with TopicConnectionFactory]]) {
      throw new ConfigException("$clazz is not derived from ConnectionFactory")
    }

    val url = config.getString(JMSConfig.JMS_URL)
    if (url == null || url.trim.length == 0) {
      throw new ConfigException(s"${JMSConfig.JMS_URL} has not been set")
    }

    val user = config.getString(JMSConfig.JMS_USER)
    val passwordRaw = config.getPassword(JMSConfig.JMS_PASSWORD)
    val sources = kcql.map(_.getSource).toSet

    val sourcesToConverterMap = Option(config.getString(JMSConfig.CONVERTER_CONFIG))
      .map { c =>
        c.split(';')
          .map(_.trim)
          .filter(_.nonEmpty)
          .map { e =>
            e.split('=') match {
              case Array(source: String, clazz: String) =>

                if (!sources.contains(source)) {
                  throw new ConfigException(s"Invalid ${JMSConfig.CONVERTER_CONFIG}. Source '$source' is not found in ${JMSConfig.KCQL}. Defined sources:${sources.mkString(",")}")
                }
                Try(getClass.getClassLoader.loadClass(clazz)) match {
                  case Failure(_) => throw new ConfigException(s"Invalid ${JMSConfig.CONVERTER_CONFIG}.$clazz can't be found")
                  case Success(clz) =>
                    if (!classOf[Converter].isAssignableFrom(clz)) {
                      throw new ConfigException(s"Invalid ${JMSConfig.CONVERTER_CONFIG}. $clazz is not inheriting Converter")
                    }
                }

                source -> clazz
              case _ => throw new ConfigException(s"Invalid ${JMSConfig.CONVERTER_CONFIG}. '$e' is not correct. Expecting source = className")
            }
          }.toMap
      }.getOrElse(Map.empty[String, String])

    val convertersMap = sourcesToConverterMap.map { s =>
      val clazz = s._2
      logger.info(s"Creating converter instance for $clazz")
      val converter = Try(this.getClass.getClassLoader.loadClass(clazz).newInstance()) match {
        case Success(value) => value.asInstanceOf[Converter]
        case Failure(_) => throw new ConfigException(s"Invalid ${JMSConfig.CONVERTER_CONFIG} is invalid. $clazz should have an empty ctor!")
      }
      s._1 -> converter
    }

    val jmsTopics = config.getList(JMSConfig.TOPIC_LIST).toSet
    val jmsQueues = config.getList(JMSConfig.QUEUE_LIST).toSet

    val settings = kcql.map(r => {
      val jmsName = if (sink) r.getTarget else r.getSource
      JMSSetting(r, getDestinationType(jmsName, jmsQueues, jmsTopics), getFormatType(r), convertersMap.get(jmsName))
    }).toList

    new JMSSettings(
      url,
      connectionFactoryClass.asInstanceOf[Class[_ <: ConnectionFactory with QueueConnectionFactory with TopicConnectionFactory]],
      destinationSelector,
      settings,
      Option(user),
      Option(passwordRaw),
      errorPolicy,
      nbrOfRetries)
  }

  def getFormatType(config: Config) : FormatType = {
    val format = Option(config.getFormatType)
    format.getOrElse(FormatType.JSON)
  }

  def getDestinationType(target: String, queues: Set[String], topics: Set[String]): DestinationType = {
    if (topics.contains(target)) {
      TopicDestination
    } else if (queues.contains(target)) {
      QueueDestination
    } else {
      throw new ConfigException(s"$target has not been configured as topic or queue.")
    }
  }
}
