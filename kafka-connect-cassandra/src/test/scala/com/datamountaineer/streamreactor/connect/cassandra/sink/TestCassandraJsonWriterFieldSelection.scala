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
 */

package com.datamountaineer.streamreactor.connect.cassandra.sink

import com.datamountaineer.streamreactor.connect.cassandra.TestConfig
import com.datamountaineer.streamreactor.connect.cassandra.config.CassandraConfigSink
import com.datamountaineer.streamreactor.connect.schemas.ConverterUtil
import com.datastax.driver.core.utils.UUIDs
import org.apache.kafka.connect.data.{Decimal, Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.sink.{SinkRecord, SinkTaskContext}
import org.mockito.Mockito._
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfter, Matchers, WordSpec}

import scala.collection.JavaConverters._

/**
  * Created by andrew@datamountaineer.com on 04/05/16. 
  * stream-reactor
  */
class TestCassandraJsonWriterFieldSelection extends WordSpec with Matchers with MockitoSugar with BeforeAndAfter
  with TestConfig {

  before {
    startEmbeddedCassandra()
  }

  "Cassandra JsonWriter should write records to Cassandra" in {
    val session = createTableAndKeySpace(secure = true, ssl = false)
    val context = mock[SinkTaskContext]
    val assignment = getAssignment
    when(context.assignment()).thenReturn(assignment)
    //get test records
    val testRecords = getTestRecords(TABLE1)
    //get config
    val props = getCassandraConfigSinkPropsFieldSelection
    val taskConfig = new CassandraConfigSink(props)

    val writer = CassandraWriter(taskConfig, context)
    writer.write(testRecords)
    Thread.sleep(2000)
    //check we can get back what we wrote
    val res = session.execute(s"SELECT * FROM $CASSANDRA_KEYSPACE.$TABLE1")
    val rs = res.all().asScala


    //check we the columns we wanted
    rs.foreach({
      r => {
        r.getString("id")
        r.getInt("int_field")
        r.getLong("long_field")
        intercept[IllegalArgumentException] {
          r.getString("float_field")
        }
      }
    })

    rs.size shouldBe testRecords.size
  }
  "should handle incoming decimal fields" in {
    val schema = SchemaBuilder.struct.name("com.data.mountaineer.cassandra.sink,json.decimaltest")
      .version(1)
      .field("id", Schema.INT32_SCHEMA)
      .field("int_field", Schema.INT32_SCHEMA)
      .field("long_field", Schema.INT64_SCHEMA)
      .field("string_field", Schema.STRING_SCHEMA)
      .field("timeuuid_field", Schema.STRING_SCHEMA)
      .field("decimal_field", Decimal.schema(4))
      .build

    val dec = new java.math.BigDecimal("1373563.1563")
    val struct = new Struct(schema)
      .put("id", 1)
      .put("int_field", 12)
      .put("long_field", 12L)
      .put("string_field", "foo")
      .put("timeuuid_field", UUIDs.timeBased().toString)
      .put("decimal_field", dec)

    val sinkRecord = new SinkRecord("topica", 0, null, null, schema, struct, 1)
    val convertUtil = new AnyRef with ConverterUtil
    val json = convertUtil.convertValueToJson(convertR(sinkRecord, Map.empty)).toString
    val str = json.toString
    str.contains("\"decimal_field\":1373563.1563")

  }

  def convertR(record: SinkRecord,
              fields: Map[String, String],
              ignoreFields: Set[String] = Set.empty[String],
              key: Boolean = false) : SinkRecord = {
    val value : Struct = if (key) record.key().asInstanceOf[Struct] else record.value.asInstanceOf[Struct]

    if (fields.isEmpty && ignoreFields.isEmpty) {
      record
    } else {
      val currentSchema = if (key) record.keySchema() else record.valueSchema()
      val builder: SchemaBuilder = SchemaBuilder.struct.name(record.topic() + "_extracted")

      //build a new schema for the fields
      if (fields.nonEmpty) {
        fields.foreach({ case (name, alias) =>
          val extractedSchema = currentSchema.field(name)
          builder.field(alias, extractedSchema.schema())
        })
      } else if (ignoreFields.nonEmpty) {
        val ignored = currentSchema.fields().asScala.filterNot(f => ignoreFields.contains(f.name()))
        ignored.foreach(i => builder.field(i.name, i.schema))
      } else {
        currentSchema.fields().asScala.foreach(f => builder.field(f.name(), f.schema()))
      }

      val extractedSchema = builder.build()
      val newStruct = new Struct(extractedSchema)
      fields.foreach({ case (name, alias) => newStruct.put(alias, value.get(name)) })

      new SinkRecord(record.topic(), record.kafkaPartition(), Schema.STRING_SCHEMA, "key", extractedSchema, newStruct,
        record.kafkaOffset())
    }
  }
}
