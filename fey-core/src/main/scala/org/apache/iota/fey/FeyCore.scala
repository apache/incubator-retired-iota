
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

import scala.concurrent.duration._
import akka.actor.{Actor, ActorLogging, ActorRef, OneForOneStrategy, PoisonPill, Props, Terminated}
import Utils._
import akka.actor.SupervisorStrategy._
import play.api.libs.json._
import JSON_PATH._
import akka.routing.GetRoutees
import org.apache.iota.fey.Orchestration.{CREATE_ENSEMBLES, DELETE_ENSEMBLES, UPDATE_ENSEMBLES}
import com.eclipsesource.schema._

import scala.collection.mutable.HashMap

protected class FeyCore extends Actor with ActorLogging{

  import FeyCore._
  import CONFIG._

  val monitoring_actor = FEY_MONITOR.actorRef

  val identifier: ActorRef = context.actorOf(Props(classOf[IdentifyFeyActors]), name = IDENTIFIER_NAME)
  context.watch(identifier)

  override def receive: Receive = {

    case JSON_TREE =>
      printActiveActors()

    case START =>
      val jsonReceiverActor: ActorRef = context.actorOf(Props[JsonReceiverActor], name = JSON_RECEIVER_NAME)
      context.watch(jsonReceiverActor)

    case ORCHESTRATION_RECEIVED(orchestrationJson, optionFile) =>
      optionFile match {
        case Some(file) =>
          orchestrationReceivedWithFile(orchestrationJson, file)
        case None =>
          orchestrationReceivedNoFile(orchestrationJson)
      }


    case STOP_EMPTY_ORCHESTRATION(orchID) =>
      log.warning(s"Deleting Empty Orchestration $orchID")
      deleteOrchestration(orchID)

    case Terminated(actor) => processTerminatedMessage(actor)

    case GetRoutees => //Discard

    case x =>
      log.info(s"Received $x")

  }

  private def orchestrationReceivedNoFile(json: JsValue) = {
    val orchGUID = (json \ GUID).as[String]
    log.info(s"Orchestration $orchGUID received")
    try{
      processJson(json)
    }catch {
      case e: Exception =>
        log.error(e, s"JSON for orchestration $orchGUID could not be processed")
    }
  }

  private def orchestrationReceivedWithFile(json: JsValue, file: File) = {
    log.info(s"NEW FILE ${file.getAbsolutePath}")
    try{
      processJson(json)
      renameProcessedFile(file, "processed")
    }catch {
      case e: Exception =>
        renameProcessedFile(file, "failed")
        log.error(e, s"JSON not processed ${file.getAbsolutePath}")
    }
  }

  private def processTerminatedMessage(actorRef: ActorRef) = {
    monitoring_actor ! Monitor.TERMINATE(actorRef.path.toString, Utils.getTimestamp)
    log.info(s"TERMINATED ${actorRef.path.name}")
    FEY_CACHE.activeOrchestrations.remove(actorRef.path.name)
    if(!FEY_CACHE.orchestrationsAwaitingTermination.isEmpty) {
      checkForOrchestrationWaitingForTermination(actorRef.path.name)
    }
  }

  /**
    * Clean up Fey Cache
    */
  override def postStop(): Unit = {
    monitoring_actor ! Monitor.STOP(Utils.getTimestamp)
    FEY_CACHE.activeOrchestrations.clear()
    FEY_CACHE.orchestrationsAwaitingTermination.clear()
    ORCHESTRATION_CACHE.orchestration_metadata.clear()
  }

  override def preStart(): Unit = {
    monitoring_actor ! Monitor.START(Utils.getTimestamp)
    log.info("Starting Fey Core")
  }

  override def postRestart(reason: Throwable): Unit = {
    monitoring_actor ! Monitor.RESTART(reason, Utils.getTimestamp)
    preStart()
    self ! START
  }

  override val supervisorStrategy =
    OneForOneStrategy(maxNrOfRetries = 3, withinTimeRange = 1 minute) {
      case _: Exception =>
        Restart
    }

