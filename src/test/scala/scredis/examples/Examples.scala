package scredis.examples

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
    passwordOpt = Some("foobar")
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
