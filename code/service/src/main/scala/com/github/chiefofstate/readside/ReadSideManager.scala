/*
 * Copyright 2020 Chief Of State.
 *
 * SPDX-License-Identifier: MIT
 */

package com.github.chiefofstate.readside

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.persistence.query.Sequence
import org.apache.pekko.projection.ProjectionId
import org.apache.pekko.projection.scaladsl.ProjectionManagement
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

sealed trait StateManager {

  /**
   * returns the offset of a read side given the offset
   *
   * @param readSideId the read side unique id
   * @param shardNumber the cluster shard number.
   *                    This number cannot be greater than the number of shard configured in the system
   * @return the offset value
   */
  def offset(readSideId: String, shardNumber: Int): Future[Long]

  /**
   * returns all the offsets of a read side across the whole cluster
   *
   * @param readSideId the read side unique id
   * @return the list of all the offsets
   */
  def offsets(readSideId: String): Future[Seq[(Int, Long)]]

  /**
   * restarts a given read side on a given shard.
   * This will clear the read side offset and start it over again from the first offset.
   *
   * @param readSideId the read side unique id
   * @param shardNumber the cluster shard number.
   *                    This number cannot be greater than the number of shard configured in the system
   * @return true when successful or false when failed
   */
  def restart(readSideId: String, shardNumber: Int): Future[Boolean]

  /**
   * restarts a given read side across the whole cluster
   *
   * @param readSideId the read side unique id
   * @return true when successful or false when failed
   */
  def restartForAll(readSideId: String): Future[Boolean]

  /**
   * pauses a given read side on a given shard.
   *
   * @param readSideId the read side unique id
   * @param shardNumber the cluster shard number
   * @return true when successful or false when failed
   */
  def pause(readSideId: String, shardNumber: Int): Future[Boolean]

  /**
   * pauses a given read side across the whole cluster
   *
   * @param readSideId the read side unique id
   * @return true when successful or false when failed
   */
  def pauseForAll(readSideId: String): Future[Boolean]

  /**
   * resumes a paused given read side on a given shard.
   *
   * @param readSideId the read side unique id
   * @param shardNumber the cluster shard number
   * @return true when successful or false when failed
   */
  def resume(readSideId: String, shardNumber: Int): Future[Boolean]

  /**
   *  resumes a paused given read side across the whole cluster
   *
   * @param readSideId the read side unique id
   * @return true when successful or false when failed
   */
  def resumeForAll(readSideId: String): Future[Boolean]

  /**
   * skips the current offset to read for a given shard and continue with next.
   * The operation will automatically restart the read side.
   *
   * @param readSideId the read side unique id
   * @param shardNumber the cluster shard number
   * @return true when successful or false when failed
   */
  def skipOffset(readSideId: String, shardNumber: Int): Unit

  /**
   * skips the current offset to read across all shards and continue with next.
   * The operation will automatically restart the read side.
   *
   * @param readSideId the read side unique id
   * @return true when successful or false when failed
   */
  def skipOffsets(readSideId: String): Unit

  /**
   * checks whether a given read side is paused or not
   *
   * @param readSideId the read side unique id
   * @return true when the projection is paused and false on the contrary
   */
  def isReadSidePaused(readSideId: String): Future[Boolean]
}

/**
 * helps manage all read sides state
 *
 * @param system the actor system
 * @param numShards the number of cluster shards
 */
class ReadSideManager(system: ActorSystem[_], numShards: Int) extends StateManager {
  // create an instance of the projection management
  val mgmt: ProjectionManagement = ProjectionManagement(system)

  // grab the execution context from the actor system
  implicit val ec: ExecutionContextExecutor = system.executionContext
  // set the logger
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * returns the offset of a read side given the offset
   *
   * @param readSideId  the read side unique id
   * @param shardNumber the cluster shard number.
   *                    This number cannot be greater than the number of shard configured in the system
   * @return the offset value
   */
  override def offset(readSideId: String, shardNumber: Int): Future[Long] = {
    // let us assert that the shardNumber is between 0 and numShards.
    require(shardNumber >= 0 && shardNumber <= (numShards - 1), "wrong shard number provided")
    // create the projection ID
    val projectionId = ProjectionId(readSideId, shardNumber.toString)
    // get the current offset
    mgmt.getOffset[Sequence](projectionId).map {
      case Some(sequence) => sequence.value
      case None =>
        logger.warn(
          s"unable to retrieve the offset of readSide=$readSideId given the shard=$shardNumber"
        )
        0L
    }
  }

