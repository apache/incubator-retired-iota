
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
          val jarName = (performer \ SOURCE \ SOURCE_NAME).as[String]
          val jarLocation = (performer \ SOURCE \ JAR_LOCATION).as[String].toLowerCase
          if( (jarLocation.startsWith("https://") || jarLocation.startsWith("http://")) && !jarDownloaded(jarName)){
            val jarLocation = (performer \ SOURCE \ JAR_LOCATION).as[String]
            downloadJAR(jarLocation, jarName)
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

  private final def downloadJAR(url: String, jarName: String): Unit = {
    var outputStream: FileOutputStream = null
    try{
      val extractedURL = extractCredentials(s"$url/$jarName")
      log.info(s"Downloading $jarName from ${extractedURL._1}")

      val connection = new URL(extractedURL._1).openConnection

      // Add authentication Header if credentials is defined
      extractedURL._2 match {
        case Some(credentials) =>
          connection.setRequestProperty(HttpBasicAuth.AUTHORIZATION, HttpBasicAuth.getHeader(credentials._1, credentials._2))
        case None =>
      }
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
    *
    * @param url
    * @return (NO_CRED_URL, (USER, PASSWORD))
    */
  private final def extractCredentials(url: String): (String, Option[(String, String)]) = {
    if(url.contains("@")) {
      val atIndex = url.indexOf("@")
      if (url.startsWith("https")) {
        val cred = url.substring(8, atIndex)
        val userPass = cred.split(":")
        (url.replace(s"$cred@",""), Option(userPass(0),userPass(1)))
      } else {
        val cred = url.substring(7, atIndex)
        val userPass = cred.split(":")
        (url.replace(s"$cred@",""), Option(userPass(0),userPass(1)))
      }
    }else{
      (url, None)
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
  val BASIC = "Basic";
  val AUTHORIZATION = "Authorization";

  def encodeCredentials(username: String, password: String): String = {
    new String(Base64.encodeBase64String((username + ":" + password).getBytes))
  }

  def getHeader(username: String, password: String): String =
    BASIC + " " + encodeCredentials(username, password)
}
