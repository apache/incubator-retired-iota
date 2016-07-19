
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

class FeyGenericActorSpec extends BaseAkkaSpec{

  val parent = TestProbe("GENERIC-PARENT")
  val monitor = TestProbe("MONITOR-GENERIC")
  val connectTB = TestProbe("CONNECT_TO")
  val genericRef: TestActorRef[FeyGenericActorTest] = TestActorRef[FeyGenericActorTest]( Props(new FeyGenericActorTest(Map.empty, 1.seconds,
    Map("CONNECTED" -> connectTB.ref),300.milliseconds,"MY-ORCH", "MY-ORCH", false){
    override private[fey] val monitoring_actor = monitor.ref
  }),parent.ref, "GENERIC-TEST")
  var genericState:FeyGenericActorTest = genericRef.underlyingActor
  val path =  genericRef.path.toString
  var latestBackoff: Long = 0

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
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/GENERIC-TEST")
    }
  }

  "Backoff of GenericActor" should {
    "be zero until the first PROCESS message" in {
      genericState.getEndBackoff() should equal(0)
    }
    "change when first PROCESS message was received" in{
      genericRef ! FeyGenericActor.PROCESS("TEST1")
      latestBackoff = genericState.getEndBackoff()
      latestBackoff should not equal(0)
      connectTB.expectMsgClass(classOf[FeyGenericActor.PROCESS[String]])
    }
  }

  "Sending PROCESS message to GenericActor" should{
    "call processMessage method" in{
      genericState.processed should be(true)
    }
  }

  "customReceive method" should {
    "process any non treated message" in{
      genericRef ! "TEST_CUSTOM"
      genericState.count should equal(1)
    }
  }

  "Sending PROCESS message to GenericActor" should {
    "be discarded when backoff is enabled" in{
      genericRef ! FeyGenericActor.PROCESS("DISCARDED")
      latestBackoff should equal(genericState.getEndBackoff())
      genericRef ! FeyGenericActor.PROCESS("DISCARDED2")
      connectTB.expectNoMsg(100.milliseconds)
    }
    "be processed when backoff has finished" in{
      while(latestBackoff >= System.nanoTime()){}
      genericRef ! FeyGenericActor.PROCESS("SHOULD BE PROCESSED")
      connectTB.expectMsgClass(classOf[FeyGenericActor.PROCESS[String]])
      latestBackoff = genericState.getEndBackoff()
    }
  }

  "Calling startBackoff" should {
    "set endBackoff with time now" in {
      genericState.getEndBackoff() > latestBackoff
    }
  }

  "Calling propagateMessage" should {
    "send message to connectTo actors" in {
      genericState.propagateMessage("PROPAGATE")
      connectTB.expectMsgClass(classOf[FeyGenericActor.PROCESS[String]])
    }
  }

  "Scheduler component" should{
    "call execute() method" in {
      genericState.executing should be(true)
    }
  }

  "Sending EXCEPTION(IllegalArgumentException) message to GenericActor" should{
    "Throw IllegalArgumentException" in{
      EventFilter[IllegalArgumentException](occurrences = 1) intercept {
        genericRef ! FeyGenericActor.EXCEPTION(new IllegalArgumentException("Testing"))
      }
    }
    "Result in restart of the actor with sequence of Monitoring: STOP -> RESTART -> START" in{
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


  val connectTB2 = TestProbe("CONNECT2_TO")
  var generic2Ref: TestActorRef[FeyGenericActorTest] = _
  var generic2State:FeyGenericActorTest = _

  "Creating GenericActor with schedule anc backoff equal to zero" should{
    "not start a scheduler" in{
      generic2Ref = TestActorRef[FeyGenericActorTest]( Props(new FeyGenericActorTest(Map.empty, 0.seconds,
        Map("CONNECTED" -> connectTB2.ref),0.milliseconds,"MY-ORCH-2", "MY-ORCH-2", false)),parent.ref, "GENERIC-TEST-2")
      generic2State = generic2Ref.underlyingActor
      generic2State.isShedulerRunning() should be(false)
    }
    "result in one active actor" in {
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(1)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/GENERIC-TEST-2")
    }
    "result in no discarded PROCESS messages" in {
      generic2Ref ! FeyGenericActor.PROCESS("NOT-DISCARDED")
      connectTB2.expectMsgClass(classOf[FeyGenericActor.PROCESS[String]])
      generic2Ref ! FeyGenericActor.PROCESS("NOT-DISCARDED2")
      connectTB2.expectMsgClass(classOf[FeyGenericActor.PROCESS[String]])
    }
  }
}
