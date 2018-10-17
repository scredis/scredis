package scredis.protocol.requests

import scredis.protocol._
import scredis.protocol.requests.GeoAddEntries.GeoAddEntry

object GeoRequests {
  import scredis.serialization.Implicits.stringReader
  import scredis.serialization.Implicits.doubleReader

  object GeoAdd extends Command("GEOADD") with WriteCommand
  object GeoHash extends Command("GEOHASH")
  object GeoPos extends Command("GEOPOS")


  case class GeoAdd(key: String, fields: List[GeoAddEntry]) extends Request[Long](
    GeoAdd, key :: fields.flatMap(_.args) :_*
  ) with Key {
    override def decode: Decoder[Long] = {
      case IntegerResponse(value) => value
    }
  }

  case class GeoHash(key: String, locations: List[String]) extends Request[List[String]](
    GeoHash, key :: locations :_*
  ) with Key {
    override def decode: Decoder[List[String]] = {
      case a: ArrayResponse => a.parsed[String, List] {
        case b: BulkStringResponse => b.flattened[String]
      }
    }
  }

  case class GeoPos(key: String, members: List[String]) extends Request[List[(Double, Double)]](
    GeoPos, key :: members :_*
  ) with Key {
    override def decode: Decoder[List[(Double, Double)]] = {
      case a: ArrayResponse => a.parsedAsPairs[Double, Double, List] {
        case b: BulkStringResponse => b.flattened[Double]
      } {
        case b: BulkStringResponse => b.flattened[Double]
      }
    }
  }
}

object GeoAddEntries {
  case class GeoAddEntry(long: Double, lat: Double, name: String) {
    def args: List[Any] = List(long, lat, name)
  }
}

