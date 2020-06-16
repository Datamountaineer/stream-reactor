
/*
 * Copyright 2020 Lenses.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.lenses.streamreactor.connect.aws.s3.sink

import com.typesafe.scalalogging.LazyLogging
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.sink.SinkRecord

import scala.collection.JavaConverters._

object HeaderConverter {

  def apply(record: SinkRecord): Map[String, String] = record.headers().asScala.map(header => (header.key() -> headerValueToString(header.value()))).toMap

  def headerValueToString(value: Any): String = {
    value match {
      case stringVal: String => stringVal
      case intVal: Int => String.valueOf(intVal)
      case longVal: Long => String.valueOf(longVal)
      case otherVal => sys.error(s"Unsupported header value type $otherVal:${otherVal.getClass.getCanonicalName}")
      //case value: Integer => value.toString
    }
  }


}

object KeyConverter extends LazyLogging {
  def apply(record: SinkRecord): Option[Struct] = keyValueToString(record.key)

  def keyValueToString(value: Any) = {
    value match {
      case null => None
      case stringVal: String => Some(StringValueConverter.convert(stringVal))
      case intVal: Int => Some(StringValueConverter.convert(String.valueOf(intVal)))
      case longVal: Long => Some(StringValueConverter.convert(String.valueOf(longVal)))
      case struct: Struct => Some(StructValueConverter.convert(struct))
      case bytes: Array[Byte] => Some(ByteArrayValueConverter.convert(bytes))
      case other => logger.warn(s"Unsupported record $other:${other.getClass.getCanonicalName}")
        None
    }
  }
}

object ValueConverter {
  def apply(record: SinkRecord): Struct = record.value match {
    case struct: Struct => StructValueConverter.convert(struct)
    case map: Map[_, _] => MapValueConverter.convert(map)
    case map: java.util.Map[_, _] => MapValueConverter.convert(map.asScala.toMap)
    case string: String => StringValueConverter.convert(string)
    case bytes: Array[Byte] => ByteArrayValueConverter.convert(bytes)
    case other => sys.error(s"Unsupported record $other:${other.getClass.getCanonicalName}")
  }
}

trait ValueConverter[T] {
  def convert(value: T): Struct
}

object StructValueConverter extends ValueConverter[Struct] {
  override def convert(struct: Struct): Struct = struct
}

object MapValueConverter extends ValueConverter[Map[_, _]] {
  def convertValue(value: Any, key: String, builder: SchemaBuilder): Any = {
    value match {
      case s: String =>
        builder.field(key, Schema.OPTIONAL_STRING_SCHEMA)
        s
      case l: Long =>
        builder.field(key, Schema.OPTIONAL_INT64_SCHEMA)
        l
      case i: Int =>
        builder.field(key, Schema.OPTIONAL_INT64_SCHEMA)
        i.toLong
      case b: Boolean =>
        builder.field(key, Schema.OPTIONAL_BOOLEAN_SCHEMA)
        b
      case f: Float =>
        builder.field(key, Schema.OPTIONAL_FLOAT64_SCHEMA)
        f.toDouble
      case d: Double =>
        builder.field(key, Schema.OPTIONAL_FLOAT64_SCHEMA)
        d
      case innerMap: java.util.Map[_, _] =>
        val innerStruct = convert(innerMap.asScala.toMap, true)
        builder.field(key, innerStruct.schema())
        innerStruct

      case innerMap: Map[_, _] =>
        val innerStruct = convert(innerMap, true)
        builder.field(key, innerStruct.schema())
        innerStruct
    }
  }

  def convert(map: Map[_, _], optional: Boolean): Struct = {
    val builder = SchemaBuilder.struct()
    val values = map.map { case (k, v) =>
      val key = k.toString
      val value = convertValue(v, key, builder)
      key -> value
    }.toList
    if (optional) builder.optional()
    val schema = builder.build
    val struct = new Struct(schema)
    values.foreach { case (key, value) =>
      struct.put(key, value)
    }
    struct
  }

  override def convert(map: Map[_, _]): Struct = convert(map, false)
}

object StringValueConverter extends ValueConverter[String] {

  val TextFieldName = "a"
  val TextFieldSchemaName = "struct"
  val TextFieldOptionalStringSchema = Schema.OPTIONAL_STRING_SCHEMA

  override def convert(string: String): Struct = {
    val schema = SchemaBuilder.struct().field(TextFieldName, TextFieldOptionalStringSchema).name(TextFieldSchemaName).build()
    new Struct(schema).put(TextFieldName, string)
  }
}

object ByteArrayValueConverter extends ValueConverter[Array[Byte]] {

  val BytesFieldName = "b"
  val BytesSchemaName = "struct"
  val OptionalBytesSchema = Schema.OPTIONAL_BYTES_SCHEMA

  override def convert(bytes: Array[Byte]): Struct = {
    val schema = SchemaBuilder.struct().field(BytesFieldName, OptionalBytesSchema).name(BytesSchemaName).build()
    new Struct(schema).put(BytesFieldName, bytes)
  }
}