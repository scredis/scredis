package scredis

import akka.actor.ActorSystem
import com.typesafe.config.Config
import scredis.commands._
import scredis.io.{ClusterConnection, Connection}
import scredis.protocol.AuthConfig

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
  * Defines a `RedisCluster` [[scredis.Client]] supporting all non-blocking commands that can be addressed to either
  * any cluster node or be automatically routed to the correct node.
  *
  * @define e [[scredis.exceptions.RedisErrorResponseException]]
  * @define redisCluster [[scredis.RedisCluster]]
  * @define typesafeConfig com.typesafe.Config
  */
class RedisCluster private[scredis](
    nodes: Seq[Server] = RedisConfigDefaults.Redis.ClusterNodes,
    maxRetries: Int = 4,
    receiveTimeoutOpt: Option[FiniteDuration] = RedisConfigDefaults.IO.ReceiveTimeoutOpt,
    connectTimeout: FiniteDuration = RedisConfigDefaults.IO.ConnectTimeout,
    maxWriteBatchSize: Int = RedisConfigDefaults.IO.MaxWriteBatchSize,
    tcpSendBufferSizeHint: Int = RedisConfigDefaults.IO.TCPSendBufferSizeHint,
    tcpReceiveBufferSizeHint: Int = RedisConfigDefaults.IO.TCPReceiveBufferSizeHint,
    akkaListenerDispatcherPath: String = RedisConfigDefaults.IO.Akka.ListenerDispatcherPath,
    akkaIODispatcherPath: String = RedisConfigDefaults.IO.Akka.IODispatcherPath,
    akkaDecoderDispatcherPath: String = RedisConfigDefaults.IO.Akka.DecoderDispatcherPath,
    tryAgainWait: FiniteDuration = RedisConfigDefaults.IO.Cluster.TryAgainWait,
    clusterDownWait: FiniteDuration = RedisConfigDefaults.IO.Cluster.ClusterDownWait,
    systemOpt:Option[ActorSystem] = None,
    failCommandOnConnecting: Boolean = RedisConfigDefaults.Global.FailCommandOnConnecting,
    authOpt: Option[AuthConfig] = RedisConfigDefaults.Config.Redis.AuthOpt
  )
  extends ClusterConnection(
    nodes = nodes,
    maxRetries = maxRetries,
    receiveTimeoutOpt = receiveTimeoutOpt,
    connectTimeout = connectTimeout,
    maxWriteBatchSize = maxWriteBatchSize,
    tcpSendBufferSizeHint = tcpSendBufferSizeHint,
    tcpReceiveBufferSizeHint = tcpReceiveBufferSizeHint,
    akkaListenerDispatcherPath = akkaListenerDispatcherPath,
    akkaIODispatcherPath = akkaIODispatcherPath,
    tryAgainWait = tryAgainWait,
    clusterDownWait = clusterDownWait,
    systemOpt = systemOpt,
    failCommandOnConnecting = failCommandOnConnecting,
    authOpt = authOpt
  ) with Connection
  with ClusterCommands
  with HashCommands
  with HyperLogLogCommands
  with KeyCommands
  with ListCommands
  with PubSubCommands
  with ScriptingCommands
  with SetCommands
  with SortedSetCommands
  with StringCommands
  //with SubscriberCommands
{
  override implicit val dispatcher: ExecutionContext =
    systemOpt.map(_.dispatcher).getOrElse(ExecutionContext.Implicits.global) // TODO perhaps implement our own for the default

  /**
    * Constructs a $redisCluster instance from a [[scredis.RedisConfig]].
    *
    * @return the constructed $redisCluster
    */
  def this(config: RedisConfig, systemOpt:Option[ActorSystem]) = this(
    nodes = config.Redis.ClusterNodes,
    maxRetries = 4,
    receiveTimeoutOpt = config.IO.ReceiveTimeoutOpt,
    connectTimeout = config.IO.ConnectTimeout,
    maxWriteBatchSize = config.IO.MaxWriteBatchSize,
    tcpSendBufferSizeHint = config.IO.TCPSendBufferSizeHint,
    tcpReceiveBufferSizeHint = config.IO.TCPReceiveBufferSizeHint,
    akkaListenerDispatcherPath = config.IO.Akka.ListenerDispatcherPath,
    akkaIODispatcherPath = config.IO.Akka.IODispatcherPath,
    akkaDecoderDispatcherPath = config.IO.Akka.DecoderDispatcherPath,
    tryAgainWait = config.IO.Cluster.TryAgainWait,
    clusterDownWait = config.IO.Cluster.ClusterDownWait,
    systemOpt = systemOpt,
    failCommandOnConnecting = config.Global.FailCommandOnConnecting,
    authOpt = config.Redis.AuthOpt
  )

  /**
    * Constructs a $redisCluster instance using the default config.
    *
    * @return the constructed $redisCluster
    */
  def this() = this(RedisConfig(), None)


  /**
    * Constructs a $redisCluster instance using the default config.
    *
    * @param system Actor system
    * @return the constructed $redisCluster
    */
  def this(system:ActorSystem) = this(RedisConfig(), Some(system))

}

/**
  * @define redisCluster [[scredis.RedisCluster]]
  * @define typesafeConfig com.typesafe.Config
  */
