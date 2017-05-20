/**
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
import sbtassembly.AssemblyPlugin.autoImport._

object BuildSettings {

  import Dependencies.Resolvers._

  val ParentProject = "iota"
  val Fey = "fey-core"
  val Stream = "fey-stream"
  val ZMQ = "fey-zmq"
  val VirtualSensor = "fey-virtual-sensor"

  val Version = "1.0"
  val ScalaVersion = "2.11.8"



  lazy val rootbuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := ParentProject,
    version := Version,
    scalaVersion := ScalaVersion,
    organization := "org.apache.iota",
    description := "Apache iota build",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint")
  )

  lazy val BasicSettings = Seq(
    organization := "org.apache.iota",
    maxErrors := 5,
    ivyScala := ivyScala.value map {
      _.copy(overrideScalaVersion = true)
    },
    triggeredMessage := Watched.clearWhenTriggered,
    resolvers := allResolvers,
    fork := true,
    connectInput in run := true
  )

  lazy val FeybuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := Fey,
    version := "1.0-SNAPSHOT",
    scalaVersion := ScalaVersion,
    description := "Framework of the event processing / actions engine for IOTA",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint"),
    mainClass := Some("org.apache.iota.fey.Application"),
    assemblyJarName in assembly := "iota-fey-core.jar",
    publishTo := {
      val nexus = "s3://maven.litbit.com/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "snapshots")
      else
        Some("releases"  at nexus + "releases")
    },
    publishMavenStyle := true,
    conflictManager := ConflictManager.latestRevision,
    assemblyMergeStrategy in assembly := {
      case "reference.conf"  => MergeStrategy.concat
      case "application.conf"  => MergeStrategy.concat
      case PathList("org", "slf4j", xs @ _*)  => MergeStrategy.last
      case PathList("META-INF", "io.netty.versions.properties") => MergeStrategy.last
      case PathList("scala", "xml", xs @ _*)         => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    },
    //All tests on Fey are integrated tests.
    //All tests need to be execute sequentially
    parallelExecution in Test := false,
    testOptions in Test += Tests.Cleanup( () => {
      print("\nCLeaning up")
      removeAll("/tmp/fey/test")
      def removeAll(path: String) = {
        def getRecursively(f: File): Seq[File] =
          f.listFiles.filter(_.isDirectory).flatMap(getRecursively) ++ f.listFiles
        getRecursively(new File(path)).foreach{f =>
          if (!f.delete()) println(s"could not delete ${f.getAbsolutePath}")}
      }
    })
  )

  lazy val StreambuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := Stream,
    version := Version,
    scalaVersion := ScalaVersion,
    description := "Simple Stream Application",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint"),
    mainClass := Some("org.apache.iota.fey.performer.Application")
  )

  lazy val ZMQbuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := ZMQ,
    version := Version,
    scalaVersion := ScalaVersion,
    description := "ZMQ Application",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint"),
    mainClass := Some("org.apache.iota.fey.performer.Application")
  )

  lazy val VirtualSensorbuildSettings = Defaults.coreDefaultSettings ++ Seq(
    name := VirtualSensor,
    version := Version,
    scalaVersion := ScalaVersion,
    description := "Virtual Sensor Application",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint"),
    mainClass := Some("org.apache.iota.fey.performer.Application")
  )

}
