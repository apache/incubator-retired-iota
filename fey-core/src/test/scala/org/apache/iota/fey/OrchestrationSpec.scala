
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

class OrchestrationSpec extends BaseAkkaSpec{

  val parent = TestProbe("ORCHESTRATION")
  val orchName = "ORCHESTRATION-TEST"
  val monitor = TestProbe()
  val orchRef = TestActorRef[Orchestration]( Props(new Orchestration("TESTING",orchName,"123124324324"){
    override val monitoring_actor = monitor.ref
  }), parent.ref, orchName)
  val orchState = orchRef.underlyingActor

  val orchestrationJson = getJSValueFromString(Utils_JSONTest.create_json_test)
  val ensembles = (orchestrationJson \ JSON_PATH.ENSEMBLES).as[List[JsObject]]
  val ensemble1 = ensembles(0)
  val ensemble2 = ensembles(1)
  var ensemble1ref:ActorRef = _
  var ensemble2ref:ActorRef = _

  "Creating an Orchestration " should {
    "result in sending START message to Monitor actor" in {
      monitor.expectMsgClass(1.seconds, classOf[Monitor.START])
    }
    "result in one paths added to IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(1)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName")
    }
    "result in empty Orchestration.ensembles state variable" in{
      orchState.ensembles shouldBe empty
    }
  }

  "Sending Orchestration.CREATE_ENSEMBLES to Orchestration" should {
   s"result in creation of Ensemble '${(ensemble1 \ JSON_PATH.GUID).as[String]}'" in {
      orchRef ! Orchestration.CREATE_ENSEMBLES(ensembles)
      ensemble1ref = TestProbe().expectActor(s"${orchRef.path}/${(ensemble1 \ JSON_PATH.GUID).as[String]}")
    }
    s"result in creation of Ensemble '${(ensemble2 \ JSON_PATH.GUID).as[String]}'" in {
      ensemble2ref = TestProbe().expectActor(s"${orchRef.path}/${(ensemble2 \ JSON_PATH.GUID).as[String]}")
    }
    s"result in creation of two Performers" in {
      TestProbe().expectActor(s"${orchRef.path}/${(ensemble2 \ JSON_PATH.GUID).as[String]}/TEST-0001")
      TestProbe().expectActor(s"${orchRef.path}/${(ensemble1 \ JSON_PATH.GUID).as[String]}/TEST-0001")
    }
    s"result in two entries in Orchestration.ensembles matching the craeted ensembles" in {
      orchState.ensembles should have size(2)
      orchState.ensembles should contain key((ensemble1 \ JSON_PATH.GUID).as[String])
      orchState.ensembles should contain key((ensemble2 \ JSON_PATH.GUID).as[String])
      orchState.ensembles.get((ensemble1 \ JSON_PATH.GUID).as[String]).get should equal(ensemble1ref)
      orchState.ensembles.get((ensemble2 \ JSON_PATH.GUID).as[String]).get should equal(ensemble2ref)
    }
    s"result in two entries in ORCHESTRATION_CACHE.orchestration_metadata for $orchName" in {
      ORCHESTRATION_CACHE.orchestration_metadata should contain key(orchName)
      ORCHESTRATION_CACHE.orchestration_metadata.get(orchName).get should have size(2)
    }
    s"result in right entry in ORCHESTRATION_CACHE.orchestration_metadata for $orchName and ${(ensemble1 \ JSON_PATH.GUID).as[String]}" in {
      ORCHESTRATION_CACHE.orchestration_metadata.get(orchName).get should contain key((ensemble1 \ JSON_PATH.GUID).as[String])
      ORCHESTRATION_CACHE.orchestration_metadata
        .get(orchName).get
        .get((ensemble1 \ JSON_PATH.GUID).as[String]).get should equal(ensemble1)
    }
    s"result in right entry in ORCHESTRATION_CACHE.orchestration_metadata for $orchName and ${(ensemble2 \ JSON_PATH.GUID).as[String]}" in {
      ORCHESTRATION_CACHE.orchestration_metadata.get(orchName).get should contain key((ensemble2 \ JSON_PATH.GUID).as[String])
      ORCHESTRATION_CACHE.orchestration_metadata
        .get(orchName).get
        .get((ensemble2 \ JSON_PATH.GUID).as[String]).get should equal(ensemble2)
    }
    "result in five paths added to IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(5)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002/TEST-0001")
    }
  }

  "Sending Orchestration.CREATE_ENSEMBLES to Orchestration with the same previous created Ensembles" should {
    s"result in logging 'already exists' at Warn " in {
      EventFilter.warning(pattern = "^Ensembles.*already exists", occurrences = 2) intercept {
        orchRef ! Orchestration.CREATE_ENSEMBLES(ensembles)
      }
    }
    s"not change state variable Orchestration.ensembles" in {
      orchState.ensembles should have size(2)
      orchState.ensembles should contain key((ensemble1 \ JSON_PATH.GUID).as[String])
      orchState.ensembles should contain key((ensemble2 \ JSON_PATH.GUID).as[String])
      orchState.ensembles.get((ensemble1 \ JSON_PATH.GUID).as[String]).get should equal(ensemble1ref)
      orchState.ensembles.get((ensemble2 \ JSON_PATH.GUID).as[String]).get should equal(ensemble2ref)
    }
  }

  val orch2Name = "TEST-ORCH-2"
  var orch2ref: TestActorRef[Orchestration] = _
  var orch2ensRef: ActorRef = _
  val orchestration2Json = getJSValueFromString(Utils_JSONTest.orchestration_test_json)
  val orch2ensembles = (orchestration2Json \ JSON_PATH.ENSEMBLES).as[List[JsObject]]
  val orch2ensemble1 = orch2ensembles(0)
  val monitor2 = TestProbe()
  var orch2state:Orchestration = null

  "Creating a second Orchestration" should {
    s"result in sending START message to Monitor actor" in {
      orch2ref = TestActorRef[Orchestration]( Props(new Orchestration("TESTING",orch2Name,"123124324324"){
        override val monitoring_actor = monitor2.ref
      }), parent.ref, orch2Name)
      monitor2.expectMsgClass(1.seconds, classOf[Monitor.START])
      orch2state = orch2ref.underlyingActor
    }
    "result in empty Orchestration.ensembles state variable" in{
      orch2state.ensembles shouldBe empty
    }
    "result in six paths added to IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(6)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002/TEST-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orch2Name")
    }
  }

  "Sending Orchestration.CREATE_ENSEMBLES to the second Orchestration" should {
    s"result in creation of Ensemble '${(orch2ensemble1 \ JSON_PATH.GUID).as[String]}'" in {
      orch2ref ! Orchestration.CREATE_ENSEMBLES(orch2ensembles)
      orch2ensRef = TestProbe().expectActor(s"${orch2ref.path}/${(orch2ensemble1 \ JSON_PATH.GUID).as[String]}")
    }
    s"result in creation of one Performers" in {
      TestProbe().expectActor(s"${orch2ref.path}/${(orch2ensemble1 \ JSON_PATH.GUID).as[String]}/TEST-0001")
    }
    s"result in one entry in Orchestration.ensembles matching the craeted ensemble" in {
      orch2state.ensembles should have size(1)
      orch2state.ensembles should contain key((orch2ensemble1 \ JSON_PATH.GUID).as[String])
      orch2state.ensembles.get((orch2ensemble1 \ JSON_PATH.GUID).as[String]).get should equal(orch2ensRef)
    }
    s"result in one entries in ORCHESTRATION_CACHE.orchestration_metadata for $orch2Name" in {
      ORCHESTRATION_CACHE.orchestration_metadata should contain key(orch2Name)
      ORCHESTRATION_CACHE.orchestration_metadata.get(orch2Name).get should have size(1)
    }
    s"result in right entry in ORCHESTRATION_CACHE.orchestration_metadata for $orch2Name and ${(orch2ensemble1 \ JSON_PATH.GUID).as[String]}" in {
      ORCHESTRATION_CACHE.orchestration_metadata.get(orch2Name).get should contain key((orch2ensemble1 \ JSON_PATH.GUID).as[String])
      ORCHESTRATION_CACHE.orchestration_metadata
        .get(orch2Name).get
        .get((orch2ensemble1 \ JSON_PATH.GUID).as[String]).get should equal(orch2ensemble1)
    }
    "result in eight paths added to IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(8)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002/TEST-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orch2Name")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orch2Name/MY-ENSEMBLE-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orch2Name/MY-ENSEMBLE-0001/TEST-0001")
    }
  }

  "Sending Orchestration.DELETE_ENSEMBLES to the second Orchestration" should {
    "result in termination of ensembles and the Orchestration itself" in {
      orch2ref ! Orchestration.DELETE_ENSEMBLES(orch2ensembles)
      TestProbe().verifyActorTermination(orch2ensRef)
    }
    s"result in sending TERMINATED message to Monitor actor" in {
      monitor2.expectMsgAllClassOf(classOf[Monitor.TERMINATE])
    }
    s"result in FeyCore.STOP_EMPTY_ORCHESTRATION triggered by $orch2Name to parent" in {
      parent.expectMsg(FeyCore.STOP_EMPTY_ORCHESTRATION(orch2Name))
    }
    "result in six paths added to IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(6)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002/TEST-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orch2Name")
    }
    "result in empty state variable Orchestration.ensembles" in {
      orch2state.ensembles shouldBe empty
    }
  }

  "Stopping second Orchestration" should {
    s"result in sending STOP message to Monitor actor" in {
      orch2ref ! PoisonPill
      monitor2.expectMsgAllClassOf(classOf[Monitor.STOP])
    }
  }

  "Sending Orchestration.UPDATE_ENSEMBLES with Ensemble that does not exist" should {
    s"result in logging 'no Ensemble' at Warn " in {
      EventFilter.warning(pattern = s"^There is no Ensemble.*$orchName", occurrences = 1) intercept {
        val updateEnsemble = (getJSValueFromString(Utils_JSONTest.orchestration_update2_test_json) \ JSON_PATH.ENSEMBLES).as[List[JsObject]]
        orchRef ! Orchestration.UPDATE_ENSEMBLES(updateEnsemble)
      }
    }
  }

  "Sending Orchestration.UPDATE_ENSEMBLES to Orchestration" should {
    val updateEnsemble = (getJSValueFromString(Utils_JSONTest.orchestration_update_test_json) \ JSON_PATH.ENSEMBLES).as[List[JsObject]]
    s"result in termination of Ensemble '$orchName/MY-ENSEMBLE-0001'" in {
      orchRef ! Orchestration.UPDATE_ENSEMBLES(updateEnsemble)
      TestProbe().verifyActorTermination(ensemble1ref)
    }
    s"result in termination of '$orchName/MY-ENSEMBLE-0001/TEST-0001'" in {
      TestProbe().notExpectActor(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0001")
    }
    s"result in creation of two new performers" in {
      TestProbe().expectActor(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0004")
      TestProbe().expectActor(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0005")
    }
    "result in six paths added to IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(6)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0005")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0001/TEST-0004")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/$orchName/MY-ENSEMBLE-0002/TEST-0001")
    }
    s"result in right entry in ORCHESTRATION_CACHE.orchestration_metadata for $orchName and ${(ensemble1 \ JSON_PATH.GUID).as[String]}" in {
      ORCHESTRATION_CACHE.orchestration_metadata.get(orchName).get should contain key((ensemble1 \ JSON_PATH.GUID).as[String])
      ORCHESTRATION_CACHE.orchestration_metadata
        .get(orchName).get
        .get((ensemble1 \ JSON_PATH.GUID).as[String]).get should equal(updateEnsemble(0))
    }
  }

  //TODO: Test restart

}
