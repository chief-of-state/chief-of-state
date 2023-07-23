/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.serialization

import akka.actor.typed.ActorRef
import scalapb.GeneratedMessage

// defines a trait that routes to the custom serializer
sealed trait Message

/**
 * wraps a generated message and an actor ref that the serializer
 * can convert to/from proto
 *
 * @param message a generated message
 * @param actorRef an actor ref
 */
case class SendReceive(message: GeneratedMessage, actorRef: ActorRef[GeneratedMessage])
    extends Message
