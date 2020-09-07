package scredis

import scredis.util.TestUtils._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class RedisClusterSpec extends AnyWordSpec
  with Matchers
  with ScalaFutures
  with BeforeAndAfterAll
  with ScalaCheckDrivenPropertyChecks {

  val keys = org.scalacheck.Arbitrary.arbString.arbitrary

  // we assume there is a local cluster started on ports 7000 - 7005
  // see testing.md
  lazy val cluster: RedisCluster = RedisCluster(Server("localhost",7000))

  val badSeed1 = Server("localhost",7777)
  val badSeed2 = Server("localhost",2302)
  val badSeeds = List(badSeed1, badSeed2, Server("localhost",7003))

  "connection to cluster" should {
    "work for a single valid seed node" in {
      val info = cluster.clusterInfo().futureValue

      info("cluster_state") should be ("ok")
      info("cluster_known_nodes").toInt should be (6) // 6 total nodes
      info("cluster_size").toInt should be (3) // 3 master nodes
    }

    "work when some of the seed nodes are offline" in {
      val badServers = RedisCluster(badSeeds)

      val info = badServers.clusterInfo().futureValue
      info("cluster_state") should be ("ok")
      info("cluster_known_nodes").toInt should be (6) // 6 total nodes
      info("cluster_size").toInt should be (3) // 3 master nodes
    }
  }

  "writes to cluster" should {
    "be readable" in {
      forAll { (key:String, value: String) =>
        whenever (value.nonEmpty) {
          val res = for {
            _ <- cluster.set(key, value)
            g <- cluster.get(key)
          } yield g.get
          res.futureValue should be(value)
        }
      }
    }

    "be idempotent" in {
      forAll { (key:String, value: String) =>
        whenever (value.nonEmpty) {
          val res = for {
            _ <- cluster.set(key, value)
            g1 <- cluster.get(key)
            _ <- cluster.set(key, value)
            g2 <- cluster.get(key)
          } yield (g1.get,g2.get)
          res.futureValue should be(value,value)
        }
      }
    }
  }

  // TODO basic test for each supported / unsupported command

  override def afterAll(): Unit = {
    cluster.quit().!
  }

}
