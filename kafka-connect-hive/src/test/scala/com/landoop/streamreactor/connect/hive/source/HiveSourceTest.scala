package com.landoop.streamreactor.connect.hive.source

import java.io.InputStreamReader
import java.util

import cats.data.NonEmptyList
import com.landoop.streamreactor.connect.hive._
import com.landoop.streamreactor.connect.hive.sink.HiveSink
import com.landoop.streamreactor.connect.hive.sink.config.{HiveSinkConfig, TableOptions}
import com.landoop.streamreactor.connect.hive.sink.staging.DefaultCommitPolicy
import com.landoop.streamreactor.connect.hive.source.config.{HiveSourceConfig, ProjectionField, SourceTableOptions}
import com.landoop.streamreactor.connect.hive.source.offset.{HiveSourceInitOffsetStorageReader, MockOffsetStorageReader}
import com.opencsv.CSVReaderHeaderAware
import com.typesafe.scalalogging.StrictLogging
import org.apache.hadoop.fs.Path
import org.apache.hadoop.hive.metastore.api.Database
import org.apache.kafka.connect.data.{SchemaBuilder, Struct}
import org.apache.kafka.connect.source.{SourceRecord, SourceTaskContext}
import org.apache.kafka.connect.storage.OffsetStorageReader
import org.mockito.MockitoSugar.{mock, when}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.JavaConverters._
import scala.util.Try

class HiveSourceTest extends AnyWordSpec with Matchers with HiveTestConfig with StrictLogging {

  val dbname = "source_test2"

  Try {
    client.dropDatabase(dbname, true, true)
  }

  Try {
    client.dropDatabase(dbname)
  }

  Try {
    client.createDatabase(new Database(dbname, null, s"/user/hive/warehouse/$dbname", new util.HashMap()))
  }

  val schema = SchemaBuilder.struct()
    .field("name", SchemaBuilder.string().required().build())
    .field("title", SchemaBuilder.string().optional().build())
    .field("salary", SchemaBuilder.float64().optional().build())
    .build()

  def populateEmployees(table: String, partitions: Seq[PartitionField] = Nil, dropTableFirst: Boolean = true, offsetAdd: Int = 0): Unit = {
    if (dropTableFirst) Try {
      client.dropTable(dbname, table, true, true)
    }

    val sinkConfig = HiveSinkConfig(DatabaseName(dbname), tableOptions = Set(
      TableOptions(TableName(table), Topic("mytopic"), dropTableFirst, dropTableFirst, partitions = partitions)
    ),
      kerberos = None,
      hadoopConfiguration = HadoopConfiguration.Empty
    )

    val sink = HiveSink.from(TableName(table), sinkConfig)

    val users = List(
      new Struct(schema).put("name", "sam").put("title", "mr").put("salary", 100.43),
      new Struct(schema).put("name", "laura").put("title", "ms").put("salary", 429.06),
      new Struct(schema).put("name", "stef").put("title", "ms").put("salary", 329.06),
      new Struct(schema).put("name", "andrew").put("title", "mr").put("salary", 529.06),
      new Struct(schema).put("name", "ant").put("title", "mr").put("salary", 629.06),
      new Struct(schema).put("name", "tom").put("title", "miss").put("salary", 395.44)
    )
    users.zipWithIndex.foreach { case (user, k) =>
      sink.write(user, TopicPartitionOffset(Topic("mytopic"), 1, Offset(k + offsetAdd)))
    }
    sink.close()
  }


