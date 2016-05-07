/**
  * Copyright 2015 Datamountaineer.
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  **/

package com.datamountaineer.streamreactor.connect.hbase.writers

import com.datamountaineer.streamreactor.connect.hbase.config.HbaseSettings
import com.datamountaineer.streamreactor.connect.hbase.{GenericRowKeyBuilder, SinkRecordKeyRowKeyBuilder, StructFieldsExtractor, StructFieldsRowKeyBuilder}
import com.typesafe.scalalogging.slf4j.StrictLogging

/**
  * Provides the logic for instantiating the appropriate Hbase writer
  */
object WriterFactoryFn extends StrictLogging {

  /**
    * Creates the Hbase writer which corresponds to the given settings
    *
    * @param settings HbaseSetting for the writer
    * @return
    */
  def apply(settings: HbaseSettings): HbaseWriter = {
    val rowKeyBuilder = settings.rowKey.mode match {
      case HbaseSettings.FieldsRowKeyMode =>
        require(settings.rowKey.keys.nonEmpty, "The fields making up the Hbase row key are missing.")

        new StructFieldsRowKeyBuilder(settings.rowKey.keys)

      case "DEFAULT" =>
        new GenericRowKeyBuilder()
      case "SINK_RECORD" =>
        new SinkRecordKeyRowKeyBuilder()

      case unknown => throw new IllegalArgumentException(s"$unknown is not a recognized running mode.")
    }

    new HbaseWriter(
      settings.columnFamily,
      settings.tableName,
      StructFieldsExtractor(settings.fields.includeAllFields, settings.fields.fieldsMappings),
      rowKeyBuilder)
  }
}
