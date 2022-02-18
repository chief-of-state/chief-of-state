package com.github.chiefofstate.readside

import akka.actor.typed.ActorSystem
import akka.projection.ProjectionId
import akka.projection.scaladsl.ProjectionManagement
import org.slf4j.{ Logger, LoggerFactory }

import scala.concurrent.{ ExecutionContextExecutor, Future }
import scala.util.{ Failure, Success }

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
}

/**
 * helps manage all read sides state
 *
 * @param system the actor system
 * @param numShards the number of cluster shards
 */
class ReadSideStateManager(system: ActorSystem[_], numShards: Int) extends StateManager {
  // set the logger
  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  // grab the execution context from the actor system
  implicit val ec: ExecutionContextExecutor = system.executionContext

  // create an instance of the projection management
  val mgmt: ProjectionManagement = ProjectionManagement(system)

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
    mgmt.getOffset[Long](projectionId).map {
      case Some(value) => value
      case None =>
        logger.warn(s"unable to retrieve the offset of readSide=$readSideId given the shard=$shardNumber")
        -1L
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
      mgmt.getOffset[Long](projectionId).map {
        case Some(value) => (shardNumber, value)
        case None =>
          logger.warn(s"unable to retrieve the offset of readSide=$readSideId given the shard=$shardNumber")
          (shardNumber, -1L)
      }
    }
    // execute the futures
    Future.sequence(futures).transformWith {
      case Failure(exception) =>
        logger.error(s"fail to fetch offsets, readSideID=$readSideId, cause=${exception.getMessage}")
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
          s"read side restart failed, readSideID=$readSideId, shardNumber=$shardNumber, cause=${exception.getMessage}")
        Future.failed(exception)
      case Success(_) =>
        logger.info(s"read side restart successfully, readSideID=$readSideId, shardNumber=$shardNumber")
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
    Future.sequence(futures).transformWith[Boolean] {
      case Failure(exception) =>
        logger.error(s"read side restart failed, readSideID=$readSideId, cause=${exception.getMessage}")
        Future.failed(exception)
      case Success(_) =>
        logger.info(s"read side restart successfully, readSideID=$readSideId")
        Future.successful(true)
    }
  }
}
