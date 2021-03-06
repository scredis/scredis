package scredis

import akka.actor._
import com.typesafe.config.Config
import scredis.commands._
import scredis.io.AkkaNonBlockingConnection
import scredis.protocol.AuthConfig
import scredis.util.UniqueNameGenerator

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Defines a `Redis` [[scredis.Client]] supporting all non-blocking commands along with a lazily
 * initialized [[scredis.BlockingClient]] and [[scredis.SubscriberClient]].
 * 
 * @define e [[scredis.exceptions.RedisErrorResponseException]]
 * @define redis [[scredis.Redis]]
 * @define tc com.typesafe.Config
 */
class Redis private[scredis] (
  systemOrName: Either[ActorSystem, String],
  host: String,
  port: Int,
  authOpt: Option[AuthConfig],
  database: Int,
  nameOpt: Option[String],
  connectTimeout: FiniteDuration,
  receiveTimeoutOpt: Option[FiniteDuration],
  maxWriteBatchSize: Int,
  tcpSendBufferSizeHint: Int,
  tcpReceiveBufferSizeHint: Int,
  akkaListenerDispatcherPath: String,
  akkaIODispatcherPath: String,
  akkaDecoderDispatcherPath: String,
  failCommandOnConnecting: Boolean,
  subscription: Subscription
) extends AkkaNonBlockingConnection(
  system = systemOrName match {
    case Left(system) => system
    case Right(name) => ActorSystem(UniqueNameGenerator.getUniqueName(name))
  },
  host = host,
  port = port,
  authOpt = authOpt,
  database = database,
  nameOpt = nameOpt,
  connectTimeout = connectTimeout,
  receiveTimeoutOpt = receiveTimeoutOpt,
  maxWriteBatchSize = maxWriteBatchSize,
  tcpSendBufferSizeHint = tcpSendBufferSizeHint,
  tcpReceiveBufferSizeHint = tcpSendBufferSizeHint,
  akkaListenerDispatcherPath = akkaListenerDispatcherPath,
  akkaIODispatcherPath = akkaIODispatcherPath,
  akkaDecoderDispatcherPath = akkaDecoderDispatcherPath,
  decodersCount = 2,
  failCommandOnConnecting = failCommandOnConnecting
) with ConnectionCommands
  with ServerCommands
  with KeyCommands
  with StringCommands
  with HashCommands
  with ListCommands
  with SetCommands
  with SortedSetCommands
  with ScriptingCommands
  with HyperLogLogCommands
  with PubSubCommands
  with TransactionCommands {
  
  private var shouldShutdownBlockingClient = false
  private var shouldShutdownSubscriberClient = false

  private val defaultBlockingTimeout: FiniteDuration = 5.seconds
  
  /**
   * Lazily initialized [[scredis.BlockingClient]].
   */
  lazy val blocking: BlockingClient = {
    shouldShutdownBlockingClient = true
    BlockingClient(
      host = host,
      port = port,
      authOpt = getAuthOpt,
      database = getDatabase,
      nameOpt = getNameOpt,
      connectTimeout = connectTimeout,
      maxWriteBatchSize = maxWriteBatchSize,
      tcpSendBufferSizeHint = tcpSendBufferSizeHint,
      tcpReceiveBufferSizeHint = tcpReceiveBufferSizeHint,
      akkaListenerDispatcherPath = akkaListenerDispatcherPath,
      akkaIODispatcherPath = akkaIODispatcherPath,
      akkaDecoderDispatcherPath = akkaDecoderDispatcherPath
    )(system)
  }
  
  /**
   * Lazily initialized [[scredis.SubscriberClient]].
   */
  lazy val subscriber: SubscriberClient = {
    shouldShutdownSubscriberClient = true
    SubscriberClient(
      subscription,
      host = host,
      port = port,
      authOpt = getAuthOpt,
      nameOpt = getNameOpt,
      connectTimeout = connectTimeout,
      receiveTimeoutOpt = receiveTimeoutOpt,
      maxWriteBatchSize = maxWriteBatchSize,
      tcpSendBufferSizeHint = tcpSendBufferSizeHint,
      tcpReceiveBufferSizeHint = tcpReceiveBufferSizeHint,
      akkaListenerDispatcherPath = akkaListenerDispatcherPath,
      akkaIODispatcherPath = akkaIODispatcherPath,
      akkaDecoderDispatcherPath = akkaDecoderDispatcherPath
    )(system)
  }
  
  
  /**
   * Constructs a $redis instance using provided parameters.
   * 
   * @param host server address
   * @param port server port
   * @param authOpt optional server authorization credentials
   * @param database database index to select
   * @param nameOpt optional client name (available since 2.6.9)
   * @param connectTimeout connection timeout
   * @param receiveTimeoutOpt optional batch receive timeout
   * @param maxWriteBatchSize max number of bytes to send as part of a batch
   * @param tcpSendBufferSizeHint size hint of the tcp send buffer, in bytes
   * @param tcpReceiveBufferSizeHint size hint of the tcp receive buffer, in bytes
   * @param actorSystemName name of the actor system
   * @param akkaListenerDispatcherPath path to listener dispatcher definition
   * @param akkaIODispatcherPath path to io dispatcher definition
   * @param akkaDecoderDispatcherPath path to decoder dispatcher definition
   * @return the constructed $redis
   */
  def this(
    host: String = RedisConfigDefaults.Redis.Host,
    port: Int = RedisConfigDefaults.Redis.Port,
    authOpt: Option[AuthConfig] = RedisConfigDefaults.Redis.AuthOpt,
    database: Int = RedisConfigDefaults.Redis.Database,
    nameOpt: Option[String] = RedisConfigDefaults.Redis.NameOpt,
    connectTimeout: FiniteDuration = RedisConfigDefaults.IO.ConnectTimeout,
    receiveTimeoutOpt: Option[FiniteDuration] = RedisConfigDefaults.IO.ReceiveTimeoutOpt,
    maxWriteBatchSize: Int = RedisConfigDefaults.IO.MaxWriteBatchSize,
    tcpSendBufferSizeHint: Int = RedisConfigDefaults.IO.TCPSendBufferSizeHint,
    tcpReceiveBufferSizeHint: Int = RedisConfigDefaults.IO.TCPReceiveBufferSizeHint,
    actorSystemName: String = RedisConfigDefaults.IO.Akka.ActorSystemName,
    akkaListenerDispatcherPath: String = RedisConfigDefaults.IO.Akka.ListenerDispatcherPath,
    akkaIODispatcherPath: String = RedisConfigDefaults.IO.Akka.IODispatcherPath,
    akkaDecoderDispatcherPath: String = RedisConfigDefaults.IO.Akka.DecoderDispatcherPath,
    failCommandOnConnecting: Boolean = RedisConfigDefaults.Global.FailCommandOnConnecting,
    subscription: Subscription = RedisConfigDefaults.LoggingSubscription
  ) = this(
    systemOrName = Right(actorSystemName),
    host = host,
    port = port,
    authOpt = authOpt,
    database = database,
    nameOpt = nameOpt,
    connectTimeout = connectTimeout,
    receiveTimeoutOpt = receiveTimeoutOpt,
    maxWriteBatchSize = maxWriteBatchSize,
    tcpSendBufferSizeHint = tcpSendBufferSizeHint,
    tcpReceiveBufferSizeHint = tcpReceiveBufferSizeHint,
    akkaListenerDispatcherPath = akkaListenerDispatcherPath,
    akkaIODispatcherPath = akkaIODispatcherPath,
    akkaDecoderDispatcherPath = akkaDecoderDispatcherPath,
    failCommandOnConnecting = failCommandOnConnecting,
    subscription = subscription
  )
  
  def this(config: RedisConfig, subscription: Subscription) = this(
    host = config.Redis.Host,
    port = config.Redis.Port,
    authOpt = config.Redis.AuthOpt,
    database = config.Redis.Database,
    nameOpt = config.Redis.NameOpt,
    connectTimeout = config.IO.ConnectTimeout,
    receiveTimeoutOpt = config.IO.ReceiveTimeoutOpt,
    maxWriteBatchSize = config.IO.MaxWriteBatchSize,
    tcpSendBufferSizeHint = config.IO.TCPSendBufferSizeHint,
    tcpReceiveBufferSizeHint = config.IO.TCPReceiveBufferSizeHint,
    actorSystemName = config.IO.Akka.ActorSystemName,
    akkaListenerDispatcherPath = config.IO.Akka.ListenerDispatcherPath,
    akkaIODispatcherPath = config.IO.Akka.IODispatcherPath,
    akkaDecoderDispatcherPath = config.IO.Akka.DecoderDispatcherPath,
    failCommandOnConnecting = config.Global.FailCommandOnConnecting,
    subscription = subscription
  )

  /**
   * Constructs a $redis instance from a [[scredis.RedisConfig]].
   *
   * @return the constructed $redis
   */
  def this(config: RedisConfig) = this(config, RedisConfigDefaults.LoggingSubscription)

  /**
   * Constructs a $redis instance using the default config.
   * 
   * @return the constructed $redis
   */
  def this() = this(RedisConfig())
  
  /**
   * Constructs a $redis instance from a $tc.
   * 
   * @note The config must contain the scredis object at its root.
   * 
   * @param config $tc
   * @return the constructed $redis
   */
  def this(config: Config) = this(RedisConfig(config))
  
  /**
   * Constructs a $redis instance from a config file.
   * 
   * @note The config file must contain the scredis object at its root.
   * This constructor is equivalent to {{{
   * new Redis(configName, "scredis")
   * }}}
   * 
   * @param configName config filename
   * @return the constructed $redis
   */
  def this(configName: String) = this(RedisConfig(configName))
  
  /**
   * Constructs a $redis instance from a config file and using the provided path.
   * 
   * @note The path must include to the scredis object, e.g. x.y.scredis.
   * 
   * @param configName config filename
   * @param path path pointing to the scredis config object
   * @return the constructed $redis
   */
  def this(configName: String, path: String) = this(RedisConfig(configName, path))
  
  /**
   * Authenticates to the server.
   * 
   * @note Use the empty string to re-authenticate with no password.
   *
   * @param password the server password
   * @throws $e if authentication failed
   *
   * @since 1.0.0
   */
  override def auth(password: String, username: Option[String]): Future[Unit] = {
    if (shouldShutdownBlockingClient) {
      try {
        blocking.auth(password)(defaultBlockingTimeout)
      } catch {
        case e: Throwable => logger.error("Could not authenticate blocking client", e)
      }
    }
    val future = if (shouldShutdownSubscriberClient) {
      subscriber.auth(password, username)
    } else {
      Future.successful(())
    }
    future.recover {
      case e: Throwable => logger.error("Could not authenticate subscriber client", e)
    }.flatMap { _ =>
      super.auth(password)
    }
  }
  
  /**
   * Sets the current client name. If the empty string is provided, the name will be unset.
   *
   * @param name name to associate the client to, if empty, unsets the client name
   *
   * @since 2.6.9
   */
  override def clientSetName(name: String): Future[Unit] = {
    if (shouldShutdownBlockingClient) {
      try {
        blocking.clientSetName(name)(defaultBlockingTimeout)
      } catch {
        case e: Throwable => logger.error("Could not set client name on blocking client", e)
      }
    }
    val future = if (shouldShutdownSubscriberClient) {
      subscriber.clientSetName(name)
    } else {
      Future.successful(())
    }
    future.recover {
      case e: Throwable => logger.error("Could not set client name on subscriber client", e)
    }.flatMap { _ =>
      super.clientSetName(name)
    }
  }

  /**
   * Closes the connection.
   *
   * @since 1.0.0
   */
  override def quit(): Future[Unit] = {
    if (shouldShutdownBlockingClient) {
      try {
        blocking.quit()(defaultBlockingTimeout)
        blocking.awaitTermination(defaultBlockingTimeout)
      } catch {
        case e: Throwable => logger.error("Could not shutdown blocking client", e)
      }
    }
    val future = if (shouldShutdownSubscriberClient) {
      subscriber.quit().map { _ =>
        subscriber.awaitTermination(defaultBlockingTimeout)
      }
    } else {
      Future.successful(())
    }
    val quitFuture: Future[Unit] = future.recover {
      case e: Throwable => logger.error("Could not shutdown subscriber client", e)
    }.flatMap { _ =>
      super.quit()
    }.flatMap { _ =>
      awaitTermination(defaultBlockingTimeout)
      systemOrName match {
        case Left(_) => Future.successful(()) // Do not shutdown provided ActorSystem
        case Right(_) => system.terminate().map(_ => ())
      }
    }
    quitFuture
  }

  /**
   * Changes the selected database on the current connection.
   *
   * @param database database index
   * @throws $e if the database index is invalid
   *
   * @since 1.0.0
   */
  override def select(database: Int): Future[Unit] = {
    if (shouldShutdownBlockingClient) {
      try {
        blocking.select(database)(defaultBlockingTimeout)
      } catch {
        case e: Throwable => Future.failed(e)
      }
    }
    super.select(database)
  }
  
  watchTermination()
  
}

