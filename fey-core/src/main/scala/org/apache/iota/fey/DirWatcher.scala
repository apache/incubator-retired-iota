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
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{FileSystems, Path}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}

/**
  *
  * @param watcherActor
  */
class WatchDirectoryTask(watcherActor: ActorRef) extends Runnable {

  private val watchService = FileSystems.getDefault.newWatchService()

  def watch(path: Path) = path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY)

  override def run() {
    try {
      while (!Thread.currentThread().isInterrupted) {
        val key = watchService.take()
        val eventsIterator = key.pollEvents().iterator()
        while(eventsIterator.hasNext) {
          val event = eventsIterator.next()
          val relativePath = event.context().asInstanceOf[Path]
          val path = key.watchable().asInstanceOf[Path].resolve(relativePath)
          event.kind() match {
            case ENTRY_CREATE =>
              watcherActor ! DirectoryWatcherActor.FILE_EVENT(path.toFile, "CREATED")
            case ENTRY_MODIFY =>
              watcherActor ! DirectoryWatcherActor.FILE_EVENT(path.toFile, "UPDATED")
            case x => println("UNDEFINED MESSAGE")
          }
        }
        key.reset()
      }
    } catch {
      case e: InterruptedException =>
        throw e
    } finally {
      watchService.close()
    }
  }
}

class DirectoryWatcherActor(val fileExtension: String) extends Actor with ActorLogging {

  import DirectoryWatcherActor._

  val watchFileTask = new WatchDirectoryTask(self)
  var watchThread = new Thread(watchFileTask, "WatchService")

  override def preStart() {
    SYSTEM_ACTORS.monitoring  ! Monitor.START(Utils.getTimestamp)
    watchThread.setDaemon(true)
    watchThread.start()
  }

  override def postStop() {
    SYSTEM_ACTORS.monitoring  ! Monitor.STOP(Utils.getTimestamp)
    watchThread.interrupt()
  }

  override def postRestart(reason: Throwable): Unit = {
    SYSTEM_ACTORS.monitoring  ! Monitor.RESTART(reason, Utils.getTimestamp)
    preStart()
  }

  override def receive: Receive = {
    case MONITOR(path) =>
      log.info(s"Start monitoring ${path.getFileName}")
      watchFileTask.watch(path)
    case FILE_EVENT(file, eventType) if file.getAbsolutePath.endsWith(fileExtension) =>
      log.info(s"$eventType = ${file.getAbsolutePath}")
      context.parent ! FeyCore.NEW_FILE_ACTION(file)
  }
}

object DirectoryWatcherActor {

  /**
    * Start monitoring directory
    *
    * @param path directory path
    */
  case class MONITOR(path: Path)
  case class FILE_EVENT(file: File, event: String)

  def props(fileExtension: String): Props = {
    Props(new DirectoryWatcherActor(fileExtension))
  }
}