  /**
   * returns all the offsets of a read side across the whole cluster
   *
   * @param readSideId the read side unique id
   * @return the list of all the offsets
   */
  override def offsets(readSideId: String): Future[Seq[(Int, Long)]] = {
    // let us loop through all the available shards and fetch the various offsets
    val futures = (0 until numShards).map { shardNumber =>
      // create the projection ID
      val projectionId = ProjectionId(readSideId, shardNumber.toString)
      // get the current offset
      mgmt.getOffset[Sequence](projectionId).map {
        case Some(sequence) => (shardNumber, sequence.value)
        case None =>
          logger.warn(
            s"unable to retrieve the offset of readSide=$readSideId given the shard=$shardNumber"
          )
          (shardNumber, 0L)
      }
    }
    // execute the futures
    Future.sequence(futures).transformWith {
      case Failure(exception) =>
        logger
          .error(s"fail to fetch offsets, readSideID=$readSideId, cause=${exception.getMessage}")
        Future.failed(exception)
      case Success(value) => Future.successful(value)
    }
  }

  /**
   * restarts a given read side on a given shard.
   * This will clear the read side offset and start it over again from the first offset.
   *
   * @param readSideId  the read side unique id
   * @param shardNumber the cluster shard number.
   *                    This number cannot be greater than the number of shard configured in the system
   * @return true when successful or false when failed
   */
  override def restart(readSideId: String, shardNumber: Int): Future[Boolean] = {
    // let us assert that the shardNumber is between 0 and numShards.
    require(shardNumber >= 0 && shardNumber <= (numShards - 1), "wrong shard number provided")
    // create the projection ID
    val projectionId = ProjectionId(readSideId, shardNumber.toString)
    // execute the restart
    ProjectionManagement(system).clearOffset(projectionId).transformWith[Boolean] {
      case Failure(exception) =>
        logger.error(
          s"read side restart failed, readSideID=$readSideId, shardNumber=$shardNumber, cause=${exception.getMessage}"
        )
        Future.failed(exception)
      case Success(_) =>
        logger
          .info(s"read side restart successfully, readSideID=$readSideId, shardNumber=$shardNumber")
        Future.successful(true)
    }
  }

  /**
   * restarts a given read side across the whole cluster
   *
   * @param readSideId the read side unique id
   * @return true when successful or false when failed
   */
  override def restartForAll(readSideId: String): Future[Boolean] = {
    // let us loop through all the available shards and fetch the various offsets
    // and restart the read side for each shard
    val futures = (0 until numShards).map { shardNumber =>
      // create the projection ID
      val projectionId = ProjectionId(readSideId, shardNumber.toString)
      // let us clear the offset and allow the readside to restart
      ProjectionManagement(system).clearOffset(projectionId)
    }
    // execute the future
    handleForAll(futures, readSideId)
  }

  /**
   * pauses a given read side on a given shard.
   *
   * @param readSideId  the read side unique id
   * @param shardNumber the cluster shard number
   * @return true when successful or false when failed
   */
  override def pause(readSideId: String, shardNumber: Int): Future[Boolean] = {
    // let us assert that the shardNumber is between 0 and numShards.
    require(shardNumber >= 0 && shardNumber <= (numShards - 1), "wrong shard number provided")
    // create the projection ID
    val projectionId = ProjectionId(readSideId, shardNumber.toString)
    // execute the restart
    ProjectionManagement(system).pause(projectionId).transformWith[Boolean] {
      case Failure(exception) =>
        logger.error(
          s"unable to pause read side readSideID=$readSideId, shardNumber=$shardNumber, cause=${exception.getMessage}"
        )
        Future.failed(exception)
      case Success(_) =>
        logger
          .info(s"read side pause successfully, readSideID=$readSideId, shardNumber=$shardNumber")
        Future.successful(true)
    }
  }

  /**
   * pauses a given read side across the whole cluster
   *
   * @param readSideId the read side unique id
   * @return true when successful or false when failed
   */
  override def pauseForAll(readSideId: String): Future[Boolean] = {
    // let us loop through all the available shards and fetch the various offsets
    // and restart the read side for each shard
    val futures = (0 until numShards).map { shardNumber =>
      // create the projection ID
      val projectionId = ProjectionId(readSideId, shardNumber.toString)
      // let us clear the offset and allow the readside to restart
      ProjectionManagement(system).pause(projectionId)
    }
    // execute the future
    handleForAll(futures, readSideId)
  }

