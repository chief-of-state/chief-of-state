/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import com.github.chiefofstate.config.ReadSideFailurePolicy
import com.github.chiefofstate.helper.BaseSpec
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}

import javax.sql.DataSource

class ReadSideSpec extends BaseSpec {
  lazy val config: Config = ConfigFactory
    .parseResources("test.conf")
    .withValue("write-side-slick.db.url", ConfigValueFactory.fromAnyRef("fake-url"))
    .withValue("write-side-slick.db.user", ConfigValueFactory.fromAnyRef("user"))
    .withValue("write-side-slick.db.password", ConfigValueFactory.fromAnyRef("password"))
    .withValue("write-side-slick.db.serverName", ConfigValueFactory.fromAnyRef("host"))
    .withValue("write-side-slick.db.databaseName", ConfigValueFactory.fromAnyRef("postgres"))
    .resolve()

  lazy val testKit: ActorTestKit = ActorTestKit(config)

  val actorSystem = testKit.system

  override protected def afterAll(): Unit = {
    super.afterAll()
    testKit.shutdownTestKit()
  }

  ".jdbcProjection" should {
    "run without failure" in {
      val projectionId           = "some-projection"
      val dataSource: DataSource = mock[DataSource]
      val readHandler: Handler   = mock[Handler]
      val numShards              = 2
      val failurePolicy          = ReadSideFailurePolicy.StopDirective
      val projection =
        new ReadSide(actorSystem, projectionId, dataSource, readHandler, numShards, failurePolicy)
      val tagName: String = "1"
      projection.jdbcProjection(tagName)
    }
  }
  ".sourceProvider" should {
    "run without failure" in {
      noException shouldBe thrownBy(ReadSide.sourceProvider(actorSystem, "1"))
    }
  }
}
