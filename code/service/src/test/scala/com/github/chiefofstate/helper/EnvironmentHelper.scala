/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.helper

import java.util.Collections
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

object EnvironmentHelper {
  def setEnv(newEnv: java.util.Map[String, String]): Unit = {
    Try {
      val processEnvironmentClass =
        Class.forName("java.lang.ProcessEnvironment")

      val theEnvironmentField =
        processEnvironmentClass.getDeclaredField("theEnvironment")
      theEnvironmentField.setAccessible(true)
      val env =
        theEnvironmentField
          .get(null)
          .asInstanceOf[java.util.Map[String, String]] // scalastyle:off null
      env.putAll(newEnv)

      val theCaseInsensitiveEnvironmentField =
        processEnvironmentClass.getDeclaredField("theCaseInsensitiveEnvironment")
      theCaseInsensitiveEnvironmentField.setAccessible(true)
      val ciEnv =
        theCaseInsensitiveEnvironmentField
          .get(null)
          .asInstanceOf[java.util.Map[String, String]] // scalastyle:off null
      ciEnv.putAll(newEnv)
    } match {
      case Failure(_: NoSuchFieldException) =>
        Try {
          val classes = classOf[Collections].getDeclaredClasses
          val env     = System.getenv
          classes
            .filter(_.getName == "java.util.Collections$UnmodifiableMap")
            .foreach(cl => {
              val field = cl.getDeclaredField("m")
              field.setAccessible(true)
              val map =
                field.get(env).asInstanceOf[java.util.Map[String, String]]
              map.clear()
              map.putAll(newEnv)
            })
        } match {
          case Failure(NonFatal(e2)) =>
            e2.printStackTrace()
          case Failure(e) =>
            e.printStackTrace()
          case Success(_) =>
        }
      case Failure(NonFatal(e1)) =>
        e1.printStackTrace()
      case Failure(e) =>
        e.printStackTrace()
      case Success(_) =>
    }
  }

  def clearEnv(): Unit = {
    val field = System.getenv().getClass.getDeclaredField("m")
    field.setAccessible(true)
    val map: java.util.Map[java.lang.String, java.lang.String] =
      field.get(System.getenv()).asInstanceOf[java.util.Map[java.lang.String, java.lang.String]]
    map.clear()
  }
}
