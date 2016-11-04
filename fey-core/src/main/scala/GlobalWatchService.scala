
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

import java.nio.file.{Files, Path, Paths, WatchEvent}

import akka.actor.{Actor, ActorLogging, ActorRef}
import org.apache.iota.fey.GlobalWatchService.{ENTRY_CREATED, REGISTER_WATCHER_PERFORMER}
import org.apache.iota.fey.WatchingDirectories.STOPPED

class GlobalWatchService extends Actor with ActorLogging{

  //WatchService
  var watchThread:Thread = null
  val watchFileTask:GlobalWatchServiceTask = new GlobalWatchServiceTask(self)

  override def preStart(): Unit = {
    startWatcher("PRE-START")
  }

  override def postStop(): Unit = {
    stopWatcher("POST-STOP")
  }

  private def startWatcher(from: String) = {
    log.info(s"Starting Global Watcher from $from")
    watchThread = new Thread(watchFileTask, "FEY_GLOBAL_WATCH_SERVICE_PERFORMERS")
    watchThread.setDaemon(true)
    watchThread.start()
  }

  private def stopWatcher(from: String) = {
    log.info(s"Stopping Global Watcher from $from")
    if(watchThread != null && watchThread.isAlive){
      watchThread.interrupt()
      watchThread = null
    }
  }

  override def receive: Receive = {
    case REGISTER_WATCHER_PERFORMER(path, file_name, actor, events, loadExists) =>
      registerPath(path,file_name,actor,events,loadExists)
    case STOPPED =>
      stopWatcher("STOPPED-THREAD")
      startWatcher("STOPPED-THREAD")
    case x => log.error(s"Unknown message $x")
  }

  private def broadcastMessageIfFileExists(actor: ActorRef, pathWithFile: String) = {
    val filePath = Paths.get(pathWithFile)
    if(Files.exists(filePath)){
      log.info(s"File $pathWithFile exists. Broadcasting message to actor ${actor.path.toString}")
      actor ! GlobalWatchService.ENTRY_CREATED(filePath)
    }
  }

  private def registerPath(dir_path: String, file_name:Option[String], actor: ActorRef, events: Array[WatchEvent.Kind[_]], loadExists: Boolean) = {
    WatchingDirectories.actorsInfo.get((dir_path,file_name)) match {
      case Some(info) =>
        val newInfo:Map[WatchEvent.Kind[_], Array[ActorRef]] = events.map(event => {
          info.get(event) match {
            case Some(actors) => (event, (Array(actor) ++ actors))
            case None => (event, Array(actor))
          }
        }).toMap
        WatchingDirectories.actorsInfo.put((dir_path,file_name), info ++ newInfo)
        watchFileTask.watch(Paths.get(dir_path),actor.path.toString,events)
      case None =>
        val tmpEvents:Map[WatchEvent.Kind[_], Array[ActorRef]] = events.map(event => {(event, Array(actor))}).toMap
        WatchingDirectories.actorsInfo.put((dir_path,file_name), tmpEvents)
        watchFileTask.watch(Paths.get(dir_path),actor.path.toString,events)
    }

    if(file_name.isDefined && loadExists){
      log.info(s"Checking if file $dir_path/${file_name.get} already exist")
      broadcastMessageIfFileExists(actor, s"$dir_path/${file_name.get}")
    }

  }

}

object GlobalWatchService{
  sealed case class ENTRY_CREATED(path:Path)
  sealed case class ENTRY_MODIFIED(path:Path)
  sealed case class ENTRY_DELETED(path:Path)
  sealed case class REGISTER_WATCHER_PERFORMER(dir_path: String, file_name:Option[String],
                                               actor: ActorRef, events: Array[WatchEvent.Kind[_]],
                                               loadIfExists: Boolean)
}