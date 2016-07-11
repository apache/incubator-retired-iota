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
    conflictManager := ConflictManager.all,
    assemblyMergeStrategy in assembly := {
      case PathList("org", "slf4j", xs @ _*)         => MergeStrategy.last
      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
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
