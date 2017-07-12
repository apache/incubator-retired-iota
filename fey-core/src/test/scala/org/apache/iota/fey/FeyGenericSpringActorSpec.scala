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

import akka.actor.{ActorRef, Props}
import akka.testkit.{EventFilter, TestActorRef, TestProbe}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class FeyGenericSpringActorSpec extends BaseAkkaSpec {
  val parent = TestProbe("GENERIC-PARENT")
  val monitor = TestProbe("MONITOR-GENERIC")
  val connectTB = TestProbe("CONNECT_TO")
  val genericRef: TestActorRef[FeyGenericSpringActorTest] = TestActorRef[FeyGenericSpringActorTest]( Props(new FeyGenericSpringActorTest(Map.empty, 1.seconds,
    Map("CONNECTED" -> connectTB.ref),300.milliseconds,"MY-ORCH", "MY-ORCH", false, "classpath:FeyApplicationContext.xml"){
    override private[fey] val monitoring_actor = monitor.ref
  }),parent.ref, "GENERIC-TEST")
  var genericState:FeyGenericSpringActorTest = genericRef.underlyingActor
  val path = genericRef.path.toString
  var latestBackoff: Long = 0

  "Creating a GenericSpringActor using the TestContext" should {
    "result in a loaded context" in {
      genericState.contextStarted should be(true)
    }
    "result in a new bean factory" in {
      genericState.factoryStarted should be(true)
    }
  }
}
