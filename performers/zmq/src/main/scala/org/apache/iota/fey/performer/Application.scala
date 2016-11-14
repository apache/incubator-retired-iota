/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.iota.fey.performer

import akka.actor.{ActorSystem, Props}
import org.apache.iota.fey.FeyGenericActor.PROCESS
import scala.concurrent.duration._

object Application extends App {

  //print("Starting")

  //implicit val system = ActorSystem("ZMQ-RUN")

  //val publish = system.actorOf(Props(classOf[ZMQPublisher], Map.empty,1.minutes, Map.empty, 1.seconds,"","",false ), name = "PUBLISH")

  //publish ! PROCESS("Publish it")

  //  val subscribe = system.actorOf(Props(classOf[ZMQPublisher], Map.empty,1.minutes, Map.empty, 1.seconds,"","",false ), name = "SUBSCRIBE")
  //
  //  subscribe ! PROCESS("Subscribe to it")
}