/**
 * The companion object provides additional friendly constructors.
 * 
 * @define redis [[scredis.Redis]]
 * @define tc com.typesafe.Config
 */
object Redis {
  
  /**
   * Constructs a $redis instance using provided parameters.
   * 
   * @param host server address
   * @param port server port
   * @param authOpt optional server authorization credentials
   * @param database database index to select
   * @param nameOpt optional client name (available since 2.6.9)
   * @param connectTimeout connection timeout
   * @param receiveTimeoutOpt optional batch receive timeout
   * @param maxWriteBatchSize max number of bytes to send as part of a batch
   * @param tcpSendBufferSizeHint size hint of the tcp send buffer, in bytes
   * @param tcpReceiveBufferSizeHint size hint of the tcp receive buffer, in bytes
   * @param actorSystemName name of the actor system
   * @param akkaListenerDispatcherPath path to listener dispatcher definition
   * @param akkaIODispatcherPath path to io dispatcher definition
   * @param akkaDecoderDispatcherPath path to decoder dispatcher definition
   * @return the constructed $redis
   */
  def apply(
    host: String = RedisConfigDefaults.Redis.Host,
    port: Int = RedisConfigDefaults.Redis.Port,
    authOpt: Option[AuthConfig] = RedisConfigDefaults.Redis.AuthOpt,
    database: Int = RedisConfigDefaults.Redis.Database,
    nameOpt: Option[String] = RedisConfigDefaults.Redis.NameOpt,
    connectTimeout: FiniteDuration = RedisConfigDefaults.IO.ConnectTimeout,
    receiveTimeoutOpt: Option[FiniteDuration] = RedisConfigDefaults.IO.ReceiveTimeoutOpt,
    maxWriteBatchSize: Int = RedisConfigDefaults.IO.MaxWriteBatchSize,
    tcpSendBufferSizeHint: Int = RedisConfigDefaults.IO.TCPSendBufferSizeHint,
    tcpReceiveBufferSizeHint: Int = RedisConfigDefaults.IO.TCPReceiveBufferSizeHint,
    actorSystemName: String = RedisConfigDefaults.IO.Akka.ActorSystemName,
    akkaListenerDispatcherPath: String = RedisConfigDefaults.IO.Akka.ListenerDispatcherPath,
    akkaIODispatcherPath: String = RedisConfigDefaults.IO.Akka.IODispatcherPath,
    akkaDecoderDispatcherPath: String = RedisConfigDefaults.IO.Akka.DecoderDispatcherPath,
    failCommandOnConnecting: Boolean = RedisConfigDefaults.Global.FailCommandOnConnecting
  ): Redis = new Redis(
    host = host,
    port = port,
    authOpt = authOpt,
    database = database,
    nameOpt = nameOpt,
    connectTimeout = connectTimeout,
    receiveTimeoutOpt = receiveTimeoutOpt,
    maxWriteBatchSize = maxWriteBatchSize,
    tcpSendBufferSizeHint = tcpSendBufferSizeHint,
    tcpReceiveBufferSizeHint = tcpSendBufferSizeHint,
    actorSystemName = actorSystemName,
    akkaListenerDispatcherPath = akkaListenerDispatcherPath,
    akkaIODispatcherPath = akkaIODispatcherPath,
    akkaDecoderDispatcherPath = akkaDecoderDispatcherPath,
    failCommandOnConnecting = failCommandOnConnecting
  )
  
