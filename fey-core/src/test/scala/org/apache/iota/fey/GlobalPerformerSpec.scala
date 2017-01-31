
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

  var ensembleglobal:ActorRef = null
  var childPerformer:ActorRef = null
  var globalPerformer: ActorRef = null

  "Creating an Global Manager " should {
    "result in sending START message to Monitor actor" in {
      global_managerRef =  TestActorRef[GlobalPerformer]( Props(new GlobalPerformer(orch_id,"TESTING-GLOBAL",globals,ensembles){
        override val monitoring_actor = monitor.ref
      }), orchRef, global_name)
      global_managerState = global_managerRef.underlyingActor
      monitor.expectMsgClass(1.seconds, classOf[Monitor.START])
    }
    s"result in creating an Orchestration child actor with the name '$orch_id'" in {
      TestProbe().expectActor(s"${orchRef.path.toString}")
    }
    s"result in creating an Ensemble child actor with the name '${orchRef.path.toString}/ENS-GLOBAL'" in {
      ensembleglobal = TestProbe().expectActor(s"${orchRef.path.toString}/ENS-GLOBAL")
    }
    s"result in creating a global Performer child actor with the name '${orchRef.path.toString}/GLOBAL_MANAGER/GLOBAL-TEST'" in {
      globalPerformer = TestProbe().expectActor(s"${orchRef.path.toString}/$global_name/GLOBAL-TEST")
      TestProbe().expectActor(s"${orchRef.path.toString}/$global_name")
    }
    s"result in creating a Performer child actor with the name '${orchRef.path.toString}/ENS-GLOBAL/PERFORMER-SCHEDULER'" in {
      childPerformer = TestProbe().expectActor(s"${orchRef.path.toString}/ENS-GLOBAL/PERFORMER-SCHEDULER")
    }
    s"result in one global actor created for orchestration" in {
      GlobalPerformer.activeGlobalPerformers should have size(1)
      GlobalPerformer.activeGlobalPerformers should contain key(orch_id)
    }
    s"result in right number of running actors" in {
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size (9)
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}")
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/GLOBAL-MANAGER")
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/GLOBAL-MANAGER/GLOBAL-TEST")
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/ENS-GLOBAL")
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/ENS-GLOBAL/PERFORMER-SCHEDULER")
    }
  }

  "Stopping performer inside ensemble" should {
    "Send stop message to monitor" in {
      EventFilter.error(pattern = s".*DEAD nPerformers.*", occurrences = 1) intercept {
        childPerformer ! PoisonPill
      }
      //Restarted ensemble
      monitor.expectMsgClass(1.seconds, classOf[Monitor.START])
    }
  }

  "Stopping ensemble" should {
    "Send stop message to monitor" in {
      EventFilter.warning(pattern = s".*ACTOR DEAD.*", occurrences = 1) intercept {
        ensembleglobal ! PoisonPill
      }
      //Restarted ensemble
      monitor.expectMsgClass(1.seconds, classOf[Monitor.TERMINATE])
    }
    "result in no orchestration running" in {
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size (3)
      IdentifyFeyActors.actorsPath should not contain(s"${orchRef.path.toString}/ENS-GLOBAL")
    }
    "not affect global performer" in {
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size (3)
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}")
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/GLOBAL-MANAGER")
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/GLOBAL-MANAGER/GLOBAL-TEST")
    }
  }

  "Stopping global performer" should {
    "result in restart the orchestration" in {
      EventFilter.error(pattern = s".*DEAD Global Performers.*", occurrences = 1) intercept {
        globalPerformer ! PoisonPill
      }
      monitor.expectMsgClass(1.seconds, classOf[Monitor.TERMINATE])
    }
    "all previous actors restarted" in {
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size (7)
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}")
      //TestProbe does not contain the right supervisor estrategy to restart global
      //IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/GLOBAL-MANAGER")
      //IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/GLOBAL-MANAGER/GLOBAL-TEST")
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/ENS-GLOBAL")
      IdentifyFeyActors.actorsPath should contain(s"${orchRef.path.toString}/ENS-GLOBAL/PERFORMER-SCHEDULER")
    }
  }

  "Stopping orchestration" should {
    "result in empty global" in {
      orchRef ! PoisonPill
      GlobalPerformer.activeGlobalPerformers.remove("GLOBAL-ORCH")
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should not contain(s"${orchRef.path.toString}/ENS-GLOBAL")
      IdentifyFeyActors.actorsPath should not contain(s"${orchRef.path.toString}/GLOBAL-MANAGER")
      IdentifyFeyActors.actorsPath should not contain(s"${orchRef.path.toString}/GLOBAL-MANAGER/GLOBAL-TEST")

      GlobalPerformer.activeGlobalPerformers should have size(0)
    }
  }
}
