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

import java.nio.file.{Path, WatchEvent}

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.routing.GetRoutees

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Defines the generic actor for Fey integration
  * @param params Map of key value pairs that will be used to configure the actor
  * @param backoff Backoff interval
  * @param connectTo List of ActorRef that are connected to this actor
  *                  (If this actor is A, and it is connect to B and C, so the network would be A -> B,C)
  * @param schedulerTimeInterval When this value is different of zero,
  *                              a scheduler will be started calling the execute method
  * @param orchestrationName
  * @param orchestrationID
  */
abstract class FeyGenericActor(val params: Map[String,String] = Map.empty,
                               val backoff: FiniteDuration = 1.minutes,
                               val connectTo: Map[String,ActorRef] = Map.empty,
                               val schedulerTimeInterval: FiniteDuration = 2.seconds,
                               val orchestrationName: String = "",
                               val orchestrationID: String = "",
                               val autoScale: Boolean = false)

  extends Actor with ActorLogging{


  import FeyGenericActor._
  import GlobalWatchService._
  /**
    * Keeps reference to the cancellable
    */
  @volatile private var scheduler: Option[Cancellable] = None
  @volatile private var endBackoff: Long = 0
  private[fey] val monitoring_actor = FEY_MONITOR.actorRef

  override final def receive: Receive = {

    case PRINT_PATH => log.info(s"** ${self.path} **")

    case STOP => context.stop(self)

    case ENTRY_CREATED(path) => onReceiveWatcherCreate(path)

    case ENTRY_MODIFIED(path) => onReceiveWatcherModify(path)

    case ENTRY_DELETED(path) => onReceiveWatcherDelete(path)

    case PROCESS(message) => checkBackoff(message, sender())

    case EXCEPTION(reason) => throw reason
    case GetRoutees => //Discard
    case x => customReceive(x) //Not treated messages will be pass over to the receiveComplement
  }

  private def checkBackoff[T](message: T, sender: ActorRef) = {
    if(System.nanoTime() >= endBackoff) {
      processMessage(message, sender)
    }
  }

  override final def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    context.children foreach { child =>
      context.unwatch(child)
      context.stop(child)
    }
    postStop()
  }

  override final def preStart(): Unit = {
    monitoring_actor  ! Monitor.START(Utils.getTimestamp, startMonitorInfo)
    onStart()
    startScheduler()
  }

  override final def postStop(): Unit = {
    monitoring_actor  ! Monitor.STOP(Utils.getTimestamp, stopMonitorInfo)
    log.info(s"STOPPED actor ${self.path.name}")
    stopScheduler()
    onStop()
  }

  override final def postRestart(reason: Throwable): Unit = {
    monitoring_actor  ! Monitor.RESTART(reason, Utils.getTimestamp)
    log.info(s"RESTARTED Actor ${self.path.name}")
    preStart()
    onRestart(reason)
  }

  def onRestart(reason: Throwable): Unit = {
    log.info("RESTARTED method")
  }

  final def registerPathToGlobalWatcher(dir_path: String, file_name:Option[String],
                                        events: Array[WatchEvent.Kind[_]], loadIfExists: Boolean = false): Unit = {
    FEY_CORE_ACTOR.actorRef !  REGISTER_WATCHER_PERFORMER(dir_path, file_name, self, events, loadIfExists)
  }

  /**
    * Stops the scheduler
    */
  private final def stopScheduler() = {
    if (scheduler.isDefined) {
      scheduler.get.cancel()
      scheduler = None
    }
  }
  /**
    * Enables the backoff.
    * Actor will drop the PROCESS messages that are sent during the backoff period time.
    */
  final def startBackoff(): Unit = {
    this.endBackoff = System.nanoTime() + this.backoff.toNanos
  }

  /**
    * start the sheduled task after 1 second
    * The time interval to be used is the one passed to the constructor
    */
  private final def startScheduler() = {
    if (scheduler.isEmpty && schedulerTimeInterval.toNanos != 0) {
      scheduler = Option(context.system.scheduler.schedule(1.seconds, schedulerTimeInterval) {
        try{
          execute()
        }catch{
          case e: Exception => self ! EXCEPTION(e)
        }
      })
    }
  }

  /**
    * Check state of scheduler
    *
    * @return true if scheduller is running
    */
  final def isShedulerRunning():Boolean = {
    if(scheduler.isDefined && !scheduler.get.isCancelled){
      true
    }else{
      false
    }
  }

  /**
    * get endBackoff
    *
    * @return
    */
  final def getEndBackoff(): Long = {
    endBackoff
  }
  /**
    * Called by the scheduler.
    */
  def execute(): Unit = {
    log.info(s"Executing action in ${self.path.name}")
  }

  /**
    * Called every time actors receives the PROCESS message.
    * The default implementation propagates the message to the connected actors
    * and fires up the backoff
    *
    * @param message message to be processed
    * @tparam T Any
    */
  def processMessage[T](message: T, sender: ActorRef): Unit = {
    log.info(s"Processing message ${message.toString}")
    propagateMessage(s"PROPAGATING FROM ${self.path.name} - Message: ${message.toString}")
    startBackoff()
  }

  /**
    * This method should be called to propagate the message
    * to the actors that are linked.
    * Call this method in the end of the method processMessage
    *
    * @param message message to be propagated
    * @tparam T Any
    */
  final def propagateMessage[T](message: T): Unit = {
    connectTo.foreach(linkedActor => {
      linkedActor._2 ! PROCESS(message)
    })
  }

  /**
    * Method called after actor has received the START message.
    * All the necessary configurations will be ready to be used.
    */
  def onStart(): Unit = {
    log.info(s"Actor ${self.path.name} started.")
  }

  /**
    * Method called after actor has been stopped.
    * Any scheduler that might have been running will be already canceled.
    */
  def onStop(): Unit = {
    log.info(s"Actor ${self.path.name} stopped.")
  }

  /**
    * Any message that was not processed by the default actor receiver
    * will be passed to this method.
    *
    * @return
    */
  def customReceive: Receive


  /**
    * Used to set a info message when sending Stop monitor events
    *
    * @return String info
    */
  def stopMonitorInfo:String = "Stopped"

  /**
    * Used to set a info message when sending Start monitor events
    *
    * @return String info
    */
  def startMonitorInfo:String = "Started"

  /**
    * Called every time the performer is notified of a file watcher event
    * of type MODIFY
    * @param path path of the file
    */
  def onReceiveWatcherModify(path: Path):Unit = {
    log.info(s"File Modified: $path")
  }

  /**
    * Called every time the performer is notified of a file watcher event
    * of type DELETE
    * @param path path of the file
    */
  def onReceiveWatcherDelete(path: Path):Unit = {
    log.info(s"File Deleted: $path")
  }

  /**
    * Called every time the performer is notified of a file watcher event
    * of type CREATE
    * @param path path of the file
    */
  def onReceiveWatcherCreate(path: Path):Unit = {
    log.info(s"File Created: $path")
  }
}

object FeyGenericActor {

  /**
    * Stops the actor
   */
  case object STOP

  /**
    * Default message to execution an action when it is received.
    *
    * @param message message to be processed
    * @tparam T Any
    */
  case class PROCESS[T](message: T)

  /**
    * Message sent to the actor when an exception happens in the scheduler
    *
    * @param reason
    */
  case class EXCEPTION(reason: Throwable)

  /**
    * Prints the path of the actor
    */
  case object PRINT_PATH

}