  /**
   * Constructs a $redis instance using the default config.
   * 
   * @return the constructed $redis
   */
  def apply() = new Redis(RedisConfig())
  
  /**
   * Constructs a $redis instance from a [[scredis.RedisConfig]].
   * 
   * @param config [[scredis.RedisConfig]]
   * @return the constructed $redis
   */
  def apply(config: RedisConfig): Redis = new Redis(config)

  def apply(subscription: Subscription): Redis = new Redis(RedisConfig(), subscription)
  /**
   * Constructs a $redis instance from a $tc.
   * 
   * @note The config must contain the scredis object at its root.
   * 
   * @param config $tc
   * @return the constructed $redis
   */
  def apply(config: Config): Redis = new Redis(config)
  
  /**
   * Constructs a $redis instance from a config file.
   * 
   * @note The config file must contain the scredis object at its root.
   * This constructor is equivalent to {{{
   * Redis(configName, "scredis")
   * }}}
   * 
   * @param configName config filename
   * @return the constructed $redis
   */
  def apply(configName: String): Redis = new Redis(configName)
  
  /**
   * Constructs a $redis instance from a config file and using the provided path.
   * 
   * @note The path must include to the scredis object, e.g. x.y.scredis
   * 
   * @param configName config filename
   * @param path path pointing to the scredis config object
   * @return the constructed $redis
   */
  def apply(configName: String, path: String): Redis = new Redis(configName, path)
  
