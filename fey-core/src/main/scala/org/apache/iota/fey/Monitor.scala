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

/**
  * Created by barbaragomes on 7/8/16.
  */
protected class Monitor extends Actor with ActorLogging{

  import Monitor._

  override def receive: Receive = {

    case START(timestamp, info) =>
      //TODO: Log
      events.append(sender().path.toString,MonitorEvent(EVENTS.START, timestamp, info))

    case STOP(timestamp, info) =>
    //TODO: Log
      events.append(sender().path.toString,MonitorEvent(EVENTS.STOP, timestamp, info))

    case RESTART(reason, timestamp) =>
    //TODO: Log
      events.append(sender().path.toString,MonitorEvent(EVENTS.RESTART, timestamp, reason.getMessage))

    case TERMINATE(actorPath, timestamp, info) =>
    //TODO: Log
      events.append(actorPath,MonitorEvent(EVENTS.TERMINATE, timestamp, info))

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


