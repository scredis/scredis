package scredis.commands

import scredis.io.{Connection, NonBlockingConnection}
import scredis.protocol.RedisStreamElement
import scredis.protocol.requests.StreamRequests._
import scredis.protocol.requests.XTrimType
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

  /**
   * The command returns the stream entries matching a given range of IDs.
   * The range is specified by a minimum and maximum ID.
   * All the entries having an ID between the two specified
   * or exactly one of the two IDs specified (closed interval) are returned.
   *
   * since 5.0.0
   */
  def xRange(
    key: String,
    start: String,
    end: String,
    count: Option[Int]
  ): Future[List[RedisStreamElement]] = send(
    XRange(key = key, start = start, end = end, count = count)
  )

  /**
   * This command is exactly like XRANGE,
   * but with the notable difference of returning the entries in reverse order,
   * and also taking the start-end range in reverse order:
   * in XREVRANGE you need to state the end ID and later the start ID,
   * and the command will produce all the element between (or exactly like) the two IDs,
   * starting from the end side.
   *
   * since 5.0.0
   */
  def xRevRange(
    key: String,
    start: String,
    end: String,
    count: Option[Int]
  ): Future[List[RedisStreamElement]] = send(
    XRevRange(key = key, start = start, end = end, count = count)
  )

  /**
   * XTRIM trims the stream by evicting older entries (entries with lower IDs) if needed.
   * Trimming the stream can be done using one of these strategies:
   * MAXLEN: Evicts entries as long as the stream's length exceeds the specified threshold, where threshold is a positive integer.
   * MINID: Evicts entries with IDs lower than threshold, where threshold is a stream ID.
   *
   * since 5.0.0
   */
  def xTrim(
    key: String,
    trimType: XTrimType,
    value: Long,
    exact: Boolean = true
  ): Future[Long] = send(
    XTrim(key = key, trimType = trimType, value = value, exact = exact)
  )

  /**
   * Read data from one or multiple streams,
   * only returning entries with an ID greater than the last received ID reported by the caller.
   *
   * since 5.0.0
   *
   * @see [[BlockingStreamCommands.xRead]] for blocking version
   */
  def xRead(
    count: Int,
    keys: Seq[String],
    ids: Seq[String]
  ):Future[Map[String, List[RedisStreamElement]]] = send(
    XRead(count = count, block = None, keys = keys, ids = ids)
  )

}
