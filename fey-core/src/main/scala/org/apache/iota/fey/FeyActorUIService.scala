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