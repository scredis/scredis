package scredis.commands

import scredis.io.BlockingConnection
import scredis.protocol.RedisStreamElement
import scredis.protocol.requests.ListRequests._
import scredis.protocol.requests.StreamRequests.XRead
import scredis.serialization.Reader

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.Try

/**
 * This trait implements blocking stream commands.
 */
trait BlockingStreamCommands { self: BlockingConnection =>

  /**
   * Read data from one or multiple streams,
   * only returning entries with an ID greater than the last received ID reported by the caller.
   *
   * since 5.0.0
   *
   * @see [[StreamCommands.xRead]] for non-blocking version
   */
  def xRead(
    count: Int,
    blockMillis: FiniteDuration,
    keys: Seq[String],
    ids: Seq[String]
  ): Try[Map[String, List[RedisStreamElement]]] = sendBlocking(
    XRead(count = count, block = Some(blockMillis.toMillis), keys = keys, ids = ids)
  )(blockMillis)

}