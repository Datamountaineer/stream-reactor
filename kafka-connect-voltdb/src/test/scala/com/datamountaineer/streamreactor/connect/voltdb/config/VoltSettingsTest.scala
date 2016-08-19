package com.datamountaineer.streamreactor.connect.voltdb.config

import com.datamountaineer.connector.config.Config
import com.datamountaineer.streamreactor.connect.errors.ThrowErrorPolicy
import org.apache.kafka.common.config.ConfigException
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{Matchers, WordSpec}

class VoltSettingsTest extends WordSpec with Matchers with MockitoSugar {

  val MEASURE_NAME = "someMeasurement"
  val TOPIC_NAME = "mykafkatopic"
  val QUERY_ALL = s"INSERT INTO $MEASURE_NAME SELECT * FROM $TOPIC_NAME"
  val QUERY_SELECT = s"INSERT INTO $MEASURE_NAME SELECT lastName as surname, firstName FROM $TOPIC_NAME"
  val QUERY_SELECT_AND_TIMESTAMP = s"INSERT INTO $MEASURE_NAME SELECT * FROM $TOPIC_NAME WITHTIMESTAMP ts"
  val QUERY_SELECT_AND_TIMESTAMP_SYSTEM = s"INSERT INTO $MEASURE_NAME SELECT * FROM $TOPIC_NAME WITHTIMESTAMP ${Config.TIMESTAMP}"

  "raise a configuration exception if the connection servers is missing" in {
    intercept[ConfigException] {
      val config = mock[VoltSinkConfig]
      when(config.getString(VoltSinkConfig.USER_CONFIG)).thenReturn("myuser")
      when(config.getString(VoltSinkConfig.PASSWORD_CONFIG)).thenReturn("apass")
      when(config.getString(VoltSinkConfig.ERROR_POLICY_CONFIG)).thenReturn("THROW")
      when(config.getString(VoltSinkConfig.EXPORT_ROUTE_QUERY_CONFIG)).thenReturn(QUERY_ALL)
      VoltSettings(config)
    }
  }

  "raise a configuration exception if the user is not set" in {
    intercept[ConfigException] {
      val config = mock[VoltSinkConfig]
      when(config.getString(VoltSinkConfig.SERVERS_CONFIG)).thenReturn("localhost:8081")
      when(config.getString(VoltSinkConfig.USER_CONFIG)).thenReturn("")
      when(config.getString(VoltSinkConfig.PASSWORD_CONFIG)).thenReturn("apass")
      when(config.getString(VoltSinkConfig.ERROR_POLICY_CONFIG)).thenReturn("THROW")
      when(config.getString(VoltSinkConfig.EXPORT_ROUTE_QUERY_CONFIG)).thenReturn(QUERY_ALL)
      VoltSettings(config)
    }
  }

  "create a settings with all fields" in {
    val servers = "localhost:8081"
    val database = "mydatabase"
    val user = "myuser"
    val config = mock[VoltSinkConfig]
    when(config.getString(VoltSinkConfig.SERVERS_CONFIG)).thenReturn(servers)
    when(config.getString(VoltSinkConfig.USER_CONFIG)).thenReturn(user)
    when(config.getString(VoltSinkConfig.PASSWORD_CONFIG)).thenReturn(null)
    when(config.getString(VoltSinkConfig.ERROR_POLICY_CONFIG)).thenReturn("THROW")
    when(config.getString(VoltSinkConfig.EXPORT_ROUTE_QUERY_CONFIG)).thenReturn(QUERY_ALL)
    val settings = VoltSettings(config)
    settings.servers shouldBe servers
    settings.user shouldBe user
    settings.password shouldBe null
    settings.errorPolicy shouldBe ThrowErrorPolicy()
    settings.fieldsExtractorMap.size shouldBe 1
    settings.fieldsExtractorMap(TOPIC_NAME).includeAllFields shouldBe true
    settings.fieldsExtractorMap(TOPIC_NAME).fieldsAliasMap shouldBe Map.empty
  }

