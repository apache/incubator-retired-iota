
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

import java.io.{File, FileOutputStream}
import java.net.URL
import java.nio.file.{Files, Paths}
import com.eclipsesource.schema._
import akka.actor.ActorRef
import com.eclipsesource.schema.SchemaValidator
import org.apache.commons.io.IOUtils
import play.api.libs.json._
import scala.concurrent.duration._
import scala.util.Properties._

abstract class FeyGenericActorReceiver(override val params: Map[String,String] = Map.empty,
                                       override val backoff: FiniteDuration = 1.minutes,
                                       override val connectTo: Map[String,ActorRef] = Map.empty,
                                       override val schedulerTimeInterval: FiniteDuration = 2.seconds,
                                       override val orchestrationName: String = "",
                                       override val orchestrationID: String = "",
                                       override val autoScale: Boolean = false) extends FeyGenericActor{

  private[fey] val feyCore = FEY_CORE_ACTOR.actorRef

  override final def processMessage[T](message: T, sender: ActorRef): Unit = {
    try {
      val jsonString = getJSONString(message)
      processJson(jsonString)
      startBackoff()
    }catch{
      case e: Exception => log.error(e, s"Could not process message $message")
    }
  }

  private[fey] def processJson(jsonString: String) = {
    var orchID:String = "None"
    try{
      val orchestrationJSON = Json.parse(jsonString)
      orchID = (orchestrationJSON \ JSON_PATH.GUID).as[String]
      val valid = validJson(orchestrationJSON)
      if(valid && (orchestrationJSON \ JSON_PATH.COMMAND).as[String].toUpperCase != "DELETE"){
        checkForLocation(orchestrationJSON)
      }
      if(valid) {
        feyCore ! FeyCore.ORCHESTRATION_RECEIVED(orchestrationJSON, None)
      }else{
        log.warning(s"Could not forward Orchestration $orchID. Invalid JSON schema")
      }
    } catch {
      case e: Exception =>
        log.error(e, s"Orchestration $orchID could not be forwarded")
    }
  }

  /**
    * Return a JSON string
    * @param input the received process message
    * @tparam T
    * @return String that can be converted to JSON
    */
  def getJSONString[T](input: T): String

  /**
    * Checks if JSON complies with defined Schema
    *
    * @param json
    * @return true if it complies or false if it does not
    */
  final def validJson(json: JsValue): Boolean = {
    try {
      val result = SchemaValidator.validate(CONFIG.JSON_SPEC, json)
      if (result.isError) {
        log.error("Incorrect JSON schema \n" + result.asEither.left.get.toJson.as[List[JsObject]].map(error => {
          val path = (error \ "instancePath").as[String]
          val msg = (error \ "msgs").as[List[String]].mkString("\n\t")
          s"$path \n\tErrors: $msg"
        }).mkString("\n"))
        false
      } else {
        true
      }
    }catch{
      case e: Exception =>
        log.error("Error while validating JSON", e)
        false
    }
  }

  /**
    * Checks if any of the performers need to have its jar downloaded
    * All the Receivers must call this method so the Jars can be downloaded at runtime
    *
    * @param json Orchestration JSON object
    */
  final def checkForLocation(json: JsValue): Unit = {
    (json \ JSON_PATH.ENSEMBLES).as[List[JsObject]].foreach(ensemble => {
      (ensemble \ JSON_PATH.PERFORMERS).as[List[JsObject]].foreach(performer => {
        if((performer \ JSON_PATH.SOURCE).as[JsObject].keys.contains(JSON_PATH.JAR_LOCATION)){
          val location = (performer \ JSON_PATH.SOURCE \ JSON_PATH.JAR_LOCATION).as[JsObject]
          val jarName = (performer \ JSON_PATH.SOURCE \ JSON_PATH.SOURCE_NAME).as[String]
          val url = (location \ JSON_PATH.JAR_LOCATION_URL).as[String].toLowerCase
          if( (url.startsWith("https://") || url.startsWith("http://")) && !jarDownloaded(jarName)){

            val credentials:Option[JsObject] = {
              if(location.keys.contains(JSON_PATH.JAR_CREDENTIALS_URL)){
                Option((location \ JSON_PATH.JAR_CREDENTIALS_URL).as[JsObject])
              }else{
                None
              }
            }

            downloadJAR(url, jarName, credentials)
          }
        }else{
          log.debug("Location not defined in JSON")
        }
      })
    })
  }

  /**
    * Checks if the jar already exists
    *
    * @param jarName
    * @return
    */
  private final def jarDownloaded(jarName: String): Boolean = {
    try {
      Files.exists(Paths.get(s"${CONFIG.DYNAMIC_JAR_REPO}/$jarName"))
    }catch{
      case e: Exception =>
        log.error(s"Could not check if $jarName exists", e)
        true
    }
  }

  private final def downloadJAR(url: String, jarName: String, credentials: Option[JsObject]): Unit = {
    var outputStream: FileOutputStream = null
    try{
      log.info(s"Downloading $jarName from $url")

      val connection = new URL(s"$url/$jarName").openConnection

      resolveCredentials(credentials) match{
        case Some(userpass) =>
          connection.setRequestProperty(HttpBasicAuth.AUTHORIZATION, HttpBasicAuth.getHeader(userpass._1, userpass._2))
        case None =>
      }

      // Download Jar
      outputStream = new FileOutputStream(s"${CONFIG.DYNAMIC_JAR_REPO}/$jarName")
      IOUtils.copy(connection.getInputStream,outputStream)
      outputStream.close()

    }catch{
      case e: Exception =>
        if(outputStream != null) {
          outputStream.close()
          (new File(s"${CONFIG.DYNAMIC_JAR_REPO}/$jarName")).delete()
        }
        log.error(s"Could not download $jarName from $url", e)
    }
  }

  /**
    * Tries to resolve the credentials looking to the environment variable
    * If it is not possible to find a env var with that name, then use the name itself
    * @param credentials
    * @return (user, password)
    */
  def resolveCredentials(credentials: Option[JsObject]):Option[(String, String)] = {
    credentials match {
      case None => None
      case Some(cred) =>
        val user = (cred \ JSON_PATH.JAR_CRED_USER).as[String]
        val password = (cred \ JSON_PATH.JAR_CRED_PASSWORD).as[String]
        Option(envOrElse(user,user), envOrElse(password,password))
    }
  }

}
