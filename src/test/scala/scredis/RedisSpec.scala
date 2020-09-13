package scredis

import org.scalatest._
import org.scalatest.concurrent._
import scredis.exceptions._
import scredis.tags._
import scredis.util.TestUtils._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import scredis.protocol.AuthConfig

class RedisSpec extends AnyWordSpec
  with GivenWhenThen
  with BeforeAndAfterAll
  with Matchers
  with ScalaFutures {

  private val redisPassword = "foobar"

  private val redis1 = Redis(port = 6380)
  private val redis2 = Redis(port = 6380)
  private val redis3 = Redis(port = 6380, authOpt = Some(AuthConfig(None, redisPassword)))

  "Auth" when {
    "lazy clients are not initialized" should {
      "authenticate all clients" taggedAs (V100) in {
        redis1.auth(redisPassword).futureValue should be (())
        redis1.subscriber.subscribe("TEST").futureValue should be (1)
        redis1.blocking.blPop(1, "LIST").get should be (empty)
        redis1.ping().futureValue should be ("PONG")
      }
    }
    "lazy clients are initialized" should {
      "authenticate all clients" taggedAs (V100) in {
        a [RedisErrorResponseException] should be thrownBy {
          redis2.subscriber.subscribe("TEST").!
        }
        a [RedisErrorResponseException] should be thrownBy {
          redis2.blocking.blPop(1, "LIST").get
        }
        a [RedisErrorResponseException] should be thrownBy {
          redis2.ping().!
        }
        redis2.auth(redisPassword).futureValue should be (())
        redis2.subscriber.subscribe("TEST").futureValue should be (1)
        redis2.blocking.blPop(1, "LIST").get should be (empty)
        redis2.ping().futureValue should be ("PONG")
      }
    }
  }
  
  "Quit" when {
    "lazy clients are not initialized" should {
      "succeed" taggedAs (V100) in {
        redis3.quit().futureValue should be (())
      }
    }
    "lazy clients are initialized" should {
      "succeed" taggedAs (V100) in {
        redis1.quit().futureValue should be (())
        redis2.quit().futureValue should be (())
      }
    }
  }
  
}
