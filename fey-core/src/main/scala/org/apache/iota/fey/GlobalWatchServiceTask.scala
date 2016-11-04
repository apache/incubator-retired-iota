
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

import java.nio.file.{FileSystems, Path, WatchEvent}
import java.nio.file.StandardWatchEventKinds._
import akka.actor.ActorRef
import org.slf4j.LoggerFactory
import scala.collection.mutable.HashMap

class GlobalWatchServiceTask(parentActor: ActorRef) extends Runnable {

  private val watchService = FileSystems.getDefault.newWatchService()
  private val log = LoggerFactory.getLogger(this.getClass)

  def watch(path: Path, actorName: String, events:Array[WatchEvent.Kind[_]]): Boolean = {
    try {
      log.info(s"Watching directory $path for actor $actorName")
      WatchingDirectories.watchingEvents.get(path.toString) match {
        case None =>
          WatchingDirectories.watchingEvents.put(path.toString, events)
          path.register(watchService, events)
        case Some(oldEvents) =>
          val newEvents = (oldEvents ++ events).distinct
          path.register(watchService, newEvents)
          WatchingDirectories.watchingEvents.put(path.toString, newEvents)
      }
      true
    }catch{
      case e: Exception =>
        log.error(s"Could not register to directory $path for actor $actorName", e)
        false
    }
  }

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
            case ENTRY_CREATE => distributeFileEvent(path, ENTRY_CREATE)
            case ENTRY_MODIFY => distributeFileEvent(path, ENTRY_MODIFY)
            case ENTRY_DELETE => distributeFileEvent(path, ENTRY_DELETE)
            case x => log.warn(s"Event unknown $x")
          }
        }
        key.reset()
      }
    } catch {
      case e: Exception =>
        log.error("Global Watch service task died.",e)
    } finally {
      watchService.close()
      parentActor ! WatchingDirectories.STOPPED
    }
  }

  private def distributeFileEvent(path: Path, event: WatchEvent.Kind[_]) = {
    val dir = path.getParent.toString
    val name = path.getFileName.toString

    WatchingDirectories.actorsInfo.get((dir,Some(name))) match{
      case Some(actorInfo) =>
        actorInfo.get(event) match {
          case Some(actors) =>
            broadcastToActors(actors, event, path)
          case None =>
        }
      case None =>
        WatchingDirectories.actorsInfo.get((dir,None)) match{
          case Some(actorInfo) =>
            actorInfo.get(event) match {
              case Some(actors) =>
                broadcastToActors(actors, event, path)
              case None =>
            }
          case None =>
        }
    }
  }

  private def broadcastToActors(actors: Array[ActorRef], event: WatchEvent.Kind[_], path: Path) = {
    actors.foreach( actor => {
      event match {
        case ENTRY_CREATE => actor ! GlobalWatchService.ENTRY_CREATED(path)
        case ENTRY_MODIFY => actor ! GlobalWatchService.ENTRY_MODIFIED(path)
        case ENTRY_DELETE => actor ! GlobalWatchService.ENTRY_DELETED(path)
        case x => log.warn(s"Event unknown $x")
      }
    })
  }
}

private object WatchingDirectories{

  case object STOPPED

  /**
    * Key: (dir_path, file_name)
    * value: Map[ EventType, List of ActorsRef]
    */
  val actorsInfo:HashMap[(String,Option[String]), Map[WatchEvent.Kind[_], Array[ActorRef]]] = HashMap.empty[(String,Option[String]), Map[WatchEvent.Kind[_], Array[ActorRef]]]

  /**
    * Keeps track off all events that has been asked to monitor
    * for each path
    * key: dir path
    * value: list of events already being tracked
    */
  val watchingEvents:HashMap[String,Array[WatchEvent.Kind[_]]] = HashMap.empty[String,Array[WatchEvent.Kind[_]]]
}