
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

import java.nio.file.Paths

import akka.actor.{ActorIdentity, ActorRef, ActorSystem, Identify, Props}
import akka.testkit.{EventFilter, TestActorRef, TestEvent, TestProbe}
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import play.api.libs.json._

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.concurrent.Await

class BaseAkkaSpec extends BaseSpec with BeforeAndAfterAll{

  //Load default configuration for Fey when running tests
  CONFIG.loadUserConfiguration(Paths.get(TestSetup.configTest.toURI()).toFile().getAbsolutePath)
  TestSetup.setup()

  val systemName = "FEY-TEST"
  implicit val system = ActorSystem(systemName, ConfigFactory.parseString("""akka.loggers = ["akka.testkit.TestEventListener"]"""))
  system.eventStream.publish(TestEvent.Mute(EventFilter.debug()))
  system.eventStream.publish(TestEvent.Mute(EventFilter.info()))
  system.eventStream.publish(TestEvent.Mute(EventFilter.warning()))
  system.eventStream.publish(TestEvent.Mute(EventFilter.error()))

  val globalIdentifierName = "GLOBAL-IDENTIFIER"
  val globalIdentifierRef = system.actorOf(Props[IdentifyFeyActors],globalIdentifierName)

  override protected def afterAll(): Unit = {
    Monitor.events.removeAllNodes()
    Await.ready(system.terminate(), 20.seconds)
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

}


