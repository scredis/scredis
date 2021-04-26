package scredis.protocol.requests

import scredis.protocol._
import scredis.serialization.UTF8StringWriter
import scredis.serialization.{Writer}

object StreamRequests {
  object XAck      extends Command(names = "XACK") with WriteCommand
  object XAdd      extends Command("XADD") with WriteCommand
  object XDel      extends Command(names = "XDEL") with WriteCommand
  object XTrim     extends Command("XTRIM") with WriteCommand
  object XLen      extends Command("XLEN")
  object XRange    extends Command("XRANGE")
  object XRevRange extends Command("XREVRANGE")
  object XRead     extends Command("XREAD")

  case class XAck(key: String, groupId: String, entryIds: Iterable[String])
      extends Request[Long](XAck, (key :+ groupId :+ entryIds): _*)
      with Key {

    override def decode: Decoder[Long] = {
      case r: IntegerResponse => r.value
    }
  }

  case class XAdd[W](key: String, entryId: String, fieldValuePairs: (String, W)*)(
    implicit writer: Writer[W]
  ) extends Request[Option[String]](
        XAdd,
        key :: entryId :: unpair(
          fieldValuePairs.map {
            case (field, value) => (UTF8StringWriter.write(field), writer.write(value))
          }
        ): _*
      )
      with Key {

    override def decode: Decoder[Option[String]] = {
      case response: BulkStringResponse => response.parsed[String]
    }
  }

  case class XDel(key: String, entryIds: Iterable[String]) extends Request[Long](XAck, key :+ entryIds) with Key {

    override def decode: Decoder[Long] = {
      case r: IntegerResponse => r.value
    }
  }

  case class XLen(key: String) extends Request[Long](XLen, key) with Key {
    override def decode: Decoder[Long] = {
      case IntegerResponse(value) => value
    }
  }

  case class XRange(
    key: String,
    start: String,
    end: String,
    count: Option[Int]
  ) extends Request[List[(String, Map[String, String])]](
        XRange,
        List(key, start, end) ++ count.map(c => s"COUNT $c"): _*
      )
      with Key {

    override def decode: Decoder[List[RedisStreamElement]] = {
      case a: ArrayResponse =>
        a.parsedAsStreamResponse()
    }
  }

  case class XRevRange(
    key: String,
    start: String,
    end: String,
    count: Option[Int]
  ) extends Request[List[(String, Map[String, String])]](
    XRevRange,
    List(key, start, end) ++ count.map(c => s"COUNT $c"): _*
  )
    with Key {
    override def decode: Decoder[List[(String, Map[String, String])]] = {
      case a: ArrayResponse => a.parsedAsStreamResponse()
    }
  }

  case class XTrim(
    key: String,
    trimType: XTrimType,
    value: Long,
    exact: Boolean
  ) extends Request[Long](
    XTrim,
    List(key, trimType.toString, if (exact) "=" else "~", value): _*
  )
    with Key {
    override def decode: Decoder[Long] = {
      case a: IntegerResponse => a.value
    }
  }

  case class XRead(
    count: Int,
    block: Option[Long],
    keys: Seq[String],
    ids: Seq[String]
  ) extends Request[Map[String, List[RedisStreamElement]]](
    XRead,
    (block.fold(List.empty[String])(b => List("BLOCK", b.toString)) :+ "COUNT" :+ count.toString :+ "STREAMS") ++ keys ++ ids :_*
  )
     {
    override def decode: Decoder[Map[String, List[RedisStreamElement]]] = {
      case a: ArrayResponse => a.parsedAsListOfStreamResponse()
    }
  }


}

sealed trait XTrimType

object XTrimType {
  case object MaxLen extends XTrimType

  case object MinId extends XTrimType
}