  /**
   * Constructs a $redis instance using provided parameters.
   * 
   * @note The provided `ActorSystem` will not be shutdown after invoking `quit`.
   * 
   * @param host server address
   * @param port server port
   * @param authOpt optional server authorization credentials
   * @param database database index to select
   * @param nameOpt optional client name (available since 2.6.9)
   * @param connectTimeout connection timeout
   * @param receiveTimeoutOpt optional batch receive timeout
   * @param maxWriteBatchSize max number of bytes to send as part of a batch
   * @param tcpSendBufferSizeHint size hint of the tcp send buffer, in bytes
   * @param tcpReceiveBufferSizeHint size hint of the tcp receive buffer, in bytes
   * @param akkaListenerDispatcherPath path to listener dispatcher definition
   * @param akkaIODispatcherPath path to io dispatcher definition
   * @param akkaDecoderDispatcherPath path to decoder dispatcher definition
   * @param system implicit `ActorSystem`
   * @return the constructed $redis
   */
  def withActorSystem(
    host: String = RedisConfigDefaults.Redis.Host,
    port: Int = RedisConfigDefaults.Redis.Port,
    authOpt: Option[AuthConfig] = RedisConfigDefaults.Redis.AuthOpt,
    database: Int = RedisConfigDefaults.Redis.Database,
    nameOpt: Option[String] = RedisConfigDefaults.Redis.NameOpt,
    connectTimeout: FiniteDuration = RedisConfigDefaults.IO.ConnectTimeout,
    receiveTimeoutOpt: Option[FiniteDuration] = RedisConfigDefaults.IO.ReceiveTimeoutOpt,
    maxWriteBatchSize: Int = RedisConfigDefaults.IO.MaxWriteBatchSize,
    tcpSendBufferSizeHint: Int = RedisConfigDefaults.IO.TCPSendBufferSizeHint,
    tcpReceiveBufferSizeHint: Int = RedisConfigDefaults.IO.TCPReceiveBufferSizeHint,
    akkaListenerDispatcherPath: String = RedisConfigDefaults.IO.Akka.ListenerDispatcherPath,
    akkaIODispatcherPath: String = RedisConfigDefaults.IO.Akka.IODispatcherPath,
    akkaDecoderDispatcherPath: String = RedisConfigDefaults.IO.Akka.DecoderDispatcherPath,
    failCommandOnConnecting: Boolean = RedisConfigDefaults.Global.FailCommandOnConnecting,
    subscription: Subscription = RedisConfigDefaults.LoggingSubscription
  )(implicit system: ActorSystem): Redis = new Redis(
    systemOrName = Left(system),
    host = host,
    port = port,
    authOpt = authOpt,
    database = database,
    nameOpt = nameOpt,
    connectTimeout = connectTimeout,
    receiveTimeoutOpt = receiveTimeoutOpt,
    maxWriteBatchSize = maxWriteBatchSize,
    tcpSendBufferSizeHint = tcpSendBufferSizeHint,
    tcpReceiveBufferSizeHint = tcpSendBufferSizeHint,
    akkaListenerDispatcherPath = akkaListenerDispatcherPath,
    akkaIODispatcherPath = akkaIODispatcherPath,
    akkaDecoderDispatcherPath = akkaDecoderDispatcherPath,
    failCommandOnConnecting = failCommandOnConnecting,
    subscription = subscription
  )
  
