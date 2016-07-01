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

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
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

  /**
    * Keeps reference to the cancellable
    */
  @volatile private var scheduler: Cancellable = null
  @volatile private var endBackoff: Long = 0

  override final def receive: Receive = {

    case PRINT_PATH =>
      log.info(s"** ${self.path} **")

    case STOP =>
      context.stop(self)

    case PROCESS(message) =>
      if(System.nanoTime() >= endBackoff) {
        processMessage(message, sender())
      }
    // In case
    case EXCEPTION(reason) => throw reason
    //Not treated messages will be pass over to the receiveComplement
    case x => customReceive(x)
  }

  override final def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    context.children foreach { child =>
      context.unwatch(child)
      context.stop(child)
    }
    postStop()
  }

  override final def preStart() = {
    onStart()
    startScheduler()
  }

  override final def postStop() = {
    log.info(s"STOPPED actor ${self.path.name}")
    stopScheduler()
    onStop()
  }

  override final def postRestart(reason: Throwable) = {
    log.info(s"RESTARTED Actor ${self.path.name}")
    preStart()
    onRestart(reason)
  }

  def onRestart(reason: Throwable) = {
    log.info("RESTARTED method")
  }

  /**
    * Stops the scheduler
    */
  private final def stopScheduler() = {
    if (scheduler != null) {
      scheduler.cancel()
      scheduler = null
    }
  }
  /**
    * Enables the backoff.
    * Actor will drop the PROCESS messages that are sent during the backoff period time.
    */
  final def startBackoff() = {
    this.endBackoff = System.nanoTime() + this.backoff.toNanos
  }

  /**
    * start the sheduled task after 1 second
    * The time interval to be used is the one passed to the constructor
    */
  private final def startScheduler() = {
    if(scheduler == null && schedulerTimeInterval.toNanos != 0){
      scheduler = context.system.scheduler.schedule(1.seconds, schedulerTimeInterval){
        try{
          execute()
        }catch{
          case e: Exception => self ! EXCEPTION(e)
        }
      }
    }
  }

  /**
    * Called by the scheduler.
    */
  def execute() = {
    log.info(s"Executing action in ${self.path.name}")
  }

  /**
    * Called every time actors receives the PROCESS message.
    * The default implementation propagates the message to the connected actors
    * and fires up the backoff
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
  final def propagateMessage[T](message: T) = {
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



