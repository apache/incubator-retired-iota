
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

import java.io.File

import akka.actor.{ActorRef, PoisonPill, Props}
import akka.testkit.{EventFilter, TestProbe}

import scala.concurrent.duration.DurationInt

class FeyCoreSpec extends BaseAkkaSpec  {

  val monitor = TestProbe()
  val feyCoreRef = system.actorOf(Props(new FeyCore{
    override val monitoring_actor = monitor.ref
  }), "FEY-CORE")

  val feyPath = feyCoreRef.path.toString

  "Creating FeyCore" should {
    s"result in creating a child actor with the name '${FeyCore.IDENTIFIER_NAME}'" in {
      TestProbe().expectActor(s"/user/FEY-CORE/${FeyCore.IDENTIFIER_NAME}")
    }
    "result in sending START message to Monitor actor" in {
      monitor.expectMsgClass(1.seconds, classOf[Monitor.START])
    }
  }

  "Sending FeyCore.START to FeyCore" should {
    s"result in creating a child actor with the name '${FeyCore.JSON_RECEIVER_NAME}'" in {
      feyCoreRef ! FeyCore.START
      TestProbe().expectActor(s"$feyPath/${FeyCore.JSON_RECEIVER_NAME}")
    }
    s"result in starting ${GLOBAL_DEFINITIONS.WATCH_SERVICE_THREAD} Thread" in {
      TestProbe().isThreadRunning(GLOBAL_DEFINITIONS.WATCH_SERVICE_THREAD) should equal(true)
    }
  }
  var ensemble1ref:ActorRef = _
  var ensemble2ref:ActorRef = _
  var ensemble1Test1ref:ActorRef = _
  var ensemble1Test2ref:ActorRef = _
  var ensemble2Test1ref:ActorRef = _
  var orchestrationref:ActorRef = _

  val orchestration_name = "TEST-ACTOR"

  "Sending FeyCore.ORCHESTRATION_RECEIVED with CREATE command to FeyCore" should {
    feyCoreRef ! FeyCore.ORCHESTRATION_RECEIVED(getJSValueFromString(Utils_JSONTest.create_json_test), None)
    s"result in creating an Orchestration child actor with the name '$orchestration_name'" in {
      orchestrationref = TestProbe().expectActor(s"$feyPath/$orchestration_name")
    }
    s"result in creating an Ensemble child actor with the name '$orchestration_name/MY-ENSEMBLE-0001'" in {
      ensemble1ref = TestProbe().expectActor(s"$feyPath/$orchestration_name/MY-ENSEMBLE-0001")
    }
    s"result in creating an Ensemble child actor with the name '$orchestration_name/MY-ENSEMBLE-0002'" in {
      ensemble2ref = TestProbe().expectActor(s"$feyPath/$orchestration_name/MY-ENSEMBLE-0002")
    }
    s"result in creating a Performer child actor with the name '$orchestration_name/MY-ENSEMBLE-0001/TEST-0001'" in {
      ensemble1Test1ref = TestProbe().expectActor(s"$feyPath/$orchestration_name/MY-ENSEMBLE-0001/TEST-0001")
    }
    s"result in creating a Performer child actor with the name '$orchestration_name/MY-ENSEMBLE-0002/TEST-0001'" in {
      ensemble2Test1ref = TestProbe().expectActor(s"$feyPath/$orchestration_name/MY-ENSEMBLE-0002/TEST-0001")
    }
    s"result in new entry to FEY_CACHE.activeOrchestrations with key '$orchestration_name'" in {
      FEY_CACHE.activeOrchestrations should contain key(orchestration_name)
    }
  }

  "Sending FeyCore.ORCHESTRATION_RECEIVED with UPDATE command to FeyCore" should {
    s"result in creating a new Performer child actor with the name '$orchestration_name/MY-ENSEMBLE-0001/TEST-0002'" in {
      feyCoreRef ! FeyCore.ORCHESTRATION_RECEIVED(getJSValueFromString(Utils_JSONTest.update_json_test), None)
      ensemble1Test2ref = TestProbe().expectActor(s"$feyPath/$orchestration_name/MY-ENSEMBLE-0001/TEST-0002")
    }
  }

