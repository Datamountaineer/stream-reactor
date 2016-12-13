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

package com.datamountaineer.streamreactor.connect.mqtt.config

import java.util

import org.apache.kafka.common.config.ConfigDef.{Importance, Type}
import org.apache.kafka.common.config.{AbstractConfig, ConfigDef}

/**
  * Created by andrew@datamountaineer.com on 22/09/16. 
  * stream-reactor
  */
object MqttSourceConfig {
  val KCQL_CONFIG = "mqtt.source.kcql"
  private val KCQL_DOC = "Contains the Kafka Connect Query Language describing the sourced MQTT source and the target Kafka topics"
  private val KCQL_DISPLAY = "KCQL commands"

  val HOSTS_CONFIG = "mqtt.hosts"
  private val HOSTS_DOC = "Contains the MQTT connection end points."
  private val HOSTS_DISPLAY = "Mqtt connection endpoints"

  val QS_CONFIG = "mqtt.service.quality"
  private val QS_DOC = "Specifies the Mqtt quality of service"
  private val QS_DISPLAY = "Quality of Service. It can be 0,1 or 2.0 = At most once; 1 = At least once; 2 = Exactly once"
  private val QS_DEFAULT = 1

  val USER_CONFIG = "mqtt.user"
  private val USER_DOC = "Contains the Mqtt connection user name"
  private val USER_DEFAULT = null
  private val USER_DISPLAY = "Username"

  val PASSWORD_CONFIG = "mqtt.password"
  private val PASSWORD_DOC = "Contains the Mqtt connection password"
  private val PASSWORD_DISPLAY = "Password"
  private val PASSWORD_DEFAULT = null

  val CLIENT_ID_CONFIG = "mqtt.client.id"
  private val CLIENT_ID_DOC = "Contains the client id for the session"
  private val CLIENT_ID_DISPLAY = "Client id"
  private val CLIENT_ID_DEFAULT = null

  val CONNECTION_TIMEOUT_CONFIG = "mqtt.connection.timeout"
  private val CONNECTION_TIMEOUT_DOC = "Provides the time interval to establish the mqtt connection"
  private val CONNECTION_TIMEOUT_DISPLAY = "Connection timeout"
  private val CONNECTION_TIMEOUT_DEFAULT = 3000

  val CLEAN_SESSION_CONFIG = "mqtt.connection.clean"
  private val CLEAN_CONNECTION_DOC = ""
  private val CLEAN_CONNECTION_DISPLAY = "Clean connection"
  private val CLEAN_CONNECTION_DEFAULT = true

  val KEEP_ALIVE_INTERVAL_CONFIG = "mqtt.connection.keep.alive"
  private val KEEP_ALIVE_INTERVAL_DOC = "A flag for the mqtt library to make sure the connection is kept alive"
  private val KEEP_ALIVE_INTERVAL_DISPLAY = "Keep alive interval"
  private val KEEP_ALIVE_INTERVAL_DEFAULT = 5000

  val SSL_CA_CERT_CONFIG = "mqtt.connection.ssl.ca.cert"
  private val SSL_CA_CERT_DOC = "Provides the path to the CA certificate file to use with the Mqtt connection"
  private val SSL_CA_CERT_DISPLAY = "CA certificate file path"
  private val SSL_CA_CERT_DEFAULT = null

  val SSL_CERT_CONFIG = "mqtt.connection.ssl.cert"
  private val SSL_CERT_DOC = "Provides the path to the certificate file to use with the Mqtt connection"
  private val SSL_CERT_DISPLAY = "Certificate key file path"
  private val SSL_CERT_DEFAULT = null

  val SSL_CERT_KEY_CONFIG = "mqtt.connection.ssl.key"
  private val SSL_CERT_KEY_DOC = "Certificate private key file path."
  private val SSL_CERT_KEY_DISPLAY = "Certificate private key file path"
  private val SSL_CERT_KEY_DEFAULT = null

  val CONVERTER_CONFIG = "mqtt.source.converters"
  private val CONVERTER_DOC =
    """
      |Contains a tuple (source and the canonical class name for the converter of a raw Mqtt message bytes to a SourceRecord).
      |If the source topic is not matched it will default to the BytesConverter
      |i.e. $mqtt_source1=com.datamountaineer.streamreactor.connect.mqtt.source.converters.AvroConverter;$mqtt_source2=com.datamountaineer.streamreactor.connect.mqtt.source.converters.JsonConverter""".stripMargin
  private val CONVERTER_DISPLAY = "Converter class"
  private val CONVERTER_DEFAULT = null