  /**
   * Constructs a $redis instance using the default config.
   * 
   * @note The provided `ActorSystem` will not be shutdown after invoking `quit`.
   * 
   * @param system implicit `ActorSystem`
   * @return the constructed $redis
   */
  def withActorSystem()(implicit system: ActorSystem): Redis = withActorSystem(RedisConfig())
  
  /**
   * Constructs a $redis instance from a [[scredis.RedisConfig]].
   * 
   * @note The provided `ActorSystem` will not be shutdown after invoking `quit`.
   * 
   * @param config [[scredis.RedisConfig]]
   * @param system implicit `ActorSystem`
   * @return the constructed $redis
   */
  def withActorSystem(config: RedisConfig)(implicit system: ActorSystem): Redis = new Redis(
    systemOrName = Left(system),
    host = config.Redis.Host,
    port = config.Redis.Port,
    authOpt = config.Redis.AuthOpt,
    database = config.Redis.Database,
    nameOpt = config.Redis.NameOpt,
    connectTimeout = config.IO.ConnectTimeout,
    receiveTimeoutOpt = config.IO.ReceiveTimeoutOpt,
    maxWriteBatchSize = config.IO.MaxWriteBatchSize,
    tcpSendBufferSizeHint = config.IO.TCPSendBufferSizeHint,
    tcpReceiveBufferSizeHint = config.IO.TCPReceiveBufferSizeHint,
    akkaListenerDispatcherPath = config.IO.Akka.ListenerDispatcherPath,
    akkaIODispatcherPath = config.IO.Akka.IODispatcherPath,
    akkaDecoderDispatcherPath = config.IO.Akka.DecoderDispatcherPath,
    failCommandOnConnecting = config.Global.FailCommandOnConnecting,
    subscription = RedisConfigDefaults.LoggingSubscription
  )
  