  "hive source" should {

    "read from a non partitioned table" in {

      val table = "employees"
      populateEmployees(table)

      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map.empty))
      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"))
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)
      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      source.toList.map(_.value.asInstanceOf[Struct]).map(StructUtils.extractValues) shouldBe
        List(Vector("sam", "mr", 100.43), Vector("laura", "ms", 429.06), Vector("stef", "ms", 329.06), Vector("andrew", "mr", 529.06), Vector("ant", "mr", 629.06), Vector("tom", "miss", 395.44))
    }

    "apply a projection to the read of a non-partitioned table" in {

      val table = "employees"
      populateEmployees(table)

      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map.empty))
      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"), projection = Some(NonEmptyList.of(ProjectionField("title", "title"), ProjectionField("name", "name"))))
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)
      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      val records = source.toList
      records.head.valueSchema.fields().asScala.map(_.name) shouldBe Seq("title", "name")
      records.map(_.value.asInstanceOf[Struct]).map(StructUtils.extractValues).toSet shouldBe
        Set(Vector("mr", "ant"), Vector("miss", "tom"), Vector("ms", "stef"), Vector("ms", "laura"), Vector("mr", "andrew"), Vector("mr", "sam"))
    }

    "apply a projection with aliases to the read of a non-partitioned table" in {

      val table = "employees"
      populateEmployees(table)

      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map.empty))
      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"), projection = Some(NonEmptyList.of(ProjectionField("title", "salutation"), ProjectionField("name", "name"))))
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)
      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      val records = source.toList
      records.head.valueSchema.fields().asScala.map(_.name) shouldBe Seq("salutation", "name")
      records.map(_.value.asInstanceOf[Struct]).map(StructUtils.extractValues).toSet shouldBe
        Set(Vector("mr", "ant"), Vector("miss", "tom"), Vector("ms", "stef"), Vector("ms", "laura"), Vector("mr", "andrew"), Vector("mr", "sam"))
    }

    "read from a partitioned table" in {

      val table = "employees_partitioned"
      populateEmployees(table, partitions = Seq(PartitionField("title")))

      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map.empty))
      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"))
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)
      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      source.map(_.value.asInstanceOf[Struct]).map(StructUtils.extractValues).toSet shouldBe
        Set(Vector("tom", 395.44, "miss"), Vector("sam", 100.43, "mr"), Vector("andrew", 529.06, "mr"), Vector("ant", 629.06, "mr"), Vector("laura", 429.06, "ms"), Vector("stef", 329.06, "ms"))
    }

    "apply a projection to the read of a partitioned table" in {

      val table = "employees_partitioned"
      populateEmployees(table, partitions = Seq(PartitionField("title")))

      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map.empty))
      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"), projection = Some(NonEmptyList.of(ProjectionField("title", "title"), ProjectionField("name", "name"))))
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)
      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      source.map(_.value.asInstanceOf[Struct]).map(StructUtils.extractValues).toSet shouldBe
        Set(Vector("mr", "ant"), Vector("miss", "tom"), Vector("ms", "stef"), Vector("ms", "laura"), Vector("mr", "andrew"), Vector("mr", "sam"))
    }

    "adhere to a LIMIT" in {
      val table = "employees"
      populateEmployees(table)

      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map.empty))
      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"), limit = 3)
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)

      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      source.toList.map(_.value.asInstanceOf[Struct]).map(StructUtils.extractValues) shouldBe
        List(Vector("sam", "mr", 100.43), Vector("laura", "ms", 429.06), Vector("stef", "ms", 329.06))
    }

    "set offset and partition on each record" in {
      val table = "employees"
      populateEmployees(table)

      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map.empty))
      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"))
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)

      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      val list = source.toList
      list.head.sourcePartition() shouldBe
        Map("db" -> dbname, "table" -> table, "topic" -> "mytopic", "path" -> s"hdfs://namenode:8020/user/hive/warehouse/$dbname/$table/streamreactor_mytopic_1_5").asJava

      list.head.sourceOffset() shouldBe
        Map("rownum" -> "0").asJava

      list.last.sourcePartition() shouldBe
        Map("db" -> dbname, "table" -> table, "topic" -> "mytopic", "path" -> s"hdfs://namenode:8020/user/hive/warehouse/$dbname/$table/streamreactor_mytopic_1_5").asJava

      list.last.sourceOffset() shouldBe
        Map("rownum" -> "5").asJava
    }

    "skip records based on offset storage" in {
      val table = "employees3"
      populateEmployees(table)

      val sourcePartition = SourcePartition(
        DatabaseName(dbname),
        TableName(table),
        Topic("mytopic"),
        new Path(s"hdfs://namenode:8020/user/hive/warehouse/$dbname/$table/streamreactor_mytopic_1_5")
      )
      val sourceOffset = SourceOffset(1)
      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map(sourcePartition -> sourceOffset)))

      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"))
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)
      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      source.toList.map(_.value.asInstanceOf[Struct]).map(StructUtils.extractValues) shouldBe
        List(Vector("stef", "ms", 329.06), Vector("andrew", "mr", 529.06), Vector("ant", "mr", 629.06), Vector("tom", "miss", 395.44))
    }

    "skip records based on offset storage before applying limit" in {
      val table = "employees3"
      populateEmployees(table)

      val sourcePartition = SourcePartition(
        DatabaseName(dbname),
        TableName(table),
        Topic("mytopic"),
        new Path(s"hdfs://namenode:8020/user/hive/warehouse/$dbname/$table/streamreactor_mytopic_1_5")
      )
      val sourceOffset = SourceOffset(1)
      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map(sourcePartition -> sourceOffset)))

      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"), limit = 2)
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)
      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      source.toList.map(_.value.asInstanceOf[Struct]).map(StructUtils.extractValues) shouldBe
        List(Vector("stef", "ms", 329.06), Vector("andrew", "mr", 529.06))
    }

    "skip file if offset is beyond file size" in {
      val table = "employees3"
      populateEmployees(table)

      val sourcePartition = SourcePartition(
        DatabaseName(dbname),
        TableName(table),
        Topic("mytopic"),
        new Path(s"hdfs://namenode:8020/user/hive/warehouse/$dbname/$table/streamreactor_mytopic_1_5")
      )
      val sourceOffset = SourceOffset(44)
      val reader = new HiveSourceInitOffsetStorageReader(new MockOffsetStorageReader(Map(sourcePartition -> sourceOffset)))

      val sourceConfig = HiveSourceConfig(DatabaseName(dbname), tableOptions = Set(
        SourceTableOptions(TableName(table), Topic("mytopic"), limit = 2)
      ), kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty)
      val source = new HiveSource(DatabaseName(dbname), TableName(table), Topic("mytopic"), reader, sourceConfig)
      source.toList.isEmpty shouldBe true
    }

    "discover partitions after the source has started" in {

      val table = "employees"
      val tblObject = client.getTable(dbname, table)

      client.dropTable(dbname, table, true, true)
      client.createTable(tblObject)

      val props = Map(
              "connect.hive.database.name" -> dbname,
              "connect.hive.metastore" -> "thrift",
              "connect.hive.metastore.uris" -> "thrift://namenode:9083",
              "connect.hive.fs.defaultFS" -> "hdfs://namenode:8020",
              "connect.hive.kcql" -> s"insert into mytopic select name, title, salary from $table",
              "connect.hive.refresh.frequency" -> "1",
      )

      val sourceTaskContext: SourceTaskContext = mock[SourceTaskContext]
      val reader = mock[OffsetStorageReader]
      when(sourceTaskContext.offsetStorageReader()).thenReturn(reader)

      val sourceTask = new HiveSourceTask()
      sourceTask.initialize(sourceTaskContext)
      sourceTask.start(props.asJava)

      val res0 = sourceTask.poll()
      res0.size shouldBe (0)

      populateEmployees(table)

      Thread.sleep(1200)
      val res2 = sourceTask.poll()
      res2.size shouldBe (6)

      populateEmployees(table, dropTableFirst = false, offsetAdd = 6)
      populateEmployees(table, dropTableFirst = false, offsetAdd = 12)

      Thread.sleep(1200)
      val res3 = sourceTask.poll()
      res3.size shouldBe (12)

      Thread.sleep(1200)
      val res4 = sourceTask.poll()
      res4.size shouldBe (0)

      //populateEmployees(table, dropTableFirst = false, offsetAdd = 18)

      //Thread.sleep(1200)
      //val res4 = sourceTask.poll()
      //res4.size shouldBe (6)

    }

    "create test data" in {

      val table = "babynames2"

      Try {
        client.dropTable(dbname, table, true, true)
      }

      val sinkConfig = HiveSinkConfig(DatabaseName(dbname), tableOptions = Set(
        TableOptions(
          TableName(table),
          Topic("babynames2"),
          true,
          true,
          partitions = Nil,
          commitPolicy = DefaultCommitPolicy(None, None, Some(50))
        )
      ),
        kerberos = None,
        hadoopConfiguration = HadoopConfiguration.Empty
      )

      val sink = HiveSink.from(TableName(table), sinkConfig)

      val schema = SchemaBuilder.struct()
        .field("id", SchemaBuilder.int32().optional().build())
        .field("year", SchemaBuilder.int32().optional().build())
        .field("name", SchemaBuilder.string().optional().build())
        .field("percent", SchemaBuilder.float32().optional().build())
        .field("sex", SchemaBuilder.string().optional().build())
        .build()


      val resource = getClass().getResourceAsStream("/baby-names.csv")
      val reader = new CSVReaderHeaderAware(new InputStreamReader(resource))

      var i = 0
      val iter = reader.iterator()
      while(iter.hasNext && i < 200) {
        val e = iter.next()
        val struct = new Struct(schema)
          .put("id", i)
          .put("year", e(0).toInt)
          .put("name", e(1))
          .put("percent", e(2).toFloat)
          .put("sex", e(3))
        sink.write(struct, TopicPartitionOffset(Topic("babynames2"), 1, Offset(i)))
        //if(i % 1000 == 0) {
          sink.commit()
        //}
        i += 1
      }


      sink.close()

      val props = Map(
        "connect.hive.database.name" -> dbname,
        "connect.hive.metastore" -> "thrift",
        "connect.hive.metastore.uris" -> "thrift://namenode:9083",
        "connect.hive.fs.defaultFS" -> "hdfs://namenode:8020",
        "connect.hive.kcql" -> s"insert into mytopic select * from $table",
        "connect.hive.refresh.frequency" -> "1",
        "connect.hive.poll.size" -> "10",
      )
      val sourceTaskContext: SourceTaskContext = mock[SourceTaskContext]
      val offsetReader = new MockOffsetStorageReader(Map())
      when(sourceTaskContext.offsetStorageReader()).thenReturn(offsetReader)
      val sourceTask = new HiveSourceTask()
      sourceTask.initialize(sourceTaskContext)
      sourceTask.start(props.asJava)
      val polled1 = sourceTask.poll()
      polled1.size() should be (10)

      val offsets: Map[SourcePartition, SourceOffset] = offsetsFromResult(polled1)

      val props2 = Map(
        "connect.hive.database.name" -> dbname,
        "connect.hive.metastore" -> "thrift",
        "connect.hive.metastore.uris" -> "thrift://namenode:9083",
        "connect.hive.fs.defaultFS" -> "hdfs://namenode:8020",
        "connect.hive.kcql" -> s"insert into mytopic select * from $table",
        "connect.hive.refresh.frequency" -> "1",
        "connect.hive.poll.size" -> "200",
      )

      val sourceTaskContext2: SourceTaskContext = mock[SourceTaskContext]
      val offsetReader2 = new MockOffsetStorageReader(offsets)
      when(sourceTaskContext2.offsetStorageReader()).thenReturn(offsetReader2)
      val sourceTask2 = new HiveSourceTask()
      sourceTask2.initialize(sourceTaskContext2)
      sourceTask2.start(props2.asJava)
      val polled2 = sourceTask2.poll()
      polled2.size() should be (190)
      sourceTask2.poll().size() should be (0)

      val updatedOffsets: Map[SourcePartition, SourceOffset] = offsets ++ offsetsFromResult(polled2)

      val sourceTaskContext3: SourceTaskContext = mock[SourceTaskContext]
      val offsetReader3 = new MockOffsetStorageReader(updatedOffsets)
      when(sourceTaskContext3.offsetStorageReader()).thenReturn(offsetReader3)
      val sourceTask3 = new HiveSourceTask()
      sourceTask3.initialize(sourceTaskContext3)
      sourceTask3.start(props2.asJava)
      val interpolled = sourceTask3.poll()
      interpolled.size() should be (0)
      sourceTask3.poll().size() should be (0)

/*
      val records : mutable.MutableList[SourceRecord] = mutable.MutableList()
      var curSource = List.empty[SourceRecord]
      while({
        curSource = sourceTask.poll().asScala.toList
        curSource.nonEmpty
      }) {
        records.++=(curSource)
      }

      records.size should be (i)
      */
    }
  }

  private def offsetsFromResult(polled1: util.List[SourceRecord]) = {
    polled1.asScala.map(e => {

      val offset = toSourceOffset(e.sourceOffset().asScala.toMap.asInstanceOf[Map[String, AnyRef]])
      val partition = toSourcePartition(e.sourcePartition().asScala.toMap.asInstanceOf[Map[String, AnyRef]])

      partition -> offset
    }
    )
      .groupBy(_._1)
      .map {
        p => p._2.maxBy(_._2.rowNumber)
      }
  }
}
