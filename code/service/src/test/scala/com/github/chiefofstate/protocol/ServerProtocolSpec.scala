/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.protocol
import com.github.chiefofstate.helper.BaseSpec

class ServerProtocolSpec extends BaseSpec {
  "ServerProtocol.fromString" should {
    "parse grpc correctly" in {
      ServerProtocol.fromString("grpc") shouldBe ServerProtocol.Grpc
      ServerProtocol.fromString("GRPC") shouldBe ServerProtocol.Grpc
    }

    "parse http correctly" in {
      ServerProtocol.fromString("http") shouldBe ServerProtocol.Http
      ServerProtocol.fromString("HTTP") shouldBe ServerProtocol.Http
    }

    "parse both correctly" in {
      ServerProtocol.fromString("both") shouldBe ServerProtocol.Both
      ServerProtocol.fromString("BOTH") shouldBe ServerProtocol.Both
    }

    "throw exception for invalid protocol" in {
      an[IllegalArgumentException] shouldBe thrownBy(ServerProtocol.fromString("invalid"))
    }
  }
}
