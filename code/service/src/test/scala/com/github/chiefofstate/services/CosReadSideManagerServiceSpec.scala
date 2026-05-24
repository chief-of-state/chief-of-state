/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.services

import com.github.chiefofstate.helper.BaseSpec
import com.github.chiefofstate.protobuf.v1.manager._
import com.github.chiefofstate.readside.StateManager

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.mutable

class CosReadSideManagerServiceSpec extends BaseSpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  /**
   * Fake StateManager that records invocations and returns canned responses.
   */
  private class FakeStateManager(
      offsetResult: Long = 0L,
      offsetsResult: Seq[(Int, Long)] = Seq.empty,
      restartResult: Boolean = true,
      pauseResult: Boolean = true,
      resumeResult: Boolean = true,
      skipFails: Boolean = false
  ) extends StateManager {
    val calls: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty

    def offset(readSideId: String, shardNumber: Int): Future[Long] = {
      calls += s"offset($readSideId,$shardNumber)"; Future.successful(offsetResult)
    }
    def offsets(readSideId: String): Future[Seq[(Int, Long)]] = {
      calls += s"offsets($readSideId)"; Future.successful(offsetsResult)
    }
    def restart(readSideId: String, shardNumber: Int): Future[Boolean] = {
      calls += s"restart($readSideId,$shardNumber)"; Future.successful(restartResult)
    }
    def restartForAll(readSideId: String): Future[Boolean] = {
      calls += s"restartForAll($readSideId)"; Future.successful(restartResult)
    }
    def pause(readSideId: String, shardNumber: Int): Future[Boolean] = {
      calls += s"pause($readSideId,$shardNumber)"; Future.successful(pauseResult)
    }
    def pauseForAll(readSideId: String): Future[Boolean] = {
      calls += s"pauseForAll($readSideId)"; Future.successful(pauseResult)
    }
    def resume(readSideId: String, shardNumber: Int): Future[Boolean] = {
      calls += s"resume($readSideId,$shardNumber)"; Future.successful(resumeResult)
    }
    def resumeForAll(readSideId: String): Future[Boolean] = {
      calls += s"resumeForAll($readSideId)"; Future.successful(resumeResult)
    }
    def skipOffset(readSideId: String, shardNumber: Int): Unit = {
      calls += s"skipOffset($readSideId,$shardNumber)"
      if (skipFails) throw new RuntimeException("skip failed")
    }
    def skipOffsets(readSideId: String): Unit = {
      calls += s"skipOffsets($readSideId)"
      if (skipFails) throw new RuntimeException("skip failed")
    }
    def isReadSidePaused(readSideId: String): Future[Boolean] = {
      calls += s"isReadSidePaused($readSideId)"; Future.successful(false)
    }
  }

  "CosReadSideManagerService" should {
    "GetLatestOffset returns mapped offsets" in {
      val mgr = new FakeStateManager(offsetsResult = Seq(0 -> 11L, 1 -> 22L))
      val svc = new CosReadSideManagerService(mgr)
      val res = svc.getLatestOffset(GetLatestOffsetRequest(readSideId = "rs")).futureValue
      res.offsets.map(o => o.clusterShardNumber -> o.offset).toSet shouldBe Set(0 -> 11L, 1 -> 22L)
      mgr.calls.contains("offsets(rs)") shouldBe true
    }

    "GetLatestOffsetByShard returns single offset" in {
      val mgr = new FakeStateManager(offsetResult = 42L)
      val svc = new CosReadSideManagerService(mgr)
      val res = svc
        .getLatestOffsetByShard(
          GetLatestOffsetByShardRequest(readSideId = "rs", clusterShardNumber = 3)
        )
        .futureValue
      res.getOffsets.offset shouldBe 42L
      res.getOffsets.clusterShardNumber shouldBe 3
      mgr.calls.contains("offset(rs,3)") shouldBe true
    }

    "RestartReadSide reports successful = true" in {
      val mgr = new FakeStateManager(restartResult = true)
      val svc = new CosReadSideManagerService(mgr)
      svc
        .restartReadSide(RestartReadSideRequest(readSideId = "rs"))
        .futureValue
        .successful shouldBe true
      mgr.calls.contains("restartForAll(rs)") shouldBe true
    }

    "RestartReadSideByShard reports successful = false" in {
      val mgr = new FakeStateManager(restartResult = false)
      val svc = new CosReadSideManagerService(mgr)
      svc
        .restartReadSideByShard(
          RestartReadSideByShardRequest(readSideId = "rs", clusterShardNumber = 2)
        )
        .futureValue
        .successful shouldBe false
      mgr.calls.contains("restart(rs,2)") shouldBe true
    }

    "PauseReadSide and PauseReadSideByShard" in {
      val mgr = new FakeStateManager()
      val svc = new CosReadSideManagerService(mgr)
      svc
        .pauseReadSide(PauseReadSideRequest(readSideId = "rs"))
        .futureValue
        .successful shouldBe true
      svc
        .pauseReadSideByShard(
          PauseReadSideByShardRequest(readSideId = "rs", clusterShardNumber = 1)
        )
        .futureValue
        .successful shouldBe true
      mgr.calls should contain allOf ("pauseForAll(rs)", "pause(rs,1)")
    }

    "ResumeReadSide and ResumeReadSideByShard" in {
      val mgr = new FakeStateManager()
      val svc = new CosReadSideManagerService(mgr)
      svc
        .resumeReadSide(ResumeReadSideRequest(readSideId = "rs"))
        .futureValue
        .successful shouldBe true
      svc
        .resumeReadSideByShard(
          ResumeReadSideByShardRequest(readSideId = "rs", clusterShardNumber = 4)
        )
        .futureValue
        .successful shouldBe true
      mgr.calls should contain allOf ("resumeForAll(rs)", "resume(rs,4)")
    }

    "SkipOffset succeeds when the manager returns normally" in {
      val mgr = new FakeStateManager()
      val svc = new CosReadSideManagerService(mgr)
      svc
        .skipOffset(SkipOffsetRequest(readSideId = "rs"))
        .futureValue
        .successful shouldBe true
      mgr.calls.contains("skipOffsets(rs)") shouldBe true
    }

    "SkipOffset fails when the manager throws" in {
      val mgr = new FakeStateManager(skipFails = true)
      val svc = new CosReadSideManagerService(mgr)
      svc.skipOffset(SkipOffsetRequest(readSideId = "rs")).failed.futureValue.getMessage should
        include("skip failed")
    }

    "SkipOffsetByShard succeeds when the manager returns normally" in {
      val mgr = new FakeStateManager()
      val svc = new CosReadSideManagerService(mgr)
      svc
        .skipOffsetByShard(SkipOffsetByShardRequest(readSideId = "rs", clusterShardNumber = 7))
        .futureValue
        .successful shouldBe true
      mgr.calls.contains("skipOffset(rs,7)") shouldBe true
    }

    "SkipOffsetByShard fails when the manager throws" in {
      val mgr = new FakeStateManager(skipFails = true)
      val svc = new CosReadSideManagerService(mgr)
      svc
        .skipOffsetByShard(SkipOffsetByShardRequest(readSideId = "rs", clusterShardNumber = 7))
        .failed
        .futureValue
        .getMessage should include("skip failed")
    }
  }
}