  val THROW_ON_CONVERT_ERRORS_CONFIG = "mqtt.converter.throw.on.error"
  private val THROW_ON_CONVERT_ERRORS_DOC = "If set to false it will swallow the exception and carry on; true will throw the exception.Default is false."
  private val THROW_ON_CONVERT_ERRORS_DISPLAY = "Throw error on conversion"
  private val THROW_ON_CONVERT_ERRORS_DEFAULT = false

  val config = new ConfigDef()
    .define(HOSTS_CONFIG, Type.STRING, Importance.HIGH, HOSTS_DOC, "Connection", 1, ConfigDef.Width.MEDIUM, HOSTS_DISPLAY)
    .define(USER_CONFIG, Type.STRING, USER_DEFAULT, Importance.HIGH, USER_DOC, "Connection", 2, ConfigDef.Width.MEDIUM, USER_DISPLAY)
    .define(PASSWORD_CONFIG, Type.PASSWORD, PASSWORD_DEFAULT, Importance.HIGH, PASSWORD_DOC, "Connection", 3, ConfigDef.Width.MEDIUM, PASSWORD_DISPLAY)
    .define(QS_CONFIG, Type.INT, Importance.MEDIUM, QS_DOC, "Connection", 4, ConfigDef.Width.MEDIUM, QS_DISPLAY)
    .define(CONNECTION_TIMEOUT_CONFIG, Type.INT, CONNECTION_TIMEOUT_DEFAULT, Importance.LOW, CONNECTION_TIMEOUT_DOC, "Connection", 5, ConfigDef.Width.MEDIUM, CONNECTION_TIMEOUT_DISPLAY)
    .define(CLEAN_SESSION_CONFIG, Type.BOOLEAN, CLEAN_CONNECTION_DEFAULT, Importance.LOW, CLEAN_SESSION_CONFIG, "Connection", 6, ConfigDef.Width.MEDIUM, CLEAN_CONNECTION_DISPLAY)
    .define(KEEP_ALIVE_INTERVAL_CONFIG, Type.INT, KEEP_ALIVE_INTERVAL_DEFAULT, Importance.LOW, KEEP_ALIVE_INTERVAL_DOC, "Connection", 7, ConfigDef.Width.MEDIUM, KEEP_ALIVE_INTERVAL_DISPLAY)
    .define(CLIENT_ID_CONFIG, Type.STRING, CLIENT_ID_DEFAULT, Importance.LOW, CLIENT_ID_DOC, "Connection", 8, ConfigDef.Width.MEDIUM, CLIENT_ID_DISPLAY)


    //ssl
    .define(SSL_CA_CERT_CONFIG, Type.STRING, SSL_CA_CERT_DEFAULT, Importance.MEDIUM, SSL_CA_CERT_DOC, "SSL", 1, ConfigDef.Width.MEDIUM, SSL_CA_CERT_DISPLAY)
    .define(SSL_CERT_CONFIG, Type.STRING, SSL_CERT_DEFAULT, Importance.MEDIUM, SSL_CERT_DOC, "SSL", 2, ConfigDef.Width.MEDIUM, SSL_CERT_DISPLAY)
    .define(SSL_CERT_KEY_CONFIG, Type.STRING, SSL_CERT_KEY_DEFAULT, Importance.MEDIUM, SSL_CERT_KEY_DOC, "SSL", 3, ConfigDef.Width.MEDIUM, SSL_CERT_KEY_DISPLAY)


    //kcql
    .define(KCQL_CONFIG, Type.STRING, Importance.HIGH, KCQL_DOC, "KCQL", 1, ConfigDef.Width.MEDIUM, KCQL_DISPLAY)

    //converter
    .define(CONVERTER_CONFIG, Type.STRING, CONVERTER_DEFAULT, Importance.HIGH, CONVERTER_DOC, "Converter", 1, ConfigDef.Width.MEDIUM, CONVERTER_DISPLAY)
    .define(THROW_ON_CONVERT_ERRORS_CONFIG, Type.BOOLEAN, THROW_ON_CONVERT_ERRORS_DEFAULT, Importance.HIGH, THROW_ON_CONVERT_ERRORS_DOC, "Converter", 2, ConfigDef.Width.MEDIUM, THROW_ON_CONVERT_ERRORS_DISPLAY)

}

case class MqttSourceConfig(props: util.Map[String, String]) extends AbstractConfig(MqttSourceConfig.config, props)

