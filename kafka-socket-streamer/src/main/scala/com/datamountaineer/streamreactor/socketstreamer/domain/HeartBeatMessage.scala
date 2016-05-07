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

package com.datamountaineer.streamreactor.socketstreamer.domain

import spray.json.DefaultJsonProtocol

/**
  * Created by andrew@datamountaineer.com on 16/03/16. 
  * stream-reactor-websocket-feeder
  */
case class HeartBeatMessage(timestamp: String, system: String, message: String)

trait HeartBeatMessageProtocol extends DefaultJsonProtocol {
  implicit val heartBeatMessageProtocol = jsonFormat3(HeartBeatMessage)
}

