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

import akka.actor.ActorRef
import org.springframework.beans.factory.config.AutowireCapableBeanFactory

import scala.concurrent.duration._
import org.springframework.context.ApplicationContext
import org.springframework.context.support.ClassPathXmlApplicationContext

abstract class FeyGenericSpringActor(override val params: Map[String,String] = Map.empty,
                                    override val backoff: FiniteDuration = 1.minutes,
                                    override val connectTo: Map[String,ActorRef] = Map.empty,
                                    override val schedulerTimeInterval: FiniteDuration = 2.seconds,
                                    override val orchestrationName: String = "",
                                    override val orchestrationID: String = "",
                                    override val autoScale: Boolean = false,
                                    val appContextPath: String = "") extends FeyGenericActor {

  val appContext: ApplicationContext = new ClassPathXmlApplicationContext(appContextPath)
  val factory: AutowireCapableBeanFactory = appContext.getAutowireCapableBeanFactory

  override def onStart(): Unit = {
    super.onStart()
    factory.autowireBeanProperties(this, AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE, true)
  }
}