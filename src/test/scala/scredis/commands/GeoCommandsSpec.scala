package scredis.commands

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}
import scredis.Client
import scredis.exceptions.RedisErrorResponseException
import scredis.protocol.requests.GeoAddEntries.GeoAddEntry
import scredis.protocol.requests.GeoRequests.{GeoAdd, GeoHash}
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
      "return number of unique coords added" in {
        client.del("GeoKey2")
        client.geoAdd("GeoKey2", GeoAddEntry(1.11, 2.22, "onetwo"), GeoAddEntry(3.33, 4.00, SomeValue)).futureValue should be(2)
        client.geoAdd("GeoKey2", GeoAddEntry(1.11, 2.22, "onetwo"), GeoAddEntry(5.55, 6.66, "otherValue")).futureValue should be(1)
        client.geoAdd("GeoKey2", GeoAddEntry(7, 8, "onetwo")).futureValue should be(0)
      }
    }
  }

  GeoHash.toString when {
    "blah" should {
      "do sth" in {
        client.del("GeoKey3")
        client.geoAdd("GeoKey3", GeoAddEntry(1.11, 2.22, "onetwo"), GeoAddEntry(3.33, 4.00, SomeValue)).futureValue should be(2)
        client.geoHash("GeoKey3").futureValue should be(List())
        // hard coded values, might change heavily dependant on Redis.
        client.geoHash("GeoKey3", "onetwo", SomeValue).futureValue should be(List("s02u9k0k2v0", "s0dqgb0vdt0"))
      }
    }
  }
}
