
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

import akka.actor.{ActorRef, PoisonPill, Props}
import akka.testkit.{EventFilter, TestActorRef, TestProbe}
import play.api.libs.json.JsObject

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import JSON_PATH._


class EnsembleSpec extends BaseAkkaSpec{

  val ensembleJson = getJSValueFromString(Utils_JSONTest.simple_ensemble_test_json)
  val orchestrationID = "ORCH-TEST-ENSEMBLE"
  val parent = TestProbe("ENSEMBLE")
  val monitor = TestProbe()

  val ensembleRef = TestActorRef[Ensemble]( Props(new Ensemble(orchestrationID,"ORCH-NAME", ensembleJson.as[JsObject]){
    override val monitoring_actor = monitor.ref
  }), parent.ref, (ensembleJson \ JSON_PATH.GUID).as[String])

  val ensembleState = ensembleRef.underlyingActor

  var simplePerformerRef: ActorRef = _

  s"Creating a simple Ensemble ${(ensembleJson \ JSON_PATH.GUID).as[String]}" should {
    s"result in creation of Ensemble actor '${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}'" in {
      TestProbe().expectActor(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}")
    }
    s"result in sending START to monitor actor" in {
      monitor.expectMsgClass(classOf[Monitor.START])
    }
    s"result in creation of Performer 'TEST-0004'" in{
      simplePerformerRef = TestProbe().expectActor(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}/TEST-0004")
    }
    s"result in Empty state variable Ensemble.connectors" in {
      ensembleState.connectors shouldBe empty
    }
    s"result in one entry added to state variable Ensemble.performer" in{
      ensembleState.performer should have size(1)
      ensembleState.performer should contain key("TEST-0004")
      ensembleState.performer.get("TEST-0004").get should equal(simplePerformerRef)
    }
    s"result in one right entry to state variable Ensemble.performers_metadata" in {
      ensembleState.performers_metadata should have size(1)
      val performers = (ensembleJson \ PERFORMERS).as[List[JsObject]]
      val performerSpec = performers(0)
      val performer = ensembleState.performers_metadata.get("TEST-0004").get

      performer.controlAware should equal(false)
      performer.jarName should equal((performerSpec \ SOURCE \ SOURCE_NAME).as[String])
      performer.jarLocation should equal(CONFIG.JAR_REPOSITORY)
      performer.autoScale should equal(0)
      performer.backoff should equal((performerSpec \ BACKOFF).as[Int].millisecond)
      performer.classPath should equal((performerSpec \ SOURCE \ SOURCE_CLASSPATH).as[String])
      performer.uid should equal((performerSpec \ GUID).as[String])
      performer.schedule should equal((performerSpec \ SCHEDULE).as[Int].millisecond)
      performer.parameters shouldBe empty
    }
    "result in two paths added to IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(2)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}/TEST-0004")
    }
  }

  s"Sending Ensemble.STOP_PERFORMERS to Ensemble" should {
    s"result in Terminate message of actor 'TEST-0004' and throw RestartEnsemble Exception" in {
      EventFilter[RestartEnsemble](occurrences = 1) intercept {
        ensembleRef ! Ensemble.STOP_PERFORMERS
        TestProbe().verifyActorTermination(simplePerformerRef)
      }
    }
    s"result in Performer 'TEST-0004' restarted" in {
      val newPerformer = TestProbe().expectActor(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}/TEST-0004")
      newPerformer.compareTo(simplePerformerRef) should not be(0)
    }
    "result in two paths added to IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(2)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}")
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}/TEST-0004")
    }
  }

  s"Sending PoisonPill to Ensemble" should{
    s"result in termination of actor '${(ensembleJson \ JSON_PATH.GUID).as[String]}'" in {
      ensembleRef ! PoisonPill
      TestProbe().verifyActorTermination(ensembleRef)
    }
    s"result in sending TERMINATE to monitor actor" in {
      monitor.expectMsgClass(classOf[Monitor.TERMINATE])
    }
    "result in termination of ensemble and performer" in {
      TestProbe().notExpectActor(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}")
      TestProbe().notExpectActor(s"${parent.ref.path}/${(ensembleJson \ JSON_PATH.GUID).as[String]}/TEST-0004")
    }
    "result in empty IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath shouldBe empty
    }
  }

  val advEnsembleJson = getJSValueFromString(Utils_JSONTest.ensemble_test_json)
  var advEnsembleRef:TestActorRef[Ensemble] = _
  var advEnsembleState: Ensemble = ensembleRef.underlyingActor
  var paramsRef: ActorRef = _
  var scheduleRef: ActorRef = _
  val generalScheduleTB = TestProbe("GENERAL-SCHEDULE")
  val schedulerScheduleTB = TestProbe("SCHEDULER-SCHEDULE")
  val generalParamsTB = TestProbe("GENERAL-PARAMS")
  val processParamsTB = TestProbe("PROCESS-PARAMS")

  s"creating more detailed Ensemble" should {
    s"result in creation of Ensemble actor " in {
      advEnsembleRef = TestActorRef[Ensemble]( Props(new Ensemble(orchestrationID,"ORCH-NAME", advEnsembleJson.as[JsObject]){
        override val monitoring_actor = monitor.ref
      }), parent.ref, (advEnsembleJson \ JSON_PATH.GUID).as[String])
      advEnsembleState = advEnsembleRef.underlyingActor
      TestProbe().expectActor(s"${parent.ref.path}/${(advEnsembleJson \ JSON_PATH.GUID).as[String]}")
    }
    s"result in creation of Performer 'PERFORMER-SCHEDULER'" in{
      scheduleRef = TestProbe().expectActor(s"${parent.ref.path}/${(advEnsembleJson \ JSON_PATH.GUID).as[String]}/PERFORMER-SCHEDULER")
    }
    s"result in creation of Performer 'PERFORMER-PARAMS'" in{
      paramsRef = TestProbe().expectActor(s"${parent.ref.path}/${(advEnsembleJson \ JSON_PATH.GUID).as[String]}/PERFORMER-PARAMS")
    }
    s"create connection PERFORMER-SCHEDULER -> PERFORMER-PARAMS" in {
      advEnsembleState.connectors should have size(1)
      advEnsembleState.connectors should contain key("PERFORMER-SCHEDULER")
      advEnsembleState.connectors.get("PERFORMER-SCHEDULER").get should equal(Array("PERFORMER-PARAMS"))
    }
    s"create 'PERFORMER-SCHEDULER' with schedule time equal to 200ms" in{
      advEnsembleState.performers_metadata.get("PERFORMER-SCHEDULER").get.schedule should  equal(200.millisecond)
    }
    s"create 'PERFORMER-SCHEDULER' with connection to 'PERFORMER-PARAMS'" in{
      scheduleRef ! ((schedulerScheduleTB.ref,system.deadLetters,generalScheduleTB.ref))
      scheduleRef ! "GET_CONNECTIONS"
      generalScheduleTB.expectMsg(Map("PERFORMER-PARAMS" -> paramsRef))
    }
    s"create 'PERFORMER-PARAMS' with no connections" in{
      paramsRef ! ((system.deadLetters,processParamsTB.ref, generalParamsTB.ref))
      paramsRef ! "GET_CONNECTIONS"
      generalParamsTB.expectMsg(Map.empty)
    }
    s"create 'PERFORMER-PARAMS' with specified params" in{
      val params = advEnsembleState.performers_metadata.get("PERFORMER-PARAMS").get.parameters
      params should contain key("param-1")
      params should contain key("param-2")
      params.get("param-1").get should equal("test")
      params.get("param-2").get should equal("test2")
    }
  }

  s"'PERFORMER-SCHEDULER'" should{
    "produce 5 messages in 1 seconds" in{
      schedulerScheduleTB.expectMsg("EXECUTE")
      Thread.sleep(100)
      schedulerScheduleTB.receiveN(5, 1.seconds)
    }
    "produce 10 messages in 2 seconds" in{
      schedulerScheduleTB.expectMsg("EXECUTE")
      Thread.sleep(100)
      schedulerScheduleTB.receiveN(10, 2.seconds)
    }
  }

  s"'PERFORMER-PARAMS'" should{
    "process 5 messages in 1 seconds" in{
      schedulerScheduleTB.expectMsg("EXECUTE")
      Thread.sleep(100)
      processParamsTB.receiveN(5, 1.seconds)
    }
    "produce 10 messages in 2 seconds" in{
      schedulerScheduleTB.expectMsg("EXECUTE")
      Thread.sleep(100)
      processParamsTB.receiveN(10, 2.seconds)
    }
  }

  "Stopping any Performer that belongs to the Ensemble" should {
    "force restart of entire Ensemble" in {
      EventFilter[RestartEnsemble](occurrences = 1) intercept {
        paramsRef ! PoisonPill
        TestProbe().verifyActorTermination(paramsRef)
        generalScheduleTB.expectMsg(s"Stopped ${scheduleRef.path.name}")
        generalParamsTB.expectMsg(s"Stopped ${paramsRef.path.name}")
      }
    }
    s"result in sending STOP - RESTART to monitor actor" in {
      monitor.expectMsgClass(classOf[Monitor.STOP])
      monitor.expectMsgClass(classOf[Monitor.RESTART])
      monitor.expectMsgClass(classOf[Monitor.START])
    }
    "keep ensemble actorRef when restarted" in {
      TestProbe().expectActor(s"${parent.ref.path}/${(advEnsembleJson \ JSON_PATH.GUID).as[String]}").compareTo(advEnsembleRef) should be(0)
    }
    "stop and start the performer with a new reference" in{
      TestProbe().expectActor(s"${parent.ref.path}/${(advEnsembleJson \ JSON_PATH.GUID).as[String]}/PERFORMER-SCHEDULER").compareTo(scheduleRef) should not be(0)
      TestProbe().expectActor(s"${parent.ref.path}/${(advEnsembleJson \ JSON_PATH.GUID).as[String]}/PERFORMER-PARAMS").compareTo(paramsRef) should not be(0)
      scheduleRef = TestProbe().expectActor(s"${parent.ref.path}/${(advEnsembleJson \ JSON_PATH.GUID).as[String]}/PERFORMER-SCHEDULER")
      paramsRef = TestProbe().expectActor(s"${parent.ref.path}/${(advEnsembleJson \ JSON_PATH.GUID).as[String]}/PERFORMER-PARAMS")
    }
  }

  "Restarting an Ensemble" should{
    "Consuming left messages on Process" in{
      processParamsTB.receiveWhile(1200.milliseconds) {
        case msg: String =>
      }
    }
    "Cleanup TestProbs" in {
      schedulerScheduleTB.expectNoMsg(400.milliseconds)
      processParamsTB.expectNoMsg(400.milliseconds)
    }
  }

  "Redefining TestProbe for performers" should {
    "start receiving messages" in {
      scheduleRef ! ((schedulerScheduleTB.ref, system.deadLetters, generalScheduleTB.ref))
      paramsRef ! ((system.deadLetters, processParamsTB.ref, generalParamsTB.ref))
      schedulerScheduleTB.expectMsg("EXECUTE")
      Thread.sleep(100)
      schedulerScheduleTB.receiveN(5, 1.seconds)
      processParamsTB.receiveN(5, 1.seconds)
    }
  }

  s"Sending PoisonPill to detailed Ensemble" should {
    s"result in termination of Ensemble" in{
      advEnsembleRef ! PoisonPill
      TestProbe().verifyActorTermination(advEnsembleRef)
    }
    "result in empty IdentifyFeyActors.actorsPath" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath shouldBe empty
    }
  }

  val backEnsembleJson = getJSValueFromString(Utils_JSONTest.ensemble_backoff_test_json)
  var backEnsembleRef:TestActorRef[Ensemble] = _
  var backEnsembleState: Ensemble = ensembleRef.underlyingActor
  var backParamsRef: ActorRef = _
  var backScheduleRef: ActorRef = _
  val backprocessParamsTB = TestProbe("BACKOFF")

  s"creating Ensemble with Backoff performer" should {
    s"result in creation of Ensemble actor " in {
      backEnsembleRef = TestActorRef[Ensemble]( Props(new Ensemble(orchestrationID,"ORCH-NAME", backEnsembleJson.as[JsObject]){
        override val monitoring_actor = monitor.ref
      }), parent.ref, (backEnsembleJson \ JSON_PATH.GUID).as[String])
      backEnsembleState = backEnsembleRef.underlyingActor
      TestProbe().expectActor(s"${parent.ref.path}/${(backEnsembleJson \ JSON_PATH.GUID).as[String]}")
    }
    s"result in creation of Performer 'PERFORMER-SCHEDULER'" in{
      backScheduleRef = TestProbe().expectActor(s"${parent.ref.path}/${(backEnsembleJson \ JSON_PATH.GUID).as[String]}/PERFORMER-SCHEDULER")
    }
    s"result in creation of Performer 'PERFORMER-PARAMS'" in{
      backParamsRef = TestProbe().expectActor(s"${parent.ref.path}/${(backEnsembleJson \ JSON_PATH.GUID).as[String]}/PERFORMER-PARAMS")
    }
    s"create 'PERFORMER-PARAMS' with backoff time equal to 1 second" in{
      backEnsembleState.performers_metadata.get("PERFORMER-PARAMS").get.backoff should  equal(1000.millisecond)
    }
    s"create 'PERFORMER-SCHEDUKE' with autoScale equal to true" in{
      backEnsembleState.performers_metadata.get("PERFORMER-SCHEDULER").get.autoScale should  equal(2)
    }
  }
  s"Performer with backoff enabled" should {
   "not process messages during the backoff period" in{
     backScheduleRef ! ((schedulerScheduleTB.ref, system.deadLetters, generalScheduleTB.ref))
     backParamsRef ! ((system.deadLetters, backprocessParamsTB.ref, generalParamsTB.ref))
     backprocessParamsTB.expectMsg("PROCESS - EXECUTE - akka://FEY-TEST/system/ENSEMBLE-1/MY-ENSEMBLE-0005/PERFORMER-SCHEDULER/$a")
     backprocessParamsTB.expectNoMsg(1000.milliseconds)
   }
  }

  s"Performer with autoScale" should {
    "result in router and routees created" in {
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(500)
      IdentifyFeyActors.actorsPath should have size(4)
      IdentifyFeyActors.actorsPath should contain("akka://FEY-TEST/system/ENSEMBLE-1/MY-ENSEMBLE-0005")
      IdentifyFeyActors.actorsPath should contain("akka://FEY-TEST/system/ENSEMBLE-1/MY-ENSEMBLE-0005/PERFORMER-PARAMS")
      IdentifyFeyActors.actorsPath should contain("akka://FEY-TEST/system/ENSEMBLE-1/MY-ENSEMBLE-0005/PERFORMER-SCHEDULER")
      IdentifyFeyActors.actorsPath should contain("akka://FEY-TEST/system/ENSEMBLE-1/MY-ENSEMBLE-0005/PERFORMER-SCHEDULER/$a")
    }
  }

}
