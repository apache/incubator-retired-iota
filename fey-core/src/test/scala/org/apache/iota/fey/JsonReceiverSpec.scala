
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

import akka.actor.ActorRef
import akka.testkit.{EventFilter, TestProbe}
import ch.qos.logback.classic.Level
import scala.concurrent.duration.{DurationInt, FiniteDuration}

class JsonReceiverSpec extends BaseAkkaSpec with LoggingTest{


  class ReceiverTest(verifyActor: ActorRef) extends JsonReceiver{

    override def execute(): Unit = {
      verifyActor ! "EXECUTED"
      Thread.sleep(500)
    }

    override def exceptionOnRun(e: Exception): Unit = {
      verifyActor ! "INTERRUPTED"
    }

  }

  val verifyTB = TestProbe("RECEIVER-TEST")
  val receiver = new ReceiverTest(verifyTB.ref)

  "Executing validJson in JsonReceiver" should {
    "return false when json schema is not right" in {
      receiver.validJson(getJSValueFromString(Utils_JSONTest.test_json_schema_invalid)) should be(false)
    }
    "log message to Error" in {
      ("Incorrect JSON schema \n/ensembles/0 \n\tErrors: Property command missing") should beLoggedAt(Level.ERROR)
    }
    "return true when Json schema is valid" in {
      receiver.validJson(getJSValueFromString(Utils_JSONTest.create_json_test)) should be(true)
    }
  }

  "Executing checkForLocation in JsonReceiver" should {
    "log message at Debug level" in {
      receiver.checkForLocation(getJSValueFromString(Utils_JSONTest.test_json_schema_invalid))
      "Location not defined in JSON" should beLoggedAt(Level.DEBUG)
    }
    "download jar dynamically from URL" in {
      receiver.checkForLocation(getJSValueFromString(Utils_JSONTest.location_test))
      Files.exists(Paths.get(s"${CONFIG.DYNAMIC_JAR_REPO}/fey-stream.jar"))
    }
  }

  var watchThread: Thread = _
  "Start a Thread with the JSON receiver" should {
    "Start Thread" in {
      watchThread = new Thread(receiver, "TESTING-RECEIVERS-IN-THREAD")
      watchThread.setDaemon(true)
      watchThread.start()
      TestProbe().isThreadRunning("TESTING-RECEIVERS-IN-THREAD") should be(true)
    }
    "execute execute() method inside run" in {
      verifyTB.expectMsgAllOf(600.milliseconds,"EXECUTED","EXECUTED")
    }
  }

  "Interrupting the receiver Thread" should {
    "Throw Interrupted exception" in {
      EventFilter[InterruptedException]() intercept {
        watchThread.interrupt()
        watchThread.join()
      }
    }
    "execute exceptionOnRun method" in {
      verifyTB.receiveWhile(1200.milliseconds) {
        case "EXECUTED" =>
      }
      verifyTB.expectMsg("INTERRUPTED")
    }
  }


}
