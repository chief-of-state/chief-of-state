/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import akka.actor.typed.{ActorSystem, Behavior}
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.SourceProvider
import akka.projection.{HandlerRecoveryStrategy, ProjectionBehavior, ProjectionId}
import com.github.chiefofstate.config.ReadSideFailurePolicy
import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import org.slf4j.{Logger, LoggerFactory}

import javax.sql.DataSource
import scala.concurrent.duration.DurationInt

/**
 * Read side processor creates a sharded daemon process for handling
 * akka projections read sides
 *
 * @param actorSystem actor system
 * @param readSideId ID for this read side
 * @param dataSource hikari data source to connect through
 * @param readSideHandler the handler implementation for the read side
 * @param numShards number of shards for projections/tags
 */
private[readside] class ReadSide(
    actorSystem: ActorSystem[_],
    val readSideId: String,
    val dataSource: DataSource,
    readSideHandler: ReadSideHandler,
    val numShards: Int,
    val failurePolicy: String
) {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  implicit val sys: ActorSystem[_] = actorSystem

  /**
   * Initialize the readside to start fetching the events that are emitted
   */
  def start(): Unit =
    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = readSideId,
      numberOfInstances = numShards,
      behaviorFactory = shardNumber => jdbcProjection(shardNumber.toString),
      settings = ShardedDaemonProcessSettings(actorSystem),
      stopMessage = Some(ProjectionBehavior.Stop)
    )

  /**
   * creates a jdbc projection behavior
   *
   * @param tagName the name of the tag
   * @return a behavior for this projection
   */
  private[readside] def jdbcProjection(tagName: String): Behavior[ProjectionBehavior.Command] = {
    val projection = JdbcProjection.exactlyOnce(
      projectionId = ProjectionId(readSideId, tagName),
      sourceProvider = ReadSide.sourceProvider(actorSystem, tagName),
      // defines a session factory that returns a jdbc
      // session connected to the hikari pool
      sessionFactory = () => new ReadSideJdbcSession(dataSource.getConnection()),
      handler = () => new ReadSideJdbcHandler(tagName, readSideId, readSideHandler)
    )

    // let us set the failure policy
    failurePolicy.toUpperCase match {
      // this will completely stop the given readside when the processing of an event failed
      case ReadSideFailurePolicy.StopDirective =>
        // here we disable restart because we are stopping the readside
        projection
          .withRecoveryStrategy(HandlerRecoveryStrategy.fail)
          .withRestartBackoff(
            minBackoff = 3.seconds,
            maxBackoff = 30.seconds,
            randomFactor = 0.2,
            maxRestarts = 0
          )

      // this will skip the failed processed event and advanced the offset to continue to the next event.
      case ReadSideFailurePolicy.SkipDirective =>
        projection.withRecoveryStrategy(HandlerRecoveryStrategy.skip)

      // this will attempt to replay the failed processed event five times and stop the given readside
      case ReadSideFailurePolicy.ReplayStopDirective =>
        // here we disable restart because we are stopping the readside
        projection
          .withRecoveryStrategy(HandlerRecoveryStrategy.retryAndFail(5, 5.seconds))
          .withRestartBackoff(
            minBackoff = 3.seconds,
            maxBackoff = 30.seconds,
            randomFactor = 0.2,
            maxRestarts = 0
          )

      // this will attempt to replay the failed processed event five times and skip to the next event
      case ReadSideFailurePolicy.ReplaySkipDirective =>
        projection.withRecoveryStrategy(HandlerRecoveryStrategy.retryAndSkip(5, 5.seconds))
      case _ => () // we do nothing. Just use the default settings in the application.conf file
    }

    ProjectionBehavior(projection)
  }
}

private[readside] object ReadSide {

  /**
   * Set the Event Sourced Provider per tag
   *
   * @param system the actor system
   * @param tag the event tag
   * @return the event sourced provider
   */
  private[readside] def sourceProvider(
      system: ActorSystem[_],
      tag: String
  ): SourceProvider[Offset, EventEnvelope[EventWrapper]] =
    EventSourcedProvider
      .eventsByTag[EventWrapper](system, readJournalPluginId = JdbcReadJournal.Identifier, tag)
}
