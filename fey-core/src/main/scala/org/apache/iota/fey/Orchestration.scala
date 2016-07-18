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
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, PoisonPill, Props, Terminated}
import akka.routing.GetRoutees
import org.apache.iota.fey.FeyCore.STOP_EMPTY_ORCHESTRATION
import play.api.libs.json._

import scala.concurrent.duration._
import scala.collection.mutable.HashMap

protected class Orchestration(val name: String,
                      val guid: String,
                      val timestamp: String) extends Actor with ActorLogging{

  import Orchestration._
  import JSON_PATH._
  /**
    * List of map of Ensembles = [EnsembleID, Ensemble]
    */
  val ensembles:HashMap[String, ActorRef] = HashMap.empty[String, ActorRef]
  val awaitingTermination:HashMap[String, JsObject] = HashMap.empty[String, JsObject]
  val monitoring_actor = FEY_MONITOR.actorRef

  override def receive: Receive = {

    case CREATE_ENSEMBLES(ensemblesJsonSpec) => createEnsembles(ensemblesJsonSpec)
    case DELETE_ENSEMBLES(ensemblesJsonSpec) => deleteEnsembles(ensemblesJsonSpec)
    case UPDATE_ENSEMBLES(ensemblesJsonSpec) => updateEnsembles(ensemblesJsonSpec)

    case PRINT_PATH =>
      log.info(s"** ${self.path} **")
      context.actorSelection(s"*") ! Ensemble.PRINT_ENSEMBLE

    case Terminated(actor) =>
      monitoring_actor  ! Monitor.TERMINATE(actor.path.toString, Utils.getTimestamp)
      context.unwatch(actor)
      log.warning(s"ACTOR DEAD ${actor.path}")
      ensembles.remove(actor.path.name)
      checkForEnsemblesWaitingTermination(actor.path.name)
      stopIfNoEnsembleIsRunning()

    case GetRoutees => //Discard

    case x => log.warning(s"Message $x not treated by Orchestrations")
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute) {
      case e: RestartEnsemble => Restart
      case e: Exception => Restart
    }

  override def preStart(): Unit = {
    monitoring_actor  ! Monitor.START(Utils.getTimestamp)
    if (ORCHESTRATION_CACHE.orchestration_metadata.contains(guid)){
      replayOrchestrationState()
    }
  }

  override def postStop() = {
    monitoring_actor  ! Monitor.STOP(Utils.getTimestamp)
    log.info(s"STOPPED ${self.path.name}")
  }

  override def postRestart(reason: Throwable): Unit = {
    monitoring_actor  ! Monitor.RESTART(reason, Utils.getTimestamp)
    log.info(s"RESTARTED ${self.path}")
    preStart()
  }

  /**
    * This method is called every time the orchestration receives a terminated message.
    * It will check if there is no more Ensembles running and will delete the orchestration
   */
  private def stopIfNoEnsembleIsRunning() = {
    if (ensembles.isEmpty){
      context.parent ! STOP_EMPTY_ORCHESTRATION(guid)
    }
  }

  /**
    * When the orchestration is restarted, all of its children (Ensemble)
    * are stopped. In order to also restart all the active ensemble when the
    * orchestration was restarted, we need to look to orchestration cache.
    */
  private def replayOrchestrationState() = {
    val ensemblesSpec = ORCHESTRATION_CACHE.orchestration_metadata.get(guid).get.map(_._2).toList
    ORCHESTRATION_CACHE.orchestration_metadata.remove(guid)
    self ! CREATE_ENSEMBLES(ensemblesSpec)
  }

  /**
    * Stops the Ensembles and starts a new one with the updated info
    *
    * @param ensemblesJsonSpec
    */
  private def updateEnsembles(ensemblesJsonSpec: List[JsObject]) = {
    ensemblesJsonSpec.foreach(ensembleSpec => {
      val ensembleID = (ensembleSpec \ GUID).as[String]
      ensembles.get(ensembleID) match {
        case Some(activeEnsemble) => {
          awaitingTermination.put(ensembleID, ensembleSpec)
          stopEnsembles(Array(ensembleID))
        }
        case None => log.warning(s"There is no Ensemble $ensembleID to be updated in Orchestration $guid")
      }
    })
  }

  /**
    * Check if there is any ensemble that is waiting for the path to be free.
    * Normally used only when updating Ensembles
    * @param terminatedEnsembleID
    * @return
    */
  private def checkForEnsemblesWaitingTermination(terminatedEnsembleID: String) = {
    awaitingTermination.get(terminatedEnsembleID) match {
      case Some(ensembleSpec) =>
        awaitingTermination.remove(terminatedEnsembleID)
        createEnsembles(List(ensembleSpec))
      case None =>
    }
  }

  /**
    * Stops the list of Ensembles and removes it from the
    *
    * @param ensemblesJsonSpec
    * @return
    */
  private def deleteEnsembles(ensemblesJsonSpec: List[JsObject]) = {
    val ids = ensemblesJsonSpec.map(json => (json \ GUID).as[String]).toArray
    stopEnsembles(ids)

    //Remove from cache
    ORCHESTRATION_CACHE.orchestration_metadata.get(guid) match {
      case None =>
      case Some(ensembles) =>
        ORCHESTRATION_CACHE.orchestration_metadata.put(guid, (ensembles -- ids))
    }
    Utils.updateOrchestrationState(guid)
  }

  /**
    * Creates Ensembles from the json specification and make it
    * a member of the orchestration Ensembles
    *
    * @param ensemblesJsonSpec
    * @return
    */
  private def createEnsembles(ensemblesJsonSpec: List[JsObject]) = {
    log.info(s"Creating Ensembles: ${ensemblesJsonSpec}")
    val newEnsembles = ensemblesJsonSpec.map(ensembleSpec => {
        try {
          createEnsemble(ensembleSpec)
        } catch {
          case e: Exception =>
            log.error(e,s"Ensembles ${(ensembleSpec \ GUID).as[String]} in Orchestration $guid could not be created")
            None
        }
      })
      .filter(_ != None).map(_.get)
    ensembles ++= (newEnsembles.map(ensemble => (ensemble._1, ensemble._2)).toMap)

    // Save to cache
    ORCHESTRATION_CACHE.orchestration_metadata.get(guid) match {
      case None =>
        ORCHESTRATION_CACHE.orchestration_metadata.put(guid, (newEnsembles.map(ensemble => (ensemble._1, ensemble._3)).toMap))
      case Some(cachedEnsemble) =>
        ORCHESTRATION_CACHE.orchestration_metadata.put(guid, cachedEnsemble ++ (newEnsembles.map(ensemble => (ensemble._1, ensemble._3)).toMap))
    }
    Utils.updateOrchestrationState(guid)
  }

  /**
    * Creates an Ensemble object from the JSON
    *
    * @param ensembleSpecJson
    * @return Some(EnsemblesUID, EnsemblesObject)
    *         None if Ensemble exists
    */
  private def createEnsemble(ensembleSpecJson: JsObject): Option[(String, ActorRef, JsObject)] = {
    val ensembleID = (ensembleSpecJson \ GUID).as[String]
    if(!ensembles.contains(ensembleID)) {

      val ensemble = context.actorOf(Props(classOf[Ensemble], guid,name,ensembleSpecJson), name = ensembleID)
      context.watch(ensemble)

      Some((ensembleID, ensemble, ensembleSpecJson))
    }else{
      log.warning(s"Ensembles $ensembleID in Orchestration $guid already exists")
      None
    }
  }

  /**
    * Send the stop message to the Ensembles
    *
    * @param ensembleesID
    */
  private def stopEnsembles(ensembleesID: Array[String]) = {
    ensembleesID.foreach(ensembleID => {
      ensembles.get(ensembleID) match {
        case Some(ensemble) =>
          ensemble ! PoisonPill
          ensembles --= ensembleesID
        case None => log.warning(s"No Ensembles $ensembleID to be stopped in Orchestration $guid")
      }
    })
  }

}

protected object Orchestration{
  case class CREATE_ENSEMBLES(ensemblesJsonSpec: List[JsObject])
  case class DELETE_ENSEMBLES(ensemblesJsonSpec: List[JsObject])
  case class UPDATE_ENSEMBLES(ensemblesJsonSpec: List[JsObject])
  case object PRINT_PATH
}

/**
  * Keeps all the necessary information to restart the orchestration Ensembles
  * in case of a restart
  */
protected object ORCHESTRATION_CACHE{
  /**
    * Key = Orchestration GUID
    * Value = Map[Ensemble GUID, JsObject of the ensemble]
    */
  val orchestration_metadata: HashMap[String, Map[String,JsObject]] = HashMap.empty[String, Map[String,JsObject]]
}