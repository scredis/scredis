package scredis.commands

import org.scalatest._
import org.scalatest.concurrent._
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Span}
import org.scalatest.wordspec.AnyWordSpec
import scredis._
import scredis.protocol.requests.StreamRequests._
import scredis.protocol.requests.XTrimType
import scredis.util.TestUtils._

import scala.concurrent.duration._
import scala.concurrent.Future


class StreamCommandsSpec extends AnyWordSpec with GivenWhenThen with BeforeAndAfterAll with Matchers with ScalaFutures {

  private val client = Client()
  private val blockingClient = BlockingClient()
  import system.dispatcher

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

  XRevRange.toString when {
    "stream has elements" should {
      "return all elements" in {
        val key = "xrevrange-stream"
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
        val result = client.xRevRange(key, "+", "-", None).futureValue
        result should have size 2
        result.head._2 shouldBe Map(
          "field1" -> "field1Value2",
          "field2" -> "field2Value",
        )
      }
    }
  }

  XTrim.toString when {
    "stream has elements" should {
      "trim the stream" in {
        val key = "xtrim-stream"
        client.xAdd(key, Map("field1" -> "value1")).futureValue
        client.xAdd(key, Map("field1" -> "value2")).futureValue
        client.xAdd(key, Map("field1" -> "value3")).futureValue
        client.xAdd(key, Map("field1" -> "value4")).futureValue
        client.xAdd(key, Map("field1" -> "value5")).futureValue

        client.xTrim(key, XTrimType.MaxLen, 3, exact = true)
        val exactTrimmed = client.xRevRange(key, "+", "-", None).futureValue
        exactTrimmed should have size 3

        client.xTrim(key, XTrimType.MaxLen, 1, exact = false)
        val nearlyExact = client.xRevRange(key, "+", "-", None).futureValue
        nearlyExact.length should be >= 1
      }
    }
  }

  XTrim.toString when {
    "stream has elements" should {
      "return stream elements" in {
        val key = "xread-stream"
        val total = 10
        for(i <- 1 to total) {
          client.xAdd(key, Map("field1" -> s"value$i")).futureValue
        }

        val count = 2
        val r1 = client.xRead(count = count, keys = List(key), ids = List("0")).futureValue
        r1 should have size 1
        r1 should contain key key
        r1.values.head should have size count
        val lastElemId = r1.values.head.last._1

        val r2 = client.xRead(count = total, keys = List(key), ids = List(lastElemId)).futureValue
        r2.values.head should have size (total - count)
      }

      "return stream elements from multiple streams" in {
        val key1 = "xread-stream2"
        val key2 = "xread-stream3"
        val total = 10
        for(i <- 1 to total) {
          client.xAdd(key1, Map("field1" -> s"value$i")).futureValue
          client.xAdd(key2, Map("field1" -> s"value$i")).futureValue
        }

        val count = 2
        val r1 = client.xRead(count = count, keys = List(key1, key2), ids = List("0", "0")).futureValue
        r1 should have size 2
        r1 should contain key key1
        r1 should contain key key2
        r1.values.head should have size count
      }
    }

    "stream no elements" should {
      "block until new elements pushed" in {
        val key = "xread-stream-block"
        client.xAdd(key, Map("field1" -> s"old-value")).futureValue

        Future {
          Thread.sleep(500)
          client.xAdd(key, Map("field1" -> "new-value")).futureValue
        }

        val received = blockingClient.xRead(count = 10, 10.second, List(key), List("$")).get
        received.values.head should have size 1
        received.values.head.head._2.get("field1") shouldBe Some("new-value")
      }
    }
  }


  override implicit def patienceConfig: PatienceConfig = {
    PatienceConfig(timeout = scaled(Span(450, Millis))
      , interval = scaled(Span(15, Millis))
    )
  }

  override def afterAll(): Unit = {
    client.flushDB().!
    client.quit().!
  }

}
