/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.writeside

import com.github.chiefofstate.config.{GrpcClient, GrpcConfig, GrpcServer}
import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.internal.RemoteCommand
import com.github.chiefofstate.protobuf.v1.persistence.StateWrapper
import com.github.chiefofstate.protobuf.v1.writeside.WriteSideHandlerServiceGrpc.WriteSideHandlerServiceBlockingStub
import com.github.chiefofstate.protobuf.v1.writeside.{
  HandleCommandRequest,
  HandleCommandResponse,
  HandleEventRequest,
  HandleEventResponse,
  WriteSideHandlerServiceGrpc
}
import com.google.protobuf.any
import io.grpc.Status
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.{ManagedChannel, ServerServiceDefinition}
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.pattern.{CircuitBreaker, CircuitBreakerOpenException}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class CircuitBreakerSpec extends BaseSpec {

  val testKit: ActorTestKit = ActorTestKit()
  val grpcConfig: GrpcConfig =
    GrpcConfig(client = GrpcClient(timeout = 5000), server = GrpcServer("", 0))

  override def afterAll(): Unit = {
    testKit.shutdownTestKit()
    super.afterAll()
  }

  def createServer(serverName: String, service: ServerServiceDefinition): Unit = {
    closeables.register(
      InProcessServerBuilder
        .forName(serverName)
        .directExecutor()
        .addService(service)
        .build()
        .start()
    )
  }

  def getChannel(serverName: String): ManagedChannel = {
    closeables.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
  }

  "CommandHandler with Circuit Breaker" should {
    "successfully process command when circuit is closed" in {
      val priorState    = StateWrapper().withState(any.Any())
      val remoteCommand = RemoteCommand().withCommand(any.Any())
      val expected      = HandleCommandResponse()

      val request = HandleCommandRequest()
        .withCommand(remoteCommand.getCommand)
        .withPriorState(priorState.getState)
        .withPriorEventMeta(priorState.getMeta)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      (serviceImpl.handleCommand _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service    = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new WriteSideHandlerServiceBlockingStub(channel)

      val breaker = new CircuitBreaker(
        testKit.system.classicSystem.scheduler,
        maxFailures = 2,
        callTimeout = 1.second,
        resetTimeout = 10.seconds
      )(testKit.system.executionContext)

      val handler = CommandHandler(grpcConfig, stub, Some(breaker))
      val result  = handler.handleCommand(remoteCommand, priorState)

      result shouldBe a[Success[_]]
    }

    "fail fast when circuit is open" in {
      val priorState    = StateWrapper().withState(any.Any())
      val remoteCommand = RemoteCommand().withCommand(any.Any())

      val request = HandleCommandRequest()
        .withCommand(remoteCommand.getCommand)
        .withPriorState(priorState.getState)
        .withPriorEventMeta(priorState.getMeta)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      (serviceImpl.handleCommand _)
        .expects(request)
        .returning(scala.concurrent.Future.failed(Status.UNAVAILABLE.asException()))
        .once()

      val service    = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new WriteSideHandlerServiceBlockingStub(channel)

      val breaker = new CircuitBreaker(
        testKit.system.classicSystem.scheduler,
        maxFailures = 1,
        callTimeout = 1.second,
        resetTimeout = 10.seconds
      )(testKit.system.executionContext)

      val handler = CommandHandler(grpcConfig, stub, Some(breaker))

      // First call fails - opens the circuit
      val firstResult = handler.handleCommand(remoteCommand, priorState)
      firstResult shouldBe a[Failure[_]]

      // Second call should fail fast without calling the stub
      val secondResult = handler.handleCommand(remoteCommand, priorState)
      secondResult shouldBe a[Failure[_]]
      secondResult.failed.get shouldBe a[CircuitBreakerOpenException]
    }

    "work without circuit breaker" in {
      val priorState    = StateWrapper().withState(any.Any())
      val remoteCommand = RemoteCommand().withCommand(any.Any())
      val expected      = HandleCommandResponse()

      val request = HandleCommandRequest()
        .withCommand(remoteCommand.getCommand)
        .withPriorState(priorState.getState)
        .withPriorEventMeta(priorState.getMeta)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      (serviceImpl.handleCommand _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service    = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new WriteSideHandlerServiceBlockingStub(channel)

      val handler = CommandHandler(grpcConfig, stub, None)
      val result  = handler.handleCommand(remoteCommand, priorState)

      result shouldBe a[Success[_]]
    }
  }

  "EventHandler with Circuit Breaker" should {
    "successfully process event when circuit is closed" in {
      val event      = any.Any()
      val priorState = any.Any()
      val eventMeta  = MetaData()
      val expected   = HandleEventResponse()

      val request = HandleEventRequest()
        .withEvent(event)
        .withPriorState(priorState)
        .withEventMeta(eventMeta)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      (serviceImpl.handleEvent _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service    = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new WriteSideHandlerServiceBlockingStub(channel)

      val breaker = new CircuitBreaker(
        testKit.system.classicSystem.scheduler,
        maxFailures = 2,
        callTimeout = 1.second,
        resetTimeout = 10.seconds
      )(testKit.system.executionContext)

      val handler = EventHandler(grpcConfig, stub, Some(breaker))
      val result  = handler.handleEvent(event, priorState, eventMeta)

      result shouldBe a[Success[_]]
    }

    "fail fast when circuit is open" in {
      val event      = any.Any()
      val priorState = any.Any()
      val eventMeta  = MetaData()

      val request = HandleEventRequest()
        .withEvent(event)
        .withPriorState(priorState)
        .withEventMeta(eventMeta)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      (serviceImpl.handleEvent _)
        .expects(request)
        .returning(scala.concurrent.Future.failed(Status.UNAVAILABLE.asException()))
        .once()

      val service    = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new WriteSideHandlerServiceBlockingStub(channel)

      val breaker = new CircuitBreaker(
        testKit.system.classicSystem.scheduler,
        maxFailures = 1,
        callTimeout = 1.second,
        resetTimeout = 10.seconds
      )(testKit.system.executionContext)

      val handler = EventHandler(grpcConfig, stub, Some(breaker))

      // First call fails - opens the circuit
      val firstResult = handler.handleEvent(event, priorState, eventMeta)
      firstResult shouldBe a[Failure[_]]

      // Second call should fail fast without calling the stub
      val secondResult = handler.handleEvent(event, priorState, eventMeta)
      secondResult shouldBe a[Failure[_]]
      secondResult.failed.get shouldBe a[CircuitBreakerOpenException]
    }

    "work without circuit breaker" in {
      val event      = any.Any()
      val priorState = any.Any()
      val eventMeta  = MetaData()
      val expected   = HandleEventResponse()

      val request = HandleEventRequest()
        .withEvent(event)
        .withPriorState(priorState)
        .withEventMeta(eventMeta)

      val serviceImpl = mock[WriteSideHandlerServiceGrpc.WriteSideHandlerService]
      (serviceImpl.handleEvent _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service    = WriteSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new WriteSideHandlerServiceBlockingStub(channel)

      val handler = EventHandler(grpcConfig, stub, None)
      val result  = handler.handleEvent(event, priorState, eventMeta)

      result shouldBe a[Success[_]]
    }
  }
}
