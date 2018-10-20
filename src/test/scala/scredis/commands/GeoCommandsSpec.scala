package scredis.commands

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import scredis.Client
import scredis.exceptions.RedisErrorResponseException
import scredis.protocol.requests.GeoAddEntries.GeoAddEntry
import scredis.protocol.requests.GeoDistUnit
import scredis.protocol.requests.GeoRequests.{GeoAdd, GeoDist, GeoHash, GeoPos}
import scredis.util.TestUtils._

class GeoCommandsSpec extends WordSpec
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  private val client = Client()
  private val SomeValue = "HelloWorld!虫àéç蟲"

  GeoAdd.toString when {
    "Adding geo coordinates" should {
      "return error when no coordinates provided" in {
        a [RedisErrorResponseException] should be thrownBy {
          client.geoAdd("GeoKey1").!
        }
      }
      "return number of unique coordinates added" in {
        client.del("GeoKey2")
        client.geoAdd("GeoKey2", GeoAddEntry(1.11, 2.22, "onetwo"), GeoAddEntry(3.33, 4.00, SomeValue)).futureValue should be(2)
        client.geoAdd("GeoKey2", GeoAddEntry(1.11, 2.22, "onetwo"), GeoAddEntry(5.55, 6.66, "otherValue")).futureValue should be(1)
        client.geoAdd("GeoKey2", GeoAddEntry(7, 8, "onetwo")).futureValue should be(0)
      }

      "throw exception when coordinates are out of range" in {
        a [RedisErrorResponseException] should be thrownBy {
          client.geoAdd("GeoKey11", GeoAddEntry(250, 0, "three")).!
        }
        a [RedisErrorResponseException] should be thrownBy {
          client.geoAdd("GeoKey11", GeoAddEntry(0, 825, "three")).!
        }
        a [RedisErrorResponseException] should be thrownBy {
          client.geoAdd("GeoKey11", GeoAddEntry(-350, 0, "three")).!
        }
        a [RedisErrorResponseException] should be thrownBy {
          client.geoAdd("GeoKey11", GeoAddEntry(-4000, 0, "four1"), GeoAddEntry(0, -4000, "four2")).!
        }
      }
    }
  }

  GeoHash.toString when {
    "fetching geohash" should {
      "return empty list when no points in request" in {
        client.del("GeoKey3")
        client.geoHash("GeoKey3").futureValue should be(List())
      }

      "return empty list when key not valid" in {
        client.geoHash("INVALID_KEY").futureValue should be(List())
      }

      "return geohash for present elements" in {
        client.geoAdd("GeoKey3", GeoAddEntry(1.11, 2.22, "onetwo"), GeoAddEntry(3.33, 4.00, SomeValue)).futureValue should be(2)
        // hard coded values, might change heavily dependant on Redis.
        client.geoHash("GeoKey3", "onetwo", SomeValue).futureValue should be(List(Some("s02u9k0k2v0"), Some("s0dqgb0vdt0")))
      }

      "return None for not present locations" in {
        client.geoAdd("GeoKey3", GeoAddEntry(1.11, 2.22, "haszz")).!
        client.geoHash("GeoKey3", "haszz", "unknownloc", "haszz").futureValue should be(List(Some("s02u9k0k2v0"), None, Some("s02u9k0k2v0")))
      }
    }
  }

  GeoPos.toString when {
    "command" should {
      "return empty list when invalid key" in {
        client.geoPos("INVALID_KEY").futureValue should be(List())
        client.geoPos("INVALID_KEY", "M1", "M2").futureValue should be(List(None, None))
      }

      "get data for added entry" in {
        client.geoAdd("Gpos_1", GeoAddEntry(1, 1, "P1"), GeoAddEntry(2, 2, "P2")).!
        client.geoPos("Gpos_1", "P2", "P1").futureValue should be(List(Some((2.0000025629997253,2.000000185646549)), Some((0.9999999403953552,0.9999994591429768))))
        client.geoPos("Gpos_1", "P2", "P3").futureValue should be(List(Some((2.0000025629997253,2.000000185646549)), None))
      }
    }
  }

  GeoDist.toString when {
    "command" should {
      "return None when one of points not in set" in {
        client.geoDist("DistKey1", "M1", "M2").futureValue should be(None)
      }

      "return distance between points" in {
        client.geoAdd("DistKey2", GeoAddEntry(1, 1, "P1"), GeoAddEntry(2, 2, "P2"), GeoAddEntry(3, 3, "P3")).!
        client.geoDist("DistKey2", "P1", "P2").futureValue should be(Some(157270.0561))
      }

      "work with explicit units" in {
        client.geoAdd("DistKey3", GeoAddEntry(1, 1, "P1"), GeoAddEntry(2, 2, "P2"), GeoAddEntry(3, 3, "P3")).!
        val d1 = client.geoDist("DistKey3", "P2", "P3", unit = Some(GeoDistUnit.km)).!.get
        val d2 = client.geoDist("DistKey3", "P2", "P3", unit = Some(GeoDistUnit.m)).!.get
        assert(d1 * 1000 === d2 +- 1)
      }

      "work for all units" in {
        client.geoAdd("DistKey4", GeoAddEntry(1, 1, "P1"), GeoAddEntry(2, 2, "P2")).!
        for { unit <- List(GeoDistUnit.km, GeoDistUnit.m, GeoDistUnit.ft, GeoDistUnit.mi) }
          yield client.geoDist("DistKey4", "P1", "P2", Some(unit)).!
      }

    }
  }
}
