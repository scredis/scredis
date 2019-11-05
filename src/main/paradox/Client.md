## Client

The `Client` class represents a non-blocking Redis client.

> Note: the default `Client` cannot be used to issue blocking requests or to subscribe to channels/patterns.

- To send blocking requests, use @ref:[BlockingClient](BlockingClient.md)
- To subscribe to channels/patterns, use @ref:[SubscriberClient](Pub-Sub.md#subscribing)

### Initialization

@@snip [Hello.scala](/src/test/scala/scredis/examples/Examples.scala) { #client_initialization_example }

### Full usage example

@@snip [Hello.scala](/src/test/scala/scredis/examples/Examples.scala) { #client_example }

