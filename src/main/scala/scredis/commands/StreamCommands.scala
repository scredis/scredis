package scredis.commands

import scredis.io.{Connection, NonBlockingConnection}
import scredis.protocol.requests.StreamRequests._
import scredis.serialization.Writer

import scala.concurrent.Future

/**
  * This trait implements stream commands.
  *
  * @define e     [[scredis.exceptions.RedisErrorResponseException]]
  * @define none  `None`
  * @define true  '''true'''
  * @define false '''false'''
  */
trait StreamCommands {
  self: Connection with NonBlockingConnection =>

  /**
    * The XACK command removes one or multiple messages from the
    * Pending Entries List (PEL) of a stream consumer group.
    *
    * since 5.0.0
    *
    * @param key      stream key
    * @param groupId  consumer group id
    * @param entryIds stream message ids
    * @return the number of messages successfully acknowledged
    */
  def xAck(key: String, groupId: String, entryIds: String*): Future[Long] = {
    send(XAck(key = key, groupId = groupId, entryIds = entryIds))
  }

  /**
    * Appends the specified stream entry to the stream at the specified key.
    * If the key does not exist, as a side effect of running this command the key is created
    * with a stream value. The creation of stream's key can be disabled with the NOMKSTREAM option.
    *
    * since 5.0.0
    *
    * @param key             stream key
    * @param fieldValuePairs field-value pair(s) to be set
    */
  def xAdd[W: Writer](key: String, fieldValuePairs: Map[String, W], entryId: String = "*"): Future[Option[String]] = {
    send(XAdd(key, entryId, fieldValuePairs.toList: _*))
  }

  /**
    * Removes the specified entries from a stream, and returns the number of entries deleted,
    * that may be different from the number of IDs passed to the command in case certain IDs do not exist.
    *
    * since 5.0.0
    *
    * @param key      stream key
    * @param entryIds stream message ids
    * @return the number of deleted entries
    */
  def xDel(key: String, entryIds: String*): Future[Long] = {
    send(XDel(key = key, entryIds = entryIds))
  }

  /**
    * Returns the number of entries inside a stream.
    * If the specified key does not exist the command returns zero, as if the stream was empty.
    *
    * @param key stream key
    * @return the number of entries inside a stream or 0 if stream doesn't exist
    */
  def xLen(key: String): Future[Long] = {
    send(XLen(key))
  }

  def xRange(
    key: String,
    start: String,
    end: String,
    count: Option[Int]
  ): Future[List[(String, Map[String, String])]] = send(
    XRange(key = key, start = start, end = end, count = count)
  )

}
