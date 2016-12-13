package com.datamountaineer.streamreactor.connect.mqtt.config

import com.datamountaineer.streamreactor.connect.mqtt.source.converters.{AvroConverter, BytesConverter}
import org.apache.kafka.common.config.ConfigException
import org.scalatest.{Matchers, WordSpec}

import scala.collection.JavaConversions._

class MqttSourceSettingsTest extends WordSpec with Matchers {
  "MqttSourceSetting" should {

    "create an instance of settings" in {
      val settings = MqttSourceSettings {
        MqttSourceConfig(Map(
          MqttSourceConfig.HOSTS_CONFIG -> "mqtt://localhost:61612?wireFormat.maxFrameSize=100000",
          MqttSourceConfig.CONVERTER_CONFIG -> s"mqttSource=${classOf[AvroConverter].getCanonicalName}",
          MqttSourceConfig.KCQL_CONFIG -> "INSERT INTO kTopic SELECT * FROM mqttSource",
          MqttSourceConfig.QS_CONFIG -> "1",
          MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
          MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
          MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
          MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
          MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
          MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
          MqttSourceConfig.USER_CONFIG -> "user"
        ))
      }
      settings.mqttQualityOfService shouldBe 1
      settings.sourcesToConverters shouldBe Map("mqttSource" -> classOf[AvroConverter].getCanonicalName)
      settings.throwOnConversion shouldBe true
      settings.cleanSession shouldBe true
      settings.clientId shouldBe "someid"
      settings.password shouldBe Some("somepassw")
      settings.user shouldBe Some("user")
      settings.keepAliveInterval shouldBe 1000
      settings.connectionTimeout shouldBe 1000
      settings.connection shouldBe "mqtt://localhost:61612?wireFormat.maxFrameSize=100000"
    }

    "converted defaults to BytesConverter if not provided" in {
      val settings = MqttSourceSettings {
        MqttSourceConfig(Map(
          MqttSourceConfig.HOSTS_CONFIG -> "mqtt://localhost:61612?wireFormat.maxFrameSize=100000",
          MqttSourceConfig.KCQL_CONFIG -> "INSERT INTO kTopic SELECT * FROM mqttSource",
          MqttSourceConfig.QS_CONFIG -> "1",
          MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
          MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
          MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
          MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
          MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
          MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
          MqttSourceConfig.USER_CONFIG -> "user"
        ))
      }

      settings.sourcesToConverters shouldBe Map("mqttSource" -> classOf[BytesConverter].getCanonicalName)
    }

    "throw an config exception if no kcql is set" in {
      intercept[ConfigException] {
        MqttSourceConfig(Map(
          MqttSourceConfig.QS_CONFIG -> "1",
          MqttSourceConfig.HOSTS_CONFIG -> "mqtt://localhost:61612?wireFormat.maxFrameSize=100000",
          MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
          MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
          MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
          MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
          MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
          MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
          MqttSourceConfig.USER_CONFIG -> "user"
        ))
      }
    }

    "throw an config exception if QS is less than 0" in {
      intercept[ConfigException] {
        MqttSourceSettings(
          MqttSourceConfig(Map(
            MqttSourceConfig.KCQL_CONFIG -> "INSERT INTO kTopic SELECT * FROM mqttSource",
            MqttSourceConfig.QS_CONFIG -> "-1",
            MqttSourceConfig.HOSTS_CONFIG -> "mqtt://localhost:61612?wireFormat.maxFrameSize=100000",
            MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
            MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
            MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
            MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
            MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
            MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
            MqttSourceConfig.USER_CONFIG -> "user"
          )))
      }
    }

    "throw an config exception if QS is more than 2" in {
      intercept[ConfigException] {
        MqttSourceSettings(
          MqttSourceConfig(Map(
            MqttSourceConfig.KCQL_CONFIG -> "INSERT INTO kTopic SELECT * FROM mqttSource",
            MqttSourceConfig.QS_CONFIG -> "3",
            MqttSourceConfig.HOSTS_CONFIG -> "mqtt://localhost:61612?wireFormat.maxFrameSize=100000",
            MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
            MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
            MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
            MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
            MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
            MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
            MqttSourceConfig.USER_CONFIG -> "user"
          )))
      }
    }

    "throw an config exception if HOSTS_CONFIG is not defined" in {
      intercept[ConfigException] {
        MqttSourceSettings(
          MqttSourceConfig(Map(
            MqttSourceConfig.KCQL_CONFIG -> "INSERT INTO kTopic SELECT * FROM mqttSource",
            MqttSourceConfig.QS_CONFIG -> "1",
            MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
            MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
            MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
            MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
            MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
            MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
            MqttSourceConfig.USER_CONFIG -> "user"
          )))
      }
    }

    "throw an config exception if the converter class can't be found" in {
      intercept[ConfigException] {
        MqttSourceConfig(Map(
          MqttSourceConfig.CONVERTER_CONFIG -> "kTopic=com.non.existance.SomeConverter",
          MqttSourceConfig.KCQL_CONFIG -> "INSERT INTO kTopic SELECT * FROM mqttSource",
          MqttSourceConfig.QS_CONFIG -> "1",
          MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
          MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
          MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
          MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
          MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
          MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
          MqttSourceConfig.USER_CONFIG -> "user"
        ))
      }
    }

    "throw an config exception if the converter settings with invalid source" in {
      intercept[ConfigException] {
        MqttSourceConfig(Map(
          MqttSourceConfig.CONVERTER_CONFIG -> s"kTopic=${classOf[AvroConverter].getCanonicalName}",
          MqttSourceConfig.KCQL_CONFIG -> "INSERT INTO kTopic SELECT * FROM mqttSource",
          MqttSourceConfig.QS_CONFIG -> "1",
          MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
          MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
          MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
          MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
          MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
          MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
          MqttSourceConfig.USER_CONFIG -> "user"
        ))
      }
    }

    "throw an config exception if the converter topic doesn't match the KCQL settings" in {
      intercept[ConfigException] {
        MqttSourceConfig(Map(
          MqttSourceConfig.CONVERTER_CONFIG -> s"kTopicA=${classOf[AvroConverter].getCanonicalName}",
          MqttSourceConfig.KCQL_CONFIG -> "INSERT INTO kTopic SELECT * FROM mqttSource",
          MqttSourceConfig.QS_CONFIG -> "1",
          MqttSourceConfig.THROW_ON_CONVERT_ERRORS_CONFIG -> "true",
          MqttSourceConfig.CLEAN_SESSION_CONFIG -> "true",
          MqttSourceConfig.CLIENT_ID_CONFIG -> "someid",
          MqttSourceConfig.CONNECTION_TIMEOUT_CONFIG -> "1000",
          MqttSourceConfig.KEEP_ALIVE_INTERVAL_CONFIG -> "1000",
          MqttSourceConfig.PASSWORD_CONFIG -> "somepassw",
          MqttSourceConfig.USER_CONFIG -> "user"
        ))
      }
    }

  }
}
