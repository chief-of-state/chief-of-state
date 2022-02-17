/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.migration

import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.meta.MTable

import java.net.InetSocketAddress
import javax.net.SocketFactory
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{ Failure, Success, Using }

object DbUtil {

  /**
   * returns true if a given table exists in the given DatabaseConfig
   *
   * @param dbConfig a JDBC DatabaseConfig
   * @param tableName the table name to search for
   * @return true if the table exists
   */
  def tableExists(dbConfig: DatabaseConfig[JdbcProfile], tableName: String): Boolean = {
    val tables: Seq[MTable] = Await.result(dbConfig.db.run(MTable.getTables), Duration.Inf)
    tables.filter(_.tableType == "TABLE").exists(_.name.name.equals(tableName))
  }

  /**
   * helps drop a table
   *
   * @param tableName the table name
   * @param dbConfig the database config
   */
  def dropTableIfExists(tableName: String, dbConfig: DatabaseConfig[JdbcProfile]): Int = {
    Await.result(
      dbConfig.db.run(sqlu"""DROP TABLE IF EXISTS #$tableName CASCADE""".withPinnedSession.transactionally),
      Duration.Inf)
  }

  /**
   * checks whether the database server is online or not
   *
   * @param host the host address of the database server
   * @param port the port of the database server
   * @return true when the database server is up and false on the contrary
   */
  def isDatabaseOnline(host: String, port: Int, timeoutInMillis: Int = 20000): Boolean = {
    val sf = SocketFactory.getDefault
    val res = Using.Manager { _ =>
      val socket = sf.createSocket()
      socket.connect(new InetSocketAddress(host, port), timeoutInMillis)
    }
    res match {
      case Failure(_) => false
      case Success(_) => true
    }
  }
}
