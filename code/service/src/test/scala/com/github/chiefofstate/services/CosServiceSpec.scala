/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.services

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.javadsl.{ClusterSharding => ClusterShardingJava}
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey}
import org.apache.pekko.cluster.sharding.typed.testkit.scaladsl.TestEntityRef
import com.github.chiefofstate.Entity
import com.github.chiefofstate.config.WriteSideConfig
import com.github.chiefofstate.helper.{BaseActorSpec, GrpcHelpers, TestConfig}
import com.github.chiefofstate.interceptors.MetadataInterceptor
import com.github.chiefofstate.protobuf.v1.common.MetaData
import com.github.chiefofstate.protobuf.v1.internal.{CommandReply, RemoteCommand, SendCommand}
import com.github.chiefofstate.protobuf.v1.persistence.StateWrapper
import com.github.chiefofstate.protobuf.v1.service.{
  ChiefOfStateServiceGrpc,
  GetStateRequest,
  ProcessCommandRequest
}
import com.github.chiefofstate.serialization.{Message, SendReceive}
import com.github.chiefofstate.utils.Util
import com.google.protobuf.any
import com.google.protobuf.wrappers.StringValue
import com.google.rpc.code
import com.google.rpc.error_details.BadRequest
import com.google.rpc.status.Status
import io.grpc.Status.Code
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.protobuf.StatusProto
import io.grpc.stub.MetadataUtils
import io.grpc.{ManagedChannel, Metadata, StatusException}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{Await, ExecutionContext}
import scala.util.Success

