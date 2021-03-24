package com.landoop.streamreactor.hive.it

import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}
import org.scalatest.wordspec.AnyWordSpec

import java.time.Duration
import java.util.concurrent.TimeUnit
import scala.io.Source

class HiveParquetTest extends AnyWordSpec with Matchers with PersonTestData with Eventually with HiveTests {

  private implicit val patience: PatienceConfig = PatienceConfig(Span(30000, Millis), Span(2000, Millis))

  "Hive" should {
    "write records" in {

      val count = 10000L

      val topic = createTopic()
      val taskDef = Source.fromInputStream(getClass.getResourceAsStream("/hive_sink_task_no_partitions.json")).getLines().mkString("\n")
        .replace("{{TOPIC}}", topic)
        .replace("{{TABLE}}", topic)
        .replace("{{NAME}}", topic)
      postTask(taskDef)

      val producer = stringStringProducer()
      writeRecords(producer, topic, JacksonSupport.mapper.writeValueAsString(person), count)

      producer.close(Duration.ofMillis(TimeUnit.SECONDS.toMillis(30)))

      // we now should have 1000 records in hive which we can test via jdbc
      eventually {
        withConn { conn =>
          val stmt = conn.createStatement
          val rs = stmt.executeQuery(s"select count(*) from $topic")
          rs.next()
          rs.getLong(1) shouldBe count
        }
      }

      stopTask(topic)
    }
  }
}
