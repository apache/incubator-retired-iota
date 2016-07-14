
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

import java.io.FileOutputStream
import java.net.URL
import java.io.File

import com.eclipsesource.schema._
import org.slf4j.LoggerFactory
import play.api.libs.json._
import JSON_PATH._
import java.nio.file.{Files, Paths}

import org.apache.commons.io.IOUtils
import org.apache.commons.codec.binary.Base64
import scala.util.Properties._

/**
  * Basic class to be used when implementing a new JSON receiver
  */
trait JsonReceiver extends Runnable{

  val log = LoggerFactory.getLogger(this.getClass)

  /**
    * Default Run if no one is specified on concret class
    */
  override def run(): Unit = {
    try {
      while (!Thread.currentThread().isInterrupted) {
        execute()
      }
    }catch{
      case e: Exception => exceptionOnRun(e)
    }
  }

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
    (json \ ENSEMBLES).as[List[JsObject]].foreach(ensemble => {
      (ensemble \ PERFORMERS).as[List[JsObject]].foreach(performer => {
        if((performer \ SOURCE).as[JsObject].keys.contains(JAR_LOCATION)){
          val location = (performer \ SOURCE \ JAR_LOCATION).as[JsObject]
          val jarName = (performer \ SOURCE \ SOURCE_NAME).as[String]
          val url = (location \ JAR_LOCATION_URL).as[String].toLowerCase
          if( (url.startsWith("https://") || url.startsWith("http://")) && !jarDownloaded(jarName)){

            val credentials:Option[JsObject] = {
              if(location.keys.contains(JAR_CREDENTIALS_URL)){
                Option((location \ JAR_CREDENTIALS_URL).as[JsObject])
              }else{
                None
              }
            }

            downloadJAR(url, jarName, credentials)
          }
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
        val user = (cred \ JAR_CRED_USER).as[String]
        val password = (cred \ JAR_CRED_PASSWORD).as[String]
        Option(envOrElse(user,user), envOrElse(password,password))
    }
  }

  /**
    * Called inside run method
    */
  def execute(): Unit = {}

  /**
    * Called when occurs an exception inside Run.
    * For example: Thread.interrupt
    *
    * @param e
    */
  def exceptionOnRun(e: Exception): Unit = {}
}

object HttpBasicAuth {
  val BASIC = "Basic"
  val AUTHORIZATION = "Authorization"

  def encodeCredentials(username: String, password: String): String = {
    new String(Base64.encodeBase64((username + ":" + password).getBytes))
  }

  def getHeader(username: String, password: String): String =
    BASIC + " " + encodeCredentials(username, password)
}
