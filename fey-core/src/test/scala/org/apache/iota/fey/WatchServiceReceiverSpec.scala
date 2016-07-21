
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
import java.nio.charset.StandardCharsets

import akka.testkit.{EventFilter, TestProbe}

import scala.concurrent.duration.{DurationInt, FiniteDuration}
import java.io.File

import ch.qos.logback.classic.Level

class WatchServiceReceiverSpec extends BaseAkkaSpec{

  val watcherTB = TestProbe("WATCH-SERVICE")
  var watchFileTask:WatchServiceReceiver = _
  val watchTestDir = s"${CONFIG.JSON_REPOSITORY}/watchtest"

  "Creating WatchServiceReceiver" should {
    "process initial files in the JSON repository" in {
      CONFIG.JSON_EXTENSION = "json.not"
      watchFileTask = new WatchServiceReceiver(watcherTB.ref)
      watcherTB.expectMsgAllClassOf(classOf[JsonReceiverActor.JSON_RECEIVED])
      CONFIG.JSON_EXTENSION = "json.test"
    }
  }

  var watchThread: Thread = _
  "Start a Thread with WatchServiceReceiver" should {
    "Start Thread" in {
      watchThread = new Thread(watchFileTask, "TESTING-WATCHER-IN-THREAD")
      watchThread.setDaemon(true)
      watchThread.start()
      TestProbe().isThreadRunning("TESTING-WATCHER-IN-THREAD") should be(true)
    }
  }

  "Start watching directory" should {
    "Starting receiving CREATED event" taggedAs(SlowTest) in {
      watchFileTask.watch(Paths.get(watchTestDir))
      Files.write(Paths.get(s"$watchTestDir/watched.json.test"), Utils_JSONTest.create_json_test.getBytes(StandardCharsets.UTF_8))
      watcherTB.expectMsgAllClassOf(20.seconds, classOf[JsonReceiverActor.JSON_RECEIVED])
    }
    "Starting receiving UPDATE event" taggedAs(SlowTest) in {
      Files.write(Paths.get(s"$watchTestDir/watched-update.json.test"), Utils_JSONTest.delete_json_test.getBytes(StandardCharsets.UTF_8))
      Thread.sleep(200)
      Files.write(Paths.get(s"$watchTestDir/watched-update.json.test"), Utils_JSONTest.create_json_test.getBytes(StandardCharsets.UTF_8))
      watcherTB.expectMsgAllClassOf(20.seconds, classOf[JsonReceiverActor.JSON_RECEIVED])
    }
  }

  "processJson" should {
    "log to warn level when json has invalid schema" in {
      Files.write(Paths.get(s"$watchTestDir/watched-invalid.json.test"), Utils_JSONTest.test_json_schema_invalid.getBytes(StandardCharsets.UTF_8))
      watchFileTask.processJson(s"$watchTestDir/watched-invalid.json.test",new File(s"$watchTestDir/watched-invalid.json.test"))
      s"File $watchTestDir/watched-invalid.json.test not processed. Incorrect JSON schema" should beLoggedAt(Level.WARN)
    }
  }

  "interrupt watchservice" should{
    "interrupt thread" in {
      watchThread.interrupt()
    }
  }

}
