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
package org.apache.iota.fey.performer

import akka.actor.ActorRef
import org.apache.iota.fey.FeyGenericActor
import org.zeromq.ZMQ

import scala.concurrent.duration._


class ZMQPublisher(override val params: Map[String, String] = Map.empty,
                   override val backoff: FiniteDuration = 1.minutes,
                   override val connectTo: Map[String, ActorRef] = Map.empty,
                   override val schedulerTimeInterval: FiniteDuration = 2.seconds,
                   override val orchestrationName: String = "",
                   override val orchestrationID: String = "",
                   override val autoScale: Boolean = false) extends FeyGenericActor {

  //-------default params----------
  val DEFAULT_PORT = 5559
  val DEFAULT_NULL = null

  var port: Int = DEFAULT_PORT
  var target: String = "localhost"
  val DEFAULT_LINGER = 200
  val DEFAULT_HMW = 10

  //-------class vars-------------------
  var ctx: ZMQ.Context = DEFAULT_NULL
  var pub: ZMQ.Socket = DEFAULT_NULL
  var count: Int = 0

  override def onStart: Unit = {
    log.info("Starting ZMQ Publisher")
    try {
      checkParams()

      ctx = ZMQ.context(1)

      pub = ctx.socket(ZMQ.PUB)
      pub.setLinger(DEFAULT_LINGER)
      pub.setHWM(DEFAULT_HMW)
      pub.connect("tcp://" + target + ":" + port)
    }
    catch {
      case e: ActorParamsNotSatisfied => throw e
    }
  }

  override def onStop: Unit = {
    pub.disconnect("tcp://" + target + ":" + port)
  }

  override def onRestart(reason: Throwable): Unit = {
    // Called after actor is up and running - after self restart
    try {
      if (Option(pub).isDefined) {
        pub.close()
      }
      if (Option(ctx).isDefined) {
        ctx.close()
      }
      ctx = ZMQ.context(1)
      pub = ctx.socket(ZMQ.PUB)
      pub.setLinger(DEFAULT_LINGER)
      pub.setHWM(DEFAULT_HMW)
      pub.connect("tcp://" + target + ":" + port)
    }
    catch {
      case e: ActorParamsNotSatisfied => throw e
      case default: Throwable => throw new UnknownException("onRestart failed because of an exception")
    }

  }

  override def customReceive: Receive = {
    case x => log.debug(s"Untreated $x")
  }

  override def execute(): Unit = {
    log.debug(s"Msg count: $count")
  }

  override def processMessage[T](message: T, sender: ActorRef): Unit = {
    //log.info(message.asInstanceOf[String])
    message match {
      case message: String =>
        // Assuming each String message has only point data
        sendZMQ(s"$message")

      //      case message: Map[String, (String,String,String,String)] =>
      //        val formatted_msgs: Array[String] = message.map(point => _format_messages(point._2)).toArray
      //        formatted_msgs.foreach(x => _zmq_send(x))

      case _ => log.error("Ignoring this message as format not expected")
    }
  }

  def formatMessages(fields: (String, String, String, String)): String = {
    // The tuple has the following elements: lrn, timestamp, value, type
    // And we have to create a message with the format:
    // DATA|cloud|lrn|timestamp|{"<type>" : <value>}
    s"""DATA|cloud| ${fields._1}|${fields._2}|{${fields._3}:${fields._4}}"""
  }

  def sendZMQ(Message: String): Unit = {
    log.debug(s"message =$Message")
    pub.send(Message)
    count += 1
  }

  def checkParams(): Unit = {
    if (params.contains("zmq_port")) {
      port = params("zmq_port").toInt
    }
    if (params.contains("zmq_target")) {
      target = params("zmq_target")
    }
  }

}




