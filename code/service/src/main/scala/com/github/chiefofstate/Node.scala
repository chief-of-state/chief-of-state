/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate

import akka.NotUsed
import akka.actor.typed.ActorSystem
import com.github.chiefofstate.config.BootConfig
import com.typesafe.config.Config

object Node extends App {
  // Application config
  val config: Config = BootConfig.get()

  // boot the actor system
  val actorSystem: ActorSystem[NotUsed] =
    ActorSystem(NodeBehaviour(config), "ChiefOfStateSystem", config)
  actorSystem.whenTerminated // remove compiler warnings
}
