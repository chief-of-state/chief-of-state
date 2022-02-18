package com.github.chiefofstate.management

import com.github.chiefofstate.protobuf.v1.common.ReadSideOffset
import com.github.chiefofstate.protobuf.v1.management.ReadSideManagementServiceGrpc.ReadSideManagementService
import com.github.chiefofstate.protobuf.v1.management._
import com.github.chiefofstate.readside.ReadSideStateManager
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContext, Future }

class ReadSideMgmtServiceImpl(readSideStateManager: ReadSideStateManager)(implicit ec: ExecutionContext)
    extends ReadSideManagementService {

  final val log: Logger = LoggerFactory.getLogger(getClass)

  /**
   * GetLatestOffset retrieves latest stored offset
   */
  override def getLatestOffset(request: GetLatestOffsetRequest): Future[GetLatestOffsetResponse] = {
    // log the method name
    log.debug(ReadSideManagementServiceGrpc.METHOD_GET_LATEST_OFFSET.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId

    // fetch the list of offsets
    readSideStateManager
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
    readSideStateManager
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
    log.debug(ReadSideManagementServiceGrpc.METHOD_RESTART_READ_SIDE.getFullMethodName)
    // get the readside id
    val readSideId = request.readSideId
    readSideStateManager
      .restartForAll(readSideId)
      .map(status => {
        RestartReadSideResponse().withSuccessful(status)
      })
  }

  /**
   * RestartReadSideByShard will clear the read side offset for the given shard and start it over again from the first offset
   */
  override def restartReadSideByShard(request: RestartReadSideByShardRequest): Future[RestartReadSideByShardResponse] =
    ???

  /**
   * PauseReadSide pauses a read side. This can be useful when running some data migration
   */
  override def pauseReadSide(request: PauseReadSideRequest): Future[PauseReadSideResponse] = ???

  /**
   * PauseReadSide pauses a read side. This can be useful when running some data
   * migration and this for a given shard
   */
  override def pauseReadSideByShard(request: PauseReadSideByShardRequest): Future[PauseReadSideByShardResponse] = ???

  /**
   * ResumeReadSide resumes a paused read side and this across all shards
   */
  override def resumeReadSide(request: ResumeReadSideRequest): Future[ResumeReadSideResponse] = ???

  /**
   * ResumeReadSideByShard  resumes a paused read side for a given shard
   */
  override def resumeReadSideByShard(request: ResumeReadSideByShardRequest): Future[ResumeReadSideByShardResponse] = ???
}
