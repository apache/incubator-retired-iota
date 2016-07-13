
// Licensed to the Apache Software Foundation (ASF) under one or more
// contributor license agreements.  See the NOTICE file distributed with
// this work for additional information regarding copyright ownership.
// The ASF licenses this file to You under the Apache License, Version 2.0
// (the "License"); you may not use this file except in compliance with
// the License.  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// ee the License for the specific language governing permissions and
// limitations under the License.

package org.apache.iota.fey.performer

import akka.actor.ActorRef
import org.apache.iota.fey.FeyGenericActor

import scala.collection.immutable.Map
import scala.concurrent.duration._

class RandomDouble(override val params: Map[String, String] = Map.empty,
                   override val backoff: FiniteDuration = 1.minutes,
                   override val connectTo: Map[String, ActorRef] = Map.empty,
                   override val schedulerTimeInterval: FiniteDuration = 30.seconds,
                   override val orchestrationName: String = "",
                   override val orchestrationID: String = "",
                   override val autoScale: Boolean = false) extends FeyGenericActor {

  override def onStart : Unit = {
  }

  override def onStop : Unit = {
  }

  override def onRestart(reason: Throwable) : Unit = {
    // Called after actor is up and running - after self restart
  }

  override def customReceive: Receive = {
    case x => log.debug(s"Untreated $x")
  }

  override def processMessage[T](message: T, sender: ActorRef): Unit = {
  }

  override def execute() : Unit = {
    val rd = scala.util.Random.nextGaussian().toString
    log.debug(rd)
    propagateMessage(rd)
  }

}