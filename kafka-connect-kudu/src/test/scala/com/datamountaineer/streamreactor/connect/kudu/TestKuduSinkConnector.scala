package com.datamountaineer.streamreactor.connect.kudu

import com.datamountaineer.streamreactor.connect.kudu.config.KuduSinkConfig
import com.datamountaineer.streamreactor.connect.kudu.sink.{KuduSinkConnector, KuduSinkTask}

import scala.collection.JavaConversions._


/**
  * Created by andrew@datamountaineer.com on 24/02/16. 
  * stream-reactor
  */
class TestKuduSinkConnector extends TestBase {
  "Should start a Kudu Connector" in {
    val config = getConfig
    val connector = new KuduSinkConnector()
    connector.start(config)
    val taskConfigs = connector.taskConfigs(1)
    taskConfigs.head.get(KuduSinkConfig.KUDU_MASTER) shouldBe KUDU_MASTER
    taskConfigs.size() shouldBe 1
    connector.taskClass() shouldBe classOf[KuduSinkTask]
    connector.stop()
  }
}
