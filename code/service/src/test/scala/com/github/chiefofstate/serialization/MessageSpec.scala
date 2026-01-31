/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.serialization

import com.github.chiefofstate.helper.BaseActorSpec
import com.github.chiefofstate.protobuf.v1.service.GetStateRequest
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scalapb.GeneratedMessage

class MessageSpec extends BaseActorSpec() {

  "Message" should {
    "support SendReceive" in {
      val msg: GeneratedMessage = GetStateRequest("e1")
      val ref =
        testKit.spawn(Behaviors.receiveMessage[GeneratedMessage](_ => Behaviors.same), "ref")
      val sr: Message = SendReceive(msg, ref)
      sr shouldBe a[SendReceive]
      sr.asInstanceOf[SendReceive].message shouldBe msg
      sr.asInstanceOf[SendReceive].actorRef shouldBe ref
    }
  }
}
