The `Redis` class provides the full feature set of scredis in one place. It is a non-blocking [[Client|Client]] which also holds a lazily initialized [[BlockingClient|BlockingClient]] and a lazily initialized [[SubscriberClient|Pub-Sub#subscribing]]. In other words, a `Redis` instance can contain from one to three active clients/connections.

### Initialization
A `Redis` instance can be initialized similarly to a regular [[Client|Client#initialization]] except that it does not require an `ActorSystem` as it automatically creates one.

### Full usage example

```scala
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
redis.subscriber.subscribe("channel") {
  case message @ PubSubMessage.Message(channel, messageBytes) => {
    val messageString = message.readAs[String]()
    // do something with the message
  }
}
```

### Global commands
`Redis` overrides four commands in order to keep all its internal clients in sync, namely `auth`, `select`, `clientSetName` and `quit`.

#### Auth
Calling `redis.auth(password)` will authenticate the non-blocking client as well as all initialized clients. If a client has not been used yet, it will save the password so that once it gets initialized, it will automatically authenticate with the latest set password.

#### Select
The same holds for `redis.select(database)`.

#### ClientSetName
The same holds for `redis.clientSetName(name)`.

#### Quit
Upon executing `redis.quit()`, the non-blocking client as well as all initialized clients will be sending a *QUIT* command to the Redis server.