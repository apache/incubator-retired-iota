
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
import akka.testkit.{EventFilter, TestActorRef, TestProbe}
import ch.qos.logback.classic.Level
import play.api.libs.json._
import java.nio.file.{Files, Paths}

import scala.collection.mutable
import scala.io.Source
import scala.concurrent.duration._

class UtilsSpec extends BaseAkkaSpec{

  "Global variable loadedJars" should{
    "be empty when starting" in {
      Utils.loadedJars.remove("fey-test-actor.jar")
      Utils.loadedJars shouldBe empty
    }
  }

  "Executing getFilesInDirectory" should {
    "return a list of all Files in the directory" in {
      val files = Utils.getFilesInDirectory(CONFIG.JAR_REPOSITORY)
      files should not be empty
      files should have size(2)
      val filepath = files.map(_.getAbsolutePath)
      filepath should contain(s"${CONFIG.JAR_REPOSITORY}/fey-test-actor.jar")
      filepath should contain(s"${CONFIG.JAR_REPOSITORY}/dynamic")
    }
  }

  "Executing loadActorClassFromJar with not yet loaded jar" should {
    "result in new entry to global variable loadedJars" in {
      Utils.loadActorClassFromJar(s"${CONFIG.JAR_REPOSITORY}/fey-test-actor.jar", "org.apache.iota.fey.TestActor","fey-test-actor.jar")
      Utils.loadedJars should have size(1)
      Utils.loadedJars should contain key("fey-test-actor.jar")
      Utils.loadedJars.get("fey-test-actor.jar").get._2 should have size(1)
      Utils.loadedJars.get("fey-test-actor.jar").get._2 should contain key("org.apache.iota.fey.TestActor")
    }
  }


  "Executing loadActorClassFromJar with loaded jar but a different class" should {
    "not add new entry to loadedJars" in {
      val loader = Utils.loadedJars.get("fey-test-actor.jar").get._1
      Utils.loadActorClassFromJar(s"${CONFIG.JAR_REPOSITORY}/fey-test-actor.jar", "org.apache.iota.fey.TestActor_2","fey-test-actor.jar")
      Utils.loadedJars should have size(1)
      Utils.loadedJars should contain key("fey-test-actor.jar")
      Utils.loadedJars.get("fey-test-actor.jar").get._1 should equal(loader)
    }
    "add a new classpath to the loadedJars value map" in{
      Utils.loadedJars.get("fey-test-actor.jar").get._2 should have size(2)
      Utils.loadedJars.get("fey-test-actor.jar").get._2 should contain key("org.apache.iota.fey.TestActor")
      Utils.loadedJars.get("fey-test-actor.jar").get._2 should contain key("org.apache.iota.fey.TestActor_2")
    }
  }

  "Executing loadActorClassFromJar with loaded jar and class" should {
    "not reload the jar" in {
      val loader = Utils.loadedJars.get("fey-test-actor.jar").get._1
      Utils.loadActorClassFromJar(s"${CONFIG.JAR_REPOSITORY}/fey-test-actor.jar", "org.apache.iota.fey.TestActor","fey-test-actor.jar")
      Utils.loadedJars.get("fey-test-actor.jar").get._1 should equal(loader)
    }
  }

  var actorRef: ActorRef = _

  "Initializing an actor from a clazz returned by loadActorClassFromJar" should {
    "result in creation of a GenericActor" in {
      val clazz = Utils.loadActorClassFromJar(s"${CONFIG.JAR_REPOSITORY}/fey-test-actor.jar", "org.apache.iota.fey.TestActor_2","fey-test-actor.jar")
      val props = Props(clazz, Map("TEST" -> "TESTED"), 0.seconds, Map.empty, 0.seconds, "MY-ORCH", "ORCH", false)
      val parent = TestProbe("UTILS-PARENT")
      actorRef = TestActorRef[FeyGenericActor](props, parent.ref, "TESTING-UTILS")
    }
    "running GenericActor actor" in{
      val respTB = TestProbe()
      TestProbe().expectActor(actorRef.path.toString)
      actorRef ! (respTB.ref)
      actorRef ! "TEST_ACTOR"
      respTB.expectMsg(Some("TESTED"))
    }
    "respond normally to stop message" in {
      actorRef ! PoisonPill
      TestProbe().verifyActorTermination(actorRef)
      TestProbe().notExpectActor(actorRef.path.toString)
    }
  }

