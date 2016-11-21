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

import akka.actor.{Actor, ActorLogging}
import akka.event.{DiagnosticLoggingAdapter, Logging}

import scala.collection.mutable.ArrayBuffer

/**
  * Created by barbaragomes on 7/8/16.
  */
protected class Monitor(eventsStore: Trie) extends Actor with ActorLogging {

  import Monitor._

  //val log: DiagnosticLoggingAdapter = Logging(this)
  //log.mdc(Map("fileName" -> "monitor_events"))


  override def preStart(): Unit = {
    if(CONFIG.MONITORING_ENABLED) {
      if (CONFIG.MONITORING_TYPE == "SIMPLE") {
        context.become(simple)
      } else {
        context.become(complete)
      }
    }
  }

  override def postStop(): Unit = {
    //log.clearMDC()
  }

  private def complete: Receive = {
    case START(timestamp, info) =>
      logInfo(sender().path.toString, EVENTS.START, timestamp, info)
      eventsStore.append(sender().path.toString,MonitorEvent(EVENTS.START, timestamp, info))

    case STOP(timestamp, info) =>
      logInfo(sender().path.toString, EVENTS.STOP, timestamp, info)
      eventsStore.append(sender().path.toString,MonitorEvent(EVENTS.STOP, timestamp, info))

    case RESTART(reason, timestamp) =>
      logInfo(sender().path.toString, EVENTS.RESTART, timestamp, "", Option(reason))
      eventsStore.append(sender().path.toString,MonitorEvent(EVENTS.RESTART, timestamp, reason.getMessage))

    case TERMINATE(actorPath, timestamp, info) =>
      logInfo(actorPath, EVENTS.TERMINATE, timestamp, info)
      eventsStore.append(actorPath,MonitorEvent(EVENTS.TERMINATE, timestamp, info))
  }

  private def simple: Receive = {
    case START(timestamp, info) =>
      logInfo(sender().path.toString, EVENTS.START, timestamp, info)
      Monitor.simpleEvents.put(sender().path.toString, ('S',timestamp))

    case STOP(timestamp, info) =>
      logInfo(sender().path.toString, EVENTS.STOP, timestamp, info)
      Monitor.simpleEvents.put(sender().path.toString, ('O',timestamp))

    case RESTART(reason, timestamp) =>
      logInfo(sender().path.toString, EVENTS.RESTART, timestamp, "", Option(reason))
      Monitor.simpleEvents.put(sender().path.toString, ('R',timestamp))

    case TERMINATE(actorPath, timestamp, info) =>
      logInfo(actorPath, EVENTS.TERMINATE, timestamp, info)
      Monitor.simpleEvents.put(actorPath, ('T',timestamp))
  }

  override def receive: Receive = {
    case _ =>
  }

  def logInfo(path: String, event: String, timestamp: Long, info: String, reason: Option[Throwable] = null): Unit = {
    if (reason.isDefined) {
      log.error(reason.get, s"$event | $timestamp | $path | $info")
    }else{
      log.info(s"$event | $timestamp | $path | $info")
    }
  }

}

protected object Monitor{
  // Monitoring Messages
  case class START(timestamp: Long, info: String = "")
  case class STOP(timestamp: Long, info: String = "")
  case class RESTART(reason: Throwable, timestamp: Long)
  case class TERMINATE(actorPath: String, timestamp: Long, info: String = "")

  // Stores Monitoring event
  case class MonitorEvent(event: String, timestamp: Long, info: String)

  /**
    * Contains the lifecycle events for actors in Fey
    */
  val events: Trie = new Trie("FEY-MANAGEMENT-SYSTEM")
  val simpleEvents:scala.collection.mutable.HashMap[String,(Char, Long)] = scala.collection.mutable.HashMap.empty

  //Static HTML content from d3
  val html = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/eventsTable.html"), "UTF-8")
    .getLines()
    .mkString("\n")

  def getHTMLevents: String = {
    html.replace("$EVENTS_TABLE_CONTENT", mapEventsToRows(events.getRootChildren(), "").mkString("\n"))
  }

  def mapEventsToRows(actors: ArrayBuffer[TrieNode], prefix:String): ArrayBuffer[String] = {
    actors.flatMap(actor => {
      val currentPath = if (prefix == "/user/FEY-CORE") actor.path else s"$prefix/${actor.path}"
      val events = actor.events.map(event => {
        getTableLine(currentPath, event.timestamp, event.event, event.info)
      })
      mapEventsToRows(actor.children, currentPath) ++ events
    })
  }

  def getSimpleHTMLEvents: String = {
    val content = simpleEvents.map(event => {
      event._2._1 match {
        case 'S' => getTableLine(event._1, event._2._2, "START", "")
        case 'O' => getTableLine(event._1, event._2._2, "STOP", "")
        case 'R' => getTableLine(event._1, event._2._2, "RESTART", "")
        case 'T' => getTableLine(event._1, event._2._2, "TERMINATE", "")
      }
    }).mkString("\n")
    html.replace("$EVENTS_TABLE_CONTENT", content)
  }

  private def getTableLine(path: String,timestamp: Long, event: String, info: String):String = {
    s"<tr><td>$path</td><td>$event</td><td>$info</td><td>$timestamp</td></tr>"
  }
}

/**
  * Events Name
  */
object EVENTS {

  val START = "START"
  val STOP = "STOP"
  val TERMINATE = "TERMINATE"
  val RESTART = "RESTART"

}