  /**
    * Process the JSON is a binary operation.
    * The network only will be established if the entire JSON can be processed.
    * JSON commands:
    *   CREATE: tells Fey that there is no previous orchestration active for this JSON.
    *           Fey will create the orchestration and all the Ensembles in the JSON.
    *           Throws exception in case there is a orchestration active for the JSON.
    *   UPDATE: tells Fey that there is a orchestration loaded for the JSON.
    *           Fey will check the command for each of the Ensembles and execute the correspondent action.
    *           See @updateOrchestration
    *   DELETE: Tells Fey to delete the active orchestration for the JSON.
    *   RECREATE: Tells Fey that might exists an active orchestration, if that is the case, delete the orchestration and recreate it
    *             otherwise, simply create it.
    *
    * @param orchestrationJSON
    */
  private def processJson(orchestrationJSON: JsValue): Unit ={
    val orchestrationName = (orchestrationJSON \ ORCHESTRATION_NAME).as[String]
    val orchestrationID = (orchestrationJSON \ GUID).as[String]
    val orchestrationCommand = (orchestrationJSON \ COMMAND).as[String].toUpperCase()
    val orchestrationTimestamp = (orchestrationJSON \ ORCHESTRATION_TIMESTAMP).as[String]
    val ensembles = (orchestrationJSON \ ENSEMBLES).as[List[JsObject]]
    orchestrationCommand match {
      case "RECREATE" => recreateOrchestration(ensembles, orchestrationID, orchestrationName, orchestrationTimestamp)
      case "CREATE" => createOrchestration(ensembles, orchestrationID, orchestrationName, orchestrationTimestamp)
      case "UPDATE" => updateOrchestration(ensembles, orchestrationID, orchestrationName, orchestrationTimestamp)
      case "DELETE" => deleteOrchestration(orchestrationID)
      case x => throw new CommandNotRecognized(s"Command: $x")
    }
  }

  /**
    * If no previous orchestration: Creates a new orchestration
    * If previous orchestration: check if timestamp is greater than the last processed timestamp
    *                      If it is greater, than cache the orchestration information to be used after
    *                      current orchestration termination, and deletes current orchestration
    *
    * @param ensemblesSpecJson
    * @param orchestrationID
    * @param orchestrationName
    * @param orchestrationTimestamp
    * @return
    */
  private def recreateOrchestration(ensemblesSpecJson: List[JsObject], orchestrationID: String,
                              orchestrationName: String, orchestrationTimestamp: String) = {
    FEY_CACHE.activeOrchestrations.get(orchestrationID) match {
      case Some(orchestration) =>
        try{
          // If timestamp is greater than the last timestamp
          if(orchestration._1 != orchestrationTimestamp){
            val orchestrationInfo = new OrchestrationInformation(ensemblesSpecJson,orchestrationID,orchestrationName,orchestrationTimestamp)
            FEY_CACHE.orchestrationsAwaitingTermination.put(orchestrationID, orchestrationInfo)
            deleteOrchestration(orchestrationID)
          }else{
            log.warning(s"Orchestration ${orchestrationID} not recreated. Timestamp did not change.")
          }
        }catch{
          case e: Exception =>
        }
      case None => createOrchestration(ensemblesSpecJson,orchestrationID,orchestrationName,orchestrationTimestamp)
    }
  }

  /**
    * Checks if there is a orchestration waiting for this ID termination
    *
    * @param terminatedOrchestrationName
    */
  private def checkForOrchestrationWaitingForTermination(terminatedOrchestrationName: String) = {
    FEY_CACHE.orchestrationsAwaitingTermination.get(terminatedOrchestrationName) match {
      case Some(orchestrationAwaiting) =>
        FEY_CACHE.orchestrationsAwaitingTermination.remove(terminatedOrchestrationName)
        createOrchestration(orchestrationAwaiting.ensembleSpecJson, orchestrationAwaiting.orchestrationID,
          orchestrationAwaiting.orchestrationName, orchestrationAwaiting.orchestrationTimestamp)
      case None =>
    }
  }

  /**
    * Creates a Orchestration according to the JSON spec.
    * If any exception happens during the creation, the orchestration actor will be killed
    * and as consequence all of its children.
    *
    * @param ensemblesSpecJson
    * @param orchestrationID
    * @param orchestrationName
    * @param orchestrationTimestamp
    */
  private def createOrchestration(ensemblesSpecJson: List[JsObject], orchestrationID: String,
                            orchestrationName: String, orchestrationTimestamp: String) = {
    try{
      if(!FEY_CACHE.activeOrchestrations.contains(orchestrationID)) {
        val orchestration = context.actorOf(
          Props(classOf[Orchestration], orchestrationName, orchestrationID, orchestrationTimestamp),
          name = orchestrationID)
        FEY_CACHE.activeOrchestrations.put(orchestrationID, (orchestrationTimestamp, orchestration))
        context.watch(orchestration)
        orchestration ! CREATE_ENSEMBLES(ensemblesSpecJson)
      }else{
        log.error(s"Orchestration $orchestrationID is already defined in the network.")
      }
    }catch{
      case e: Exception =>
        FEY_CACHE.activeOrchestrations.get(orchestrationID) match{
          case Some(orchestration) =>
            context.unwatch(orchestration._2)
            orchestration._2 ! PoisonPill
            FEY_CACHE.activeOrchestrations.remove(orchestrationID)
          case None => context.actorSelection(orchestrationID) ! PoisonPill
        }
        log.error(e, s"Could not create Orchestration $orchestrationID")
    }
  }