  "Executing loadJsonFromFile with a valid JSON" should {
    "return JsValue" in {
      val json = Utils.loadJsonFromFile(new File(s"${CONFIG.JSON_REPOSITORY}/valid-json.json.not"))
      json shouldBe defined
    }
  }

  "Executing loadJsonFromFile with a invalid JSON" should {
    "return None" in {
      val json = Utils.loadJsonFromFile(new File(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not"))
      json should not be defined
    }
    "Log message at Error Level" in {
      "Could not parse JSON" should beLoggedAt(Level.ERROR)
    }
  }

  "Executing renameProcessedFile when CHECKPOINT is disabled" should {
    "not concatenated extension to the file" in {
      Utils.renameProcessedFile(new File(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not"), "processed")
      Utils.getFilesInDirectory(CONFIG.JSON_REPOSITORY).map(_.getAbsolutePath) should contain(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not")
      Utils.getFilesInDirectory(CONFIG.JSON_REPOSITORY).map(_.getAbsolutePath) should not contain(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not.processed")
    }
  }

  "Executing renameProcessedFile when CHECKPOINT is enabled" should {
    "concatenated extension to the file" in {
      CONFIG.CHEKPOINT_ENABLED = true
      Utils.renameProcessedFile(new File(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not"), "processed")
      Utils.getFilesInDirectory(CONFIG.JSON_REPOSITORY).map(_.getAbsolutePath) should not contain(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not")
      Utils.getFilesInDirectory(CONFIG.JSON_REPOSITORY).map(_.getAbsolutePath) should contain(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not.processed")
      new File(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not.processed").renameTo(new File(s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not"))
      CONFIG.CHEKPOINT_ENABLED = false
    }
  }

  val jsonObj = getJSValueFromString(Utils_JSONTest.orchestration_update2_test_json).as[JsObject]

  "Executing updateOrchestrationState" should {
    "result in log message at Debug when Checkpoint is disables" in {
      Utils.updateOrchestrationState("TEST-15")
      "Checkpoint not enabled" should beLoggedAt(Level.DEBUG)
    }
    "result in log message at warn when Orchestration to be updated is not cached" in {
      CONFIG.CHEKPOINT_ENABLED = true
      Utils.updateOrchestrationState("MY-TEST-UPDATE")
      "Could not save state for Orchestration MY-TEST-UPDATE. It is not active on Fey." should beLoggedAt(Level.WARN)
      CONFIG.CHEKPOINT_ENABLED = false
    }
    "result in creating a new file at Checkpoint dir" in {
      CONFIG.CHEKPOINT_ENABLED = true
      FEY_CACHE.activeOrchestrations.put("TEST_ORCHESTRATION_FOR_UTILS", ("", null))
      ORCHESTRATION_CACHE.orchestration_metadata.put("TEST_ORCHESTRATION_FOR_UTILS",
        Map("ENSEMBLE-UTILS" -> jsonObj))
      Utils.updateOrchestrationState("TEST_ORCHESTRATION_FOR_UTILS")
      Files.exists(Paths.get(s"${CONFIG.CHECKPOINT_DIR}/TEST_ORCHESTRATION_FOR_UTILS.json")) should be(true)
      CONFIG.CHEKPOINT_ENABLED = false
    }
    "result in correct file created" in {
      val file = Source.fromFile(s"${CONFIG.CHECKPOINT_DIR}/TEST_ORCHESTRATION_FOR_UTILS.json").getLines.mkString("")
      val jsonFile = getJSValueFromString(file)
      val ensembles = (jsonFile \ JSON_PATH.ENSEMBLES).as[List[JsObject]]
      ensembles should have size(1)
      Json.stringify(ensembles(0).as[JsValue]) should equal(Json.stringify(jsonObj))
      new File(s"${CONFIG.CHECKPOINT_DIR}/TEST_ORCHESTRATION_FOR_UTILS.json").delete()
    }
  }

}
