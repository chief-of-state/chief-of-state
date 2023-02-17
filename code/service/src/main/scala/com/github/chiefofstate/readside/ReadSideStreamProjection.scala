/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import akka.actor.typed.{ ActorSystem, Behavior }
import akka.cluster.sharding.typed.ShardedDaemonProcessSettings
import akka.cluster.sharding.typed.scaladsl.ShardedDaemonProcess
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.Offset
import akka.projection.{ ProjectionBehavior, ProjectionId }
import akka.projection.eventsourced.EventEnvelope
import akka.projection.eventsourced.scaladsl.EventSourcedProvider
import akka.projection.jdbc.scaladsl.JdbcProjection
import akka.projection.scaladsl.{ GroupedProjection, SourceProvider }
import com.github.chiefofstate.protobuf.v1.persistence.EventWrapper
import org.slf4j.{ Logger, LoggerFactory }

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
private[readside] class ReadSideStreamProjection(
    actorSystem: ActorSystem[_],
    val readSideId: String,
    val dataSource: DataSource,
    readSideStreamHandler: ReadSideStreamHandler,
    val numShards: Int)
    extends Projection {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  implicit val sys: ActorSystem[_] = actorSystem

  /**
   * Initialize the projection to start fetching the events that are emitted
   */
  def start(): Unit = {
    ShardedDaemonProcess(actorSystem).init[ProjectionBehavior.Command](
      name = readSideId,
      numberOfInstances = numShards,
      behaviorFactory = shardNumber => jdbcGroupedProjection(shardNumber.toString),
      settings = ShardedDaemonProcessSettings(actorSystem),
      stopMessage = Some(ProjectionBehavior.Stop))
  }

  /**
   * creates a jdbc grouped projection
   *
   * @param tagName the event tag
   * @return the jdbc grouped projection behavior
   */
  private[readside] def jdbcGroupedProjection(tagName: String): Behavior[ProjectionBehavior.Command] = {
    val projection: GroupedProjection[Offset, EventEnvelope[EventWrapper]] =
      JdbcProjection
        .groupedWithin(
          projectionId = ProjectionId(readSideId, tagName),
          sourceProvider = ReadSideProjection.sourceProvider(actorSystem, tagName),
          // defines a session factory that returns a jdbc
          // session connected to the hikari pool
          sessionFactory = () => new ReadSideJdbcSession(dataSource.getConnection()),
          handler = () => new ReadSideJdbcStreamHandler(readSideId, readSideStreamHandler))
        .withGroup(groupAfterEnvelopes = 100, groupAfterDuration = 500.millis) // FIXME set this values in configuration

    ProjectionBehavior(projection)
  }
}

private[readside] object ReadSideStreamProjection {

  /**
   * Set the Event Sourced Provider per tag
   *
   * @param system the actor system
   * @param tag the event tag
   * @return the event sourced provider
   */
  private[readside] def sourceProvider(
      system: ActorSystem[_],
      tag: String): SourceProvider[Offset, EventEnvelope[EventWrapper]] = {
    EventSourcedProvider.eventsByTag[EventWrapper](system, readJournalPluginId = JdbcReadJournal.Identifier, tag)
  }
}
