
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

import java.nio.file.{Files, Paths}

import akka.actor.Props
import akka.testkit.{EventFilter, TestActorRef, TestProbe}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class FeyGenericActorReceiverSpec extends BaseAkkaSpec{

  val parent = TestProbe("GENERIC-RECEIVER-PARENT")
  val monitor = TestProbe("MONITOR-GENERIC")
  val feyTB = TestProbe("GENERIC-FEY")
  val connectToTB = TestProbe("REC-CONNECT")

  val genericRef: TestActorRef[FeyGenericActorReceiverTest] =
    TestActorRef[FeyGenericActorReceiverTest]( Props(new FeyGenericActorReceiverTest(Map.empty, 0.seconds,
    Map("connect" -> connectToTB.ref),300.milliseconds,"MY-ORCH", "MY-ORCH", false){
    override private[fey] val monitoring_actor = monitor.ref
    override private[fey] val feyCore = feyTB.ref
  }),parent.ref, "GENERIC-RECEIVER-TEST")

  var genericState:FeyGenericActorReceiverTest = genericRef.underlyingActor
  val path =  genericRef.path.toString


  "Creating a GenericActor with Schedule time defined" should {
    "result in scheduler started" in{
      genericState.isShedulerRunning() should be(true)
    }
    "result in onStart method called" in {
      genericState.started should be(true)
    }
    "result in START message sent to Monitor" in{
      monitor.expectMsgClass(classOf[Monitor.START])
    }
    "result in one active actor" in {
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(1)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/GENERIC-RECEIVER-TEST")
    }
    s"result in normal functioning of GenericActor" in {
      genericRef ! "PROPAGATE"
      connectToTB.expectMsg(FeyGenericActor.PROCESS("PROPAGATE-CALLED"))
    }
  }

  "Sending PROCESS message to GenericReceiver" should {
    "log message to Warn saying that the JSON could not be forwarded to FeyCore when JSON is invalid" in {
      EventFilter.warning(message = s"Could not forward Orchestration TEST-ACTOR. Invalid JSON schema", occurrences = 1) intercept {
        genericRef ! FeyGenericActor.PROCESS("INVALID_JSON")
        feyTB.expectNoMsg(1.seconds)
      }
    }
    "send ORCHESTRATION_RECEIVED to FeyCore when JSON to be processed has a valid schema" in {
      genericRef ! FeyGenericActor.PROCESS("VALID_JSON")
      feyTB.expectMsgClass(classOf[FeyCore.ORCHESTRATION_RECEIVED])
    }
    "Download jar from location and send ORCHESTRATION_RECEIVED to FeyCore when JSON has a location defined" in {
      genericRef ! FeyGenericActor.PROCESS("JSON_LOCATION")
      Files.exists(Paths.get(s"${CONFIG.DYNAMIC_JAR_REPO}/fey-virtual-sensor.jar"))
      feyTB.expectMsgClass(classOf[FeyCore.ORCHESTRATION_RECEIVED])
    }
  }

  "Scheduler component" should {
    "call execute() method" in {
      genericState.executing should be(true)
    }
  }

  "Sending EXCEPTION(IllegalArgumentException) message to GenericActor" should {
    "Throw IllegalArgumentException" in {
      EventFilter[IllegalArgumentException](occurrences = 1) intercept {
        genericRef ! FeyGenericActor.EXCEPTION(new IllegalArgumentException("Testing"))
      }
    }
    "Result in restart of the actor with sequence of Monitoring: STOP -> RESTART -> START" in {
      monitor.expectMsgClass(classOf[Monitor.STOP])
      monitor.expectMsgClass(classOf[Monitor.RESTART])
      //Restart does not change the actorRef but does change the object inside the ActorReference
      genericState = genericRef.underlyingActor
      monitor.expectMsgClass(classOf[Monitor.START])
    }
    "call onStart method" in {
      genericState.started should be(true)
    }
    "call onRestart method" in {
      Thread.sleep(100)
      genericState.restarted should be(true)
    }
    "restart scheduler" in {
      genericState.isShedulerRunning() should be(true)
    }
  }

  "Sending STOP to GenericActor" should{
    "terminate GenericActor" in{
      genericRef ! FeyGenericActor.STOP
      TestProbe().verifyActorTermination(genericRef)
      TestProbe().notExpectActor(path)
    }
    "call onStop method" in {
      genericState.stopped should be(true)
    }
    "cancel scheduler" in{
      genericState.isShedulerRunning() should be(false)
    }
    "send STOP - TERMINATE message to Monitor" in{
      monitor.expectMsgClass(classOf[Monitor.STOP])
    }
    "result in no active actors" in {
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath shouldBe empty
    }
  }
}