  /**
   * Constructs a $redis instance from a $tc.
   * 
   * @note The config must contain the scredis object at its root.
   * @note The provided `ActorSystem` will not be shutdown after invoking `quit`.
   * 
   * @param config $tc
   * @param system implicit `ActorSystem`
   * @return the constructed $redis
   */
  def withActorSystem(config: Config)(implicit system: ActorSystem): Redis = withActorSystem(
    RedisConfig(config)
  )
  
  /**
   * Constructs a $redis instance from a config file.
   * 
   * @note The config file must contain the scredis object at its root.
   * This constructor is equivalent to {{{
   * Redis(configName, "scredis")
   * }}}
   * @note The provided `ActorSystem` will not be shutdown after invoking `quit`.
   * 
   * @param configName config filename
   * @param system implicit `ActorSystem`
   * @return the constructed $redis
   */
  def withActorSystem(configName: String)(implicit system: ActorSystem): Redis = withActorSystem(
    RedisConfig(configName)
  )
  
  /**
   * Constructs a $redis instance from a config file and using the provided path.
   * 
   * @note The path must include to the scredis object, e.g. x.y.scredis
   * @note The provided `ActorSystem` will not be shutdown after invoking `quit`.
   * 
   * @param configName config filename
   * @param path path pointing to the scredis config object
   * @param system implicit `ActorSystem`
   * @return the constructed $redis
   */
  def withActorSystem(configName: String, path: String)(
    implicit system: ActorSystem
  ): Redis = withActorSystem(RedisConfig(configName, path))
  
}
