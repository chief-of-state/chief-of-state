/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import com.github.chiefofstate.config.ReadSideFailurePolicy
import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import com.github.chiefofstate.types.FailurePolicy
import org.apache.pekko.actor.typed.{ActorSystem, Behavior}
import org.apache.pekko.cluster.sharding.typed.ShardedDaemonProcessSettings
import org.apache.pekko.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import org.apache.pekko.persistence.jdbc.query.scaladsl.JdbcReadJournal
import org.apache.pekko.persistence.query.Offset
import org.apache.pekko.projection.eventsourced.EventEnvelope
import org.apache.pekko.projection.eventsourced.scaladsl.EventSourcedProvider
import org.apache.pekko.projection.jdbc.scaladsl.JdbcProjection
import org.apache.pekko.projection.scaladsl.SourceProvider
import org.apache.pekko.projection.{HandlerRecoveryStrategy, ProjectionBehavior, ProjectionId}
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
    readSideHandler: Handler,
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
      sessionFactory = () => new JdbcSession(dataSource.getConnection()),
      handler = () => new JdbcHandler(tagName, readSideId, readSideHandler)
    )

    // Parse and apply failure policy using type-safe ADT
    val policy = FailurePolicy.fromStringOrDefault(failurePolicy)

    val configuredProjection = policy match {
      // this will completely stop the given readside when the processing of an event failed
      case FailurePolicy.Stop =>
        log.info(s"Configuring read side $readSideId with STOP policy")
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
      case FailurePolicy.Skip =>
        log.info(s"Configuring read side $readSideId with SKIP policy")
        projection.withRecoveryStrategy(HandlerRecoveryStrategy.skip)

      // this will attempt to replay the failed processed event and stop the given readside
      case FailurePolicy.RetryAndStop(retries, delaySeconds) =>
        log.info(
          s"Configuring read side $readSideId with RETRY-AND-STOP policy (retries=$retries, delay=${delaySeconds}s)"
        )
        // here we disable restart because we are stopping the readside
        projection
          .withRecoveryStrategy(HandlerRecoveryStrategy.retryAndFail(retries, delaySeconds.seconds))
          .withRestartBackoff(
            minBackoff = 3.seconds,
            maxBackoff = 30.seconds,
            randomFactor = 0.2,
            maxRestarts = 0
          )

      // this will attempt to replay the failed processed event and skip to the next event
      case FailurePolicy.RetryAndSkip(retries, delaySeconds) =>
        log.info(
          s"Configuring read side $readSideId with RETRY-AND-SKIP policy (retries=$retries, delay=${delaySeconds}s)"
        )
        projection.withRecoveryStrategy(
          HandlerRecoveryStrategy.retryAndSkip(retries, delaySeconds.seconds)
        )
    }

    ProjectionBehavior(configuredProjection)
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
