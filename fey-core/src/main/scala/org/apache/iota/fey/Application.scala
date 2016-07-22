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

package org.apache.iota.fey

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.can.Http

import scala.concurrent.duration._

object Application extends App {

  CONFIG.loadUserConfiguration(if(args.length == 1) args(0) else "")

  SYSTEM_ACTORS

}

object FEY_SYSTEM{
  implicit val system = ActorSystem("FEY-MANAGEMENT-SYSTEM")
}

object SYSTEM_ACTORS{

  import FEY_SYSTEM._

  FEY_CORE_ACTOR

  FEY_CORE_ACTOR.actorRef ! FeyCore.START

  val service = system.actorOf(Props[MyServiceActor], name = "FEY_REST_API")

  implicit val timeout = Timeout(800.seconds)
  IO(Http) ? Http.Bind(SYSTEM_ACTORS.service, interface = "0.0.0.0", port = 16666)

}

object FEY_CORE_ACTOR{
  import FEY_SYSTEM._
  val actorRef = system.actorOf(FeyCore.props, name = "FEY-CORE")
}

object FEY_MONITOR{
  import FEY_SYSTEM._

  val actorRef = system.actorOf(Props(new Monitor(Monitor.events)), "FEY-MONITOR")
}
