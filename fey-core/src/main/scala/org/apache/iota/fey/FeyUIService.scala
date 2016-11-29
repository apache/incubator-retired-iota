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

import org.apache.iota.fey.FeyCore.JSON_TREE
import play.api.BuiltInComponents
import play.api.http.DefaultHttpErrorHandler
import play.api.libs.json.Json
import play.api.mvc._
import play.api.routing.Router
import play.api.routing.sird._
import play.core.server._
import CONFIG._

import scala.concurrent.Future

object FeyUIService {


  val components = new FeyUIService(URL_PATH, PORT)
  val server = components.server
}

class FeyUIService(urlPath: String, port: Int) extends NettyServerComponents with BuiltInComponents {

  val THREAD_SLEEP_TIME = 2000
  lazy val router = Router.from {
    case GET(p"/fey/activeactors") => Action {
      FEY_CORE_ACTOR.actorRef ! JSON_TREE
      Thread.sleep(THREAD_SLEEP_TIME)
      val json = IdentifyFeyActors.generateTreeJson()
      val jsonTree: String = IdentifyFeyActors.getHTMLTree(json)
      Results.Ok(jsonTree).as("text/html")
    }
    case GET(p"/fey/actorslifecycle") => Action {
      val jsonTree = Json.stringify(Monitor.events.printWithEvents)
      Results.Ok(jsonTree).as("application/json")
    }
    case GET(p"/fey/monitoringevents") => Action {
      val returnValue: String = try {
        if (CONFIG.MONITORING_TYPE == "COMPLETE") {
          Monitor.getHTMLevents
        } else {
          Monitor.getSimpleHTMLEvents
        }
      } catch {
        case e: Exception => ""
      }
      Results.Ok(returnValue).as("text/html")
    }
  }

  override lazy val serverConfig = ServerConfig(
    port = Some(port),
    address = urlPath
  )
  override lazy val httpErrorHandler = new DefaultHttpErrorHandler(environment,
    configuration, sourceMapper, Some(router)) {

    override protected def onNotFound(request: RequestHeader, message: String) = {
      Future.successful(Results.NotFound("NO_DATA_FOUND"))
    }
  }
}
