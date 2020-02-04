package scredis.protocol

import java.nio.ByteBuffer

import akka.util.ByteString
import scredis.exceptions._
import scredis.serialization.Reader
import scredis.{ClusterSlotRange, ClusterSlotRangeNodeInfo, Server}

import scala.collection.compat._
import scala.language.higherKinds
import scala.util.{Failure, Success, Try}

sealed trait Response

case class ErrorResponse(value: String) extends Response

case class SimpleStringResponse(value: String) extends Response

case class IntegerResponse(value: Long) extends Response {
  def toBoolean: Boolean = value > 0
}

/** Errors specific to cluster operation */
sealed trait ClusterError
case class Moved(hashSlot: Int, host: String, port: Int) extends ClusterError
case class Ask(hashSlot: Int, host: String, port: Int) extends ClusterError
case object TryAgain extends ClusterError
case object ClusterDown extends ClusterError
case object CrossSlot extends ClusterError

case class ClusterErrorResponse(error: ClusterError, message: String) extends Response

case class BulkStringResponse(valueOpt: Option[Array[Byte]]) extends Response {
  def parsed[R](implicit reader: Reader[R]): Option[R] = valueOpt.map(reader.read)

  def flattened[R](implicit reader: Reader[R]): R = flattenedOpt.get

  def flattenedOpt[R](implicit reader: Reader[R]): Option[R] = parsed[R]

  override def toString: String = s"BulkStringResponse(" +
    s"${valueOpt.map(ByteString(_).decodeString("UTF-8"))})"
}

