package com.datamountaineer.streamreactor.connect.ftp

import java.time.Duration
import java.util

import com.datamountaineer.streamreactor.connect.ftp.SourceRecordProducers.SourceRecordProducer
import com.typesafe.scalalogging.slf4j.StrictLogging
import org.apache.kafka.connect.data.{Schema, SchemaBuilder, Struct}
import org.apache.kafka.connect.errors.ConnectException
import org.apache.kafka.connect.source.{SourceRecord, SourceTask}
import org.apache.kafka.connect.storage.OffsetStorageReader

import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

// holds functions that translate a file meta+body into a source record
object SourceRecordProducers {
  type SourceRecordProducer = (ConnectFileMetaDataStore, String, FileMetaData, FileBody) => SourceRecord

  val fileInfoSchema = SchemaBuilder.struct().name("com.datamountaineer.streamreactor.connect.ftp.FileInfo")
    .field("name", Schema.STRING_SCHEMA)
    .field("offset", Schema.INT64_SCHEMA)
    .build()

  def stringKeyRecord(store: ConnectFileMetaDataStore, topic: String, meta: FileMetaData, body: FileBody): SourceRecord =
    new SourceRecord(
      store.fileMetasToConnectPartition(meta), // source part
      store.fileMetasToConnectOffset(meta), // source off
      topic, //topic
      Schema.STRING_SCHEMA, // key sch
      meta.attribs.path, // key
      Schema.BYTES_SCHEMA, // val sch
      body.bytes // val
    )

  def structKeyRecord(store: ConnectFileMetaDataStore, topic: String, meta: FileMetaData, body: FileBody): SourceRecord = {
    new SourceRecord(
      store.fileMetasToConnectPartition(meta), // source part
      store.fileMetasToConnectOffset(meta), // source off
      topic, //topic
      fileInfoSchema, // key sch
      new Struct(fileInfoSchema)
        .put("name",meta.attribs.path)
        .put("offset",body.offset),
      Schema.BYTES_SCHEMA, // val sch
      body.bytes // val
    )
  }
}

// bridges between the Connect reality and the (agnostic) FtpMonitor reality.
// logic could have been in FtpSourceTask directly, but FtpSourceTask's imperative nature made things a bit ugly,
// from a Scala perspective.
class FtpSourcePoller(cfg: FtpSourceConfig, offsetStorage: OffsetStorageReader) extends StrictLogging {
  val metaStore = new ConnectFileMetaDataStore(offsetStorage)

  val monitor2topic = cfg.ftpMonitorConfigs
    .map(monitorCfg => (MonitoredPath(monitorCfg.path, monitorCfg.tail), monitorCfg.topic)).toMap

  val pollDuration = Duration.parse(cfg.getString(FtpSourceConfig.RefreshRate))
  val maxBackoff = Duration.parse(cfg.getString(FtpSourceConfig.MaxBackoff))

  var backoff = new ExponentialBackOff(pollDuration, maxBackoff)

  val ftpMonitor = {val (host,optPort) = cfg.address
    new FtpMonitor(
    FtpMonitorSettings(
      host,
      optPort,
      cfg.getString(FtpSourceConfig.User),
      cfg.getPassword(FtpSourceConfig.Password).value,
      Some(Duration.parse(cfg.getString(FtpSourceConfig.FileMaxAge))),
      monitor2topic.keys.toSeq,
      cfg.timeoutMs()),
    metaStore)}

  val recordMaker:SourceRecordProducer = cfg.keyStyle match {
    case KeyStyle.String => SourceRecordProducers.stringKeyRecord
    case KeyStyle.Struct => SourceRecordProducers.structKeyRecord
  }

  val recordConverter:SourceRecordConverter = cfg.sourceRecordConverter

  def poll(): Seq[SourceRecord] = {
    if (backoff.passed) {
      logger.info("poll")
      ftpMonitor.poll() match {
        case Success(fileChanges) =>
          backoff = backoff.nextSuccess
          fileChanges.map({ case (meta, body, w) =>
            logger.info(s"got some fileChanges: ${meta.attribs.path}")
            metaStore.set(meta.attribs.path, meta)
            val topic = monitor2topic(w)
            recordMaker(metaStore, topic, meta, body)
          }).flatMap(recordConverter.convert(_).asScala)
        case Failure(err) =>
          logger.warn(s"ftp monitor failed: $err")
          backoff = backoff.nextFailure
          logger.info(s"let's backoff ${backoff.remaining}")
          Seq[SourceRecord]()
      }
    } else {
      Thread.sleep(1000)
      Seq[SourceRecord]()
    }
  }
}

class FtpSourceTask extends SourceTask with StrictLogging {
  var poller: Option[FtpSourcePoller] = None

  override def stop(): Unit = {
    logger.info("stop")
    poller = None
  }

  override def start(props: util.Map[String, String]): Unit = {
    logger.info("start")
    val sourceConfig = new FtpSourceConfig(props)
    sourceConfig.ftpMonitorConfigs.foreach(cfg => {
      val style = if (cfg.tail) "tail" else "updates"
      logger.info(s"config tells us to track the $style of files in `${cfg.path}` to topic `${cfg.topic}")
    })
    poller = Some(new FtpSourcePoller(sourceConfig, context.offsetStorageReader))
  }

  override def version(): String = getClass.getPackage.getImplementationVersion

  override def poll(): util.List[SourceRecord] = poller match {
    case Some(p) => p.poll().asJava
    case None => throw new ConnectException("FtpSourceTask is not initialized but it is polled")
  }
}
