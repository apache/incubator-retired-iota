
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

class IdentifyFeyActorsSpec extends BaseAkkaSpec {

  val identifyRef = system.actorOf(Props[IdentifyFeyActors],"IDENTIFIER")

  "Sending IdentifyFeyActors.IDENTIFY_TREE to IdentifyFeyActors" should {
    s"result in one path added to IdentifyFeyActors.actorsPath" in {
      identifyRef ! IdentifyFeyActors.IDENTIFY_TREE(s"akka://$systemName/user")
      Thread.sleep(1000)
      IdentifyFeyActors.actorsPath.size should equal(1)
    }
    s"result in path 'akka://FEY-TEST/user/IDENTIFIER' " in {
      IdentifyFeyActors.actorsPath should contain("akka://FEY-TEST/user/IDENTIFIER")
    }
  }

  var actor2: ActorRef = _

  "Creating a new actor in the system and sending IdentifyFeyActors.IDENTIFY_TREE to IdentifyFeyActors" should {
    s"result in two paths added to IdentifyFeyActors.actorsPath" in {
      actor2 = system.actorOf(Props[Monitor],"MONITOR")
      identifyRef ! IdentifyFeyActors.IDENTIFY_TREE(s"akka://$systemName/user")
      Thread.sleep(1000)
      IdentifyFeyActors.actorsPath.size should equal(2)
    }
    s"result in matching paths" in {
      IdentifyFeyActors.actorsPath should contain("akka://FEY-TEST/user/IDENTIFIER")
      IdentifyFeyActors.actorsPath should contain("akka://FEY-TEST/user/MONITOR")
    }
  }

  "Stopping precious added actor and sending IdentifyFeyActors.IDENTIFY_TREE to IdentifyFeyActors" should {
    "result in going back to have just one path added to IdentifyFeyActors.actorsPath" in {
      identifyRef ! IdentifyFeyActors.IDENTIFY_TREE(s"akka://$systemName/user")
      Thread.sleep(1000)
      IdentifyFeyActors.actorsPath.size should equal(2)
    }
    s"result in path 'akka://FEY-TEST/user/IDENTIFIER' " in {
      IdentifyFeyActors.actorsPath should contain("akka://FEY-TEST/user/IDENTIFIER")
    }
  }
}
