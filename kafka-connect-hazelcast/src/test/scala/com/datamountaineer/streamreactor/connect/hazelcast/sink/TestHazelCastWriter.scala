/*
 * *
 *   * Copyright 2016 Datamountaineer.
 *   *
 *   * Licensed under the Apache License, Version 2.0 (the "License");
 *   * you may not use this file except in compliance with the License.
 *   * You may obtain a copy of the License at
 *   *
 *   * http://www.apache.org/licenses/LICENSE-2.0
 *   *
 *   * Unless required by applicable law or agreed to in writing, software
 *   * distributed under the License is distributed on an "AS IS" BASIS,
 *   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   * See the License for the specific language governing permissions and
 *   * limitations under the License.
 *   *
 *
 */

package com.datamountaineer.streamreactor.connect.hazelcast.sink

import com.datamountaineer.streamreactor.connect.hazelcast.config.{HazelCastSinkConfig, HazelCastSinkSettings}
import com.datamountaineer.streamreactor.connect.hazelcast.writers.HazelCastWriter
import com.datamountaineer.streamreactor.connect.hazelcast.{HazelCastConnection, MessageListenerImplAvro, MessageListenerImplJson, TestBase}
import com.hazelcast.config.Config
import com.hazelcast.core.{Hazelcast, ITopic}
import com.hazelcast.ringbuffer.Ringbuffer
import org.apache.avro.generic.GenericRecord

/**
  * Created by andrew@datamountaineer.com on 11/08/16. 
  * stream-reactor
  */
class TestHazelCastWriter extends TestBase {

   "should write avro to hazelcast reliable topic" in {
     val configApp1 = new Config()
     configApp1.getGroupConfig.setName(GROUP_NAME).setPassword(HazelCastSinkConfig.SINK_GROUP_PASSWORD_DEFAULT)
     val instance = Hazelcast.newHazelcastInstance(configApp1)

     val props = getProps
     val config = new HazelCastSinkConfig(props)
     val settings = HazelCastSinkSettings(config)
     val writer = HazelCastWriter(settings)
     val records = getTestRecords()


     //get client and check hazelcast
     val conn = HazelCastConnection(settings.connConfig)
     val reliableTopic = conn.getReliableTopic(TABLE).asInstanceOf[ITopic[Object]]
     val listener = new MessageListenerImplAvro
     reliableTopic.addMessageListener(listener)

     //write
     writer.write(records)
     writer.close

     while (!listener.gotMessage) {
       Thread.sleep(1000)
     }

     val message = listener.message.get
     message.isInstanceOf[GenericRecord] shouldBe true
     message.get("int_field") shouldBe 12
     message.get("string_field").toString shouldBe "foo"
     instance.shutdown()
     conn.shutdown()
   }

  "should write avro to hazelcast ringbuffer" in {
    val configApp1 = new Config()
    configApp1.getGroupConfig.setName(GROUP_NAME).setPassword(HazelCastSinkConfig.SINK_GROUP_PASSWORD_DEFAULT)
    val instance = Hazelcast.newHazelcastInstance(configApp1)

    val props = getPropsRB
    val config = new HazelCastSinkConfig(props)
    val settings = HazelCastSinkSettings(config)
    val writer = HazelCastWriter(settings)
    val records = getTestRecords()

    //write
    writer.write(records)
    writer.close

    //get client and check hazelcast
    val conn = HazelCastConnection(settings.connConfig)
    val ringbuffer = conn.getRingbuffer(TABLE).asInstanceOf[Ringbuffer[Object]]

    val message = ringbuffer.readOne(ringbuffer.headSequence())
    new String(message.asInstanceOf[Array[Byte]]) shouldBe json
    instance.shutdown()
    conn.shutdown()
  }


  "should write json to hazelcast reliable topic" in {
    val configApp1 = new Config()
    configApp1.getGroupConfig.setName(GROUP_NAME).setPassword(HazelCastSinkConfig.SINK_GROUP_PASSWORD_DEFAULT)
    val instance = Hazelcast.newHazelcastInstance(configApp1)

    val props = getPropsJson
    val config = new HazelCastSinkConfig(props)
    val settings = HazelCastSinkSettings(config)
    val writer = HazelCastWriter(settings)
    val records = getTestRecords()


    //get client and check hazelcast
    val conn = HazelCastConnection(settings.connConfig)
    val reliableTopic = conn.getReliableTopic(TABLE).asInstanceOf[ITopic[Object]]
    val listener = new MessageListenerImplJson
    reliableTopic.addMessageListener(listener)

    //write
    writer.write(records)
    writer.close

    while (!listener.gotMessage) {
      Thread.sleep(1000)
    }

    val message = listener.message.get
    message.toString shouldBe json
    instance.shutdown()
    conn.shutdown()
  }
}

