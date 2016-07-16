
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
import java.nio.file.Paths

import akka.actor.{ActorIdentity, ActorRef, ActorSystem, Identify}
import akka.testkit.{EventFilter, TestActorRef, TestEvent, TestProbe}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.apache.commons.io.FileUtils
import org.scalatest.BeforeAndAfterAll
import play.api.libs.json._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.Await

class BaseAkkaSpec extends BaseSpec with BeforeAndAfterAll{

  createFeyTmpDirectoriesForTest()
  val conf = getClass.getResource("/test-fey-configuration.conf")
  CONFIG.loadUserConfiguration(Paths.get(conf.toURI()).toFile().getAbsolutePath)
  copyTestActorToTmp()

  implicit val system = ActorSystem("FEY-TEST", ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))
  system.eventStream.publish(TestEvent.Mute(EventFilter.debug()))
  system.eventStream.publish(TestEvent.Mute(EventFilter.info()))
  system.eventStream.publish(TestEvent.Mute(EventFilter.warning()))
  system.eventStream.publish(TestEvent.Mute(EventFilter.error()))

  override protected def afterAll(): Unit = {
    Await.ready(system.terminate(), 20.seconds)
  }

  private def copyTestActorToTmp(): Unit = {
    val jarTest = getClass.getResource("/fey-test-actor.jar")
    val dest = new File(s"${CONFIG.JAR_REPOSITORY}/fey-test-actor.jar")
    FileUtils.copyURLToFile(jarTest, dest)
  }

  private def createFeyTmpDirectoriesForTest(): Unit = {
    var file = new File(s"/tmp/fey/test/checkpoint")
    file.mkdirs()
    file = new File(s"/tmp/fey/test/json")
    file.mkdirs()
    file = new File(s"/tmp/fey/test/jars")
    file.mkdirs()
    file = new File(s"/tmp/fey/test/jars/dynamic")
    file.mkdirs()
  }

  implicit class TestProbeOps(probe: TestProbe) {

    def expectActor(path: String, max: FiniteDuration = 3.seconds): ActorRef = {
      probe.within(max) {
        var actor = null: ActorRef
        probe.awaitAssert {
          (probe.system actorSelection path).tell(Identify(path), probe.ref)
          probe.expectMsgPF(100 milliseconds) {
            case ActorIdentity(`path`, Some(ref)) => actor = ref
          }
        }
        actor
      }
    }

    def verifyActorTermination(actor: ActorRef)(implicit system: ActorSystem): Unit = {
      val watcher = TestProbe()
      watcher.watch(actor)
      watcher.expectTerminated(actor)
    }

    def notExpectActor(path: String, max: FiniteDuration = 3.seconds): Unit = {
      probe.within(max) {
        probe.awaitAssert {
          (probe.system actorSelection path).tell(Identify(path), probe.ref)
          probe.expectMsgPF(100 milliseconds) {
            case ActorIdentity(`path`, None) =>
          }
        }
      }
    }

    def isThreadRunning(threadName: String): Boolean = {
      Thread.getAllStackTraces.keySet().toArray
        .map(_.asInstanceOf[Thread])
        .find(_.getName == threadName) match {
        case Some(thread) =>
          if(thread.isAlive) true else false
        case None => false
      }
    }
  }

  //Utils Functions
  def getJSValueFromString(json: String): JsValue = {
    Json.parse(json)
  }

  def getActorRefFromPath(path: String, timeout: Timeout = 2.seconds): ActorRef = {
    Await.result(
      system.actorSelection("akka.tcp://REMOTE@192.168.0.136:2552/user/Testing")
        .resolveOne()(timeout), 5.seconds)
  }
}


