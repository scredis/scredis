package scredis.examples

import scredis.protocol.AuthConfig

import scala.concurrent.Future

object BlockingClientExample {
  // #blocking_client_example

  import scredis._
  import akka.actor.ActorSystem
  import scala.util.{Success, Failure}

  // Defines an actor system to use with the blocking client
  implicit val system: ActorSystem = ActorSystem("my-actor-system")

  // Creates a blocking client with default configuration (see reference.conf)
  val client = BlockingClient()

  // Send a blocking request and match upon the result
  client.brPop(timeoutSeconds = 5, "list") match {
    case Success(Some((key, value))) => // do something with the popped value
    case Success(None) => // there was no value to be popped
    case Failure(e) => // an error occurred while processing the request
  }
  // #blocking_client_example

}

object ClientInitializationExample {
  // #client_initialization_example
  import scredis._
  import com.typesafe.config.ConfigFactory
  import akka.actor.ActorSystem

  // Defines an actor system to use with the client
  implicit val system = ActorSystem("my-actor-system")

  // Creates a non-blocking client with default configuration (see reference.conf)
  val client = Client()

  // Creates a non-blocking client with provided parameters, falling back to default
  // configuration for non-provided parameters.
  val clientWithParameters = Client(
    host = "192.168.1.1",
    port = 6380,
    authOpt = Some(AuthConfig(username = Some("userX"), password = "foobar"))
  )

  // Explicitly load a config instance
  val config = ConfigFactory.load("scredis.conf").getConfig("scredis")
  // Creates a non-blocking client with specified config, falling back to default configuration
  val clientWithExplicitConfig = Client(config)

  // Creates a non-blocking client with specified config file name and path,
  // falling back to default configuration
  val clientWithExplicitConfigFileNameAndPath = Client("scredis.conf", "scredis")
  // #client_initialization_example
}

object ClientExample {
  // #client_example
  import scredis._
  import akka.actor.ActorSystem
  import scala.util.{ Success, Failure }

  // Defines an actor system to use with the client
  implicit val system = ActorSystem("my-actor-system")

  // Creates a non-blocking client with default configuration (see reference.conf)
  val client = Client()
  // Import the ActorSystem's dispatcher (execution context) to be able to register callbacks
  import client.dispatcher

  // Send a GET request and register a callback
  client.get("foo").onComplete {
    case Success(Some(value)) => // do something with the value
    case Success(None) => // there is no value associated to key 'foo'
    case Failure(e) => // an error occurred while processing the request
  }
  // #client_example
}

object PublishExample {
  // #publish_example
  import scredis._
  import scala.util.{ Success, Failure }

  // Creates a Redis instance with default configuration (see reference.conf)
  val redis = Redis()

  // Import the intenral ActorSystem's dispatcher (execution context) to register callbacks
  import redis.dispatcher

  // Send a GET request and register a callback
  redis.publish("channel", "foo").onComplete {
    case Success(count) => // the message was published to 'count' client
    case Failure(e) => // an error occurred while publishing the message 'foo' to channel 'channel'
  }
  // #publish_example
}

object SubscribeExample {
  // #subscribe_example
  import scredis.PubSubMessage
  private val subscriptionHandler: Function[PubSubMessage, Unit] = {
    case m: PubSubMessage.Subscribe => println(s"Subscribed to channel ${m.channel}")
    case m: PubSubMessage.Message => println(s"Received message for channel ${m.channel} with data ${m.readAs[String]()}")
    case m: PubSubMessage.Unsubscribe => println(s"Unsubscribed from channel ${m.channelOpt}")
    case m: PubSubMessage.PSubscribe => println(s"Subscribed to channels matching pattern ${m.pattern}")
    case m: PubSubMessage.PMessage => println(s"Received message for pattern ${m.pattern} on channel ${m.channel} with data ${m.readAs[String]()}")
    case m: PubSubMessage.PUnsubscribe => println(s"Unsubscribed from pattern matching ${m.patternOpt}")
    case e: PubSubMessage.Error => println(s"Scredis received error $e")
  }

  // Creates a Redis instance with default configuration.
  // Provide custom function handling pub/sub related events
  val redis = scredis.Redis(subscription = subscriptionHandler)

  // Subscribe to 'channel1' and 'channel2'
  val subscribeF: Future[Int] = redis.subscriber.subscribe("channel1", "channel2")
  // subscribeF will evaluate to 2
  // #subscribe_example
}

object RedisExample {
  // #redis_example
  import scredis._
  import scala.util.{ Success, Failure }

  // Creates a Redis instance with default configuration (see reference.conf)
  val redis = Redis()

  // Import the intenral ActorSystem's dispatcher (execution context) to register callbacks
  import redis.dispatcher

  // Send a GET request and register a callback
  redis.get("foo").onComplete {
    case Success(Some(value)) => // do something with the value
    case Success(None) => // there is no value associated to key 'foo'
    case Failure(e) => // an error occurred while processing the request
  }

  // Send a blocking request using the internal, lazily initialized BlockingClient and match upon
  // the result
  redis.blocking.brPop(timeoutSeconds = 5, "list") match {
    case Success(Some((key, value))) => // do something with the popped value
    case Success(None) => // there was no value to be popped
    case Failure(e) => // an error occurred while processing the request
  }

  // Subscribe to a channel using the internal, lazily initialized SubscriberClient
  redis.subscriber.subscribe("channel")
  // #redis_example
}
