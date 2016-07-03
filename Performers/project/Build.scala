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

object BuildSettings {

  val ParentProject = "jars_parent"
  val Fey = "fey"
  val Stream = "fey_stream"
  val ZMQ = "fey_zmq"


  val Version = "1.0"
  val ScalaVersion = "2.11.8"

  lazy val rootbuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := ParentProject,
    version := Version,
    scalaVersion := ScalaVersion,
    organization := "com.libit",
    description := "Fey External Jars Project",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint")
  )

  lazy val FeybuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := Fey,
    version := Version,
    scalaVersion := ScalaVersion,
    organization := "com.litbit",
    description := "Fey Development Instance",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint")
  )

  lazy val StreambuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := Stream,
    version := Version,
    scalaVersion := ScalaVersion,
    organization := "org.apache.iota.fey.performer",
    description := "Simple Stream Application",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint")
  )

   lazy val ZMQbuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := ZMQ,
    version := Version,
    scalaVersion := ScalaVersion,
    organization := "org.apache.iota.fey.performer",
    description := "ZMQ Application",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint")
  )

}

object Resolvers {

  val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  val sonatype = "Sonatype Release" at "https://oss.sonatype.org/content/repositories/releases"
  val mvnrepository = "MVN Repo" at "http://mvnrepository.com/artifact"
  val litbitBitbucket = "Litbit Repo" at "https://s3-us-west-2.amazonaws.com/maven.litbit.com/snapshots"
  val emuller = "emueller-bintray" at "http://dl.bintray.com/emueller/maven"

  val allResolvers = Seq(typesafe, sonatype, mvnrepository, emuller, litbitBitbucket)

}

object Dependency {
  //Use when building Artifacts (jar files)
  val akka_actor = "com.typesafe.akka" %% "akka-actor" % "2.4.2" % "provided"
  val fey = "org.apache.iota" %% "fey-core" % "1.0-SNAPSHOT" % "provided"
  val zmq = "org.zeromq" % "jeromq" % "0.3.5"
}

object Dependencies {

  import Dependency._

  val FeyDependencies = Seq(akka_actor, fey)
  val StreamDependencies = Seq(akka_actor, fey)
  val ZMQDependecies = Seq(akka_actor, zmq, fey)
}

object JarsBuild extends Build {

  import Resolvers._
  import BuildSettings._

  lazy val parent = Project(
    id = "jars_parent",
    base = file("."),
    aggregate = Seq(Stream, ZMQ, Fey),
    settings = rootbuildSettings ++ Seq(
      aggregate in update := false
    )
  )

  lazy val fey = Project(
    id = "fey",
    base = file("./fey"),
    settings = FeybuildSettings ++ Seq(
      maxErrors := 5,
      ivyScala := ivyScala.value map {
        _.copy(overrideScalaVersion = true)
      },
      triggeredMessage := Watched.clearWhenTriggered,
      resolvers := allResolvers,
      libraryDependencies ++= Dependencies.FeyDependencies,
      mainClass := Some("org.apache.iota.fey.Application"),
      fork := true,
      connectInput in run := true
    ))

  lazy val stream = Project(
    id = "fey_stream",
    base = file("./stream"),
    settings = StreambuildSettings ++ Seq(
      maxErrors := 5,

      ivyScala := ivyScala.value map {
        _.copy(overrideScalaVersion = true)
      },
      triggeredMessage := Watched.clearWhenTriggered,
      resolvers := allResolvers,
      libraryDependencies ++= Dependencies.StreamDependencies,
      mainClass := Some("org.apache.iota.fey.performer.stream.Application"),
      fork := true,
      connectInput in run := true
    ))

   lazy val zmq = Project(
    id = "fey_zmq",
    base = file("./zmq"),
    settings = ZMQbuildSettings ++ Seq(
      maxErrors := 5,
      ivyScala := ivyScala.value map {
        _.copy(overrideScalaVersion = true)
      },
      triggeredMessage := Watched.clearWhenTriggered,
      resolvers := allResolvers,
      libraryDependencies ++= Dependencies.ZMQDependecies,
      mainClass := Some("org.apache.iota.fey.performer.zmq.Application"),
      fork := true,
      connectInput in run := true
    ))

}
