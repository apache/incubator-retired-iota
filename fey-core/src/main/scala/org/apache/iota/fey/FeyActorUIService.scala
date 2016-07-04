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


import com.typesafe.config.ConfigFactory
import org.apache.iota.fey.FeyCore.JSON_TREE

import play.core.server._
import play.api.routing.Router
import play.api.routing.sird._
import play.api.mvc._
import play.api.BuiltInComponents
import play.api.http.DefaultHttpErrorHandler
import scala.concurrent.Future

object FeyActorUIService {
  val config = ConfigFactory.load()
  val port = config.getInt("port")
  val urlPath = config.getString("urlPath")
  val components = new NettyServerComponents with BuiltInComponents {

    val ACTIVE_ACTORS_PATH = "fey/activeactors"

    lazy val router = Router.from {
      case GET(pACTIVE_ACTORS_PATH) => Action {
        Application.fey ! JSON_TREE
        Thread.sleep(2000)
        val json = IdentifyFeyActors.generateTreeJson()
        val jsonTree: String = IdentifyFeyActors.getHTMLTree(json)
        Results.Ok(jsonTree).as("text/html")
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
  val server = components.server
}