case class ArrayResponse(length: Int, buffer: ByteBuffer) extends Response {

  def headOpt[R](decoder: Decoder[R]): Option[R] = if (length > 0) {
    val position = buffer.position()
    val response = Protocol.decode(buffer)
    if (decoder.isDefinedAt(response)) {
      val decoded = decoder.apply(response)
      buffer.position(position)
      Some(decoded)
    } else {
      throw new IllegalArgumentException(s"Does not know how to parse response: $response")
    }
  } else {
    None
  }

  def parsed[R, CC[X] <: Iterable[X]](decoder: Decoder[R])(
    implicit factory: Factory[R, CC[R]]
    ): CC[R] = {
    val builder = factory.newBuilder
    var i = 0
    while (i < length) {
      val response = Protocol.decode(buffer)
      if (decoder.isDefinedAt(response)) {
        builder += decoder.apply(response)
      } else {
        throw new IllegalArgumentException(s"Does not know how to parse response: $response")
      }
      i += 1
    }
    builder.result()
  }

  def parsedAsPairs[R1, R2, CC[X] <: Iterable[X]](
    firstDecoder: Decoder[R1]
  )(
    secondDecoder: Decoder[R2]
  )(implicit factory: Factory[(R1, R2), CC[(R1, R2)]]): CC[(R1, R2)] = {
    val builder = factory.newBuilder
    var i = 0
    while (i < length) {
      val firstResponse = Protocol.decode(buffer)
      val firstValue = if (firstDecoder.isDefinedAt(firstResponse)) {
        firstDecoder.apply(firstResponse)
      } else {
        throw new IllegalArgumentException(
          s"Does not know how to parse first response: $firstResponse"
        )
      }
      val secondResponse = Protocol.decode(buffer)
      val secondValue = if (secondDecoder.isDefinedAt(secondResponse)) {
        secondDecoder.apply(secondResponse)
      } else {
        throw new IllegalArgumentException(
          s"Does not know how to parse second response: $secondResponse"
        )
      }
      builder += ((firstValue, secondValue))
      i += 2
    }
    builder.result()
  }

  def parsedAsPairsMap[R1, R2, CC[X, Y] <: collection.Map[X, Y]](
    firstDecoder: Decoder[R1]
  )(
    secondDecoder: Decoder[R2]
  )(implicit factory: Factory[(R1, R2), CC[R1, R2]]): CC[R1, R2] = {
    val builder = factory.newBuilder
    var i = 0
    while (i < length) {
      val firstResponse = Protocol.decode(buffer)
      val firstValue = if (firstDecoder.isDefinedAt(firstResponse)) {
        firstDecoder.apply(firstResponse)
      } else {
        throw new IllegalArgumentException(
          s"Does not know how to parse first response: $firstResponse"
        )
      }
      val secondResponse = Protocol.decode(buffer)
      val secondValue = if (secondDecoder.isDefinedAt(secondResponse)) {
        secondDecoder.apply(secondResponse)
      } else {
        throw new IllegalArgumentException(
          s"Does not know how to parse second response: $secondResponse"
        )
      }
      builder += ((firstValue, secondValue))
      i += 2
    }
    builder.result()
  }

  def parsedAsScanResponse[R, CC[X] <: Iterable[X]](
    decoder: Decoder[CC[R]]
  ): (Long, CC[R]) = {
    if (length != 2) {
      throw RedisProtocolException(s"Unexpected length for scan-like array response: $length")
    }

    val nextCursor = Protocol.decode(buffer) match {
      case b: BulkStringResponse => b.flattened[String].toLong
      case x => throw RedisProtocolException(s"Unexpected response for scan cursor: $x")
    }

    Protocol.decode(buffer) match {
      case a: ArrayResponse if decoder.isDefinedAt(a) => (nextCursor, decoder.apply(a))
      case a: ArrayResponse => throw new IllegalArgumentException(
        s"Does not know how to parse response: $a"
      )
      case x => throw RedisProtocolException(s"Unexpected response for scan elements: $x")
    }
  }


  def parsedAsClusterSlotsResponse[CC[X] <: Iterable[X]](
    implicit factory: Factory[ClusterSlotRange, CC[ClusterSlotRange]]) : CC[ClusterSlotRange] = {
    val builder = factory.newBuilder
    var i = 0
    try {
      while (i < length) {
        Protocol.decode(buffer) match {
          case a: ArrayResponse =>
            val begin = Protocol.decode(buffer).asInstanceOf[IntegerResponse].value
            val end = Protocol.decode(buffer).asInstanceOf[IntegerResponse].value

            // master for this slotrange
            val mha = Protocol.decode(buffer).asInstanceOf[ArrayResponse] // mast host/port header
            val masterHost = Protocol.decode(buffer).asInstanceOf[BulkStringResponse].parsed[String].get
            val masterPort = Protocol.decode(buffer).asInstanceOf[IntegerResponse].value
            val masterNodeId: Option[String] = if (mha.length > 2) {
                              Some(Protocol.decode(buffer).asInstanceOf[BulkStringResponse].flattened[String])
                            } else None
            val master = ClusterSlotRangeNodeInfo(Server(masterHost,masterPort.toInt),masterNodeId)

            // first replica begins at index 3 
            var r = 3 
            var replicaList: List[ClusterSlotRangeNodeInfo] = Nil
            while (r < a.length) {
              val rh =   Protocol.decode(buffer).asInstanceOf[ArrayResponse] 
              val replicaHost = Protocol.decode(buffer).asInstanceOf[BulkStringResponse].flattened[String]
              val replicaPort = Protocol.decode(buffer).asInstanceOf[IntegerResponse].value
              val replicaNodeId = if (rh.length > 2) {
                         Some(Protocol.decode(buffer).asInstanceOf[BulkStringResponse].flattened[String])
                     } else {
                         None
                     }
              // skip additional fields (which can be added in future according to doc)
              var skipIndex=3
              while(skipIndex < rh.length) {
                 Protocol.decode(buffer)
                 skipIndex += 1
              }
              val replica = ClusterSlotRangeNodeInfo(Server(replicaHost,replicaPort.toInt),replicaNodeId)
              replicaList = replica :: replicaList
              r += 1
            }

            builder += ClusterSlotRange((begin, end), master, replicaList)

          case other => throw RedisProtocolException(s"Expected an array parsing CLUSTER SLOTS reply, got $other")
        }

        i += 1
      }
    } catch {
      case rpe: RedisProtocolException => throw rpe
      case x: Exception => throw RedisProtocolException("Unexpected values parsing CLUSTER SLOTS reply", x)
    }

    builder.result()
  }

  def parsed[CC[X] <: Iterable[X]](decoders: Iterable[Decoder[Any]])(
    implicit factory: Factory[Try[Any], CC[Try[Any]]]
    ): CC[Try[Any]] = {
    val builder = factory.newBuilder
    var i = 0
    val decodersIterator = decoders.iterator
    while (i < length) {
      val response = Protocol.decode(buffer)
      val decoder = decodersIterator.next()
      val result = response match {
        case ErrorResponse(message) => Failure(RedisErrorResponseException(message))
        case ClusterErrorResponse(error, message) => Failure(RedisClusterErrorResponseException(error, message))
        case reply => if (decoder.isDefinedAt(reply)) {
          try {
            Success(decoder.apply(reply))
          } catch {
            case e: Throwable => Failure(RedisProtocolException("", e))
          }
        } else {
          Failure(RedisProtocolException(s"Unexpected reply: $reply"))
        }
      }
      builder += result
      i += 1
    }
    builder.result()
  }

  override def toString: String = s"ArrayResponse(length=$length, buffer=" +
    s"${ByteString(buffer).decodeString("UTF-8")})"

}
