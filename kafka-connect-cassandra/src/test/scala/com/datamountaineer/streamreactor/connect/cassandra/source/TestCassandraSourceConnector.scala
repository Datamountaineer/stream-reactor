package com.datamountaineer.streamreactor.connect.cassandra.source

import com.datamountaineer.streamreactor.connect.cassandra.TestConfig
import com.datamountaineer.streamreactor.connect.cassandra.config.CassandraConfigConstants
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}

import scala.collection.JavaConverters._
/**
  * Created by andrew@datamountaineer.com on 20/04/16.
  * stream-reactor
  */
class TestCassandraSourceConnector extends WordSpec with BeforeAndAfter with Matchers with TestConfig {
  "Should start a Cassandra Source Connector" in {
    val props = getCassandraConfigSourcePropsSecureBulk
    val connector = new CassandraSourceConnector()
    connector.start(props)
    val taskConfigs = connector.taskConfigs(1)
    taskConfigs.asScala.head.get(CassandraConfigConstants.IMPORT_TABLE_TOPIC_MAP) shouldBe IMPORT_TABLE_TOPIC_MAP
    taskConfigs.asScala.head.get(CassandraConfigConstants.CONTACT_POINTS) shouldBe CONTACT_POINT
    taskConfigs.asScala.head.get(CassandraConfigConstants.KEY_SPACE) shouldBe TOPIC1
    taskConfigs.asScala.head.get(CassandraConfigConstants.IMPORT_TABLE_TOPIC_MAP) shouldBe IMPORT_TABLE_TOPIC_MAP
    taskConfigs.asScala.head.get(CassandraConfigConstants.ASSIGNED_TABLES) shouldBe ASSIGNED_TABLES
    taskConfigs.size() shouldBe 1
    connector.taskClass() shouldBe classOf[CassandraSourceTask]
    connector.stop()
  }
}
