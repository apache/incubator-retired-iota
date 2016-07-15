
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

import java.nio.file.Paths
import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import play.api.libs.json.{JsValue, Json}

class JsonReceiverActor extends Actor with ActorLogging {

  import JsonReceiverActor._

  val monitoring_actor = FEY_MONITOR.actorRef
  val watchFileTask = new WatchServiceReceiver(self)
  var watchThread = new Thread(watchFileTask, "WatchService")

  override def preStart() {
    prepareDynamicJarRepo()
    processCheckpointFiles()

    monitoring_actor  ! Monitor.START(Utils.getTimestamp)
    watchThread.setDaemon(true)
    watchThread.start()

    watchFileTask.watch(Paths.get(CONFIG.JSON_REPOSITORY))
  }

  private def prepareDynamicJarRepo() = {
    val jarDir = new File(CONFIG.DYNAMIC_JAR_REPO)
    if (!jarDir.exists()){
      jarDir.mkdir()
    }else if(CONFIG.DYNAMIC_JAR_FORCE_PULL){
      jarDir.listFiles().foreach(_.delete())
    }
  }


  private def processCheckpointFiles() = {
    if (CONFIG.CHEKPOINT_ENABLED) {
      val checkpoint = new CheckpointProcessor(self)
      checkpoint.run()
    }
  }

  override def postStop() {
    monitoring_actor  ! Monitor.STOP(Utils.getTimestamp)
    watchThread.interrupt()
    watchThread.join()
  }

  override def postRestart(reason: Throwable): Unit = {
    monitoring_actor  ! Monitor.RESTART(reason, Utils.getTimestamp)
    preStart()
  }

  override def receive: Receive = {
    case JSON_RECEIVED(json, file) =>
      log.info(s"JSON RECEIVED => ${Json.stringify(json)}")
      context.parent ! FeyCore.ORCHESTRATION_RECEIVED(json, file)

    case _ =>
  }

}

object JsonReceiverActor {

  case class JSON_RECEIVED(json: JsValue, file: File)

}
