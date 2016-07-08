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

import akka.actor.Actor
import akka.event.{DiagnosticLoggingAdapter, Logging}

/**
  * Created by barbaragomes on 7/8/16.
  */
protected class Monitor extends Actor {

  import Monitor._

  val log: DiagnosticLoggingAdapter = Logging(this)
  log.mdc(Map("fileName" -> "monitor_events"))

  override def postStop() = {
    log.clearMDC()
  }


  override def receive: Receive = {

    case START(timestamp, info) =>
      logInfo(sender().path.toString, EVENTS.START, timestamp, info)
      events.append(sender().path.toString,MonitorEvent(EVENTS.START, timestamp, info))

    case STOP(timestamp, info) =>
      logInfo(sender().path.toString, EVENTS.STOP, timestamp, info)
      events.append(sender().path.toString,MonitorEvent(EVENTS.STOP, timestamp, info))

    case RESTART(reason, timestamp) =>
      logInfo(sender().path.toString, EVENTS.RESTART, timestamp, "", reason)
      events.append(sender().path.toString,MonitorEvent(EVENTS.RESTART, timestamp, reason.getMessage))

    case TERMINATE(actorPath, timestamp, info) =>
      logInfo(sender().path.toString, EVENTS.TERMINATE, timestamp, info)
      events.append(actorPath,MonitorEvent(EVENTS.TERMINATE, timestamp, info))

  }

  def logInfo(path:String, event:String, timestamp: Long, info:String, reason:Throwable = null) = {
    if(reason != null){
      log.error(reason, s"$event | $timestamp | $path | $info")
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
  val events: Trie = new Trie()

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


