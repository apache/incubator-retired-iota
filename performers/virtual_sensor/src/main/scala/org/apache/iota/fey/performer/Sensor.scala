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

import scala.collection.immutable.Map
import scala.concurrent.duration._
import org.apache.commons.math3.distribution.NormalDistribution
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, JsValue, Json}

class Sensor(override val params: Map[String, String] = Map.empty,
             override val backoff: FiniteDuration = 1.minutes,
             override val connectTo: Map[String, ActorRef] = Map.empty,
             override val schedulerTimeInterval: FiniteDuration = 30.seconds,
             override val orchestrationName: String = "",
             override val orchestrationID: String = "",
             override val autoScale: Boolean = false) extends FeyGenericActor {

  var lrn = "VIRTUAL_SENSOR"
  var name = "sensor"
  var expected_value: Double = 70.0
  var sigma: Double = 1.0
  var exceptions: JsValue = null
  var value: NormalDistribution = null
  var sensor_type = "environmental"
  // wav    vibration
  var vib = ""
  var normal_vib = ""
  var exception_vib = ""
  var sound = ""
  var normal_sound = ""
  var exception_sound = ""

  override def onStart = {

    _params_check()

    if (sensor_type == "vibration") {
      normal_vib = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/vibration"))
        .getLines()
        .mkString("")
      exception_vib = normal_vib
    }
    if (sensor_type == "wav") {
      normal_sound = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/background_noise"))
        .getLines()
        .mkString("")
      exception_sound = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/door_slam"))
        .getLines()
        .mkString("")
    }
  }

  override def onStop = {
  }

  override def onRestart(reason: Throwable) = {
    // Called after actor is up and running - after self restart
  }

  override def customReceive: Receive = {
    case x => log.debug(s"Untreated $x")
  }

  override def processMessage[T](message: T, sender: ActorRef): Unit = {
  }

  override def execute() = {
    val ts = java.lang.System.currentTimeMillis().toString
    var out = ""
    if (!isException) {
      value = new NormalDistribution(expected_value, sigma)
      sound = normal_sound
      vib = normal_vib
    }

    sensor_type match {
      case "environmental" => out = "DATA|cloud|" + lrn + "|" + ts + "|{\"x\":\"" + value.sample() + "\"}"
      case "vibration" => out = "DATA|cloud|" + lrn + "|" + ts + "|{\"blob\":\"" + vib + "\"}"
      case "wav" => out = "DATA|cloud|" + lrn + "|" + ts + "|{\"wav\":\"" + sound + "\"}"
    }
    log.debug(out)
    propagateMessage(out)
  }

  def isException: Boolean = {
    var efile = ""
    var ev = 0.0
    val date = DateTime.now
    val all_exceptions = exceptions.as[List[JsObject]]
    all_exceptions.foreach(x => {
      try {
        val st = (x \ "start_time").as[Array[Int]]
        val et = (x \ "end_time").as[Array[Int]]
        val sTime = new DateTime(date.getYear, date.getMonthOfYear, date.getDayOfMonth, st(0), st(1), st(2))
        val eTime = new DateTime(date.getYear, date.getMonthOfYear, date.getDayOfMonth, et(0), et(1), et(2))
        if (date.isAfter(sTime) && date.isBefore(eTime)) {
          //log.info("We have an exception")
          if (x.keys.contains("type")) {
            efile = (x \ "type").as[String]
            vib = exception_vib
            sound = exception_sound
          } else {
            ev = (x \ "expected_value").as[Double]
            value = new NormalDistribution(ev, sigma)
          }
          return true
        }
      } catch {
        case e: Exception => log.error(s"Bad exception specified $x")
      }

    })
    //log.info("No exception")
    false
  }

  def _params_check() = {
    if (params.contains("lrn")) {
      lrn = params("lrn")
    }
    if (params.contains("name")) {
      name = params("name")
    }
    if (params.contains("expected_value")) {
      val ev = params("expected_value")
      try {
        expected_value = ev.toFloat
      } catch {
        case e: Exception => log.error(s"Expected a Float you used $ev ")
      }

    }
    if (params.contains("exceptions")) {
      val p = params("exceptions")
      try {
        exceptions = Json.parse(p)
      } catch {
        case e: Exception => log.error(s"Invalid JSON defining exception $p")
      }
    }
    if (params.contains("sensor_type")) {
      sensor_type = params("sensor_type")
      sensor_type match {
        case "wav" =>
        case "vibration" =>
        case "environmental" =>
        case default => log.error(s"""Only "vibration", "wav", and "environmental" are permitted you have $sensor_type""")
          sensor_type = "environmental"
      }
    }
  }

}