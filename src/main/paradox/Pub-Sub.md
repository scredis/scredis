## Pub-Sub

This page describes how to do Pub/Sub with scredis.

### Publishing
To publish messages, you can use the @ref:[Redis](Redis.md) instance or the regular non-blocking @ref:[Client](Client.md).

@@snip [Hello.scala](/src/test/scala/scredis/examples/Examples.scala) { #publish_example }

### Subscribing
To subscribe to messages and events, you must use a `SubscriberClient`. You can either create one or use the lazily initialized one present in a `Redis` instance.

The `SubscriberClient` requires a `scredis.Subscription` which simply denotes a function of `scredis.PubSubMessage` to `Any`.
When you use `SubscriberClient` from Redis instance then a `scredis.Subscription` handler is taken from `Redis` instance.

```scala
type Subscription = Function[scredis.PubSubMessage, Any]
```

The complete list of `scredis.PubSubMessage` can be found [here](http://scredis.github.io/scredis/api/snapshot/#scredis.package$$PubSubMessage$).

@@snip [Hello.scala](/src/test/scala/scredis/examples/Examples.scala) { #subscribe_example }