  "Sending FeyCore.ORCHESTRATION_RECEIVED with UPDATE command and DELETE ensemble to FeyCore" should {
    s"result in termination of Ensemble with the name '$orchestration_name/MY-ENSEMBLE-0001'" in {
      feyCoreRef ! FeyCore.ORCHESTRATION_RECEIVED(getJSValueFromString(Utils_JSONTest.update_delete_json_test), None)
      TestProbe().verifyActorTermination(ensemble1ref)
    }
    s"result in termination of Performer with the name '$orchestration_name/MY-ENSEMBLE-0001/TEST-0001'" in {
      TestProbe().notExpectActor(ensemble1Test1ref.path.toString)
    }
    s"result in termination of Performer with the name '$orchestration_name/MY-ENSEMBLE-0001/TEST-0002'" in {
      TestProbe().notExpectActor(ensemble1Test2ref.path.toString)
    }
  }

  "Sending FeyCore.ORCHESTRATION_RECEIVED with RECREATE command and same Timestamp to FeyCore" should {
    s"result in logging a 'not recreated' message at Warn " in {
      EventFilter.warning(pattern = s".*$orchestration_name not recreated.*", occurrences = 1) intercept {
        feyCoreRef ! FeyCore.ORCHESTRATION_RECEIVED(getJSValueFromString(Utils_JSONTest.recreate_timestamp_json_test), None)
      }
    }
  }

  "Sending FeyCore.JSON_TREE to FeyCore" should {
    s"result in logging a 6 path messages at Info " in {
      EventFilter.info(pattern = s"^akka://.*/user/.*", occurrences = 6) intercept {
        feyCoreRef ! FeyCore.JSON_TREE
      }
    }
  }

  "Sending FeyCore.ORCHESTRATION_RECEIVED with DELETE command to FeyCore" should {
    s"result in termination of Orchestration with the name '$orchestration_name'" in {
      feyCoreRef ! FeyCore.ORCHESTRATION_RECEIVED(getJSValueFromString(Utils_JSONTest.delete_json_test), None)
      TestProbe().verifyActorTermination(orchestrationref)
    }
    "result in sending TERMINATE message to Monitor actor" in {
      monitor.expectMsgClass(1.seconds, classOf[Monitor.TERMINATE])
    }
    s"result in termination of Ensemble with the name '$orchestration_name/MY-ENSEMBLE-0002'" in {
      TestProbe().notExpectActor(ensemble2ref.path.toString)
    }
    s"result in termination of Performer with the name '$orchestration_name/MY-ENSEMBLE-0002/TEST-0001'" in {
      TestProbe().notExpectActor(ensemble2Test1ref.path.toString)
    }
    s"result in removing key '$orchestration_name' at FEY_CACHE.activeOrchestrations" in {
      FEY_CACHE.activeOrchestrations should not contain key(orchestration_name)
    }
  }

  "Sending FeyCore.STOP_EMPTY_ORCHESTRATION to FeyCore" should {
    s"result in termination of 'TEST-ORCH-2'" in {
      feyCoreRef ! FeyCore.ORCHESTRATION_RECEIVED(getJSValueFromString(Utils_JSONTest.orchestration_test_json), None)
      val ref = TestProbe().expectActor(s"$feyPath/TEST-ORCH-2")
      FEY_CACHE.activeOrchestrations should have size(1)
      FEY_CACHE.activeOrchestrations should contain key("TEST-ORCH-2")
      feyCoreRef ! FeyCore.STOP_EMPTY_ORCHESTRATION("TEST-ORCH-2")
      TestProbe().verifyActorTermination(ref)
    }
    s"result in sending Terminate message to Monitor actor" in{
      monitor.expectMsgClass(1.seconds, classOf[Monitor.TERMINATE])
    }
    s"result in empty FEY_CACHE.activeOrchestrations" in {
      FEY_CACHE.activeOrchestrations shouldBe empty
    }
  }

  "Stopping FeyCore" should {
    "result in sending STOP message to Monitor actor" in {
      feyCoreRef ! PoisonPill
      monitor.expectMsgClass(1.seconds, classOf[Monitor.STOP])
    }
  }

  //TODO: Test restart
  //TODO: Test checkpoint
}
