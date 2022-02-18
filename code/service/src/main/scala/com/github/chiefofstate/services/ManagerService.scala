/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.services

import com.github.chiefofstate.protobuf.v1.common.ReadSideOffset
import com.github.chiefofstate.protobuf.v1.readside_manager.ReadSideManagerServiceGrpc.ReadSideManagerService
import com.github.chiefofstate.protobuf.v1.readside_manager._
import com.github.chiefofstate.readside.ReadSideManager
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContext, Future }

class ManagerService(readSideManager: ReadSideManager)(implicit ec: ExecutionContext) extends ReadSideManagerService {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * GetLatestOffset retrieves latest stored offset
   */
  override def getLatestOffset(request: GetLatestOffsetRequest): Future[GetLatestOffsetResponse] = {
    // log the method name
    log.debug(ReadSideManagerServiceGrpc.METHOD_GET_LATEST_OFFSET.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId
    // fetch the list of offsets.
    // In case there is an error an internal error will be returned
    readSideManager
      .offsets(readSideId = readSideId)
      .map(records => {
        records.map { case (shardNum, offset) =>
          ReadSideOffset().withOffset(offset).withClusterShardNumber(shardNum)
        }
      })
      .map(GetLatestOffsetResponse().withOffsets(_))
  }

  /**
   * GetLatestOffsetByShard retrieves the latest offset given a shard
   */
  override def getLatestOffsetByShard(
      request: GetLatestOffsetByShardRequest): Future[GetLatestOffsetByShardResponse] = {
    // log the method name
    log.debug(ReadSideManagerServiceGrpc.METHOD_GET_LATEST_OFFSET_BY_SHARD.getFullMethodName)
    // execute the request.
    // In case there is an error an internal error will be returned
    readSideManager
      .offset(request.readSideId, request.clusterShardNumber.intValue())
      .map(offset =>
        GetLatestOffsetByShardResponse().withOffsets(
          ReadSideOffset().withOffset(offset).withClusterShardNumber(request.clusterShardNumber)))
  }

  /**
   * RestartReadSide will clear the read side offset and start it over again
   * from the first offset.
   */
  override def restartReadSide(request: RestartReadSideRequest): Future[RestartReadSideResponse] = {
    // log the method name
    log.debug(ReadSideManagerServiceGrpc.METHOD_RESTART_READ_SIDE.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId
    // execute the request.
    // In case there is an error an internal error will be returned
    readSideManager
      .restartForAll(readSideId)
      .map(status => {
        RestartReadSideResponse().withSuccessful(status)
      })
  }

  /**
   * RestartReadSideByShard will clear the read side offset for the given shard and start it over again from the first offset
   */
  override def restartReadSideByShard(
      request: RestartReadSideByShardRequest): Future[RestartReadSideByShardResponse] = {
    // log the method name
    log.debug(ReadSideManagerServiceGrpc.METHOD_RESTART_READ_SIDE_BY_SHARD.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId
    // execute the request.
    // In case there is an error an internal error will be returned
    readSideManager
      .restart(readSideId, request.clusterShardNumber.intValue())
      .map(status => {
        RestartReadSideByShardResponse().withSuccessful(status)
      })
  }

  /**
   * PauseReadSide pauses a read side. This can be useful when running some data migration
   */
  override def pauseReadSide(request: PauseReadSideRequest): Future[PauseReadSideResponse] = {
    // log the method name
    log.debug(ReadSideManagerServiceGrpc.METHOD_PAUSE_READ_SIDE.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId
    // execute the request.
    // In case there is an error an internal error will be returned
    readSideManager
      .pauseForAll(readSideId)
      .map(status => {
        PauseReadSideResponse().withSuccessful(status)
      })
  }

  /**
   * PauseReadSide pauses a read side. This can be useful when running some data
   * migration and this for a given shard
   */
  override def pauseReadSideByShard(request: PauseReadSideByShardRequest): Future[PauseReadSideByShardResponse] = {
    // log the method name
    log.debug(ReadSideManagerServiceGrpc.METHOD_PAUSE_READ_SIDE_BY_SHARD.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId
    // execute the request.
    // In case there is an error an internal error will be returned
    readSideManager
      .pause(readSideId, request.clusterShardNumber.intValue())
      .map(status => {
        PauseReadSideByShardResponse().withSuccessful(status)
      })
  }

  /**
   * ResumeReadSide resumes a paused read side and this across all shards
   */
  override def resumeReadSide(request: ResumeReadSideRequest): Future[ResumeReadSideResponse] = {
    // log the method name
    log.debug(ReadSideManagerServiceGrpc.METHOD_RESUME_READ_SIDE.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId
    // execute the request.
    // In case there is an error an internal error will be returned
    readSideManager
      .resumeForAll(readSideId)
      .map(status => {
        ResumeReadSideResponse().withSuccessful(status)
      })
  }

  /**
   * ResumeReadSideByShard  resumes a paused read side for a given shard
   */
  override def resumeReadSideByShard(request: ResumeReadSideByShardRequest): Future[ResumeReadSideByShardResponse] = {
    // log the method name
    log.debug(ReadSideManagerServiceGrpc.METHOD_RESUME_READ_SIDE_BY_SHARD.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId
    // execute the request.
    // In case there is an error an internal error will be returned
    readSideManager
      .resume(readSideId, request.clusterShardNumber.intValue())
      .map(status => {
        ResumeReadSideByShardResponse().withSuccessful(status)
      })
  }
}
