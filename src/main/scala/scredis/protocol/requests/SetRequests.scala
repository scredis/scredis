package scredis.protocol.requests

import scala.language.higherKinds
import scredis.protocol._
import scredis.serialization.{ Reader, Writer }

import scala.collection.generic.CanBuildFrom

object SetRequests {
  
  object SAdd extends Command("SADD") with WriteCommand
  object SCard extends Command("SCARD")
  object SDiff extends Command("SDIFF")
  object SDiffStore extends Command("SDIFFSTORE") with WriteCommand
  object SInter extends Command("SINTER")
  object SInterStore extends Command("SINTERSTORE") with WriteCommand
  object SIsMember extends Command("SISMEMBER")
  object SMembers extends Command("SMEMBERS")
  object SMIsMember extends Command("SMISMEMBER")
  object SMove extends Command("SMOVE") with WriteCommand
  object SPop extends Command("SPOP") with WriteCommand
  object SRandMember extends Command("SRANDMEMBER")
  object SRem extends Command("SREM") with WriteCommand
  object SScan extends Command("SSCAN")
  object SUnion extends Command("SUNION")
  object SUnionStore extends Command("SUNIONSTORE") with WriteCommand
  
  case class SAdd[W](key: String, members: W*)(implicit writer: Writer[W]) extends Request[Long](
    SAdd, key +: members.map(writer.write): _*
  ) with Key {
    override def decode = {
      case IntegerResponse(value) => value
    }
  }
  
  case class SCard(key: String) extends Request[Long](SCard, key) with Key {
    override def decode = {
      case IntegerResponse(value) => value
    }
  }
  
  case class SDiff[R: Reader](keys: String*)
    extends Request[Set[R]](SDiff, keys: _*) with Key {
    override def decode = {
      case a: ArrayResponse => a.parsed[R, Set] {
        case b: BulkStringResponse => b.flattened[R]
      }
    }
    override val key = keys.head
  }
  
  case class SDiffStore(destination: String, keys: String*) extends Request[Long](
    SDiffStore, destination +: keys: _*
  ) with Key {
    override def decode = {
      case IntegerResponse(value) => value
    }
    override val key = keys.head
  }
  
  case class SInter[R: Reader](keys: String*)
    extends Request[Set[R]](SInter, keys: _*) with Key {
    override def decode = {
      case a: ArrayResponse => a.parsed[R, Set] {
        case b: BulkStringResponse => b.flattened[R]
      }
    }
    override val key = keys.head
  }
  
  case class SInterStore(destination: String, keys: String*) extends Request[Long](
    SInterStore, destination +: keys: _*
  ) with Key {
    override def decode = {
      case IntegerResponse(value) => value
    }
    override val key = keys.head
  }
  
  case class SIsMember[W: Writer](key: String, member: W) extends Request[Boolean](
    SIsMember, key, implicitly[Writer[W]].write(member)
  ) with Key {
    override def decode = {
      case i: IntegerResponse => i.toBoolean
    }
  }

  case class SMembers[R: Reader](key: String) extends Request[Set[R]](SMembers, key) with Key {
    override def decode = {
      case a: ArrayResponse => a.parsed[R, Set] {
        case b: BulkStringResponse => b.flattened[R]
      }
    }
  }

  case class SMIsMember[W: Writer](key: String, members: W*) extends Request[Seq[Boolean]](
    SMIsMember, key +: members: _*
  ) with Key {
    override def decode = {
      case a: ArrayResponse =>
        val replies = a.parsed[Boolean, Seq] {
          case i: IntegerResponse => i.toBoolean
        }
        replies
    }
  }

  case class SMove[W: Writer](
    source: String, destination: String, member: W
  ) extends Request[Boolean](
    SMove, source, destination, implicitly[Writer[W]].write(member)
  ) {
    override def decode = {
      case i: IntegerResponse => i.toBoolean
    }
  }
  
  case class SPop[R: Reader](key: String) extends Request[Option[R]](SPop, key) with Key {
    override def decode: Decoder[Option[R]] = {
      case b: BulkStringResponse => b.parsed[R]
    }
  }

  case class SPopCount[R: Reader](key: String, count: Int) extends Request[List[R]](SPop, key, count) with Key {
    override def decode: Decoder[List[R]] = {
      case a: ArrayResponse => a.parsed[Option[R], List] {
        case b: BulkStringResponse => b.parsed[R]
      }.flatten
    }
  }

  case class SRandMember[R: Reader](key: String) extends Request[Option[R]](SRandMember, key) with Key {
    override def decode = {
      case b: BulkStringResponse => b.parsed[R]
    }
  }
  
  case class SRandMembers[R: Reader](key: String, count: Int) extends Request[Set[R]](SRandMember, key, count) with Key {
    override def decode = {
      case a: ArrayResponse => a.parsed[R, Set] {
        case b: BulkStringResponse => b.flattened[R]
      }
    }
  }
  
  case class SRem[W](key: String, members: W*)(implicit writer: Writer[W]) extends Request[Long](
    SRem, key +: members.map(writer.write): _*
  ) with Key {
    override def decode = {
      case IntegerResponse(value) => value
    }
  }
  
  case class SScan[R: Reader](
    key: String,
    cursor: Long,
    matchOpt: Option[String],
    countOpt: Option[Int]
  ) extends Request[(Long, Set[R])](
    SScan,
    generateScanLikeArgs(
      keyOpt = Some(key),
      cursor = cursor,
      matchOpt = matchOpt,
      countOpt = countOpt
    ): _*
  ) with Key {
    override def decode = {
      case a: ArrayResponse => a.parsedAsScanResponse[R, Set] {
        case a: ArrayResponse => a.parsed[R, Set] {
          case b: BulkStringResponse => b.flattened[R]
        }
      }
    }
  }
  
  case class SUnion[R: Reader](keys: String*)
    extends Request[Set[R]](SUnion, keys: _*) with Key {
    override def decode = {
      case a: ArrayResponse => a.parsed[R, Set] {
        case b: BulkStringResponse => b.flattened[R]
      }
    }
    override val key = keys.head
  }
  
  case class SUnionStore(destination: String, keys: String*) extends Request[Long](
    SUnionStore, destination +: keys: _*
  ) with Key {
    override def decode = {
      case IntegerResponse(value) => value
    }
    override val key = keys.head
  }
  
}