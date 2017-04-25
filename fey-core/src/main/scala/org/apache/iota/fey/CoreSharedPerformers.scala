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

import akka.actor.SupervisorStrategy.Restart
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, Props, Terminated}
import akka.routing._
import play.api.libs.json.JsObject

import scala.collection.mutable.HashMap
import scala.concurrent.duration._

protected class CoreSharedPerformers(val sharedJson: List[JsObject]) extends Actor with ActorLogging{

  val monitoring_actor = FEY_MONITOR.actorRef
  var shared_metadata: Map[String, Performer] = Map.empty[String, Performer]

  override def receive: Receive = {

    case CoreSharedPerformers.PRINT_GLOBAL =>
      context.actorSelection(s"*") ! FeyGenericActor.PRINT_PATH

    case CoreSharedPerformers.RESTART_SHARED(uuid) =>
      restartShared(uuid)

    case Terminated(actor) =>
      monitoring_actor  ! Monitor.TERMINATE(actor.path.toString, Utils.getTimestamp)
      log.error(s"DEAD Shared performer: ${actor.path.name} . All the ensembles that uses this performer will be restarted")
      self ! CoreSharedPerformers.RESTART_SHARED(actor.path.name)

    case x => log.warning(s"Message $x not treated by Shared Performers")
  }

  /**
    * If any of the shared performer dies, it tries to restart it.
    * If we could not be restarted, then the terminated message will be received
    * and Shared Performer is going to throw an Exception to its orchestration
    * asking it to Restart all the entire orchestration. The restart will then stop all of its
    * children when call the preStart.
    */
  override val supervisorStrategy =
  OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute) {
    case e: Exception => Restart
  }

  /**
    * Uses the json spec to create the performers
    */
  override def preStart() : Unit = {
    monitoring_actor ! Monitor.START(Utils.getTimestamp)

    shared_metadata = Ensemble.extractPerformers(sharedJson)

    createSharedPerformers()
  }

  override def postStop() : Unit = {
    monitoring_actor  ! Monitor.STOP(Utils.getTimestamp)
  }

  override def postRestart(reason: Throwable): Unit = {
    monitoring_actor  ! Monitor.RESTART(reason, Utils.getTimestamp)
    preStart()
  }

  private def restartShared(uuid: String) = {
    try {
      log.warning(s"Restarting Shared Performer ${uuid}")
      val metadata = shared_metadata.get(uuid)
      if (metadata.isDefined) {
        createFeyActor(uuid, metadata.get)
      } else {
        log.error(s"Could not restart shared $uuid because metadata is not configured")
      }

      CoreSharedPerformers.ensemblesUsingShared.keySet.foreach((ensAndShared) => {
        if (ensAndShared._2 == uuid) {
          val actor = CoreSharedPerformers.ensemblesUsingShared.get(ensAndShared).get
          actor ! Ensemble.FORCE_RESTART_ENSEMBLE
        }
      })
    }catch{
      case e: Exception=>
        log.error(e, s"Could not restart shared $uuid")
    }
  }

  private def createSharedPerformers() = {
    shared_metadata.foreach((shared) => {
      try{
        createFeyActor(shared._1, shared._2)
      }catch{
        case e: Exception =>
          log.error(e, s"Could not created shared performer ${shared._1}")
      }
    })
  }

  private def createFeyActor(performerID: String, performerInfo: Performer) = {
    val actor: ActorRef = {
      val actorProps = getPerformer(performerInfo)
      if (performerInfo.autoScale) {

        val resizer = DefaultResizer(lowerBound = performerInfo.lowerBound, upperBound = performerInfo.upperBound,
          messagesPerResize = CONFIG.MESSAGES_PER_RESIZE, backoffThreshold = performerInfo.backoffThreshold, backoffRate = 0.1)

        val strategy =
          if (performerInfo.isRoundRobin) {
            log.info(s"Using Round Robin for performer ${performerID}")
            RoundRobinPool(1, Some(resizer))
          } else {
            log.info(s"Using Smallest mailbox for performer ${performerID}")
            SmallestMailboxPool(1, Some(resizer))
          }

        context.actorOf(strategy.props(actorProps), name = performerID)
      } else {
        context.actorOf(actorProps, name = performerID)
      }
    }

    context.watch(actor)
    CoreSharedPerformers.activeSharedPerformers.put(performerID, actor)
  }

  /**
    * Creates actor props based on JSON configuration
    *
    * @param performerInfo Performer object
    * @return Props of actor based on JSON config
    */
  private def getPerformer(performerInfo: Performer): Props = {

    var clazz:Option[Class[FeyGenericActor]] = None

    Utils.loadedJars.synchronized {
      clazz = Some(loadClazzFromJar(performerInfo.classPath, s"${performerInfo.jarLocation}/${performerInfo.jarName}", performerInfo.jarName))
    }

    if(clazz.isDefined) {
      val dispatcher = if (performerInfo.dispatcher != "") s"fey-custom-dispatchers.${performerInfo.dispatcher}" else ""

      val actorProps = Props(clazz.get, performerInfo.parameters, performerInfo.backoff,
        Map.empty, performerInfo.schedule, self.path.name, self.path.name, performerInfo.autoScale)

      // dispatcher has higher priority than controlAware. That means that if both are defined
      // then the custom dispatcher will be used
      if (dispatcher != "") {
        log.info(s"Using dispatcher: $dispatcher")
        actorProps.withDispatcher(dispatcher)
      }
      else if (performerInfo.controlAware) {
        actorProps.withDispatcher(CONFIG.CONTROL_AWARE_MAILBOX)
      } else {
        actorProps
      }
    }else{
      log.error(s"Could not load class for performer ${performerInfo.uid}")
      throw new ClassNotFoundException(s"${performerInfo.jarName} -- ${performerInfo.jarLocation}")
    }
  }

  /**
    * Load a clazz instance of FeyGenericActor from a jar
    *
    * @param classPath class path
    * @param jarLocation Full path where to load the jar from
    * @return clazz instance of FeyGenericActor
    */
  private def loadClazzFromJar(classPath: String, jarLocation: String, jarName: String):Class[FeyGenericActor] = {
    try {
      Utils.loadActorClassFromJar(jarLocation,classPath,jarName)
    }catch {
      case e: Exception =>
        log.error(e,s"Could not load class $classPath from jar $jarLocation. Please, check the Jar repository path as well the jar name")
        throw e
    }
  }
}

object CoreSharedPerformers{

  // [sharedID, actorRef]
  val activeSharedPerformers:HashMap[String,ActorRef] = HashMap.empty[String,ActorRef]
  // [[ensembleID, sharedID] -> actorRef]
  val ensemblesUsingShared:HashMap[Tuple2[String,String], ActorRef] = HashMap.empty[Tuple2[String,String], ActorRef]

  case class RESTART_SHARED(shared_id: String)
  case object PRINT_GLOBAL
}