/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate
import com.github.chiefofstate.protobuf.v1.internal.StartMigration
import com.github.chiefofstate.serialization.{Message, SendReceive}
import com.typesafe.config.Config
import org.apache.pekko.NotUsed
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.{ActorRef, Behavior, SupervisorStrategy, Terminated}
import org.apache.pekko.cluster.typed.{
  Cluster,
  ClusterSingleton,
  ClusterSingletonSettings,
  SingletonActor
}
import org.apache.pekko.management.cluster.bootstrap.ClusterBootstrap
import org.apache.pekko.management.scaladsl.PekkoManagement
import org.slf4j.{Logger, LoggerFactory}

object CosBehavior {
  final val log: Logger = LoggerFactory.getLogger(getClass)

  def apply(config: Config): Behavior[NotUsed] = {
    Behaviors.setup { context =>
      // Grab the akka cluster instance
      val cluster: Cluster = Cluster(context.system)
      context.log.info(s"starting node with roles: ${cluster.selfMember.roles}")

      // Start the akka cluster management tool
      PekkoManagement(context.system).start()
      // start the cluster boostrap
      ClusterBootstrap(context.system).start()

      // initialize the service bootstrapper
      val bootstrapper: ActorRef[scalapb.GeneratedMessage] =
        context.spawn(
          Behaviors
            .supervise(ServiceStarter(config))
            .onFailure[Exception](SupervisorStrategy.restart),
          "CosBootstrapper"
        )

      // initialise the migration cluster singleton settings
      val singletonSettings = ClusterSingletonSettings(context.system)

      // create the migration cluster singleton
      val migrationRunner = SingletonActor(
        Behaviors.supervise(Migration(config)).onFailure[Exception](SupervisorStrategy.stop),
        "CosMigrationRunner"
      ).withSettings(singletonSettings)

      // initialise the migration runner in a singleton
      val migration: ActorRef[Message] = ClusterSingleton(context.system).init(migrationRunner)
      // tell the migrator to kickstart
      migration ! SendReceive(StartMigration(), bootstrapper)

      // let us watch both actors to handle any on them termination
      context.watch(bootstrapper)

      // let us handle the Terminated message received
      Behaviors.receiveSignal[NotUsed] { case (_, Terminated(ref)) =>
        val actorName = ref.path.name
        log.info(s"Actor stopped: $actorName")
        // whenever the ServiceBootstrapper stop
        // we need to panic here and halt the whole system
        throw new RuntimeException("unable to boot ChiefOfState properly....")
      }

      Behaviors.empty
    }
  }
}
