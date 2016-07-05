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

import sbt._
import sbt.Keys._
import sbtassembly.AssemblyPlugin.autoImport._

object Settings {

  lazy val iota_connectors = Defaults.coreDefaultSettings ++ Seq(
    name := "FEY-CORE",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.8",
    organization := "org.apache.iota",
    description := "Framework of the event processing / actions engine for IOTA",
    scalacOptions := Seq("-deprecation", "-unchecked", "-encoding", "utf8", "-Xlint")
  )

}

object Resolvers {

  val typesafe = "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
  val sonatype = "Sonatype Release" at "https://oss.sonatype.org/content/repositories/releases"
  val mvnrepository = "MVN Repo" at "http://mvnrepository.com/artifact"
  val emuller = "emueller-bintray" at "http://dl.bintray.com/emueller/maven"
  val typeSafeMavenResolvers = "Typesafe repository" at "https://repo.typesafe.com/typesafe/maven-releases/"

  val allResolvers = Seq(typesafe, sonatype, mvnrepository, emuller, typeSafeMavenResolvers)

}

object Dependency {

  val akka_actor = "com.typesafe.akka" %% "akka-actor" % "2.4.2"
  val typesafe_config = "com.typesafe" % "config" % "1.3.0"

  val playJson = "com.typesafe.play" %% "play-json" % "2.5.3"
  val jsonValidator = "com.eclipsesource" %% "play-json-schema-validator" % "0.7.0"
  val playNetty = "com.typesafe.play" %% "play-netty-server" % "2.5.3"
  //Logger
  val slf4j = "com.typesafe.akka" %% "akka-slf4j" % "2.4.2"
  val log4jbind = "ch.qos.logback" % "logback-classic" % "1.1.7"
  val javaFilter = "janino" % "janino" % "2.5.10"


}

object Dependencies {

  import Dependency._

  val iota_dep_conn = Seq(akka_actor,
    typesafe_config,
    playJson,
    playNetty,
    slf4j,
    log4jbind,
    jsonValidator,
    javaFilter)
}

object IotaBuild extends Build {

  import Resolvers._
  import Dependencies._
  import Settings._

  lazy val iota = Project(
    id = "iota-fey-core",
    base = file("."),
    settings = iota_connectors ++ Seq(
      maxErrors := 5,
      ivyScala := ivyScala.value map {
        _.copy(overrideScalaVersion = true)
      },
      triggeredMessage := Watched.clearWhenTriggered,
      resolvers := allResolvers,
      libraryDependencies ++= iota_dep_conn,
      mainClass := Some("org.apache.iota.fey.Application"),
      fork := true,
      connectInput in run := true,
      assemblyJarName in assembly := "iota-fey-core.jar",
      publishTo := {
        val nexus = "s3://maven.litbit.com/"
        if (isSnapshot.value)
          Some("snapshots" at nexus + "snapshots")
        else
          Some("releases" at nexus + "releases")
      },
      publishMavenStyle := true,
      conflictManager := ConflictManager.all,
      assemblyMergeStrategy in assembly := {
        case PathList("org", "slf4j", xs@_*) => MergeStrategy.last
        case x =>
          val oldStrategy = (assemblyMergeStrategy in assembly).value
          oldStrategy(x)
      }
    )
  )
}



