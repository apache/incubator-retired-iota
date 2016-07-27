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

import play.api.libs.json.{JsArray, JsObject, JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * Trie data structure used to create actors hierarchy in Fey
  */
case class TrieNode(path: String, children: ArrayBuffer[TrieNode], events: ArrayBuffer[Monitor.MonitorEvent])

protected class Trie(systemName: String) {

  private val root: TrieNode = TrieNode(systemName, ArrayBuffer.empty, ArrayBuffer.empty)
  var elements: Int = 0

  def append(path: String, event: Monitor.MonitorEvent = CONSTANTS.NULL): Unit = {
    append(path.replaceFirst("akka://", "").split("/"), root, 1, event)
  }

  @tailrec private def append(path: Array[String], root: TrieNode, index: Int, event: Monitor.MonitorEvent): Unit = {
    if (Option(root).isDefined && index < path.length) {
      var nextRoot = root.children.filter(child => child.path == path(index))
      if (nextRoot.isEmpty) {
        nextRoot = ArrayBuffer(TrieNode(path(index), ArrayBuffer.empty, ArrayBuffer.empty))
        root.children += nextRoot(0)
        elements += 1
      }
      if (Option(event).isDefined && index == path.length - 1) {
        nextRoot(0).events += event
      }
      append(path, nextRoot(0), index + 1, event)
    }
  }

  def hasPath(path: String): Boolean = {
    recHasPath(root, path.replaceFirst("akka://", "").split("/"), 1)
  }

  @tailrec private def recHasPath(root: TrieNode, path: Array[String], index: Int): Boolean = {
    if (Option(root).isDefined && index < path.length) {
      val nextRoot = root.children.filter(child => child.path == path(index))
      if (nextRoot.isEmpty) {
        false
      } else {
        recHasPath(nextRoot(0), path, index + 1)
      }
    } else {
      true
    }
  }

  def getNode(path: String): Option[TrieNode] = {
    recGetNode(root, path.replaceFirst("akka://", "").split("/"), 1)
  }

  @tailrec private def recGetNode(root: TrieNode, path: Array[String], index: Int): Option[TrieNode] = {
    if (Option(root).isDefined && index < path.length) {
      val nextRoot = root.children.filter(child => child.path == path(index))
      if (nextRoot.isEmpty) {
        None
      } else {
        if (path.length - 1 == index) {
          Some(nextRoot(0))
        } else {
          recGetNode(nextRoot(0), path, index + 1)
        }
      }
    } else {
      None
    }
  }

  def removeAllNodes(): Unit = {
    var index = 0
    while (index < root.children.length) {
      root.children.remove(index)
      index += 1
    }
    elements = 0
  }

  def print: JsValue = {
    getObject(root, CONSTANTS.NULL)
  }

  def printWithEvents: JsValue = {
    getObjectEvent(root, CONSTANTS.NULL)
  }

  def getRootChildren(): ArrayBuffer[TrieNode] = {
    root.children
  }

  private def getObject(root: TrieNode, parent: TrieNode): JsObject = {
    if (Option(root).isDefined) {
      Json.obj("name" -> root.path,
        "parent" -> (if (Option(parent).isDefined) parent.path else "null"),
        "children" -> root.children.map(getObject(_, root))
      )
    } else {
      Json.obj()
    }
  }

  private def getObjectEvent(root: TrieNode, parent: TrieNode): JsObject = {
    if (Option(root).isDefined) {
      Json.obj("name" -> root.path,
        "parent" -> (if (Option(parent).isDefined) parent.path else "null"),
        "events" -> root.events.map(event => {
          Json.obj("type" -> event.event,
            "timestamp" -> event.timestamp,
            "info" -> event.info
          )
        }),
        "children" -> root.children.map(getObjectEvent(_, root))
      )
    } else {
      Json.obj()
    }
  }
}
