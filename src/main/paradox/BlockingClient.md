## Blocking Client

The `BlockingClient` class represents a blocking Redis client. The major practical difference with blocking requests is that they return `scala.util.Try` instead of `scala.concurrent.Future`.

> Note: the `BlockingClient` can only be used to issue blocking requests such as *BRPOP*.

- To send regular requests, use @ref:[Client](Client.md)
- To subscribe to channels/patterns, use @ref:[SubscriberClient](Pub-Sub.md#subscribing)

### Initialization
A `BlockingClient` can be initialized similarly to a regular @ref:[Client](Client.md#initialization) with the exception that `receiveTimeout` cannot be set.

### Full usage example

@@snip [Hello.scala](/src/test/scala/scredis/examples/Examples.scala) { #blocking_client_example }
