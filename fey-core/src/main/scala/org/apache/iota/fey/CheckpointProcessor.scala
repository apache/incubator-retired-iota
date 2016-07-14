
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

import java.io.File

import akka.actor.ActorRef
import org.apache.iota.fey.JsonReceiverActor.JSON_RECEIVED
import play.api.libs.json.{JsValue, Json}

import scala.io.Source

/**
  * Altough checkpoint processor is not a receiver, it will use the same principle
  * as a receiver.
  * It will run just once, when the application starts.
  * @param receiverActor
  */
class CheckpointProcessor(receiverActor: ActorRef) extends JsonReceiver{

  override def run(): Unit = {
    processCheckpointFiles()
  }

  def getJsonObject(params: String): Option[JsValue] = {
    try{
      val stringJson = Source.fromFile(params).getLines.mkString
      Option(Json.parse(stringJson))
    }catch{
      case e: Exception =>
        log.error("Could not parse JSON", e)
        None
    }
  }

  private def processJson(path: String, file: File) = {
    try{
      getJsonObject(path) match {
        case Some(orchestrationJSON) =>
          val valid = validJson(orchestrationJSON)
          if(valid && (orchestrationJSON \ JSON_PATH.COMMAND).as[String].toUpperCase != "DELETE"){
            checkForLocation(orchestrationJSON)
          }
          if(valid) {
            receiverActor ! JSON_RECEIVED(orchestrationJSON, file)
          }else{
            log.warn(s"File $path not processed. Incorrect JSON schema")
          }
          file.delete()
        case None =>
      }
    } catch {
      case e: Exception =>
        log.error(s"File $path will not be processed", e)
    }
  }

  private def processCheckpointFiles() = {
    Utils.getFilesInDirectory(CONFIG.CHECKPOINT_DIR)
      .filter(file => file.getName.endsWith(CONFIG.JSON_EXTENSION))
      .foreach(file => {
        processJson(file.getAbsolutePath, file)
      })
  }
}
