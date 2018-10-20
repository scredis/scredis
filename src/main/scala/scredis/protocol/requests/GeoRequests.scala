package scredis.protocol.requests

import scredis.protocol._
import scredis.protocol.requests.GeoAddEntries.GeoAddEntry
import scredis.protocol.requests.GeoDistUnit.GeoDistUnit

object GeoRequests {
  import scredis.serialization.Implicits.{doubleReader, stringReader}

  object GeoAdd extends Command("GEOADD") with WriteCommand
  object GeoHash extends Command("GEOHASH")
  object GeoPos extends Command("GEOPOS")
  object GeoDist extends Command("GEODIST")


  case class GeoAdd(key: String, fields: List[GeoAddEntry]) extends Request[Long](
    GeoAdd, key :: fields.flatMap(_.args) :_*
  ) with Key {
    override def decode: Decoder[Long] = {
      case IntegerResponse(value) => value
    }
  }

  case class GeoHash(key: String, locations: List[String]) extends Request[List[Option[String]]](
    GeoHash, key :: locations :_*
  ) with Key {
    override def decode: Decoder[List[Option[String]]] = {
      case a: ArrayResponse => a.parsed[Option[String], List] {
        case b: BulkStringResponse => b.flattenedOpt[String]
      }
    }
  }

  case class GeoPos(key: String, members: List[String]) extends Request[List[Option[(Double, Double)]]](
    GeoPos, key :: members :_*
  ) with Key {
    override def decode: Decoder[List[Option[(Double, Double)]]] = {
      case a: ArrayResponse =>
        a.parsed[Option[(Double, Double)], List] {
          case b: ArrayResponse =>
            b.parsed[String, List] {
              case c: BulkStringResponse => c.flattened[String]
            } match {
              case fst :: snd :: _ => Some((fst.toDouble, snd.toDouble))
              case _ => None
            }
        }
    }
  }

  case class GeoDist(key: String, member1: String, member2: String, unitMaybe: Option[GeoDistUnit]) extends Request[Option[Double]](
    GeoDist, unitMaybe.map(unit => List(key, member1, member2, unit.toString)).getOrElse(List(key, member1, member2)) :_*
  ) with Key {
    override def decode: Decoder[Option[Double]] = {
      case b: BulkStringResponse => b.flattenedOpt[Double]
    }
  }
}

object GeoAddEntries {
  case class GeoAddEntry(long: Double, lat: Double, name: String) {
    def args: List[Any] = List(long, lat, name)
  }
}

object GeoDistUnit {
  sealed trait GeoDistUnit
  case object m extends GeoDistUnit
  case object km extends GeoDistUnit
  case object mi extends GeoDistUnit
  case object ft extends GeoDistUnit
}