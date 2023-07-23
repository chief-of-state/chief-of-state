/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.interceptors

import com.github.chiefofstate.helper.GrpcHelpers.getHeaders
import com.github.chiefofstate.helper.{BaseSpec, GrpcHelpers}
import com.github.chiefofstate.test.helloworld.GreeterGrpc.{Greeter, GreeterBlockingStub}
import com.github.chiefofstate.test.helloworld.{GreeterGrpc, HelloReply, HelloRequest}
import io.grpc.inprocess.{InProcessChannelBuilder, InProcessServerBuilder}
import io.grpc.stub.MetadataUtils
import io.grpc.{ManagedChannel, Metadata, ServerServiceDefinition}

import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future

class MetadataInterceptorSpec extends BaseSpec {
  override protected def afterEach(): Unit = {
    super.afterEach()
    closeables.closeAll()
  }

  "header interceptor" should {
    "catch the headers" in {
      // Generate a unique in-process server name.
      val serverName: String   = InProcessServerBuilder.generateName
      val serviceImpl: Greeter = mock[Greeter]

      // declare a variable and interceptor to capture the headers
      var responseHeaders: Option[Metadata] = None

      (serviceImpl.sayHello _).expects(*).onCall { hello: HelloRequest =>
        responseHeaders = Option(MetadataInterceptor.REQUEST_META.get())
        Future.successful(HelloReply().withMessage(hello.name))
      }

      val service: ServerServiceDefinition = GreeterGrpc.bindService(serviceImpl, global)

      closeables.register(
        InProcessServerBuilder
          .forName(serverName)
          .directExecutor()
          .addService(service)
          .intercept(MetadataInterceptor)
          .build()
          .start()
      )

      val channel: ManagedChannel =
        closeables.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())

      val stub: GreeterBlockingStub = GreeterGrpc.blockingStub(channel)

      val key                      = "x-custom-header"
      val value                    = "value"
      val requestHeaders: Metadata = getHeaders((key, value))

      stub
        .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(requestHeaders))
        .sayHello(HelloRequest("hi"))

      responseHeaders.isDefined shouldBe true
      GrpcHelpers.getStringHeader(responseHeaders.get, key) shouldBe value
    }
  }
}