  /**
    * Stops the orchestration and remove it from the list of active orchestrations
    *
    * @param orchestrationID
    * @return
    */
  private def deleteOrchestration(orchestrationID: String) = {
    try{
      FEY_CACHE.activeOrchestrations.get(orchestrationID) match {
        case Some(orchestration) =>
          orchestration._2 ! PoisonPill
          FEY_CACHE.activeOrchestrations.remove(orchestrationID)
          updateOrchestrationState(orchestrationID,true)
        case None =>
          log.warning(s"No active Orchestration $orchestrationID to be deleted")
      }
    }catch{
      case e: Exception => log.error(e, s"Could not delete Orchestration $orchestrationID")
    }
  }

  private def updateOrchestration(ensemblesSpecJson: List[JsObject], orchestrationID: String,
                            orchestrationName: String, orchestrationTimestamp: String) = {
    FEY_CACHE.activeOrchestrations.get(orchestrationID) match {
      case None => log.warning(s"Orchestration not update. No active Orchestration $orchestrationID.")
      case Some(orchestration) => {
        ensemblesSpecJson.foreach(ensemble => {
          (ensemble \ COMMAND).as[String].toUpperCase() match {
            case "CREATE" => orchestration._2 ! CREATE_ENSEMBLES(List(ensemble))
            case "DELETE" => orchestration._2 ! DELETE_ENSEMBLES(List(ensemble))
            case "UPDATE" => orchestration._2 ! UPDATE_ENSEMBLES(List(ensemble))
            case "NONE" =>
            case x => log.warning(s"Command $x not recognized")
          }
        })
      }
    }
  }

  def printStatus(): Unit = {
    printActiveOrchestrations
    printWaitingTermination
    printActiveActors
  }

  def printWaitingTermination(): Unit = {
    val ids = FEY_CACHE.orchestrationsAwaitingTermination.map(orchestration => {
      orchestration._1
    }).mkString("[",",","]")
    log.info(s"\n === Waiting: $ids")
  }

  def printActiveOrchestrations(): Unit = {
    val ids = FEY_CACHE.activeOrchestrations.map(orchestration => {
      orchestration._1
    }).mkString("[",",","]")
    log.info(s"\n === Active: $ids")
  }

  def printActiveActors(): Unit = {
    identifier ! IdentifyFeyActors.IDENTIFY_TREE(self.path.toString)
  }

}

protected object FeyCore{

  case object JSON_TREE

  /**
    * After creating an actorOf FeyCore send this message to configure.
    */
  case object START

  /**
    * Json Receiver actor will send this message everytime a json is received
    * Does not matter from where it was received
    * @param json
    * @param file
    */
  case class ORCHESTRATION_RECEIVED(json: JsValue, file: Option[File])


  case class STOP_EMPTY_ORCHESTRATION(orchID: String)

  def props: Props = {
    Props(new FeyCore)
  }

  final val JSON_RECEIVER_NAME: String = "JSON_RECEIVER"
  final val IDENTIFIER_NAME: String = "FEY_IDENTIFIER"

  /**
    * Loads the specification for validating a Fey JSON
    */
  val jsonSchemaSpec: SchemaType = {
    Json.fromJson[SchemaType](Json.parse(scala.io.Source
      .fromInputStream(getClass.getResourceAsStream("/fey-json-schema-validator.json"))
      .getLines()
      .mkString(""))).get
  }

}

private object FEY_CACHE{
  /**
    * Keeps track of all active orchestrations
    * [OrchestrationID, (Orchestration Timestamp, Orchestration ActorRef)]
    */
  val activeOrchestrations:HashMap[String, (String, ActorRef)] = HashMap.empty[String, (String, ActorRef)]

  /**
    * Keeps a list of the orchestrations that are waiting for termination so then can be restarted
    * Used mainly inside the Terminated
    */
  val orchestrationsAwaitingTermination:HashMap[String,OrchestrationInformation] = HashMap.empty[String,OrchestrationInformation]
}

sealed case class OrchestrationInformation(ensembleSpecJson: List[JsObject], orchestrationID: String,
                                     orchestrationName: String, orchestrationTimestamp: String)