class CosServiceSpec extends BaseActorSpec(s"""
      pekko.cluster.sharding.number-of-shards = 1
      pekko.persistence.journal.plugin = "pekko.persistence.journal.inmem"
      pekko.persistence.snapshot-store.plugin = "pekko.persistence.snapshot-store.local"
      pekko.persistence.snapshot-store.local.dir = "tmp/snapshot"
    """) {

  // creates a trait to mock cluster sharding
  // this is necessary as the scaladsl ClusterSharding uses a self-type
  // to require mixing with the java ClusterSharding
  // https://docs.scala-lang.org/tour/self-types.html
  trait FakeClusterSharding extends ClusterShardingJava with ClusterSharding

  // creates a mock cluster sharding that returns a specific EntityRef
  def getClusterShard(output: EntityRef[SendReceive]): ClusterSharding = {
    val clusterSharding = mock[FakeClusterSharding]

    ((a: EntityTypeKey[SendReceive], b: String) => clusterSharding.entityRefFor(a, b))
      .expects(Entity.TypeKey, *)
      .returning(output)
      .repeat(1)

    clusterSharding
  }

  val actorSystem: ActorSystem[Nothing] = testKit.system
  val replyTimeout: FiniteDuration      = FiniteDuration(1, TimeUnit.SECONDS)

  val writeSideConfig: WriteSideConfig = WriteSideConfig(
    host = "",
    port = 0,
    useTls = false,
    enableProtoValidation = false,
    eventsProtos = Seq(),
    statesProtos = Seq(),
    propagatedHeaders = Seq(),
    persistedHeaders = Seq()
  )

  val cosConfig = TestConfig.cosConfig

  ".processCommand" should {
    "require entity ID" in {
      val clusterSharding: ClusterSharding = mock[FakeClusterSharding]
      val impl                             = new CosService(clusterSharding, writeSideConfig)

      val request = ProcessCommandRequest(entityId = "")

      val actualErr = intercept[StatusException] {
        Await.result(impl.processCommand(request), Duration.Inf)
      }

      actualErr.getStatus().getCode() shouldBe Code.INVALID_ARGUMENT
      actualErr.getStatus().getDescription() shouldBe "empty entity ID"
    }
    "handles happy returns" in {
      // create the expected state
      val expectedState =
        StateWrapper()
          .withState(any.Any.pack(StringValue("some state")))
          .withMeta(MetaData().withRevisionNumber(2))
      // create a behavior that returns a state
      val mockedBehavior = Behaviors.receiveMessage[Message] { case SendReceive(message, replyTo) =>
        replyTo ! CommandReply().withState(expectedState)
        Behaviors.same
      }
      // create a mocked entity & probe to run this behavior
      val probe        = testKit.createTestProbe[Message]()
      val mockedEntity = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))
      // create mocked cluster sharding with the actor
      val entityId: String                      = "id-1"
      val typeKey                               = EntityTypeKey[SendReceive](entityId)
      val testEntityRef: EntityRef[SendReceive] = TestEntityRef(typeKey, entityId, mockedEntity.ref)
      val clusterSharding                       = getClusterShard(testEntityRef)
      // instantiate the service
      val impl = new CosService(clusterSharding, writeSideConfig)
      // call method
      val request =
        ProcessCommandRequest()
          .withEntityId(entityId)
          .withCommand(any.Any.pack(StringValue("some-command")))
      val sendFuture = impl.processCommand(request)

      // assert message sent to actor
      val akkaReceived = probe.receiveMessage()

      val remoteCommand =
        akkaReceived.asInstanceOf[SendReceive].message.asInstanceOf[SendCommand].getRemoteCommand

      remoteCommand.entityId shouldBe request.entityId
      remoteCommand.getCommand shouldBe request.getCommand

      // assert response
      val response = Await.result(sendFuture, Duration.Inf)
      response.getState shouldBe expectedState.getState
      response.getMeta shouldBe expectedState.getMeta
    }
    "inject persisted and propagated headers" in {
      // define a config that persists & propagates headers
      val headerKey   = "x-custom-header"
      val headerValue = "value"
      val customWriteConfig =
        writeSideConfig.copy(persistedHeaders = Seq(headerKey), propagatedHeaders = Seq(headerKey))
      // create the expected state
      val entityId = "some-entity"
      val expectedState =
        StateWrapper()
          .withState(any.Any.pack(StringValue("some state")))
          .withMeta(MetaData().withRevisionNumber(2))
      // create a behavior that returns the state
      val mockedBehavior = Behaviors.receiveMessage[Message] { case SendReceive(message, replyTo) =>
        replyTo ! CommandReply().withState(expectedState)
        Behaviors.same
      }
      // create a mocked entity & probe to run this behavior
      val probe        = testKit.createTestProbe[Message]()
      val mockedEntity = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))
      // create mocked cluster sharding with the actor
      val typeKey                               = EntityTypeKey[SendReceive](entityId)
      val testEntityRef: EntityRef[SendReceive] = TestEntityRef(typeKey, entityId, mockedEntity.ref)
      val clusterSharding                       = getClusterShard(testEntityRef)
      // instantiate the service
      val impl = new CosService(clusterSharding, customWriteConfig)
      // bind service and intercept headers
      val serverName: String = InProcessServerBuilder.generateName();
      val service            = ChiefOfStateServiceGrpc.bindService(impl, ExecutionContext.global)
      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .intercept(MetadataInterceptor)
          .build()
          .start()
      )
      // create a client
      val channel: ManagedChannel =
        closeables.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
      val client = ChiefOfStateServiceGrpc.blockingStub(channel)

      // send request
      val requestHeaders: Metadata = GrpcHelpers.getHeaders((headerKey, headerValue))
      val request = ProcessCommandRequest(entityId = entityId).withCommand(
        any.Any.pack(StringValue("some-command"))
      )

      client
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(requestHeaders))
        .processCommand(request)

      // assert headers sent to actor
      val remoteCommand: RemoteCommand =
        probe
          .receiveMessage()
          .asInstanceOf[SendReceive]
          .message
          .asInstanceOf[SendCommand]
          .getRemoteCommand

      remoteCommand.persistedHeaders.map(_.key).toSeq shouldBe Seq(headerKey)
      remoteCommand.persistedHeaders.map(_.getStringValue).toSeq shouldBe Seq(headerValue)
    }
    "handle failure responses" in {
      // create the expected error
      val errorStatus = Status().withCode(code.Code.NOT_FOUND.value)
      // create a behavior that returns a state
      val mockedBehavior = Behaviors.receiveMessage[Message] { case SendReceive(message, replyTo) =>
        replyTo ! CommandReply().withError(errorStatus)
        Behaviors.same
      }
      // create a mocked entity & probe to run this behavior
      val probe        = testKit.createTestProbe[Message]()
      val mockedEntity = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))
      // create mocked cluster sharding with the actor
      val entityId: String                      = "id-1"
      val typeKey                               = EntityTypeKey[SendReceive](entityId)
      val testEntityRef: EntityRef[SendReceive] = TestEntityRef(typeKey, entityId, mockedEntity.ref)
      val clusterSharding                       = getClusterShard(testEntityRef)
      // instantiate the service
      val impl = new CosService(clusterSharding, writeSideConfig)
      // call method
      val request    = ProcessCommandRequest().withEntityId(entityId)
      val sendFuture = impl.processCommand(request)
      // assert message sent to actor
      val akkaMsg = probe.receiveMessage()
      akkaMsg.shouldBe(an[SendReceive])
      akkaMsg
        .asInstanceOf[SendReceive]
        .message
        .asInstanceOf[SendCommand]
        .getRemoteCommand
        .entityId shouldBe entityId

      // assert response
      val actualError = intercept[StatusException] {
        Await.result(sendFuture, Duration.Inf)
      }
      Util.toRpcStatus(actualError.getStatus) shouldBe errorStatus
    }
  }

  ".getState" should {
    "require entity ID" in {
      val clusterSharding: ClusterSharding = mock[FakeClusterSharding]
      val impl                             = new CosService(clusterSharding, writeSideConfig)

      val request = GetStateRequest(entityId = "")
      val actualErr = intercept[StatusException] {
        Await.result(impl.getState(request), Duration.Inf)
      }

      actualErr.getStatus().getCode() shouldBe Code.INVALID_ARGUMENT
      actualErr.getStatus().getDescription() shouldBe "empty entity ID"
    }
    "handle happy return" in {
      // create the expected state
      val expectedState =
        StateWrapper()
          .withState(any.Any.pack(StringValue("some state")))
          .withMeta(MetaData().withRevisionNumber(2))
      // create a behavior that returns a state
      val mockedBehavior = Behaviors.receiveMessage[Message] { case SendReceive(message, replyTo) =>
        replyTo ! CommandReply().withState(expectedState)
        Behaviors.same
      }
      // create a mocked entity & probe to run this behavior
      val probe        = testKit.createTestProbe[Message]()
      val mockedEntity = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))
      // create mocked cluster sharding with the actor
      val entityId: String                      = "id-1"
      val typeKey                               = EntityTypeKey[SendReceive](entityId)
      val testEntityRef: EntityRef[SendReceive] = TestEntityRef(typeKey, entityId, mockedEntity.ref)
      val clusterSharding                       = getClusterShard(testEntityRef)
      // instantiate the service
      val impl = new CosService(clusterSharding, writeSideConfig)
      // call method
      val request    = GetStateRequest().withEntityId(entityId)
      val sendFuture = impl.getState(request)
      // assert message sent to actor
      val akkaResponse = probe.receiveMessage()
      akkaResponse.shouldBe(an[SendReceive])
      akkaResponse
        .asInstanceOf[SendReceive]
        .message
        .asInstanceOf[SendCommand]
        .getGetStateCommand
        .entityId shouldBe entityId

      // assert response
      val response = Await.result(sendFuture, Duration.Inf)
      response.getState shouldBe expectedState.getState
      response.getMeta shouldBe expectedState.getMeta
    }
    "handle failure responses" in {
      // create the expected error
      val errorStatus = Status().withCode(code.Code.NOT_FOUND.value)
      // create a behavior that returns a state
      val mockedBehavior = Behaviors.receiveMessage[Message] { case SendReceive(message, replyTo) =>
        replyTo ! CommandReply().withError(errorStatus)
        Behaviors.same
      }
      // create a mocked entity & probe to run this behavior
      val probe        = testKit.createTestProbe[Message]()
      val mockedEntity = testKit.spawn(Behaviors.monitor(probe.ref, mockedBehavior))
      // create mocked cluster sharding with the actor
      val entityId: String                      = "id-1"
      val typeKey                               = EntityTypeKey[SendReceive](entityId)
      val testEntityRef: EntityRef[SendReceive] = TestEntityRef(typeKey, entityId, mockedEntity.ref)
      val clusterSharding                       = getClusterShard(testEntityRef)
      // instantiate the service
      val impl = new CosService(clusterSharding, writeSideConfig)
      // call method
      val request    = GetStateRequest().withEntityId(entityId)
      val sendFuture = impl.getState(request)
      // assert message sent to actor
      val akkaMsg = probe.receiveMessage()
      akkaMsg.shouldBe(an[SendReceive])
      akkaMsg
        .asInstanceOf[SendReceive]
        .message
        .asInstanceOf[SendCommand]
        .getGetStateCommand
        .entityId shouldBe entityId

      // assert response
      val actualError = intercept[StatusException] {
        Await.result(sendFuture, Duration.Inf)
      }
      Util.toRpcStatus(actualError.getStatus) shouldBe errorStatus
    }
  }

  ".requireEntityId" should {
    "fail if entity missing" in {
      assertThrows[StatusException] {
        Await.result(CosService.requireEntityId(""), Duration.Inf)
      }
    }
    "pass if entity provided" in {
      noException shouldBe thrownBy {
        Await.result(CosService.requireEntityId("x"), Duration.Inf)
      }
    }
  }

  ".handleCommandReply" should {
    "pass through success" in {
      val stateWrapper = StateWrapper().withMeta(MetaData().withRevisionNumber(2))

      val commandReply: CommandReply = CommandReply().withState(stateWrapper)

      val actual = CosService.handleCommandReply(commandReply)

      actual shouldBe Success(stateWrapper)
    }
    "preserve error details" in {
      // define a field violation
      val errField = BadRequest.FieldViolation().withField("some_field").withDescription("oh no")

      // create the bad request detail
      val errDetail: BadRequest = BadRequest().addFieldViolations(errField)

      // create an error status with this detail
      val expectedStatus: com.google.rpc.status.Status =
        com.google.rpc.status
          .Status()
          .withCode(com.google.rpc.code.Code.INVALID_ARGUMENT.value)
          .withMessage("some error message")
          .addDetails(com.google.protobuf.any.Any.pack(errDetail))

      val commandReply: CommandReply = CommandReply().withError(expectedStatus)

      val statusException: StatusException = intercept[StatusException] {
        CosService.handleCommandReply(commandReply).get
      }

      val javaStatus = StatusProto.fromStatusAndTrailers(
        statusException.getStatus(),
        statusException.getTrailers()
      )

      val actual = Status.fromJavaProto(javaStatus)

      actual shouldBe expectedStatus

    }
    "handle default case" in {
      val commandReply: CommandReply = CommandReply().withReply(CommandReply.Reply.Empty)

      assertThrows[StatusException] {
        CosService.handleCommandReply(commandReply).get
      }
    }
  }
}
