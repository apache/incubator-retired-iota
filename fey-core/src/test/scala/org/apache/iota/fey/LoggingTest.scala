
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

/*
 * Source: https://github.com/Dwolla/scala-test-utils
 * The MIT License (MIT)
 * Copyright © 2016 Dwolla
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF
 * OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.apache.iota.fey

import ch.qos.logback.classic.{Level, Logger}
import org.scalatest.matchers.{MatchResult, Matcher}
import org.slf4j.helpers.SubstituteLogger
import org.slf4j.{LoggerFactory, Logger => SLF4JLogger}

trait LoggingTest{

  protected val logAppenderName = "inMemory"
  private val appender = findInMemoryAppender(logAppenderName)

  def beLoggedAt(logLevel: Level): Matcher[String] = new Matcher[String] {
    def apply(left: String) = {
      val containsAtLevel = appender.containsAtLevel(left, logLevel)
      MatchResult(containsAtLevel,
        s" '$left' was not found at log level",
        s" '$left' was found at log level")
    }
  }

  def resetCapturedLogs(): Unit = appender.reset()

  def dumpCapturedLogsToSysOut(): Unit = appender.dumpLogs()

  private def findInMemoryAppender(s: String): InMemoryAppender = {
    LoggerFactory.getLogger(SLF4JLogger.ROOT_LOGGER_NAME) match {
      case logger: Logger => logger.getAppender(s) match {
        case inMemoryAppender: InMemoryAppender => inMemoryAppender
        case _ => throw new IllegalStateException(s"Is the InMemoryAppender registered with logback in its configuration file with the name $s?")
      }
      case sub: SubstituteLogger => throw new IllegalStateException("SLF4J is probably still initializing. Is LoggingTest part of the outermost class wrapping your tests?")
      case _ => throw new IllegalStateException("Are you using LogBack logging?")
    }
  }
}