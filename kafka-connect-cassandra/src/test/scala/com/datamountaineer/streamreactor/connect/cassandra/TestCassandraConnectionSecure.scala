package com.datamountaineer.streamreactor.connect.cassandra

import com.datamountaineer.streamreactor.connect.cassandra.config.CassandraConfigSink
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}

/**
  * Created by andrew@datamountaineer.com on 14/04/16.
  * stream-reactor
  */
class TestCassandraConnectionSecure extends WordSpec with Matchers with BeforeAndAfter with TestConfig {

  before {
    startEmbeddedCassandra()
  }


  "should return a secured session" in {
    createTableAndKeySpace(secure = true, ssl = false)
    val taskConfig = CassandraConfigSink(getCassandraConfigSinkPropsSecure)
    val conn = CassandraConnection(taskConfig)
    val session = conn.session
    session should not be null
    session.getCluster.getConfiguration.getProtocolOptions.getAuthProvider should not be null

    val cluster = session.getCluster
    session.close()
    cluster.close()
  }
}

