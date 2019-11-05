Scredis provides two methods for executing transactions, namely `inTransaction` and `withTransaction`. They only differ in what they return.

```scala
// Returns a Vector containing the result of each queued command
def inTransaction(build: TransactionBuilder => Any): Future[Vector[Try[Any]]]
// Returns whatever the build function returns
def withTransaction[A](build: TransactionBuilder => A): A
```

> Note that the `TransactionBuilder` returns a `Future` for every queued command. Every command part of a transaction will be completed with its respective result once the transaction is executed.

### Examples

```scala
import scredis._
import scredis.exceptions.RedisTransactionAbortedException
import scala.util.{ Success, Failure }

// Creates a Redis instance with default configuration (see reference.conf)
val redis = Redis()

// Import the intenral ActorSystem's dispatcher (execution context) to register callbacks
import redis.dispatcher

// Execute a transaction and returns all the results of the queued commands in a Vector
redis.inTransaction { t =>
  t.set("foo", "bar")
  t.get("foo")
}.onComplete {
  case Success(results) => // Vector(Success(true), Success(Some("bar")))
  case Failure(RedisTransactionAbortedException) => // transaction was aborted due to watched keys
  case Failure(e) => // an error occurred while executing the transaction
}

// Executes a transaction and returns whatever the block returns
redis.withTransaction { t =>
  t.set("foo", "bar")
  t.get("foo")
}.onComplete {
  case Success(valueOpt) => // Some("bar")
  case Failure(RedisTransactionAbortedException) => // transaction was aborted due to watched keys
  case Failure(e) => // an error occurred while executing the transaction
}
```