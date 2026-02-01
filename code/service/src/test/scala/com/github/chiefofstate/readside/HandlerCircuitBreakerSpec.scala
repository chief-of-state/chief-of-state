/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.readside.ReadSideHandlerServiceGrpc.ReadSideHandlerServiceBlockingStub
import com.github.chiefofstate.protobuf.v1.readside.{
  HandleReadSideRequest,
  HandleReadSideResponse,
  ReadSideHandlerServiceGrpc
}
import com.google.protobuf.any
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.{ManagedChannel, ServerServiceDefinition, Status}
import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.pattern.CircuitBreaker

import scala.concurrent.ExecutionContext.global
import scala.concurrent.duration.*

class HandlerCircuitBreakerSpec extends BaseSpec {

  val testKit: ActorTestKit = ActorTestKit()

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

  "HandlerImpl with Circuit Breaker" should {
    "successfully process event when circuit is closed" in {
      val event          = any.Any()
      val resultingState = any.Any()
      val meta           = MetaData().withEntityId("test-entity").withRevisionNumber(1)
      val eventTag       = "test-tag"
      val expected       = HandleReadSideResponse().withSuccessful(true)

      val request = HandleReadSideRequest()
        .withEvent(event)
        .withState(resultingState)
        .withMeta(meta)
        .withReadSideId("test-processor")

      val serviceImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]
      (serviceImpl.handleReadSide _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service    = ReadSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new ReadSideHandlerServiceBlockingStub(channel)

      val breaker = new CircuitBreaker(
        testKit.system.classicSystem.scheduler,
        maxFailures = 2,
        callTimeout = 1.second,
        resetTimeout = 10.seconds
      )(testKit.system.executionContext)

      val handler = new HandlerImpl("test-processor", stub, 30000L, Some(breaker))
      val result  = handler.processEvent(event, eventTag, resultingState, meta)

      result shouldBe true
    }

    "return false when circuit is open" in {
      val event          = any.Any()
      val resultingState = any.Any()
      val meta           = MetaData().withEntityId("test-entity").withRevisionNumber(1)
      val eventTag       = "test-tag"

      val request = HandleReadSideRequest()
        .withEvent(event)
        .withState(resultingState)
        .withMeta(meta)
        .withReadSideId("test-processor")

      val serviceImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]
      (serviceImpl.handleReadSide _)
        .expects(request)
        .returning(scala.concurrent.Future.failed(Status.UNAVAILABLE.asException()))
        .once()

      val service    = ReadSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new ReadSideHandlerServiceBlockingStub(channel)

      val breaker = new CircuitBreaker(
        testKit.system.classicSystem.scheduler,
        maxFailures = 1,
        callTimeout = 1.second,
        resetTimeout = 10.seconds
      )(testKit.system.executionContext)

      val handler = new HandlerImpl("test-processor", stub, 30000L, Some(breaker))

      // First call fails - opens the circuit
      val firstResult = handler.processEvent(event, eventTag, resultingState, meta)
      firstResult shouldBe false

      // Second call should fail fast (circuit open) - returns false without calling stub
      val secondResult = handler.processEvent(event, eventTag, resultingState, meta)
      secondResult shouldBe false
    }

    "work without circuit breaker" in {
      val event          = any.Any()
      val resultingState = any.Any()
      val meta           = MetaData().withEntityId("test-entity").withRevisionNumber(1)
      val eventTag       = "test-tag"
      val expected       = HandleReadSideResponse().withSuccessful(true)

      val request = HandleReadSideRequest()
        .withEvent(event)
        .withState(resultingState)
        .withMeta(meta)
        .withReadSideId("test-processor")

      val serviceImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]
      (serviceImpl.handleReadSide _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service    = ReadSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new ReadSideHandlerServiceBlockingStub(channel)

      val handler = new HandlerImpl("test-processor", stub, 30000L, None)
      val result  = handler.processEvent(event, eventTag, resultingState, meta)

      result shouldBe true
    }

    "handle remote server returning false" in {
      val event          = any.Any()
      val resultingState = any.Any()
      val meta           = MetaData().withEntityId("test-entity").withRevisionNumber(1)
      val eventTag       = "test-tag"
      val expected       = HandleReadSideResponse().withSuccessful(false)

      val request = HandleReadSideRequest()
        .withEvent(event)
        .withState(resultingState)
        .withMeta(meta)
        .withReadSideId("test-processor")

      val serviceImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]
      (serviceImpl.handleReadSide _)
        .expects(request)
        .returning(scala.concurrent.Future.successful(expected))

      val service    = ReadSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new ReadSideHandlerServiceBlockingStub(channel)

      val breaker = new CircuitBreaker(
        testKit.system.classicSystem.scheduler,
        maxFailures = 2,
        callTimeout = 1.second,
        resetTimeout = 10.seconds
      )(testKit.system.executionContext)

      val handler = new HandlerImpl("test-processor", stub, 30000L, Some(breaker))
      val result  = handler.processEvent(event, eventTag, resultingState, meta)

      result shouldBe false
    }

    "count remote server false response as success (no circuit trip)" in {
      val event          = any.Any()
      val resultingState = any.Any()
      val meta           = MetaData().withEntityId("test-entity").withRevisionNumber(1)
      val eventTag       = "test-tag"

      val request = HandleReadSideRequest()
        .withEvent(event)
        .withState(resultingState)
        .withMeta(meta)
        .withReadSideId("test-processor")

      val serviceImpl = mock[ReadSideHandlerServiceGrpc.ReadSideHandlerService]

      // First call returns false (business logic says no) - should NOT open circuit
      (serviceImpl.handleReadSide _)
        .expects(request)
        .returning(
          scala.concurrent.Future.successful(HandleReadSideResponse().withSuccessful(false))
        )

      // Second call should still go through (circuit not open)
      (serviceImpl.handleReadSide _)
        .expects(request)
        .returning(
          scala.concurrent.Future.successful(HandleReadSideResponse().withSuccessful(true))
        )

      val service    = ReadSideHandlerServiceGrpc.bindService(serviceImpl, global)
      val serverName = InProcessServerBuilder.generateName()
      createServer(serverName, service)
      val channel = getChannel(serverName)
      val stub    = new ReadSideHandlerServiceBlockingStub(channel)

      val breaker = new CircuitBreaker(
        testKit.system.classicSystem.scheduler,
        maxFailures = 1,
        callTimeout = 1.second,
        resetTimeout = 10.seconds
      )(testKit.system.executionContext)

      val handler = new HandlerImpl("test-processor", stub, 30000L, Some(breaker))

      val firstResult = handler.processEvent(event, eventTag, resultingState, meta)
      firstResult shouldBe false

      val secondResult = handler.processEvent(event, eventTag, resultingState, meta)
      secondResult shouldBe true
    }
  }
}
