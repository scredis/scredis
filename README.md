[ ![snapshot](https://api.bintray.com/packages/scredis/maven/scredis/images/download.svg) ](https://bintray.com/scredis/maven/scredis/_latestVersion)
[![Build Status](https://travis-ci.org/scredis/scredis.svg?branch=master)](https://travis-ci.org/scredis/scredis)

# scredis

Scredis is a reactive, non-blocking and ultra-fast Scala [Redis](http://redis.io) client built on top of Akka IO. 
It has been (and still is) extensively used in production at Livestream.

* [Documentation](https://scredis.github.io/scredis/)
* [Scaladoc](http://scredis.github.io/scredis/api/scredis/)

## Features
* Supports all Redis commands up to v3.0.0
* Built on top of Akka non-blocking IO
* Super fast, see [Benchmarks](#benchmarks) section below
* Automatic reconnection
* Automatic pipelining
* Transactions
* Pub/Sub
  * Subscribe selectively with partial functions
  * Tracked Subscribe and Unsubscribe commands (they return a Future as any other commands)
  * Automatically resubscribes to previously subscribed channels/patterns upon reconnection
* Cluster support via `RedisCluster`
* Customizable serialization and deserialization of command inputs and outputs
* Fully configurable
  * Akka dispatchers
  * Pipelined write batch size
  * Receive timeout
  * TCP buffer size hints
  * Request encoding buffer pool
  * Concurrent requests cap (bounded memory consumption)

## Getting started

### Binaries

Scredis 2.2.5+ is compatible with Scala 2.11, 2.12 and 2.13.
Binary releases are hosted on the Sonatype Central Repository.

```scala
libraryDependencies += "com.github.scredis" %% "scredis" % "2.3.6"
```

Snapshots / development versions are hosted on a separate bintray repository.

```scala
resolvers += Resolver.bintrayRepo("scredis","maven")

libraryDependencies += "com.github.scredis" %% "scredis" % "<version>"
```

### Quick example
```scala
import scredis._
import scala.util.{ Success, Failure }

// Creates a Redis instance with default configuration.
// See reference.conf for the complete list of configurable parameters.
val redis = Redis()

// Import internal ActorSystem's dispatcher (execution context) to register callbacks
import redis.dispatcher

// Executing a non-blocking command and registering callbacks on the returned Future
redis.hGetAll("my-hash") onComplete {
  case Success(content) => println(content)
  case Failure(e) => e.printStackTrace()
}

// Executes a blocking command using the internal, lazily initialized BlockingClient
redis.blocking.blPop(0, "queue")

// Shutdown all initialized internal clients along with the ActorSystem
redis.quit()
```

```scala
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

// Subscribes to a Pub/Sub channel using the internal, lazily initialized SubscriberClient
redis.subscriber.subscribe("My Channel")

// Later unsubscribe from channel
redis.subscriber.unsubscribe("My Channel")

// Shutdown all initialized internal clients along with the ActorSystem
redis.quit()
```

## Performance benchmark

The following benchmarks have been performed using [ScalaMeter](http://scalameter.github.io/) with the 
`SeparateJvmsExecutor`, configured with `Warmer.Default`, `Measurer.Default` and `Aggregator.average`.
The source code can be found [here](src/bench/scala/scredis/ClientBenchmark.scala).



### Running benchmark tests

Execute `./start-redis.sh` script to launch redis-server which will be used for performance testing. 
Tests are executed against locally running redis-server instance.

You can simply run tests from intellij or execute them from console with command:
 
`sbt "bench:testOnly scredis.ClientBenchmark"`

When you want to update test results please remember to do following steps:
- Check scala version / scredis client version / scredis-server version and update if needed.
- Replace current results with output of a command from above

### Results

```
# scala version: 2.13.1
# scredis version: 2.3.4

> java -version
java version "1.7.0_45"
Java(TM) SE Runtime Environment (build 1.7.0_45-b18)
Java HotSpot(TM) 64-Bit Server VM (build 24.45-b08, mixed mode)

> redis-server --version
Redis server v=5.0.7 sha=00000000:0 malloc=jemalloc-5.2.1 bits=64 build=49eb440ab0786b6a


[info] :::Summary of regression test results - Accepter():::
[info] Test group: Client.PING
[info] - Client.PING.Test-0 measurements:
[info]   - at size -> 100000: passed
[info]     (mean = 194.73 ms, ci = <41.74 ms, 347.73 ms>, significance = 1.0E-10)
[info]   - at size -> 200000: passed
[info]     (mean = 323.44 ms, ci = <159.08 ms, 487.81 ms>, significance = 1.0E-10)
[info]   - at size -> 300000: passed
[info]     (mean = 421.09 ms, ci = <352.69 ms, 489.48 ms>, significance = 1.0E-10)
[info] Test group: Client.GET
[info] - Client.GET.Test-1 measurements:
[info]   - at size -> 100000: passed
[info]     (mean = 312.43 ms, ci = <159.13 ms, 465.73 ms>, significance = 1.0E-10)
[info]   - at size -> 200000: passed
[info]     (mean = 404.21 ms, ci = <304.02 ms, 504.41 ms>, significance = 1.0E-10)
[info]   - at size -> 300000: passed
[info]     (mean = 793.53 ms, ci = <544.09 ms, 1042.97 ms>, significance = 1.0E-10)
[info] Test group: Client.SET
[info] - Client.SET.Test-2 measurements:
[info]   - at size -> 100000: passed
[info]     (mean = 272.35 ms, ci = <134.87 ms, 409.83 ms>, significance = 1.0E-10)
[info]   - at size -> 200000: passed
[info]     (mean = 471.69 ms, ci = <303.79 ms, 639.59 ms>, significance = 1.0E-10)
[info]   - at size -> 300000: passed
[info]     (mean = 615.98 ms, ci = <412.76 ms, 819.19 ms>, significance = 1.0E-10)
[info] Summary: 3 tests passed, 0 tests failed.

```

## Running the tests locally

The tests require two Redis instances to be running with some specific configuration options set.
They can be started with the `start-redis-test-instances.sh` script. Stopping is done with script `stop-redis.sh`

Some tests require redis-cluster, redis-cluster can be started with `run-redis-cluster.sh`.
6 redis instances will be started on ports 7000-7005 without authorization.


## Releasing
Scredis uses [sbt-dynver](https://github.com/dwijnand/sbt-dynver) plugin to automatically manage versioning.

Releases are only performed from a master branch.

If current commit on master branch is not tagged then a previous tag is used as base version with added current date (snapshot version).

If current commit on master is tagged then a version is the same as tag and this version is stable.

Build on travis checks what is current branch and if it is master it executes publish step.

Publish step uses sbt plugin [sbt-bintray](https://github.com/sbt/sbt-bintray) to publish artifacts to bintray repository.

Bintray repository [https://bintray.com/scredis/maven/scredis](https://bintray.com/scredis/maven/scredis).

Released version is automatically populated to jcenter bintray [https://search.maven.org/search?q=g:com.github.scredis](https://search.maven.org/search?q=g:com.github.scredis)

Manual step is needed to populate to [mvnrepository](https://mvnrepository.com/artifact/com.github.scredis/scredis)

Maintainer performing stable release should:
* switch to master branch and update README with tag `X.Y.Z`
* commit changes `git add README.md && git commit -m "Release version vX.Y.Z`
* `git tag -a "vX.Y.Z" -m "Release version vX.Y.Z"` # tag current commit with given tag
* execute `./gen-doc.sh` and `git add -A && git commit --amend --no-edit` to append changes to last commit
* `git push origin vX.Y.Z && git push` # tags are not automatically pushed by `git push`
* go to [bintray](https://bintray.com/scredis/maven/scredis#central) and click 'Sync' to push released version to mvnrepository.

## Documentation

Can be found  in [https://scredis.github.io/scredis/](https://scredis.github.io/scredis/)

Documentation is generated using sbt plugin [paradox](https://developer.lightbend.com/docs/paradox/current/index.html).

All files found in `src/main/paradox` are converted to proper html files and placed into `target/paradox/site/main`.

To generate documentation one must execute `sbt paradox`.

Github is configured to serve scredis page from files found in `/docs` that's why there is a need to copy generated
documentation to mentioned directory to be properly served by github.

To ease this process there is a script called `gen-doc.sh` that generates and moves documentation into proper place.

When locally working on documentation this script can be used and documentation can be found in `./docs/index.html`.

All snipets should be added to `Examples.scala` in test scope and included from documentation to
ensure snipets always compile with latest version.


## License

Copyright (c) 2013 Livestream LLC. All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License. See accompanying LICENSE file.
