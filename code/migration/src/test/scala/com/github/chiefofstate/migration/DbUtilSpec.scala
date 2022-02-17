/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.migration

import com.dimafeng.testcontainers.{ ForAllTestContainer, PostgreSQLContainer }
import com.github.chiefofstate.migration.helper.TestConfig
import org.postgresql.PGProperty
import org.testcontainers.utility.DockerImageName

import java.sql.DriverManager

class DbUtilSpec extends BaseSpec with ForAllTestContainer {
  override val container: PostgreSQLContainer =
    PostgreSQLContainer.Def(dockerImageName = DockerImageName.parse("postgres:11")).createContainer()

  def connection = {
    // load the driver
    Class.forName("org.postgresql.Driver")

    DriverManager.getConnection(container.jdbcUrl, container.username, container.password)
  }

  def dbConfig = TestConfig.dbConfigFromUrl(container.jdbcUrl, container.username, container.password)

  ".tableExists" should {
    "return true if table exists" in {
      val statement = connection.createStatement()
      statement.addBatch(s"create table real_table(id int)")
      statement.executeBatch()

      DbUtil.tableExists(dbConfig, "real_table") shouldBe true
    }
    "return false for missing table" in {
      DbUtil.tableExists(dbConfig, "fake_table") shouldBe false
    }
  }
  ".isDatabaseOnline" should {
    "return true when the database is online" in {
      // let us parse the database jdbc url to get the port and the ost
      val props = org.postgresql.Driver.parseURL(container.jdbcUrl, null);

      val host = props.getProperty(PGProperty.PG_HOST.getName)
      val port = Integer.parseInt(props.getProperty(PGProperty.PG_PORT.getName))

      val online = DbUtil.isDatabaseOnline(host, port)
      online shouldBe true
    }
    "return false when the database is not online" in {
      val host = "localhost"
      val port = 2000
      val online = DbUtil.isDatabaseOnline(host, port)
      online shouldBe false
    }
  }
}
