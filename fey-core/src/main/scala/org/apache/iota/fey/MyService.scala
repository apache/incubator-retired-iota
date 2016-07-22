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

import akka.actor.Actor
import org.apache.iota.fey.FeyCore.JSON_TREE
import play.api.libs.json.Json
import spray.http.MediaTypes._
import spray.routing._

class MyServiceActor extends Actor with MyService {

  // the HttpService trait defines only one abstract member, which
  // connects the services environment to the enclosing actor or test
  def actorRefFactory = context

  // this actor only runs our route, but you could add
  // other things here, like request stream processing
  // or timeout handling
  def receive = runRoute(myRoute)
}

sealed trait MyService extends HttpService {

  val home = pathPrefix("fey")
  val activeActors = path("activeactors")
  val actorLifecycle = path("actorslifecycle")
  val eventsTable = path("monitoringevents")
  val test = path("test")

  val myRoute =
    home {
      activeActors {
        get{
          respondWithMediaType(`text/html`) {
            complete {
              FEY_CORE_ACTOR.actorRef ! JSON_TREE
              Thread.sleep(2000)
              val json = IdentifyFeyActors.generateTreeJson()
              IdentifyFeyActors.getHTMLTree(json)
            }
          }
        }
      } ~
      actorLifecycle {
        get{
          respondWithMediaType(`application/json`) {
            complete {
              Json.stringify(Monitor.events.printWithEvents)
            }
          }
        }
      } ~
        eventsTable {
        get{
          respondWithMediaType(`text/html`) {
            complete {
              Monitor.getHTMLevents
            }
          }
        }
      }
    }

}