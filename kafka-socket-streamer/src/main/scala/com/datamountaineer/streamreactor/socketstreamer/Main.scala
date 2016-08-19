/**
  * Copyright 2016 Datamountaineer.
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

package com.datamountaineer.streamreactor.socketstreamer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.datamountaineer.streamreactor.socketstreamer.routes.KafkaSocketRoutes
import com.typesafe.scalalogging.slf4j.StrictLogging

import scala.concurrent.duration.DurationInt

object Main extends App with StrictLogging {
  implicit val system = ActorSystem()
  implicit val executor = system.dispatcher
  implicit val materializer = ActorMaterializer()
  implicit val timeout = Timeout(1000.millis)
  logger.info(
    """
      |
      |    ____        __        __  ___                  __        _
      |   / __ \____ _/ /_____ _/  |/  /___  __  ______  / /_____ _(_)___  ___  ___  _____
      |  / / / / __ `/ __/ __ `/ /|_/ / __ \/ / / / __ \/ __/ __ `/ / __ \/ _ \/ _ \/ ___/
      | / /_/ / /_/ / /_/ /_/ / /  / / /_/ / /_/ / / / / /_/ /_/ / / / / /  __/  __/ /
      |/_____/\__,_/\__/\__,_/_/  /_/\____/\__,_/_/ /_/\__/\__,_/_/_/ /_/\___/\___/_/
      |       _____            __        __  _____ __
      |      / ___/____  _____/ /_____  / /_/ ___// /_________  ____ _____ ___  ___  _____
      |      \__ \/ __ \/ ___/ //_/ _ \/ __/\__ \/ __/ ___/ _ \/ __ `/ __ `__ \/ _ \/ ___/
      |     ___/ / /_/ / /__/ ,< /  __/ /_ ___/ / /_/ /  /  __/ /_/ / / / / / /  __/ /
      |    /____/\____/\___/_/|_|\___/\__//____/\__/_/   \___/\__,_/_/ /_/ /_/\___/_/
      |
      |by Andrew Stevenson
    """.stripMargin)

  implicit val configuration = SocketStreamerConfig()

  logger.info(
    s"""
       |System name      : ${configuration.actorSystemName}
       |Kafka brokers    : ${configuration.kafkaBrokers}
       |Zookeepers       : ${configuration.zookeeper}
       |Schema registry  : ${configuration.schemaRegistryUrl}
       |Listening on port : ${configuration.port}
    """.stripMargin)

  implicit val kafkaAvroDecoder = KafkaAvroDecoderFn(configuration)
  val flowRoute = KafkaSocketRoutes()
  val serverBinding = Http().bindAndHandle(interface = "0.0.0.0", port = configuration.port, handler = flowRoute)
}
