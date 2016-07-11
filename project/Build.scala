/**
  * (C) Copyright Litbit 2016
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  *
  */

import sbt._
import sbt.Keys._

object ModuleDependencies {

  import Dependencies._
  val FeyDependencies           = compile(akka_actor,typesafe_config,playJson,slf4j,log4jbind,sprayCan,sprayRouting,jsonValidator,javaFilter)
  val StreamDependencies        = provided(akka_actor, fey)
  val ZMQDependencies           = provided(akka_actor,  fey) ++ compile(zmq)
  val VirtualSensorDependencies = provided(akka_actor,  fey) ++ compile(math3)
}

object IotaBuild extends Build {

  import BuildSettings._

  lazy val parent = Project(
    id = "iota",
    base = file("."),
    aggregate = Seq(Stream, ZMQ, VirtualSensor, Fey),
    settings = rootbuildSettings ++ Seq(
      aggregate in update := false
    )
  )

  lazy val fey = Project(
    id = "fey-core",
    base = file("./fey-core"),
    settings = BasicSettings ++ FeybuildSettings ++ Seq(
      libraryDependencies ++= ModuleDependencies.FeyDependencies

    ))

  lazy val stream = Project(
    id = "fey-stream",
    base = file("./performers/stream"),
    settings = BasicSettings ++ StreambuildSettings ++ Seq(
      libraryDependencies ++= ModuleDependencies.StreamDependencies

    ))

   lazy val zmq = Project(
    id = "fey-zmq",
    base = file("./performers/zmq"),
    settings = BasicSettings ++ ZMQbuildSettings ++ Seq(
      libraryDependencies ++= ModuleDependencies.ZMQDependencies
    ))

  lazy val virtual_sensor = Project(
    id = "fey-virtual-sensor",
    base = file("./performers/virtual_sensor"),
    settings = BasicSettings ++ VirtualSensorbuildSettings ++ Seq(
      libraryDependencies ++= ModuleDependencies.VirtualSensorDependencies
    ))

}
