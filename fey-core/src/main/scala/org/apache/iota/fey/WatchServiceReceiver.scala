
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

import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{FileSystems, Path}
import java.io.File
import akka.actor.ActorRef
import org.apache.iota.fey.JsonReceiverActor.JSON_RECEIVED
import play.api.libs.json._

import scala.io.Source

class WatchServiceReceiver(receiverActor: ActorRef) extends JsonReceiver{

  private val watchService = FileSystems.getDefault.newWatchService()

  def watch(path: Path) : Unit = path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY)

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

  override def execute(): Unit = {
    processInitialFiles()

    val key = watchService.take()
    val eventsIterator = key.pollEvents().iterator()

    while(eventsIterator.hasNext) {
      val event = eventsIterator.next()
      val relativePath = event.context().asInstanceOf[Path]
      val path = key.watchable().asInstanceOf[Path].resolve(relativePath)

      event.kind() match {
        case (ENTRY_CREATE | ENTRY_MODIFY) if path.toString.endsWith(CONFIG.JSON_EXTENSION) =>
          processJson(path.toString, path.toFile)
        case _ =>
      }
    }

    key.reset()
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
        case None =>
      }
    } catch {
      case e: Exception =>
        log.error(s"File $path will not be processed", e)
    }
  }

  private def processInitialFiles() = {
    Utils.getFilesInDirectory(CONFIG.JSON_REPOSITORY)
      .filter(file => file.getName.endsWith(CONFIG.JSON_EXTENSION))
      .foreach(file => {
        processJson(file.getAbsolutePath, file)
      })
  }

  override def exceptionOnRun(e: Exception): Unit = {
    log.error("Watch Service stopped", e)
    watchService.close()
    throw e
  }

}
