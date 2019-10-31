package scredis

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging

import scala.jdk.CollectionConverters._
import scala.concurrent.duration._

/**
 * Holds all configurable parameters.
 * 
 * @see reference.conf
 */
class RedisConfig(config: Config = ConfigFactory.load().getConfig("scredis")) {
  def this(configName: String) = this(ConfigFactory.load(configName).getConfig("scredis"))
  def this(configName: String, path: String) = this(ConfigFactory.load(configName).getConfig(path))
  
  private val referenceConfig = ConfigFactory.defaultReference().getConfig("scredis")
  private val mergedConfig = config.withFallback(referenceConfig)
  mergedConfig.checkValid(referenceConfig)
  
  private def optionally[A](key: String)(f: => A)(implicit config: Config): Option[A] = {
    if (config.hasPath(key)) {
      Some(f)
    } else {
      None
    }
  }
  
  private def parseDuration(key: String)(implicit config: Config): Duration = {
    val duration = Duration.create(config.getString(key))
    if (!duration.isFinite || duration.toMillis <= 0) {
      Duration.Inf
    } else {
      duration
    }
  }
  
  private def parseFiniteDuration(key: String)(implicit config: Config): FiniteDuration = {
    val duration = parseDuration(key)
    if (duration.isFinite) {
      FiniteDuration(duration.length, duration.unit)
    } else {
      throw new IllegalArgumentException(s"$key must be finite")
    }
  }

  object Redis {
    private implicit val config = mergedConfig.getConfig("redis")
    val Host = config.getString("host")
    val Port = config.getInt("port")
    val PasswordOpt = optionally("password") {
      config.getString("password")
    }
    val Database = config.getInt("database")
    val NameOpt = optionally("name") {
      config.getString("name")
    }

    val ClusterNodes: List[Server] = config.getStringList("cluster-nodes").asScala.map { node =>
      node.split(':') match {
        case Array(hostPort) => Server(hostPort, Redis.Port)
        case Array(hostStr, portStr) => Server(hostStr, portStr.toInt)
        case _ =>
          throw new IllegalArgumentException(s"Bad cluster node setting: $node. Expecting String in the form host:port")
      }
    }.toList

  }
  
  object IO {
    private implicit val config = mergedConfig.getConfig("io")
    val ConnectTimeout = parseFiniteDuration("connect-timeout")
    val ReceiveTimeoutOpt = optionally("receive-timeout") {
      parseFiniteDuration("receive-timeout")
    }
    
    val MaxWriteBatchSize = config.getInt("max-write-batch-size")
    val TCPSendBufferSizeHint = config.getInt("tcp-send-buffer-size-hint")
    val TCPReceiveBufferSizeHint = config.getInt("tcp-receive-buffer-size-hint")
    
    object Akka {
      private implicit val config = IO.config.getConfig("akka")
      val ActorSystemName = config.getString("actor-system-name")
      val IODispatcherPath = config.getString("io-dispatcher-path")
      val ListenerDispatcherPath = config.getString("listener-dispatcher-path")
      val DecoderDispatcherPath = config.getString("decoder-dispatcher-path")
    }

    object Cluster {
      private implicit val config = IO.config.getConfig("cluster")

      val TryAgainWait = parseFiniteDuration("try-again-wait")
      val ClusterDownWait = parseFiniteDuration("clusterdown-wait")
    }
  }
  
  object Global {
    private implicit val config = mergedConfig.getConfig("global")
    val MaxConcurrentRequestsOpt = optionally("max-concurrent-requests") {
      config.getInt("max-concurrent-requests")
    }
    val FailCommandOnConnecting = config.getBoolean("fail-command-on-connecting")
    object EncodeBufferPool {
      private implicit val config = Global.config.getConfig("encode-buffer-pool")
      val PoolMaxCapacity = config.getInt("pool-max-capacity")
      val BufferMaxSize = config.getInt("buffer-max-size")
    }
  }
  
  // Initialization
  Redis
  IO
  Global
  
}

object RedisConfig {
  def apply() = new RedisConfig()
  def apply(config: Config) = new RedisConfig(config)
  def apply(configName: String) = new RedisConfig(configName)
  def apply(configName: String, path: String) = new RedisConfig(configName, path)
}

object RedisConfigDefaults extends LazyLogging {
  val Config = new RedisConfig()
  val Redis = Config.Redis
  val IO = Config.IO
  val Global = Config.Global

  val LoggingSubscription: Subscription = {
    case m: PubSubMessage.Error => logger.info(s"Subscription received $m")
    case m: PubSubMessage.Message => logger.info(s"Subscription received $m")
    case m: PubSubMessage.PMessage => logger.info(s"Subscription received $m")
    case m: PubSubMessage.Subscribe => logger.info(s"Subscription received $m")
    case m: PubSubMessage.PSubscribe => logger.info(s"Subscription received $m")
    case m: PubSubMessage.Unsubscribe => logger.info(s"Subscription received $m")
    case m: PubSubMessage.PUnsubscribe => logger.info(s"Subscription received $m")
  }
}
