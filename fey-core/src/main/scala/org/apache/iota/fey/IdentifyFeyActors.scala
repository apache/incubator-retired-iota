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

package org.apache.iota.fey

import akka.actor.{Actor, ActorIdentity, ActorLogging, ActorPath, Identify}
import akka.routing.{ActorRefRoutee, GetRoutees, Routees}
import play.api.libs.json._

import scala.collection.mutable.HashSet

protected class IdentifyFeyActors extends Actor with ActorLogging {

  import IdentifyFeyActors._

  override def receive: Receive = {
    case IDENTIFY_TREE(startPath) =>
      log.info("Current Actors in system:")
      actorsPath = HashSet.empty
      rootPath = startPath
      log.info(startPath)
      self ! ActorPath.fromString(startPath)

    case path: ActorPath =>
      context.actorSelection(path / "*") ! Identify(())
      context.actorSelection(path / "*") ! GetRoutees

    case ActorIdentity(_, Some(ref)) =>
      actorsPath.add(ref.path.toString)
      log.info(ref.path.toString)
      self ! ref.path

    case routees:Routees =>
      routees.routees
        .map(_.asInstanceOf[ActorRefRoutee])
        .foreach(routee => {
          log.info(routee.ref.path.toString)
          actorsPath.add(routee.ref.path.toString)
        })

    case _ =>
  }
}

protected object IdentifyFeyActors{

  /**
    * Fires the identity event to all of the actors
    *
    * @param startPath
    */
  case class IDENTIFY_TREE(startPath: String)

  /**
    * Path of the root that fired the identity event
    */
  private var rootPath: String = ""

  /**
    * Creates a Set of all actors path that responded to the Identity
   */
  var actorsPath:HashSet[String] = HashSet.empty

  /**
    * Generates a Tree in JSON format.
    * Uses a Trie as data structure
    * @return string JSON
    */
  def generateTreeJson(): String = {
    val trie = new Trie("FEY-MANAGEMENT-SYSTEM")
    actorsPath.map(_.replace("user/","")).foreach(trie.append(_))

    Json.stringify(trie.print)
  }

  //Static HTML content from d3
  val html = scala.io.Source.fromInputStream(getClass.getResourceAsStream("/d3Tree.html"), "UTF-8")
    .getLines()
    .mkString("\n")

  def getHTMLTree(json: String): String = {
   html.replace("$MYJSONHIERARCHY", json)
  }

}

