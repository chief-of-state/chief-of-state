/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate

import com.github.chiefofstate.config.BootConfig
import com.typesafe.config.Config
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.ActorSystem

object Entrypoint extends App {
  // Application config
  val config: Config = BootConfig.get()

  // boot the actor system
  val actorSystem: ActorSystem[NotUsed] =
    ActorSystem(Behavior(config), "ChiefOfStateSystem", config)
  actorSystem.whenTerminated // remove compiler warnings
}
