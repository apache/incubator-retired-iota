
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

import akka.actor.ActorRef
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.duration.FiniteDuration

class FeyGenericActorReceiverTest(override val params: Map[String,String] = Map.empty,
                          override val backoff: FiniteDuration = 1.minutes,
                          override val connectTo: Map[String,ActorRef] = Map.empty,
                          override val schedulerTimeInterval: FiniteDuration = 2.seconds,
                          override val orchestrationName: String = "",
                          override val orchestrationID: String = "",
                          override val autoScale: Boolean = false) extends FeyGenericActorReceiver {

  override def customReceive:Receive = {
    case "PROPAGATE" => propagateMessage("PROPAGATE-CALLED")
    case x => log.debug(s"Message not treated: $x")
  }

  override def getJSONString[T](input: T): String = {
    input match{
      case "VALID_JSON" => Utils_JSONTest.create_json_test
      case "INVALID_JSON" => Utils_JSONTest.test_json_schema_invalid
      case "JSON_LOCATION" => Utils_JSONTest.location_test_2
    }
  }

  var count = 0
  var started = false
  var executing = false
  var stopped = false
  var restarted = false

  override def onStart(): Unit = {
    started = true
  }

  override def execute(): Unit = {
    log.info(s"Executing action in ${self.path.name}")
    executing = true
  }

  override def onStop(): Unit = {
    log.info(s"Actor ${self.path.name} stopped.")
    stopped = true
  }

  override def onRestart(reason: Throwable): Unit = {
    restarted = true
  }

}
