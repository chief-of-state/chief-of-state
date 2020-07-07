package com.namely.chiefofstate

import akka.actor.ActorSystem
import com.namely.protobuf.chief_of_state.cos_persistence.State
import com.typesafe.config.Config
import io.superflat.lagompb.{AggregateRoot, CommandHandler, EventHandler}
import scalapb.GeneratedMessageCompanion

/**
 * ChiefOfStateAggregate
 *
 * @param actorSystem    the actor system
 * @param config         config object reading the application configuration file
 * @param commandHandler the commands handler
 * @param eventHandler   the events handler
 */
class ChiefOfStateAggregate(
    actorSystem: ActorSystem,
    config: Config,
    commandHandler: CommandHandler[State],
    eventHandler: EventHandler[State]
) extends AggregateRoot[State](actorSystem, commandHandler, eventHandler) {
  // $COVERAGE-OFF$
  override def aggregateName: String = "chiefOfState"

  override def stateCompanion: GeneratedMessageCompanion[State] = State

  // $COVERAGE-ON$
}
