## Serialization

Scredis provides full support for customizing serialization and deserialization of relevant command parameters and command results.

### Reading
On relevant commands, you can tell scredis the type of data you expect and it will be deserialized for you. Indeed, selected commands have an extra implicit `Reader` argument, meaning that you can deserialize any type as long as you provide an implicit `Reader` for it.

```scala
def get[R: Reader](key: String): Future[Option[R]]
```

By default, most commands return `String` values. This is because the `scredis` package defines an implicit `UTF8StringReader`.

```scala
implicit val stringReader: Reader[String] = UTF8StringReader
```

Therefore, when writing `import scredis._`, the implicit `stringReader` is imported into the scope.

The library provides a `Reader` for every Scala basic type, namely `Array[Byte]`, `String`, `Boolean`, `Short`, `Int`, `Long`, `Float` and `Double`. All `Reader` objects and implicit values are located in `scredis.serialization` and `scredis.serialization.Implicits`, respectively.

#### Example

```scala
import scredis._
import scala.util.{ Success, Failure }

// Creates a Redis instance with default configuration (see reference.conf)
val redis = Redis()

// Import the intenral ActorSystem's dispatcher (execution context) to register callbacks
import redis.dispatcher

// Import implicit LongReader
import scredis.serialization.Implicits.longReader

redis.get[Long]("foo").onComplete {
  case Success(Some(value: Long)) => // handle long value
  case Success(None) => // there was no value associated to key 'foo'
  case Failure(e) => // an error occurred while executing the command
}
```

### Writing
Similarly, you can define how some command arguments should be serialized. Indeed, selected commands have one or multiple extra implicit `Writer` argument(s), meaning that you can serialize any type as long as you provide an implicit `Writer` for it.

```scala
def append[W: Writer](key: String, value: W): Future[Long]
```

The `scredis` package defines an implicit `Writer` for every base Scala type.

```scala
implicit val bytesWriter: Writer[Array[Byte]] = BytesWriter
implicit val stringWriter: Writer[String] = UTF8StringWriter
implicit val booleanWriter: Writer[Boolean] = BooleanWriter
implicit val shortWriter: Writer[Short] = ShortWriter
implicit val intWriter: Writer[Int] = IntWriter
implicit val longWriter: Writer[Long] = LongWriter
implicit val floatWriter: Writer[Float] = FloatWriter
implicit val doubleWriter: Writer[Double] = DoubleWriter
```

Therefore, when writing `import scredis._`, all of them are imported into the scope and any base type can be serialized.

Additionally, scredis provides an `AnyWriter` class which can serialize any type by first calling the `toString` method on it. It is not imported by default as it would interfere with user defined custom `Writer` objects. You can import it to serialize a collection containing multiple types, e.g. `List[Any]("foo", 5, 3.0, true)`. All `Writer` objects and implicit values are located in `scredis.serialization` and `scredis.serialization.Implicits`, respectively.

### Defining Custom Readers & Writers
You can define your own `Reader` and `Writer` instances by extending the base class. Let's consider the following example in which we want to store and retrieve a `Person`.

```scala
case class Person(name: String, age: Int)
```

Let's define a `Writer` and `Reader` class capable of serializing and deserializing a `Person`. To make it even more fun, let's say we want to encode the `String` values in UTF-16 instead of default UTF-8.

```scala
import scredis.serialization._

implicit object PersonWriter extends Writer[Person] {
  private val utf16StringWriter = new StringWriter("UTF-16")
  
  override def writeImpl(person: Person): Array[Byte] = {
    utf16StringWriter.write(s"${person.name}:${person.age}")
  }
}

implicit object PersonReader extends Reader[Person] {
  val utf16StringReader = new StringReader("UTF-16")
  
  override def readImpl(bytes: Array[Byte]): Person = {
    val split = utf16StringReader.read(bytes).split(":")
    val name = split(0)
    val age = split(1).toInt
    Person(name, age)
  }
}
```

#### Full example

```scala
import scredis._
import scredis.serialization._
import scredis.exceptions._
import scala.util.{ Success, Failure }

// Creates a Redis instance with default configuration (see reference.conf)
val redis = Redis()

// Import the intenral ActorSystem's dispatcher (execution context) to register callbacks
import redis.dispatcher

case class Person(name: String, age: Int)

implicit object PersonWriter extends Writer[Person] {
  private val utf16StringWriter = new StringWriter("UTF-16")
  
  override def writeImpl(person: Person): Array[Byte] = {
    utf16StringWriter.write(s"${person.name}:${person.age}")
  }
}

implicit object PersonReader extends Reader[Person] {
  val utf16StringReader = new StringReader("UTF-16")
  
  override def readImpl(bytes: Array[Byte]): Person = {
    val split = utf16StringReader.read(bytes).split(":")
    val name = split(0)
    val age = split(1).toInt
    Person(name, age)
  }
}

redis.set("key", Person("John Smith", 39)).onComplete {
  case Success(true) => redis.get[Person]("key").onComplete {
    case Success(personOpt) => {
      println(personOpt)
      redis.quit()
    }
    case Failure(e: RedisReaderException) => // could not deserialize result
    case Failure(e) => // any other error occurred
  }
  case Success(false) => // value was not set
  case Failure(e: RedisWriterException) => // could not serialize argument
  case Failure(e) => // any other error occurred
}
```