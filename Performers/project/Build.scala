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

import _root_.sbtassembly.AssemblyPlugin.autoImport._
import sbt._
import sbt.Keys._

object ModuleDependencies {

  import Dependencies._

  val StreamDependencies = provided(akka_actor, fey)
  val ZMQDependecies = provided(akka_actor,  fey) ++ compile(zmq)
}

object PerformersBuild extends Build {

  import BuildSettings._

  lazy val parent = Project(
    id = "jars_parent",
    base = file("."),
    aggregate = Seq(Stream, ZMQ),
    settings = rootbuildSettings ++ Seq(
      aggregate in update := false
    )
  )

  lazy val stream = Project(
    id = "fey_stream",
    base = file("./stream"),
    settings = BasicSettings ++ StreambuildSettings ++ Seq(
      libraryDependencies ++= ModuleDependencies.StreamDependencies ,
      assemblyJarName in assembly := "fey_stream.jar"

    ))

  lazy val zmq = Project(
    id = "fey_zmq",
    base = file("./zmq"),
    settings = BasicSettings ++ ZMQbuildSettings ++ Seq(
      libraryDependencies ++= ModuleDependencies.ZMQDependecies,
      assemblyJarName in assembly := "fey_zmq.jar"
    ))

}
