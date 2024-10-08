/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.migration

import com.dimafeng.testcontainers.{ForAllTestContainer, PostgreSQLContainer}
import com.github.chiefofstate.migration.helper.TestConfig
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.testcontainers.utility.DockerImageName
import slick.basic.DatabaseConfig
import slick.dbio.DBIOAction
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._

import java.sql.{Connection, DriverManager}
import scala.concurrent.Await
import scala.concurrent.duration.Duration

class SchemasUtilSpec extends BaseSpec with ForAllTestContainer {
  val testKit: ActorTestKit = ActorTestKit()

  val cosSchema: String = "cos"

  override val container: PostgreSQLContainer = PostgreSQLContainer
    .Def(
      dockerImageName = DockerImageName.parse("postgres:11"),
      urlParams = Map("currentSchema" -> cosSchema)
    )
    .createContainer()

  // journal jdbc config
  lazy val journalJdbcConfig: DatabaseConfig[JdbcProfile] =
    TestConfig.dbConfigFromUrl(
      container.jdbcUrl,
      container.username,
      container.password,
      "write-side-slick"
    )

  def runAndWait[R](a: DBIOAction[R, NoStream, Nothing]): R = {
    Await.result(journalJdbcConfig.db.run(a), Duration.Inf)
  }

  /**
   * create connection to the container db for test statements
   */
  def getConnection: Connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
  }

  // helper to drop the schema
  def recreateSchema(): Unit = {
    val statement = getConnection.createStatement()
    statement.addBatch(s"drop schema if exists $cosSchema cascade")
    statement.addBatch(s"create schema $cosSchema")
    statement.executeBatch()
  }

  override def beforeEach(): Unit = {
    recreateSchema()
  }

  "An instance of SchemasUtils" should {
    "create the journal tables" in {
      SchemasUtil.createStoreTables(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true
    }

    " drop the journal tables" in {
      SchemasUtil.createStoreTables(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe true
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe true

      SchemasUtil.dropJournalTables(journalJdbcConfig) shouldBe {}
      DbUtil.tableExists(journalJdbcConfig, "event_journal") shouldBe false
      DbUtil.tableExists(journalJdbcConfig, "event_tag") shouldBe false
      DbUtil.tableExists(journalJdbcConfig, "state_snapshot") shouldBe false
    }
  }
}
