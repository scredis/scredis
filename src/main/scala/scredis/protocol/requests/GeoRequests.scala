package scredis.protocol.requests

import scredis.exceptions.RedisErrorResponseException
import scredis.protocol._
import scredis.protocol.requests.GeoAddEntries.GeoAddEntry
import scredis.protocol.requests.GeoDistUnit.GeoDistUnit
import scredis.protocol.requests.GeoSortOrder.GeoSortOrder

object GeoRequests {
  import scredis.serialization.Implicits.{doubleReader, stringReader}

  object GeoAdd extends Command("GEOADD") with WriteCommand
  object GeoHash extends Command("GEOHASH")
  object GeoPos extends Command("GEOPOS")
  object GeoDist extends Command("GEODIST")
  object GeoRadius extends Command("GEORADIUS") with WriteCommand
  object GeoRadiusByMember extends Command("GEORADIUSBYMEMBER") with WriteCommand


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

  case class GeoRadius(key: String, long: Double, lat: Double, radius: Double, radiusUnit: GeoDistUnit) extends Request[List[String]](
    GeoRadius, key, long, lat, radius, radiusUnit.toString
  ) with Key {
    override def decode: Decoder[List[String]] = {
      case a: ArrayResponse => a.parsed[String, List] {
        case b: BulkStringResponse => b.flattened[String]
      }
    }
  }

  case class GeoRadiusComplex(key: String, long: Double, lat: Double, radius: Double,
                              radiusUnit: GeoDistUnit, sort: Option[GeoSortOrder], count: Option[Int]) extends Request[List[RadiusResult]](
    GeoRadius, paramsGeoRadiusComplex(key, List[Any](long, lat, radius, radiusUnit), sort, count) :_*
  ) with Key {
    override def decode: Decoder[List[RadiusResult]] = {
      case a: ArrayResponse => a.parsed[RadiusResult, List] {
        case b: ArrayResponse => b.parsed[List[String], List] {
            case c: BulkStringResponse => List(c.flattened[String])
            case c: ArrayResponse => c.parsed[String, List] {
              case d: BulkStringResponse => d.flattened[String]
            }
          }.flatten match {
            case name :: distance :: longS :: latS :: _ => RadiusResult(name, distance.toDouble, longS.toDouble, latS.toDouble)
            case other => throw RedisErrorResponseException(s"Response from Redis in unexpected format: $other")
        }
      }
    }
  }

  case class GeoRadiusByMember(key: String, member: String, radius: Double, radiusUnit: GeoDistUnit) extends Request[List[String]](
    GeoRadiusByMember, key, member, radius, radiusUnit.toString
  ) with Key {
    override def decode: Decoder[List[String]] = {
      case a: ArrayResponse => a.parsed[String, List] {
        case b: BulkStringResponse => b.flattened[String]
      }
    }
  }

  case class GeoRadiusByMemberComplex(key: String, member: String, radius: Double, radiusUnit: GeoDistUnit,
                                      sort: Option[GeoSortOrder], count: Option[Int]) extends Request[List[RadiusResult]](
    GeoRadiusByMember, paramsGeoRadiusComplex(key, List[Any](member, radius, radiusUnit), sort, count): _*
  ) with Key {
    override def decode: Decoder[List[RadiusResult]] = {
      case a: ArrayResponse => a.parsed[RadiusResult, List] {
        case b: ArrayResponse => b.parsed[List[String], List] {
          case c: BulkStringResponse => List(c.flattened[String])
          case c: ArrayResponse => c.parsed[String, List] {
            case d: BulkStringResponse => d.flattened[String]
          }
        }.flatten match {
          case name :: distance :: longS :: latS :: _ => RadiusResult(name, distance.toDouble, longS.toDouble, latS.toDouble)
          case other => throw RedisErrorResponseException(s"Response from Redis in unexpected format: $other")
        }
      }
    }
  }

  private def paramsGeoRadiusComplex(key: String, args: List[Any], sort: Option[GeoSortOrder], count: Option[Int]): List[Any] = {
    val countParam = count.map(List("COUNT", _)).getOrElse(List())
    val sortParam = sort.toList
    List[Any](key) ::: args ::: List("WITHCOORD", "WITHDIST") ::: countParam ::: sortParam
  }

}

object GeoAddEntries {
  case class GeoAddEntry(long: Double, lat: Double, name: String) {
    def args: List[Any] = List(long, lat, name)
  }
}


case class RadiusResult(name: String, dist: Double, longitude: Double, latitude: Double)

object GeoSortOrder {
  sealed trait GeoSortOrder
  case object ASC extends GeoSortOrder
  case object DESC extends GeoSortOrder
}

object GeoDistUnit {
  sealed trait GeoDistUnit
  case object m extends GeoDistUnit   // meters
  case object km extends GeoDistUnit  // kilometers
  case object mi extends GeoDistUnit  // miles
  case object ft extends GeoDistUnit  // feet
}