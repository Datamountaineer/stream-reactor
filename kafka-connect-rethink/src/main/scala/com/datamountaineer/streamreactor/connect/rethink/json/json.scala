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

package com.datamountaineer.streamreactor.connect.rethink.json

/**
  * Created by andrew@datamountaineer.com on 22/09/16. 
  * stream-reactor
  */
import org.json4s.{DefaultFormats, _}
import org.json4s.native.JsonMethods._

object Json {
  implicit val formats = DefaultFormats

  def fromJson[T <: Product:Manifest](json: String): T = {
    parse(json).extract[T]
  }
}