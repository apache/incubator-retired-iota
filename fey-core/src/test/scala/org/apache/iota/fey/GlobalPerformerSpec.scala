
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

import play.api.libs.json._
import akka.actor.{ActorRef, PoisonPill, Props}
import akka.testkit.{EventFilter, TestActorRef, TestProbe}

import scala.concurrent.duration.DurationInt

class GlobalPerformerSpec extends BaseAkkaSpec{

  // Orchestration that is the parent of ensembles and global
  val orch_id = "GLOBAL-ORCH"
  val parent = TestProbe("CORE")

  val monitor = TestProbe()

  val orchRef = TestActorRef[Orchestration]( Props(new Orchestration("TESTING-GLOBAL",orch_id,"123124324324"){
    override val monitoring_actor = monitor.ref
  }), parent.ref, orch_id)
  val orchState = orchRef.underlyingActor

  val orchestrationJson = getJSValueFromString(Utils_JSONTest.global_perf_test)
  val ensembles = (orchestrationJson \ JSON_PATH.ENSEMBLES).as[List[JsObject]]
  val globals = (orchestrationJson \ JSON_PATH.GLOBAL_PERFORMERS).as[List[JsObject]]

  val global_name = "GLOBAL-MANAGER"
  var global_managerRef:TestActorRef[GlobalPerformer] = null
  var global_managerState:GlobalPerformer = null


  "Creating an Global Manager " should {
    "result in sending START message to Monitor actor" in {
      global_managerRef =  TestActorRef[GlobalPerformer]( Props(new GlobalPerformer(orch_id,"TESTING-GLOBAL",globals,ensembles){
        override val monitoring_actor = monitor.ref
      }), orchRef, global_name)
      global_managerState = global_managerRef.underlyingActor
      monitor.expectMsgClass(1.seconds, classOf[Monitor.START])
    }
  }
}
