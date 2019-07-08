package scredis.protocol.requests

import scredis.protocol._
import scredis.serialization.Writer

import scala.collection.generic.CanBuildFrom
import scala.language.higherKinds

object PubSubRequests {
  
  import scredis.serialization.Implicits.{intReader, stringReader}
  
  object PSubscribe extends Command("PSUBSCRIBE")
  object Publish extends Command("PUBLISH") with WriteCommand
  object PubSubChannels extends Command("PUBSUB", "CHANNELS")
  object PubSubNumSub extends Command("PUBSUB", "NUMSUB")
  object PubSubNumPat extends ZeroArgCommand("PUBSUB", "NUMPAT")
  object PUnsubscribe extends Command("PUNSUBSCRIBE")
  object Subscribe extends Command("SUBSCRIBE")
  object Unsubscribe extends Command("UNSUBSCRIBE")
  
  case class PSubscribe(patterns: String*) extends Request[Int](
    PSubscribe, patterns: _*
  ) {
    override def decode = ???
  }
  
  case class Publish[W: Writer](channel: String, message: W) extends Request[Long](
    Publish, channel, implicitly[Writer[W]].write(message)
  ) {
    override def decode = {
      case IntegerResponse(value) => value
    }
  }
  
  case class PubSubChannels(patternOpt: Option[String])
    extends Request[List[String]](PubSubChannels, patternOpt.toSeq: _*) {
    override def decode = {
      case a: ArrayResponse => a.parsed[String, List] {
        case b: BulkStringResponse => b.flattened[String]
      }
    }
  }
  
  case class PubSubNumSub(channels: String*)
    extends Request[Map[String, Int]](PubSubNumSub, channels: _*) {
    override def decode = {
      case a: ArrayResponse => a.parsedAsPairsMap[String, Int, Map] {
        case b: BulkStringResponse => b.flattened[String]
      } {
        case b: BulkStringResponse => b.flattened[Int]
      }
    }
  }
  
  case class PubSubNumPat() extends Request[Long](PubSubNumPat) {
    override def decode = {
      case IntegerResponse(value) => value
    }
  }
  
  case class PUnsubscribe(patterns: String*) extends Request[Int](
    PUnsubscribe, patterns: _*
  ) {
    override def decode = ???
  }
  
  case class Subscribe(channels: String*) extends Request[Int](
    Subscribe, channels: _*
  ) {
    override def decode = ???
  }
  
  case class Unsubscribe(channels: String*) extends Request[Int](
    Unsubscribe, channels: _*
  ) {
    override def decode = ???
  }
  
}