  private def handleForAll(futures: Seq[Future[Done]], readSideId: String): Future[Boolean] = {
    Future.sequence(futures).transformWith[Boolean] {
      case Failure(exception) =>
        logger
          .error(s"read side restart failed, readSideID=$readSideId, cause=${exception.getMessage}")
        Future.failed(exception)
      case Success(_) =>
        logger.info(s"read side restart successfully, readSideID=$readSideId")
        Future.successful(true)
    }
  }

  /**
   * resumes a paused given read side on a given shard.
   *
   * @param readSideId  the read side unique id
   * @param shardNumber the cluster shard number
   * @return true when successful or false when failed
   */
  override def resume(readSideId: String, shardNumber: Int): Future[Boolean] = {
    // let us assert that the shardNumber is between 0 and numShards.
    require(shardNumber >= 0 && shardNumber <= (numShards - 1), "wrong shard number provided")
    // create the projection ID
    val projectionId = ProjectionId(readSideId, shardNumber.toString)
    // execute the restart
    ProjectionManagement(system).resume(projectionId).transformWith[Boolean] {
      case Failure(exception) =>
        logger.error(
          s"unable to pause read side readSideID=$readSideId, shardNumber=$shardNumber, cause=${exception.getMessage}"
        )
        Future.failed(exception)
      case Success(_) =>
        logger
          .info(s"read side pause successfully, readSideID=$readSideId, shardNumber=$shardNumber")
        Future.successful(true)
    }
  }

  /**
   * resumes a paused given read side across the whole cluster
   *
   * @param readSideId the read side unique id
   * @return true when successful or false when failed
   */
  override def resumeForAll(readSideId: String): Future[Boolean] = {
    // let us loop through all the available shards and fetch the various offsets
    // and restart the read side for each shard
    val futures = (0 until numShards).map { shardNumber =>
      // create the projection ID
      val projectionId = ProjectionId(readSideId, shardNumber.toString)
      // let us clear the offset and allow the readside to restart
      ProjectionManagement(system).resume(projectionId)
    }
    // execute the future
    handleForAll(futures, readSideId)
  }

  /**
   * skips the current offset to read for a given shard and continue with next.
   * The operation will automatically restart the read side.
   *
   * @param readSideId  the read side unique id
   * @param shardNumber the cluster shard number
   * @return true when successful or false when failed
   */
  override def skipOffset(readSideId: String, shardNumber: Int): Unit = {
    val projectionId = ProjectionId(readSideId, shardNumber.toString)
    val currentOffset: Future[Option[Sequence]] =
      ProjectionManagement(system).getOffset[Sequence](projectionId)
    currentOffset.foreach {
      case Some(s) =>
        ProjectionManagement(system).updateOffset[Sequence](projectionId, Sequence(s.value + 1))
      case None => // already removed
    }
  }

  /**
   * skips the current offset to read across all shards and continue with next.
   * The operation will automatically restart the read side.
   *
   * @param readSideId the read side unique id
   * @return true when successful or false when failed
   */
  override def skipOffsets(readSideId: String): Unit = {
    // let us loop through all the available shards and fetch the various offsets
    // and restart the read side for each shard
    (0 until numShards).foreach { shardNumber =>
      // create the projection ID
      val projectionId = ProjectionId(readSideId, shardNumber.toString)
      val currentOffset: Future[Option[Sequence]] =
        ProjectionManagement(system).getOffset[Sequence](projectionId)
      // process the value of the future
      currentOffset.foreach {
        case Some(s) =>
          ProjectionManagement(system).updateOffset[Sequence](projectionId, Sequence(s.value + 1))
        case None => // already removed
      }
    }
  }

  /**
   * checks whether a given read side is paused or not
   *
   * @param readSideId the read side unique id
   * @return true when the projection is paused and false on the contrary
   */
  override def isReadSidePaused(readSideId: String): Future[Boolean] = {
    // set the management
    val mgmt = ProjectionManagement(system)
    // let us loop through all the available shards and fetch the various offsets
    // and restart the read side for each shard
    // check the status of the projection
    val results: Seq[Future[Boolean]] = (0 until numShards).map { shardNumber =>
      // create the projection ID
      val projectionId = ProjectionId(readSideId, shardNumber.toString)
      // check the pause mode of the projection
      mgmt.isPaused(projectionId)
    }
    // run the future
    Future.sequence(results).transformWith[Boolean] {
      case Failure(exception) => Future.failed(exception)
      case Success(value)     => Future.successful(value.forall(_ == true))
    }
  }
}
