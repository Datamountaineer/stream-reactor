package com.datamountaineer.streamreactor.connect.cassandra.config

import com.datamountaineer.streamreactor.connect.cassandra.TestConfig
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}

class TestCassandraSinkConfig extends WordSpec with BeforeAndAfter with Matchers with TestConfig {

  "A CassandraConfig should return configured for username and password" in {
    val taskConfig = CassandraConfigSink(getCassandraConfigSinkPropsSecure)
    taskConfig.getString(CassandraConfigConstants.CONTACT_POINTS) shouldBe CONTACT_POINT
    taskConfig.getString(CassandraConfigConstants.KEY_SPACE) shouldBe CASSANDRA_KEYSPACE
    taskConfig.getString(CassandraConfigConstants.USERNAME) shouldBe USERNAME
    taskConfig.getPassword(CassandraConfigConstants.PASSWD).value shouldBe PASSWD
    taskConfig.getString(CassandraConfigConstants.EXPORT_ROUTE_QUERY) shouldBe QUERY_ALL
  }

  "A CassandraConfig should return configured for SSL" in {
    val taskConfig  = CassandraConfigSink(getCassandraConfigSinkPropsSecureSSL)
    taskConfig.getString(CassandraConfigConstants.CONTACT_POINTS) shouldBe CONTACT_POINT
    taskConfig.getString(CassandraConfigConstants.KEY_SPACE) shouldBe CASSANDRA_KEYSPACE
    taskConfig.getString(CassandraConfigConstants.USERNAME) shouldBe USERNAME
    taskConfig.getPassword(CassandraConfigConstants.PASSWD).value shouldBe PASSWD
    taskConfig.getBoolean(CassandraConfigConstants.SSL_ENABLED) shouldBe true
    taskConfig.getString(CassandraConfigConstants.TRUST_STORE_PATH) shouldBe TRUST_STORE_PATH
    taskConfig.getPassword(CassandraConfigConstants.TRUST_STORE_PASSWD).value shouldBe TRUST_STORE_PASSWORD
    //taskConfig.getString(CassandraConfigConstants.EXPORT_MAPPINGS) shouldBe EXPORT_TOPIC_TABLE_MAP
    taskConfig.getString(CassandraConfigConstants.EXPORT_ROUTE_QUERY) shouldBe QUERY_ALL
  }

  "A CassandraConfig should return configured for SSL without client certficate authentication" in {
    val taskConfig  = CassandraConfigSink(getCassandraConfigSinkPropsSecureSSLwithoutClient)
    taskConfig.getString(CassandraConfigConstants.CONTACT_POINTS) shouldBe CONTACT_POINT
    taskConfig.getString(CassandraConfigConstants.KEY_SPACE) shouldBe CASSANDRA_KEYSPACE
    taskConfig.getString(CassandraConfigConstants.USERNAME) shouldBe USERNAME
    taskConfig.getPassword(CassandraConfigConstants.PASSWD).value shouldBe PASSWD
    taskConfig.getBoolean(CassandraConfigConstants.SSL_ENABLED) shouldBe true
    taskConfig.getString(CassandraConfigConstants.KEY_STORE_PATH) shouldBe KEYSTORE_PATH
    taskConfig.getPassword(CassandraConfigConstants.KEY_STORE_PASSWD).value shouldBe KEYSTORE_PASSWORD
    taskConfig.getBoolean(CassandraConfigConstants.USE_CLIENT_AUTH) shouldBe false
    taskConfig.getString(CassandraConfigConstants.KEY_STORE_PATH) shouldBe KEYSTORE_PATH
    taskConfig.getPassword(CassandraConfigConstants.KEY_STORE_PASSWD).value shouldBe KEYSTORE_PASSWORD
    taskConfig.getString(CassandraConfigConstants.EXPORT_ROUTE_QUERY) shouldBe QUERY_ALL
  }
}
