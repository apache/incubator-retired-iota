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
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.duration.FiniteDuration

class FeyGenericSpringActorTest(override val params: Map[String,String] = Map.empty,
                                override val backoff: FiniteDuration = 1.minutes,
                                override val connectTo: Map[String,ActorRef] = Map.empty,
                                override val schedulerTimeInterval: FiniteDuration = 2.seconds,
                                override val orchestrationName: String = "",
                                override val orchestrationID: String = "",
                                override val autoScale: Boolean = false,
                                override val appContextPath: String) extends FeyGenericSpringActor(params, backoff, connectTo, schedulerTimeInterval, orchestrationName, orchestrationID, autoScale, appContextPath) {

  var contextStarted = false
  var factoryStarted = false
  var received = false

  override def onStart(): Unit = {
    if (appContext.isInstanceOf[ApplicationContext]) contextStarted = true
    if (factory.isInstanceOf[AutowireCapableBeanFactory]) factoryStarted = true
  }

  override def processMessage[T](message: T, sender: ActorRef): Unit = {
  }

  override def execute(): Unit = {
  }

  override def customReceive: Receive = {
    case "SPRING_TEST" => received = true
  }

  override def onStop(): Unit = {
  }

  override def onRestart(reason: Throwable): Unit = {
  }
}
