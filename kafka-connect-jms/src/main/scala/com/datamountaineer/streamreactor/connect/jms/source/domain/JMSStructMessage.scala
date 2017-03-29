package com.datamountaineer.streamreactor.connect.jms.source.domain

import java.util
import javax.jms.{BytesMessage, MapMessage, Message, TextMessage}

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.source.SourceRecord

import scala.collection.JavaConversions._

/**
  * Created by andrew@datamountaineer.com on 11/03/2017.
  * stream-reactor
  */
  object JMSStructMessage {
    val mapper = new ObjectMapper()
      val schema = getSchema()
      private val sourcePartition =  Map.empty[String, String]
      private val offset = Map.empty[String, String]

      def propStruct() : Schema = {
        SchemaBuilder.struct().name("properties")
        .field("key", Schema.OPTIONAL_STRING_SCHEMA)
        .field("value", Schema.OPTIONAL_STRING_SCHEMA)
        .build()
      }

      def getSchema(): Schema = {
        SchemaBuilder.struct().name("com.datamountaineer.streamreactor.connect.jms")
          .field("message_timestamp", Schema.OPTIONAL_INT64_SCHEMA)
          .field("correlation_id", Schema.OPTIONAL_STRING_SCHEMA)
          .field("redelivered", Schema.OPTIONAL_BOOLEAN_SCHEMA)
          .field("reply_to", Schema.OPTIONAL_STRING_SCHEMA)
          .field("destination", Schema.OPTIONAL_STRING_SCHEMA)
          .field("message_id", Schema.OPTIONAL_STRING_SCHEMA)
          .field("mode", Schema.OPTIONAL_INT32_SCHEMA)
          .field("type", Schema.OPTIONAL_STRING_SCHEMA)
          .field("priority", Schema.OPTIONAL_INT32_SCHEMA)
          .field("bytes_payload", Schema.OPTIONAL_BYTES_SCHEMA)
          .field("properties", SchemaBuilder.array(propStruct()).optional())
          .build()
      }

      def getStruct(target: String, message: Message): SourceRecord = {
        val struct = new Struct(schema)
                        .put("message_timestamp", Option(message.getJMSTimestamp).getOrElse(null))
                        .put("correlation_id",  Option(message.getJMSCorrelationID).getOrElse(null))
                        .put("redelivered",  Option(message.getJMSRedelivered).getOrElse(null))
                        .put("reply_to",  Option(message.getJMSReplyTo).getOrElse(null))
                        .put("destination",  Option(message.getJMSDestination.toString).getOrElse(null))
                        .put("message_id",  Option(message.getJMSMessageID).getOrElse(null))
                        .put("mode",  Option(message.getJMSDeliveryMode).getOrElse(null))
                        .put("type",  Option(message.getJMSType).getOrElse(null))
                        .put("priority",  Option(message.getJMSPriority).getOrElse(null))


        if (message.getPropertyNames.hasMoreElements) {
          struct.put("properties", getProperties(message))
        }
        struct.put("bytes_payload", getPayload(message))
        new SourceRecord(sourcePartition, offset, target, null, null, struct.schema(), struct)
      }

      def getProperties(message: Message) = {
        val map = scala.collection.mutable.Map[String, String]()
        val props = message.getPropertyNames
        while (props.hasMoreElements) {
          val name  = props.nextElement().toString
          val value = message.getStringProperty(name)
          map.put(name, value)
        }
      }

      def getPayload(message: Message): Array[Byte] = {
        message match {
          case t: TextMessage =>  t.getText.getBytes
          case b: BytesMessage => {
            val length = b.getBodyLength.toInt
            val dest = new Array[Byte](length)
            b.readBytes(dest, length)
            dest
          }
          case m: MapMessage => {
            val map = scala.collection.mutable.Map[String, String]()
            val props = m.getMapNames
            while (props.hasMoreElements) {
              val name  = props.nextElement().toString
              val value = m.getStringProperty(name)
              map.put(name, value)
            }
            mapper.writeValueAsBytes(map)
          }
        }
      }
}