  "create a settings with selected fields" in {
    val servers = "localhost:8081"
    val database = "mydatabase"
    val user = "myuser"
    val config = mock[VoltSinkConfig]
    when(config.getString(VoltSinkConfig.SERVERS_CONFIG)).thenReturn(servers)
    when(config.getString(VoltSinkConfig.USER_CONFIG)).thenReturn(user)
    when(config.getString(VoltSinkConfig.PASSWORD_CONFIG)).thenReturn("mememe")
    when(config.getString(VoltSinkConfig.ERROR_POLICY_CONFIG)).thenReturn("THROW")
    when(config.getString(VoltSinkConfig.EXPORT_ROUTE_QUERY_CONFIG)).thenReturn(QUERY_SELECT)
    val settings = VoltSettings(config)
    settings.servers shouldBe servers
    settings.user shouldBe user
    settings.password shouldBe "mememe"
    settings.errorPolicy shouldBe ThrowErrorPolicy()
    settings.fieldsExtractorMap.size shouldBe 1
    settings.fieldsExtractorMap(TOPIC_NAME).includeAllFields shouldBe false
    settings.fieldsExtractorMap(TOPIC_NAME).fieldsAliasMap shouldBe Map("firstName" -> "firstName", "lastName" -> "surname")
  }

  "create a settings with selected fields with timestamp set to a field" in {
    val servers = "localhost:8081"
    val database = "mydatabase"
    val user = "myuser"
    val config = mock[VoltSinkConfig]
    when(config.getString(VoltSinkConfig.SERVERS_CONFIG)).thenReturn(servers)
    when(config.getString(VoltSinkConfig.USER_CONFIG)).thenReturn(user)
    when(config.getString(VoltSinkConfig.PASSWORD_CONFIG)).thenReturn("mememe")
    when(config.getString(VoltSinkConfig.ERROR_POLICY_CONFIG)).thenReturn("THROW")
    when(config.getString(VoltSinkConfig.EXPORT_ROUTE_QUERY_CONFIG)).thenReturn(QUERY_SELECT_AND_TIMESTAMP)
    val settings = VoltSettings(config)
    settings.servers shouldBe servers
    settings.user shouldBe user
    settings.password shouldBe "mememe"
    settings.errorPolicy shouldBe ThrowErrorPolicy()
    settings.fieldsExtractorMap.size shouldBe 1
    settings.fieldsExtractorMap(TOPIC_NAME).includeAllFields shouldBe true
    settings.fieldsExtractorMap(TOPIC_NAME).fieldsAliasMap shouldBe Map.empty
  }

  "create a settings with selected fields with timestamp set to a sys_timestamp" in {
    val servers = "localhost:8081"
    val database = "mydatabase"
    val user = "myuser"
    val config = mock[VoltSinkConfig]
    when(config.getString(VoltSinkConfig.SERVERS_CONFIG)).thenReturn(servers)
    when(config.getString(VoltSinkConfig.USER_CONFIG)).thenReturn(user)
    when(config.getString(VoltSinkConfig.PASSWORD_CONFIG)).thenReturn("mememe")
    when(config.getString(VoltSinkConfig.ERROR_POLICY_CONFIG)).thenReturn("THROW")
    when(config.getString(VoltSinkConfig.EXPORT_ROUTE_QUERY_CONFIG)).thenReturn(QUERY_SELECT_AND_TIMESTAMP_SYSTEM)
    val settings = VoltSettings(config)
    settings.servers shouldBe servers
    settings.user shouldBe user
    settings.password shouldBe "mememe"
    settings.errorPolicy shouldBe ThrowErrorPolicy()
    settings.fieldsExtractorMap.size shouldBe 1
    settings.fieldsExtractorMap(TOPIC_NAME).includeAllFields shouldBe true
    settings.fieldsExtractorMap(TOPIC_NAME).fieldsAliasMap shouldBe Map.empty
  }
}
