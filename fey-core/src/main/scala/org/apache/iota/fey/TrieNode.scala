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

import play.api.libs.json.{JsObject, JsValue, Json}

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * Trie data structure used to create actors hierarchy in Fey
 */
case class TrieNode(path: String, children: ArrayBuffer[TrieNode], events:ArrayBuffer[Monitor.MonitorEvent])

protected class Trie(systemName: String){

  private val DEFAULT_TRIE_NODE = TrieNode(systemName, ArrayBuffer.empty, ArrayBuffer.empty)

  private val root: Option[TrieNode] = Option(DEFAULT_TRIE_NODE)
  var elements: Int = 0

  def append(path: String, event: Monitor.MonitorEvent = null): Unit = {
    append(path.replaceFirst("akka://", "").split("/"), root, 1, event)
  }

  @tailrec private def append(path: Array[String], root: Option[TrieNode], index: Int, event: Monitor.MonitorEvent): Unit = {
    if (root.isDefined && index < path.length) {
      var nextRoot = root.fold(DEFAULT_TRIE_NODE)(identity).children.filter(child => child.path == path(index))
      if(nextRoot.isEmpty){
        nextRoot = ArrayBuffer(TrieNode(path(index), ArrayBuffer.empty, ArrayBuffer.empty))
        root.fold(DEFAULT_TRIE_NODE)(identity).children += nextRoot(0)
        elements += 1
      }
      if (Option(event).isDefined && index == path.length - 1) {
        nextRoot(0).events += event
      }
      append(path, nextRoot.headOption, index + 1, event)
    }
  }

  def hasPath(path: String): Boolean = {
    recHasPath(root, path.replaceFirst("akka://","").split("/"),1)
  }

  @tailrec private def recHasPath(root: Option[TrieNode], path: Array[String], index: Int): Boolean = {
    if (root.isDefined && index < path.length) {
      var nextRoot = root.fold(DEFAULT_TRIE_NODE)(identity).children.filter(child => child.path == path(index))
      if(nextRoot.isEmpty){
        false
      }else{
        recHasPath(nextRoot.headOption, path, index + 1)
      }
    }else{
      true
    }
  }

  def getNode(path: String): Option[TrieNode] = {
    recGetNode(root, path.replaceFirst("akka://","").split("/"),1)
  }

  @tailrec private def recGetNode(root: Option[TrieNode], path: Array[String], index: Int): Option[TrieNode] = {
    if (root.isDefined && index < path.length) {
      var nextRoot = root.fold(DEFAULT_TRIE_NODE)(identity).children.filter(child => child.path == path(index))
      if(nextRoot.isEmpty){
        None
      }else{
        if(path.length - 1 == index){
            Some(nextRoot(0))
        }else {
          recGetNode(nextRoot.headOption, path, index + 1)
        }
      }
    }else{
      None
    }
  }

  def removeAllNodes(): Unit = {
    var index = 0
    while (index < root.fold(DEFAULT_TRIE_NODE)(identity).children.length) {
      root.fold(DEFAULT_TRIE_NODE)(identity).children.remove(index)
      index += 1
    }
    elements = 0
  }

  def print:JsValue = {
    getObject(root, null)
  }

  def printWithEvents:JsValue = {
    getObjectEvent(root, null)
  }

  def getRootChildren():ArrayBuffer[TrieNode] = {
    root.fold(DEFAULT_TRIE_NODE)(identity).children
  }

  private def getObject(root: Option[TrieNode], parent: Option[TrieNode]): JsObject = {
    if (root.isDefined) {
      Json.obj("name" -> root.fold(DEFAULT_TRIE_NODE)(identity).path,
       "parent" -> (if (parent.isDefined) parent.fold(DEFAULT_TRIE_NODE)(identity).path else "null"),
        "children" -> root.fold(DEFAULT_TRIE_NODE)(identity).children.map(a => getObject(Option(a), root))
     )
    }else{
      Json.obj()
    }
  }

  private def getObjectEvent(root: Option[TrieNode], parent: Option[TrieNode]):JsObject = {
    if (root.isDefined) {
      Json.obj("name" -> root.fold(DEFAULT_TRIE_NODE)(identity).path,
        "parent" -> (if (parent.isDefined) parent.fold(DEFAULT_TRIE_NODE)(identity).path else "null"),
        "events" -> root.fold(DEFAULT_TRIE_NODE)(identity).events.map(event => {
          Json.obj("type" -> event.event,
          "timestamp" -> event.timestamp,
            "info" -> event.info
          )
        }),
        "children" -> root.fold(DEFAULT_TRIE_NODE)(identity).children.map(a=>getObjectEvent(Option(a), root))
      )
    }else{
      Json.obj()
    }
  }
}
