This page describes how to do Pub/Sub with scredis.

### Publishing
To publish messages, you can use the [[Redis|Redis]] instance or the regular non-blocking [[Client|Client]].

```scala
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
```

### Subscribing
To subscribe to messages and events, you must use a `SubscriberClient`. You can either create one or use the lazily initialized one present in a `Redis` instance.

The `subscribe` and `pSubscribe` methods require a `scredis.Subscription` which simply denotes a partial function of `scredis.PubSubMessage` to `Any`.

```scala
type Subscription = PartialFunction[PubSubMessage, Any]
```

The complete list of `scredis.PubSubMessage` can be found [here](http://scredis.github.io/scredis/api/snapshot/#scredis.package$$PubSubMessage$).

```scala
import scredis._

// Creates a Redis instance with default configuration (see reference.conf)
val redis = Redis()

// Subscribe to 'channel1' and 'channel2'
redis.subscriber.subscribe("channel1", "channel2") {
  case message @ PubSubMessage.Message(channel, messageBytes) => {
    val messageString = message.readAs[String]()
    // handle received message
  }
  case PubSubMessage.Subscribe(channel, channelsCount) => // client subscribed to channel
  case PubSubMessage.Unsubscribe(channel, channelsCount) => // client unsubscribed from channel
  case PubSubMessage.Error(exception) => {
    // an error occurred, e.g. NOAUTH when trying to subscribe without being authenticated
  }
}
```

As a convenient feature, scredis also tracks `Subscribe`, `PSubscribe`, `Unsubscribe` and `PUnsubscribe` messages in order to detect when a command is completed. This allows `SubscriberClient` commands to also return a `Future` as any other commands.

```scala
import scredis._
import scala.util.{ Success, Failure }

// Creates a Redis instance with default configuration (see reference.conf)
val redis = Redis()

// Import the intenral ActorSystem's dispatcher (execution context) to register callbacks
import redis.dispatcher

// Subscribe to 'channel1' and 'channel2'
redis.subscriber.subscribe("channel1", "channel2") {
  case message @ PubSubMessage.Message(channel, messageBytes) => {
    val messageString = message.readAs[String]()
    // handle received message
  }
}.onComplete {
  case Success(channelsCount) => // the client successfully subscribed to both channels
  case Failure(e) => {
    // an error occurred while subscribing to the channels, e.g. NOAUTH when trying to subscribe
    // without being authenticated
  }
}
```
