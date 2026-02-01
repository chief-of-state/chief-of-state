/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.serialization

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.protobuf.v1.common.Header
import com.github.chiefofstate.protobuf.v1.internal.{
  GetStateCommand,
  RemoteCommand,
  SendCommand,
  WireMessageWithActorRef
}
import com.github.chiefofstate.protobuf.v1.tests.OpenAccount
import com.google.protobuf.any.Any
import com.google.protobuf.wrappers.StringValue
import org.apache.pekko.actor.ExtendedActorSystem
import org.apache.pekko.actor.testkit.typed.scaladsl.TestProbe
import org.apache.pekko.actor.typed.scaladsl.adapter.*
import scalapb.GeneratedMessage

class SerializerSpec extends BaseActorSpec(s"""
    pekko {
      actor {
        serialize-messages = on
        serializers {
          cosSerializer = "com.github.chiefofstate.serialization.Serializer"
        }
        serialization-bindings {
          "scalapb.GeneratedMessage" = cosSerializer
          "com.github.chiefofstate.serialization.Message" = cosSerializer
        }
      }
    }
    """) {

  // create a shared extended system for use in constructors
  lazy val extendedSystem: ExtendedActorSystem = system.toClassic.asInstanceOf[ExtendedActorSystem]

  "Pekko serialization" should {
    "serialize ChiefOfState command" in {
      val probe: TestProbe[GeneratedMessage] = createTestProbe[GeneratedMessage]()

      val remoteCommand = RemoteCommand()
        .withCommand(Any.pack(OpenAccount()))
        .addPropagatedHeaders(Header().withKey("header-1").withStringValue("header-value-1"))

      val sendCommand = SendCommand().withRemoteCommand(remoteCommand)

      val command: SendReceive = SendReceive(message = sendCommand, actorRef = probe.ref)

      serializationTestKit.verifySerialization(command)
    }
  }

  ".manifest" should {
    "recognize a SendReceive" in {
      val msg = SendReceive(SendCommand(), null)
      (new Serializer(extendedSystem)).manifest(msg).nonEmpty shouldBe true
    }
    "recognize a GeneratedMessage" in {
      val msg = SendCommand.defaultInstance
      (new Serializer(extendedSystem)).manifest(msg).nonEmpty shouldBe true
    }
    "fail on unrecognized messages" in {
      assertThrows[IllegalArgumentException] {
        (new Serializer(extendedSystem)).manifest(StringValue.defaultInstance)
      }
    }
  }

  ".fromBinary" should {
    "error for unrecognized type url" in {
      val serializer = new Serializer(extendedSystem)
      val err = intercept[IllegalArgumentException] {
        serializer.fromBinary(Array.emptyByteArray, "bad-url")
      }

      err.getMessage() shouldBe "unrecognized manifest, bad-url"
    }
  }
  "SendReceive" should {
    "successfully serialize and deserialize" in {
      val probe: TestProbe[GeneratedMessage] = createTestProbe[GeneratedMessage]()
      val serializer                         = new Serializer(extendedSystem)
      val innerMsg = SendCommand().withGetStateCommand(GetStateCommand().withEntityId("x"))
      val outerMsg = SendReceive(innerMsg, probe.ref)
      // serialize the message
      val serialized: Array[Byte] = serializer.toBinary(outerMsg)
      // prove you can deserialize the byte array
      noException shouldBe thrownBy(WireMessageWithActorRef.parseFrom(serialized))
      // deserialize it
      val manifest = serializer.manifest(outerMsg)
      val actual   = serializer.fromBinary(serialized, manifest)
      actual.isInstanceOf[SendReceive] shouldBe true
      // assert unchanged
      actual.asInstanceOf[SendReceive] shouldBe outerMsg
    }
    "fail to serialize with unknown child message" in {
      val probe: TestProbe[GeneratedMessage] = createTestProbe[GeneratedMessage]()
      val serializer                         = new Serializer(extendedSystem)
      // construct a message with an unregistered type
      val outerMsg = SendReceive(StringValue("x"), probe.ref)
      // serialize the message
      val err = intercept[IllegalArgumentException] {
        serializer.toBinary(outerMsg)
      }
      err.getMessage().startsWith("cannot serialize") shouldBe true
    }
    "fail to deserialize with unknown child message" in {
      val msg = WireMessageWithActorRef().withMessage(Any.pack(StringValue("x")))

      val manifest = WireMessageWithActorRef.scalaDescriptor.fullName

      val serializer = new Serializer(extendedSystem)
      val err = intercept[IllegalArgumentException] {
        serializer.fromBinary(msg.toByteArray, manifest)
      }

      err.getMessage().startsWith("unknown message type") shouldBe true
    }
  }
  "scalapb GeneratedMessages" should {
    "successfully serialize and deserialize" in {
      val serializer = new Serializer(extendedSystem)
      val msg        = SendCommand().withGetStateCommand(GetStateCommand().withEntityId("x"))
      val serialized: Array[Byte] = serializer.toBinary(msg)
      // check proto serialization
      noException shouldBe thrownBy { SendCommand.parseFrom(serialized) }
      // deserialize
      serializer.fromBinary(serialized, serializer.manifest(msg)) shouldBe msg
    }
    "fail to deserialize unknown messages" in {
      val msg        = StringValue("x")
      val manifest   = msg.companion.scalaDescriptor.fullName
      val serializer = new Serializer(extendedSystem)
      val err = intercept[IllegalArgumentException] {
        serializer.fromBinary(msg.toByteArray, manifest)
      }

      err.getMessage().startsWith("unrecognized manifest") shouldBe true
    }
  }
}
