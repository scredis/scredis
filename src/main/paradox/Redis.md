## Redis

The `Redis` class provides the full feature set of scredis in one place. It is a non-blocking [[Client|Client]] which also holds a lazily initialized [[BlockingClient|BlockingClient]] and a lazily initialized [[SubscriberClient|Pub-Sub#subscribing]]. In other words, a `Redis` instance can contain from one to three active clients/connections.

### Initialization
A `Redis` instance can be initialized similarly to a regular @ref:[Client](Client.md#initialization) except that it does not require an `ActorSystem` as it automatically creates one.

### Full usage example

@@snip [Hello.scala](/src/test/scala/scredis/examples/Examples.scala) { #redis_example }

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