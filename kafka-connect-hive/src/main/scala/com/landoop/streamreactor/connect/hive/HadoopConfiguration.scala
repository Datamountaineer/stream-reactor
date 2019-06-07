package com.landoop.streamreactor.connect.hive

import java.io.File

import org.apache.kafka.common.config.AbstractConfig
import org.apache.kafka.common.config.ConfigException

case class HadoopConfiguration(hdfsDir: Option[String], hiveDir: Option[String])

trait HadoopConfigurationConstants {
  def CONNECTOR_PREFIX: String

  val HdfsConfigDirKey = s"$CONNECTOR_PREFIX.hdfs.conf.dir"
  val HdfsConfigDirDoc = "The Hadoop configuration directory."
  val HdfsConfigDirDefault: String = null
  val HdfsConfigDirDisplay = "HDFS Config Folder"

  val HiveConfigDirKey = s"$CONNECTOR_PREFIX.hive.conf.dir"
  val HiveConfigDirDoc = "The Hive configuration directory."
  val HiveConfigDirDefault: String = null
  val HiveConfigDirDisplay = "Hive Config Folder"
}

object HadoopConfiguration {
  val Empty = HadoopConfiguration(None, None)

  def from(config: AbstractConfig, constants: HadoopConfigurationConstants): HadoopConfiguration = {
    val hdfs = Option(config.getString(constants.HdfsConfigDirKey))
    val hive = Option(config.getString(constants.HiveConfigDirKey))

    def validate(dir: String, key: String): Unit = {
      val folder = new File(dir)
      if (!folder.exists() || !folder.isDirectory) {
        throw new ConfigException(s"Invalid configuration for [$key]. Folder can not be found")
      }
    }
    hdfs.foreach(validate(_, constants.HdfsConfigDirKey))
    hive.foreach(validate(_, constants.HiveConfigDirKey))
    HadoopConfiguration(hdfs, hive)
  }
}
