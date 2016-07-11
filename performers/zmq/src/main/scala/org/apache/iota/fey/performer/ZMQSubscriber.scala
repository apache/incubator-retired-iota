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

class ZMQSubscriber(override val params: Map[String, String] = Map.empty,
                    override val backoff: FiniteDuration = 1.minutes,
                    override val connectTo: Map[String, ActorRef] = Map.empty,
                    override val schedulerTimeInterval: FiniteDuration = 2.seconds,
                    override val orchestrationName: String = "",
                    override val orchestrationID: String = "",
                    override val autoScale: Boolean = false) extends FeyGenericActor {

  //-------default params----------
  var port: Int = 5563
  var target: String = "localhost"
  val topic_filter: String = "DATA"

  //-------class vars-------------------
  var ctx: ZMQ.Context = null
  var pub: ZMQ.Socket = null
  var count: Int = 0

  override def onStart = {
    log.info("Starting ZMQ Subscriber")
    try {

      _params_check()

      // Prepare our context and subscriber
      ctx = ZMQ.context(1)
      val subscriber = ctx.socket(ZMQ.SUB)

      subscriber.bind(s"tcp://$target:$port")
      subscriber.subscribe(topic_filter.getBytes())
      while (true) {
        // Read envelope with address
        val address = new String(subscriber.recv(0))
        // Read message contents
        val contents = new String(subscriber.recv(0))
        log.info(s"HERE IT IS $address : $contents")
        count += 1
      }
    }
    catch {
      case e: ActorParamsNotSatisfied => throw e
    }
  }

  override def onStop = {
    pub.disconnect("tcp://" + target + ":" + port)
    pub.close()
    ctx.close()
    pub = null
    ctx = null
  }

  override def onRestart(reason: Throwable) = {
    // Called after actor is up and running - after self restart
    try {
      if (pub != null) {
        pub.close()
      }
      if (ctx != null) {
        ctx.close()
      }
    }
    catch {
      case e: ActorParamsNotSatisfied => throw e
      case default: Throwable => throw new UnknownException("onRestart failed because of an exception")
    }

  }

  override def customReceive: Receive = {
    case x => log.info(s"Untreated $x")
  }

  override def execute() = {
    log.info(s"Msg count: $count")
  }

  override def processMessage[T](message: T, sender: ActorRef): Unit = {
    message match {
      case _ => log.info("Ignoring this message as format not expected")
    }
  }

  def _format_messages(fields: (String, String, String, String)): String = {
    // The tuple has the following elements: lrn, timestamp, value, type
    // And we have to create a message with the format:
    // DATA|cloud|lrn|timestamp|{"<type>" : <value>}
    "DATA|cloud|" + fields._1 + "|" + fields._2 + "|" + s"""{"${fields._3}":"${fields._4}"}"""
  }

  def _params_check() = {
    if (params.contains("zmq_port")) {
      port = params("zmq_port").toInt
    }
    if (params.contains("zmq_target")) {
      target = params("zmq_target")
    }
  }

}