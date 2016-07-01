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

/**
  * Trie data structure used to create actors hierarchy in Fey
 */
case class TrieNode(path: String, var children: Array[TrieNode])

class Trie{

  private val root: TrieNode = TrieNode("FEY-MANAGEMENT-SYSTEM", Array.empty)
  var elements: Int = 0

  def appendPath(path: String): Unit = {
    appendPath(path.replaceFirst("akka://","").split("/"),root,1)
  }

  @tailrec private def appendPath(path: Array[String], root: TrieNode, index: Int): Unit = {
    if(root != null && index < path.length){
      var nextRoot = root.children.filter(child => child.path == path(index))
      if(nextRoot.isEmpty){
        nextRoot = Array(TrieNode(path(index), Array.empty))
        val children = root.children ++: nextRoot
        root.children = children
        elements += 1
      }
      appendPath(path, nextRoot(0),index+1)
    }
  }

  def print:JsValue = {
    getObject(root, null)
  }

  private def getObject(root: TrieNode, parent: TrieNode):JsObject = {
    if(root != null) {
     Json.obj("name" -> root.path,
       "parent" -> (if(parent != null) parent.path else "null"),
        "children" -> root.children.map(getObject(_, root))
     )
    }else{
      Json.obj()
    }
  }
}
