import sbt.{Defaults, Watched}
import sbt.Keys._

object BuildSettings {

  import Dependencies.Resolvers._

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

  lazy val BasicSettings = Seq(
    maxErrors := 5,
    ivyScala := ivyScala.value map {
      _.copy(overrideScalaVersion = true)
    },
    triggeredMessage := Watched.clearWhenTriggered,
    resolvers := allResolvers,
    mainClass := Some("org.apache.iota.fey.Application"),
    fork := true,
    connectInput in run := true
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
