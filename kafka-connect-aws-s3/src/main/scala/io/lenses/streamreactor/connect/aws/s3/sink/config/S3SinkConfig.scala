
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

package io.lenses.streamreactor.connect.aws.s3.sink.config

import com.datamountaineer.kcql.Kcql
import io.lenses.streamreactor.connect.aws.s3.config.Format.Json
import io.lenses.streamreactor.connect.aws.s3.config.S3FlushSettings.{defaultFlushCount, defaultFlushInterval, defaultFlushSize}
import io.lenses.streamreactor.connect.aws.s3.config.{FormatSelection, S3Config, S3ConfigDefBuilder, S3WriteModeSettings}
import io.lenses.streamreactor.connect.aws.s3.model.{BucketAndPrefix, LocalLocation, PartitionSelection, S3WriteMode}
import io.lenses.streamreactor.connect.aws.s3.sink._

import scala.collection.JavaConverters._

object S3SinkConfig {

  def apply(props: Map[String, String]): S3SinkConfig = S3SinkConfig(
    S3Config(props),
    SinkBucketOptions(props)
  )

}

case class S3SinkConfig(
                         s3Config: S3Config,
                         bucketOptions: Set[SinkBucketOptions] = Set.empty
                       )

object SinkBucketOptions {

  def apply(props: Map[String, String]): Set[SinkBucketOptions] = {

    val config = S3ConfigDefBuilder(props.asJava)

    config.getKCQL.map { kcql: Kcql =>

      val formatSelection: FormatSelection = Option(kcql.getStoredAs) match {
        case Some(format: String) => FormatSelection(format)
        case None => FormatSelection(Json, Set.empty)
      }

      val partitionSelection = PartitionSelection(kcql)
      val namingStrategy = partitionSelection match {
        case Some(partSel) => new PartitionedS3FileNamingStrategy(formatSelection, partSel)
        case None => new HierarchicalS3FileNamingStrategy(formatSelection)
      }

      SinkBucketOptions(
        kcql.getSource,
        BucketAndPrefix(kcql.getTarget),
        formatSelection = formatSelection,
        fileNamingStrategy = namingStrategy,
        partitionSelection = partitionSelection,
        commitPolicy = config.commitPolicy(kcql),
        writeMode = config.s3WriteMode(),
        localBuildDirectory = config.s3LocalBuildDirectory(props.get("name"))
      )
    }

  }

}

case class SinkBucketOptions(
                              sourceTopic: String,
                              bucketAndPrefix: BucketAndPrefix,
                              formatSelection: FormatSelection,
                              fileNamingStrategy: S3FileNamingStrategy,
                              partitionSelection: Option[PartitionSelection] = None,
                              commitPolicy: CommitPolicy = DefaultCommitPolicy(Some(defaultFlushSize), Some(defaultFlushInterval), Some(defaultFlushCount)),
                              writeMode: S3WriteMode = S3WriteModeSettings.defaultWriteMode,
                              localBuildDirectory: Option[LocalLocation] = None
                            )
