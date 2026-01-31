/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate

import com.github.chiefofstate.migration.versions.v6.V6
import com.github.chiefofstate.migration.{JdbcConfig, Migrator}
import com.github.chiefofstate.protobuf.v1.internal.{
  MigrationFailed,
  MigrationSucceeded,
  StartMigration
}
import com.github.chiefofstate.serialization.{Message, SendReceive}
import com.typesafe.config.Config
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.slf4j.{Logger, LoggerFactory}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

import scala.util.{Failure, Success, Try}

/**
 * kick starts the various migrations needed to run.
 * When the migration process is successful it replies back to the [[ServiceStarter]] to continue the boot process.
 * However when the migration process fails then  halt the whole boot process by shutting down the underlying actor system.
 * This is the logic behind running the actual migration
 * <p>
 *   <ol>
 *     <li> check the existence of the cos_migrations table
 *     <li> if the table exists go to step 4
 *     <li> if the table does not exist run the whole migration and reply back to the [[ServiceStarter]]
 *     <li> check the current version against the available versions
 *     <li> if current version is the last version then no need to run the migration just reply back to [[ServiceStarter]]
 *     <li> if not then run the whole migration and reply back to the [[ServiceStarter]]
 *   </ol>
 * </p>
 */
object Migration {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[Message] = Behaviors.setup[Message] { _ =>
    Behaviors.receiveMessage[Message] {
      case SendReceive(message, replyTo) if message.isInstanceOf[StartMigration] =>
        // Use resource management to ensure database is always closed
        val result: Try[Unit] = {
          // get the underlying database
          val journalJdbcConfig: DatabaseConfig[JdbcProfile] =
            JdbcConfig.journalConfig(config)

          try {
            // get the database schema to use
            val schema: String = config.getString("jdbc-default.schema")

            // create an instance of the v6 migration
            val v6: V6 = V6(journalJdbcConfig)
            // instance of the migrator
            val migrator: Migrator =
              new Migrator(journalJdbcConfig, schema).addVersion(v6)
            // run the migration
            migrator.run()
          } finally {
            // Always close the database connection to prevent memory leaks
            try {
              journalJdbcConfig.db.close()
              log.debug("Database connection closed successfully")
            } catch {
              case ex: Exception =>
                log.error(s"Error closing database connection: ${ex.getMessage}", ex)
            }
          }
        }

        result match {
          case Failure(exception) =>
            log.error(s"Migration failed: ${exception.getMessage}", exception)
            // notify the bootstrapper actor that the migration has failed
            replyTo ! MigrationFailed().withErrorMessage(exception.getMessage)
            // stopping the actor
            Behaviors.stopped

          case Success(_) =>
            log.info("ChiefOfState migration successfully done...")
            replyTo ! MigrationSucceeded()
            Behaviors.same
        }

      case SendReceive(message, _) =>
        log.warn(s"unhandled message ${message.companion.scalaDescriptor.fullName}")
        Behaviors.stopped

      case unhandled =>
        log.warn(s"cannot serialize ${unhandled.getClass.getName}")
        Behaviors.stopped
    }
  }
}
