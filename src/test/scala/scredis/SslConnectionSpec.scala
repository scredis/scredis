package scredis

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Matchers, WordSpec}

class SslConnectionSpec extends WordSpec
    with GivenWhenThen
    with BeforeAndAfterAll
    with Matchers
    with ScalaFutures {

  val password = Some("???")
  val host = "???"
  val port = 0

  val keystore = "store.jks"
  val kpass = "password"
  val sslProps = (keystore, kpass)
  val redis: RedisCluster = RedisCluster.apply(nodes = List(Server(host, port)), password = password, sslSettings = Some(sslProps))

  "Auth" when {
    "lazy clients are not initialized" should {
      "authenticate all clients" in {
        redis.set("I-EXIST", "YES")
        redis.del("I-EXIST").futureValue should be (1)
        redis.del("I-EXIST").futureValue should be (0)
      }
    }
  }
}
