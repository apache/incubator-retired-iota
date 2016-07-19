
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

import akka.actor.Props
import akka.testkit.{EventFilter, TestActorRef, TestProbe}

class MonitorSpec extends BaseAkkaSpec{

  val parent = TestProbe("MONITOR-PARENT")
  val events = new Trie(systemName)
  val monitorRef: TestActorRef[Monitor] = TestActorRef[Monitor]( Props(new Monitor(events)),parent.ref, "MONITOR-TEST")
  val test_sender = TestProbe("MONITOR-SENDER")
  var monitor1Node:TrieNode = _

  "Creating a Monitor actor" should {
    "result in creation of one actor in the system" in{
      TestProbe().expectActor(s"${parent.ref.path}/MONITOR-TEST")
    }
    "result in one active actors" in{
      globalIdentifierRef ! IdentifyFeyActors.IDENTIFY_TREE(parent.ref.path.toString)
      Thread.sleep(200)
      IdentifyFeyActors.actorsPath should have size(1)
      IdentifyFeyActors.actorsPath should contain(s"${parent.ref.path}/MONITOR-TEST")
    }
  }

  "Sending Monitor.START to monitor actor" should {
    "result in logging START message" in {
      EventFilter.info(message = s"START | 123 | ${test_sender.ref.path} | STARTINFO", occurrences = 1) intercept {
        test_sender.send(monitorRef,Monitor.START(123,"STARTINFO"))
      }
    }
    "result in new entry to events Trie" in {
      events.elements should be(2)
      events.hasPath(test_sender.ref.path.toString) should be(true)
      val tmp = events.getNode(test_sender.ref.path.toString)
      tmp shouldBe defined
      monitor1Node = tmp.get
      monitor1Node.events should have size(1)
      monitor1Node.events should contain(Monitor.MonitorEvent("START", 123, "STARTINFO"))
    }
  }

  "Sending Monitor.STOP to monitor actor" should {
    "result in logging STOP message" in {
      EventFilter.info(message = s"STOP | 789 | ${test_sender.ref.path} | STOPINFO", occurrences = 1) intercept {
        test_sender.send(monitorRef,Monitor.STOP(789,"STOPINFO"))
      }
    }
    "not add new path to events Trie" in {
      events.elements should be(2)
      events.hasPath(test_sender.ref.path.toString) should be(true)
    }
    "add new event to TrieNode" in{
      monitor1Node.events should have size(2)
      monitor1Node.events should contain(Monitor.MonitorEvent("START", 123, "STARTINFO"))
      monitor1Node.events should contain(Monitor.MonitorEvent("STOP", 789, "STOPINFO"))
    }
  }

  "Sending Monitor.RESTART to monitor actor" should {
    "result in logging RESTART message" in {
      EventFilter[IllegalArgumentException](occurrences = 1) intercept {
        test_sender.send(monitorRef,Monitor.RESTART(new IllegalArgumentException("MONITOR-TEST"), 123))
      }
    }
    "not add new path to events Trie" in {
      events.elements should be(2)
      events.hasPath(test_sender.ref.path.toString) should be(true)
    }
    "add new event to TrieNode" in{
      monitor1Node.events should have size(3)
      monitor1Node.events should contain(Monitor.MonitorEvent("START", 123, "STARTINFO"))
      monitor1Node.events should contain(Monitor.MonitorEvent("STOP", 789, "STOPINFO"))
      val exc = new IllegalArgumentException("MONITOR-TEST")
      monitor1Node.events should contain(Monitor.MonitorEvent("RESTART", 123, exc.getMessage))
    }
  }

  "Sending Monitor.TERMINATE to monitor actor" should {
    "result in logging TERMINATE message" in {
      EventFilter.info(message = s"TERMINATE | 789 | ${test_sender.ref.path} | TERMINATEINFO", occurrences = 1) intercept {
       monitorRef ! Monitor.TERMINATE(test_sender.ref.path.toString,789,"TERMINATEINFO")
      }
    }
    "not add new path to events Trie" in {
      events.elements should be(2)
      events.hasPath(test_sender.ref.path.toString) should be(true)
    }
    "add new event to TrieNode" in{
      monitor1Node.events should have size(4)
      monitor1Node.events should contain(Monitor.MonitorEvent("START", 123, "STARTINFO"))
      monitor1Node.events should contain(Monitor.MonitorEvent("STOP", 789, "STOPINFO"))
      val exc = new IllegalArgumentException("MONITOR-TEST")
      monitor1Node.events should contain(Monitor.MonitorEvent("RESTART", 123, exc.getMessage))
      monitor1Node.events should contain(Monitor.MonitorEvent("TERMINATE", 789, "TERMINATEINFO"))
    }
  }

  "Calling logInfo from a Monitor actor" should {
    "result in logging info when no throwable is specified" in {
      EventFilter.info(message = s"STOP | 1 | path | info", occurrences = 1) intercept {
        monitorRef.underlyingActor.logInfo("path", "STOP", 1, "info")
      }
    }
    "result in logging error when throwable is specified" in {
      EventFilter[IllegalArgumentException](occurrences = 1) intercept {
        monitorRef.underlyingActor.logInfo("path", "STOP", 1, "info", new IllegalArgumentException("Test"))
      }
    }
  }

  var test_sender2 = TestProbe("MONITOR-SENDER-2")

  "Sending Monitor.START from a different sender to monitor actor" should {
    "result in logging START message" in {
      EventFilter.info(message = s"START | 123 | ${test_sender2.ref.path} | STARTINFO2", occurrences = 1) intercept {
        test_sender2.send(monitorRef,Monitor.START(123,"STARTINFO2"))
      }
    }
    "result in new entry to events Trie" in {
      events.elements should be(3)
      events.hasPath(test_sender2.ref.path.toString) should be(true)
      val tmpNode = events.getNode(test_sender2.ref.path.toString)
      tmpNode shouldBe defined
      tmpNode.get.events should have size(1)
      tmpNode.get.events should contain(Monitor.MonitorEvent("START", 123, "STARTINFO2"))
    }
  }

}
