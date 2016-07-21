
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

import java.io.File
import java.nio.file.Paths

import org.apache.commons.io.FileUtils

object TestSetup {

  private var runSetup = true

  val configTest = getClass.getResource("/test-fey-configuration.conf")

  def setup(): Unit = {
    if(runSetup){
      println("SETTING UP ...")
      createFeyTmpDirectoriesForTest()
      copyTestActorToTmp()
      copyJSONstoTmp()
      runSetup = false
    }
  }

  private def copyTestActorToTmp(): Unit = {
    copyResourceFileToLocal("/fey-test-actor.jar",s"${CONFIG.JAR_REPOSITORY}/fey-test-actor.jar")
  }

  private def copyJSONstoTmp(): Unit = {
    copyResourceFileToLocal("/json/valid-json.json",s"${CONFIG.JSON_REPOSITORY}/valid-json.json.not")
    copyResourceFileToLocal("/json/invalid-json.json",s"${CONFIG.JSON_REPOSITORY}/invalid-json.json.not")
  }

  private def copyResourceFileToLocal(resourcePath: String, destination: String): Unit = {
    val resourceFile = getClass.getResource(resourcePath)
    val dest = new File(destination)
    FileUtils.copyURLToFile(resourceFile, dest)
  }

  private def createFeyTmpDirectoriesForTest(): Unit = {
    var file = new File(s"/tmp/fey/test/checkpoint")
    file.mkdirs()
    file = new File(s"/tmp/fey/test/json")
    file.mkdirs()
    file = new File(s"/tmp/fey/test/jars")
    file.mkdirs()
    file = new File(s"/tmp/fey/test/jars/dynamic")
    file.mkdirs()
  }


}