object RedisCluster {

  /**
    * Constructs a $redisCluster instance using provided parameters.
    *
    * @param nodes list of server nodes, used as seed nodes to initially connect to the cluster.
    * @param maxRetries maximum number of retries and redirects to perform for a single command
    * @param receiveTimeoutOpt optional batch receive timeout
    * @param connectTimeout connection timeout
    * @param maxWriteBatchSize max number of bytes to send as part of a batch
    * @param tcpSendBufferSizeHint size hint of the tcp send buffer, in bytes
    * @param tcpReceiveBufferSizeHint size hint of the tcp receive buffer, in bytes
    * @param akkaListenerDispatcherPath path to listener dispatcher definition
    * @param akkaIODispatcherPath path to io dispatcher definition
    * @param akkaDecoderDispatcherPath path to decoder dispatcher definition
    * @param tryAgainWait time to wait after a TRYAGAIN response by a cluster node
    * @param clusterDownWait time to wait for a retry after CLUSTERDOWN response
    * @param systemOpt Actor System (optionally)
    * @param failCommandOnConnecting indicates whether to fail fast on all requests until there is a working connection.
    * @param authOpt optional server authorization credentials
    * @return the constructed $redisCluster
    */
  def apply(
    nodes: Seq[Server] = RedisConfigDefaults.Redis.ClusterNodes,
    maxRetries: Int = 4,
    receiveTimeoutOpt: Option[FiniteDuration] = RedisConfigDefaults.IO.ReceiveTimeoutOpt,
    connectTimeout: FiniteDuration = RedisConfigDefaults.IO.ConnectTimeout,
    maxWriteBatchSize: Int = RedisConfigDefaults.IO.MaxWriteBatchSize,
    tcpSendBufferSizeHint: Int = RedisConfigDefaults.IO.TCPSendBufferSizeHint,
    tcpReceiveBufferSizeHint: Int = RedisConfigDefaults.IO.TCPReceiveBufferSizeHint,
    akkaListenerDispatcherPath: String = RedisConfigDefaults.IO.Akka.ListenerDispatcherPath,
    akkaIODispatcherPath: String = RedisConfigDefaults.IO.Akka.IODispatcherPath,
    akkaDecoderDispatcherPath: String = RedisConfigDefaults.IO.Akka.DecoderDispatcherPath,
    tryAgainWait: FiniteDuration = RedisConfigDefaults.IO.Cluster.TryAgainWait,
    clusterDownWait: FiniteDuration = RedisConfigDefaults.IO.Cluster.ClusterDownWait,
    systemOpt: Option[ActorSystem] = None,
    failCommandOnConnecting: Boolean = RedisConfigDefaults.Global.FailCommandOnConnecting,
    authOpt: Option[AuthConfig] = RedisConfigDefaults.Config.Redis.AuthOpt
  ) = new RedisCluster(
    nodes = nodes,
    maxRetries = maxRetries,
    receiveTimeoutOpt = receiveTimeoutOpt,
    connectTimeout = connectTimeout,
    maxWriteBatchSize = maxWriteBatchSize,
    tcpSendBufferSizeHint = tcpReceiveBufferSizeHint,
    akkaListenerDispatcherPath = akkaListenerDispatcherPath,
    akkaIODispatcherPath = akkaIODispatcherPath,
    tryAgainWait = tryAgainWait,
    clusterDownWait = clusterDownWait,
    systemOpt = systemOpt,
    failCommandOnConnecting = failCommandOnConnecting,
    authOpt = authOpt
  )


  /**
    * Constructs a $redisCluster instance with given seed nodes, using the default config for all other parameters.
    *
    * @return the constructed $redisCluster
    */
  def apply(node: Server, nodes: Server*): RedisCluster = RedisCluster( nodes = node +: nodes)

  /**
    * Constructs a $redisCluster instance from a [[scredis.RedisConfig]].
    *
    * @param config a [[scredis.RedisConfig]]
    * @return the constructed $redisCluster
    */
  def apply(config: RedisConfig, systemOpt:Option[ActorSystem]) = new RedisCluster(config, systemOpt)

  /**
    * Constructs a $redisCluster instance from a $typesafeConfig.
    *
    * @note The config must contain the scredis object at its root.
    *
    * @param config a $typesafeConfig
    * @return the constructed $redisCluster
    */
  def apply(config: Config) = new RedisCluster(RedisConfig(config), None)


  /**
    * Constructs a $redisCluster instance from a config file.
    *
    * @note The config file must contain the scredis object at its root.
    * This constructor is equivalent to {{{
    * Redis(configName, "scredis")
    * }}}
    *
    * @param configName config filename
    * @return the constructed $redisCluster
    */
  def apply(configName: String): RedisCluster = new RedisCluster(RedisConfig(configName), None)

  /**
    * Constructs a $redisCluster instance from a config file and using the provided path.
    *
    * @note The path must include to the scredis object, e.g. x.y.scredis
    *
    * @param configName config filename
    * @param path path pointing to the scredis config object
    * @return the constructed $redisCluster
    */
  def apply(configName: String, path: String): RedisCluster = new RedisCluster(RedisConfig(configName, path), None)

}