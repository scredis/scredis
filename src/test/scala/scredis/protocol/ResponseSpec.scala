package scredis.protocol

import java.nio.ByteBuffer

import org.scalatest._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import scredis.{ClusterSlotRange, ClusterSlotRangeNodeInfo, Server}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ResponseSpec extends AnyWordSpec with ScalaCheckDrivenPropertyChecks with Inside
  with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers {

  "ArrayResponse" when {
    "parsedAsClusterSlotsResponse" should {
      "correctly decode an example" in {
        val bytes = "*3\r\n*4\r\n:5461\r\n:10922\r\n*2\r\n$9\r\n127.0.0.1\r\n:7002\r\n*2\r\n$9\r\n127.0.0.1\r\n:7004\r\n*4\r\n:0\r\n:5460\r\n*2\r\n$9\r\n127.0.0.1\r\n:7000\r\n*2\r\n$9\r\n127.0.0.1\r\n:7003\r\n*4\r\n:10923\r\n:16383\r\n*2\r\n$9\r\n127.0.0.1\r\n:7001\r\n*2\r\n$9\r\n127.0.0.1\r\n:7005\r\n".getBytes(Protocol.Encoding)
        val response = Protocol.decode(ByteBuffer.wrap(bytes)).asInstanceOf[ArrayResponse]
        val parsed = response.parsedAsClusterSlotsResponse[Vector]
        println(parsed)
        parsed should have size (3)
        parsed.head should be (ClusterSlotRange((5461,10922),
                               ClusterSlotRangeNodeInfo(Server("127.0.0.1",7002),None),
                               List(ClusterSlotRangeNodeInfo(Server("127.0.0.1",7004),None))))
      }

      "correctly decode an Redis v7 example" in {
        val bytes = "*3\r\n*4\r\n:5461\r\n:10922\r\n*4\r\n$9\r\n127.0.0.1\r\n:7002\r\n$40\r\nd5ff7bef54dd479458b5297edb6869e471a6c451\r\n*2\r\n$8\r\nhostname\r\n$0\r\n\r\n*4\r\n$9\r\n127.0.0.1\r\n:7004\r\n$40\r\n27b402faa19a1d3f618f9a1b7ef58fef4124d0ab\r\n*2\r\n$8\r\nhostname\r\n$0\r\n\r\n*4\r\n:0\r\n:5460\r\n*4\r\n$9\r\n127.0.0.1\r\n:7000\r\n$40\r\n9fb299bbffbd2896efec2322ab3faf1451107d4c\r\n*2\r\n$8\r\nhostname\r\n$0\r\n\r\n*4\r\n$9\r\n127.0.0.1\r\n:7003\r\n$40\r\n468e2ccef99fdbbe945e0dbc90ecd048666b6cfc\r\n*2\r\n$8\r\nhostname\r\n$0\r\n\r\n*4\r\n:10923\r\n:16383\r\n*4\r\n$9\r\n127.0.0.1\r\n:7001\r\n$40\r\n468e2ccef99fdbbe945e0dbc90ecd048666baaaa\r\n*2\r\n$8\r\nhostname\r\n$0\r\n\r\n*4\r\n$9\r\n127.0.0.1\r\n:7005\r\n$40\r\n468e2ccef99fdbbe945e0dbc90ecd048666bbbbb\r\n*2\r\n$8\r\nhostname\r\n$0\r\n\r\n".getBytes(Protocol.Encoding)
        val response = Protocol.decode(ByteBuffer.wrap(bytes)).asInstanceOf[ArrayResponse]
        val parsed = response.parsedAsClusterSlotsResponse[Vector]
        println(parsed)
        parsed should have size (3)
        parsed.head should be(ClusterSlotRange((5461, 10922),
          ClusterSlotRangeNodeInfo(Server("127.0.0.1", 7002), Some("d5ff7bef54dd479458b5297edb6869e471a6c451")),
          List(ClusterSlotRangeNodeInfo(Server("127.0.0.1", 7004), Some("27b402faa19a1d3f618f9a1b7ef58fef4124d0ab")))))
      }
    }
  }

}
