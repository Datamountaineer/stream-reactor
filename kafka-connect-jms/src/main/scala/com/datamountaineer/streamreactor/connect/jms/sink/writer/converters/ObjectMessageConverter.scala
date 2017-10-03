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

package com.datamountaineer.streamreactor.connect.jms.sink.writer.converters

import javax.jms.{Message, ObjectMessage, Session}

import org.apache.kafka.connect.data.{Schema, Struct}
import org.apache.kafka.connect.sink.SinkRecord

import scala.collection.JavaConversions._
import scala.collection.JavaConverters._

class ObjectMessageConverter extends JMSMessageConverter {
  override def convert(record: SinkRecord, session: Session): Message = {
    val msg = session.createObjectMessage()
    val value = record.value()
    val schema = record.valueSchema()
    schema.`type`() match {
      case Schema.Type.STRUCT =>
        val struct = value.asInstanceOf[Struct]
        struct.schema().fields().foreach { f =>
          ObjectMessageConverterFn(f.name(), struct.get(f), f.schema(), msg, session)
        }

      case _ => ObjectMessageConverterFn("field", value, schema, msg, session)
    }
    msg
  }
}

object ObjectMessageConverterFn {
  def apply(fieldName: String, value: AnyRef, schema: Schema, msg: ObjectMessage, session: Session): Unit = {
    schema.`type`() match {
      case Schema.Type.BYTES => msg.setObjectProperty(fieldName, value.asInstanceOf[Array[Byte]].toList.asJava)
      case Schema.Type.BOOLEAN => msg.setBooleanProperty(fieldName, value.asInstanceOf[Boolean])
      case Schema.Type.FLOAT32 => msg.setFloatProperty(fieldName, value.asInstanceOf[Float])
      case Schema.Type.FLOAT64 => msg.setDoubleProperty(fieldName, value.asInstanceOf[Double])
      case Schema.Type.INT8 => msg.setByteProperty(fieldName, value.asInstanceOf[Byte])
      case Schema.Type.INT16 => msg.setShortProperty(fieldName, value.asInstanceOf[Short])
      case Schema.Type.INT32 => msg.setIntProperty(fieldName, value.asInstanceOf[Int])
      case Schema.Type.INT64 => msg.setLongProperty(fieldName, value.asInstanceOf[Long])
      case Schema.Type.STRING => msg.setStringProperty(fieldName, value.asInstanceOf[String])
      case Schema.Type.MAP => msg.setObjectProperty(fieldName, value)
      case Schema.Type.ARRAY => msg.setObjectProperty(fieldName, value)
      case Schema.Type.STRUCT =>
        val nestedMsg = session.createObjectMessage()
        val struct = value.asInstanceOf[Struct]
        struct.schema().fields().foreach { f =>
          ObjectMessageConverterFn(f.name(), struct.get(f), f.schema(), nestedMsg, session)
        }
        msg.setObjectProperty(fieldName, nestedMsg)

    }
  }
}