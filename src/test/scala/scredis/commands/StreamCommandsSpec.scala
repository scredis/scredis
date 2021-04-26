package scredis.commands

import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scredis._
import scredis.exceptions._
import scredis.protocol.requests.HashRequests._
import scredis.protocol.requests.StreamRequests.{XAdd, XRange}
import scredis.tags._
import scredis.util.TestUtils._

import scala.collection.mutable.ListBuffer

class StreamCommandsSpec extends AnyWordSpec with GivenWhenThen with BeforeAndAfterAll with Matchers with ScalaFutures {

  private val client = Client()

  XAdd.toString when {
    "the key does not exist" should {
      "create new stream" in {
        val key = "xlen-stream"
        client.xAdd(key, Map("field" -> "fieldValue")).futureValue should not be empty
        client.xLen(key).futureValue shouldBe 1
      }
    }
  }

  XRange.toString when {
    "stream has elements" should {
      "return all elements" in {
        val key = "xrange-stream"
        client
          .xAdd(
            key,
            Map("field1" -> "field1Value1"),
          )
          .futureValue
        client
          .xAdd(
            key,
            Map(
              "field1" -> "field1Value2",
              "field2" -> "field2Value",
            ),
          )
          .futureValue
        val result = client.xRange(key, "-", "+", None).futureValue
        result should have size 2
        result.head._2 shouldBe Map("field1" -> "field1Value1")
      }
    }
  }

  override def afterAll(): Unit = {
    client.flushDB().!
    client.quit().!
  }

}
