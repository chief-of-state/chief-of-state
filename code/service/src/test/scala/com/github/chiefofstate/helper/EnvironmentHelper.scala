/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.helper

import scala.collection.mutable
import scala.jdk.CollectionConverters.MapHasAsScala
import scala.language.postfixOps

object EnvironmentHelper {

  def getEnv: mutable.Map[String, String] = {
    val pe = Class.forName("java.lang.ProcessEnvironment")
    val env = pe.getDeclaredMethod("getenv")
    env.setAccessible(true)
    val props = pe.getDeclaredField("theCaseInsensitiveEnvironment")
    props.setAccessible(true)
    props.get(null).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]].asScala
  }

  def setEnv(key: String, value: String): Unit = {
    getEnv.put(key, value)
  }

  def clearEnv(): Unit = {
    getEnv.clear()
  }
}
