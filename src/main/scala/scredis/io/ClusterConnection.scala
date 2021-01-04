package scredis.io

import akka.actor.{ActorRef, ActorSystem}
import com.typesafe.scalalogging.LazyLogging
import scredis.exceptions._
import scredis.protocol._
import scredis.protocol.requests.ClusterRequests.{ClusterCountKeysInSlot, ClusterInfo, ClusterSlots}
import scredis.protocol.requests.ConnectionRequests.Quit
import scredis.util.UniqueNameGenerator
import scredis.{ClusterSlotRange, RedisConfigDefaults, Server}

import scala.collection.mutable.HashMap
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

/**
 * The connection logic for a whole Redis cluster. Handles redirection and sharding logic as specified in
 * http://redis.io/topics/cluster-spec
 *
 * @param nodes List of servers to initialize the cluster connection with.
 * @param maxRetries Maximum number of retries or redirects for a request. The ClusterConnection may attempt to try
 *                   several nodes before giving up on sending a command.
 */
abstract class ClusterConnection(
    nodes: Seq[Server],
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
  ) extends NonBlockingConnection with LazyLogging {

  // Int parameter - count number of errors for given connection.
  // When defined threshold is reached connection to this server is removed and no longer used.
  type CONNECTIONS = Map[Server, (AkkaNonBlockingConnection, Int)]

  private val maxHashMisses = RedisConfigDefaults.Redis.MaxClusterHashMisses
  private val maxConnectionMisses = 3

  // Externally provided ActorSystem must be closed by caller.
  // Only internally created ActorSystem is stopped automatically on shutdown.
  private val systemState: ActorSystemState = {
    val systemName = RedisConfigDefaults.IO.Akka.ActorSystemName
    systemOpt.map(ActorSystemState(_, shouldTerminate = false))
      .getOrElse(ActorSystemState(ActorSystem(UniqueNameGenerator.getUniqueName(systemName)), shouldTerminate = true))
  }

  private def system: ActorSystem = systemState.system

  private val scheduler = system.scheduler

  /** Set of active cluster node connections. Initialized from `nodes` parameter. */
  // TODO it may be more efficient to save the connections in hashSlots directly
  // TODO we need some information about node health for updates
  private[scredis] var connections: CONNECTIONS = initialConnections

  // TODO keep master-slave associations to be able to ask slaves for data with appropriate config

  /** hash slot - connection mapping */
  // TODO we can probably use a more space-efficient storage here, but this is simplest
  private[scredis] var hashSlots: Vector[Option[Server]] = Vector.fill(Protocol.CLUSTER_HASHSLOTS)(None)

  /** Miss counter for hashSlots accesses. */
  private var hashMisses = 0

  // bootstrapping: init with info from cluster
  // I guess is it okay to to a blocking await here? or move it to a factory method?
  Await.ready(updateCache(maxRetries), connectTimeout)

  /** Set up initial connections from configuration. */
  private def initialConnections: CONNECTIONS =
    nodes.map { server => (server, (makeConnection(server, system), 0))}.toMap

  /**
   * Update the ClusterClient's connections and hash slots cache.
   * Sends a CLUSTER SLOTS query to the cluster to get a current mapping of hash slots to servers, and updates
   * the internal cache based on the reply.
   */
  private def updateCache(retry: Int): Future[Unit] = {

    // only called when cluster is in ok state
    lazy val upd = send(ClusterSlots()).map { slotRanges =>
      val newConnections = slotRanges.foldLeft(connections) {
        case (cons, ClusterSlotRange(_, master, slaves)) =>
          val nodes = master :: slaves
          cons ++ nodes.flatMap { nodeInfo =>
            val server = nodeInfo.server
            if (cons contains server) Nil
            else List( (server, (makeConnection(server, system),0)) )
          }
      }

      val newSlots =
        slotRanges.foldLeft(hashSlots) {
          case (slots, ClusterSlotRange((begin, end), master, replicas)) =>
            val masterOpt = Option(master).map(_.server)
            (begin.toInt to end.toInt).foldLeft(slots) { (s, i) => s.updated(i, masterOpt) }
        }
      this.synchronized {
        connections = newConnections
        hashSlots = newSlots
      }

      logger.info(s"initialized cluster slot cache and connections. Connected to Redis nodes: ${connections.keys}")
    }

    if (retry <= 0) Future.failed(RedisClusterException(s"cluster_state not ok, aborting cache update after $maxRetries attempts"))
    // ensure availability of cluster first
    else send(ClusterInfo()).flatMap { info =>
      info.get("cluster_state") match {
        case Some("ok") => upd
        case _ => delayed(clusterDownWait) { updateCache(retry - 1) }
      }
    }
  }

  /** Creates a new connection to a server. */
  private def makeConnection(server: Server, system:ActorSystem): AkkaNonBlockingConnection = {
    logger.info("Starting new connection to server {}", server)
    new AkkaNonBlockingConnection(
      system = system,
      host = server.host,
      port = server.port,
      authOpt = authOpt,
      database = 0,
      nameOpt = None,
      decodersCount = 2,
      receiveTimeoutOpt,
      connectTimeout,
      maxWriteBatchSize,
      tcpSendBufferSizeHint,
      tcpReceiveBufferSizeHint,
      akkaListenerDispatcherPath,
      akkaIODispatcherPath,
      akkaDecoderDispatcherPath,
      failCommandOnConnecting
    ) {
      watchTermination()
    }
  }

  /** Delay a Future-returning operation. */
  private def delayed[A](delay: FiniteDuration)(f: => Future[A]): Future[A] = {
    val delayedF = Promise[A]()
    scheduler.scheduleOnce(delay) {
      delayedF.completeWith(f)
    }
    delayedF.future
  }

  /**
   * Send a Redis request object and handle cluster specific error cases
   * @param request request object
   * @param server server to contact
   * @param retry remaining retries
   * @param remainingTimeout how much longer to retry an action, rather than number of retries
   * @tparam A response type
   * @return
   */
  private def send[A](request: Request[A],
                      server: Server,
                      triedServers: Set[Server] = Set.empty,
                      retry: Int = maxRetries,
                      remainingTimeout: Duration = connectTimeout,
                      error: Option[RedisException] = None): Future[A] = {
    if (retry <= 0) Future.failed(RedisIOException(s"Gave up on request after $maxRetries retries: $request", error.orNull))
    else {
      val connection = connections.getOrElse(server, createConnectionIfMissing(server))._1

      // handle cases that should be retried (MOVE, ASK, TRYAGAIN)
      connection.send(request).recoverWith {

        case err @ RedisClusterErrorResponseException(Moved(slot, host, port), _) =>
          request.reset()
          // this will usually happen when cache is missed.
          val movedServer: Server = Server(host, port)
          updateHashMisses(slot, movedServer)

          send(request, movedServer, triedServers+server, retry - 1, remainingTimeout, Option(err))

        case err @ RedisClusterErrorResponseException(Ask(hashSlot, host, port), _) =>
          request.reset()
          val askServer = Server(host,port)
          send(request, askServer, triedServers+server, retry - 1, remainingTimeout, Option(err))

        case err @ RedisClusterErrorResponseException(TryAgain, _) =>
          request.reset()
          // TODO what is actually the intended semantics of TryAgain?
          delayed(tryAgainWait) { send(request, server, triedServers+server, retry - 1, remainingTimeout - tryAgainWait, Option(err)) }

        case err @ RedisClusterErrorResponseException(ClusterDown, _) =>
          request.reset()
          logger.debug(s"Received CLUSTERDOWN error from request $request. Retrying ...")
          val nextTimeout = remainingTimeout - clusterDownWait
          if (nextTimeout <= 0.millis) Future.failed(RedisIOException(s"Aborted request $request after trying for ${connectTimeout}", err))
          else delayed(clusterDownWait) { send(request, server, triedServers, retry, nextTimeout, Option(err)) }
          // wait a bit because the cluster may not be fully initialized or fixing itself

        case err @ RedisIOException(message, cause) =>
          request.reset()
          updateServerErrors(server)
          val nextTriedServers = triedServers + server
          // try any server that isn't one we tried already
          connections.keys.find { s => !nextTriedServers.contains(s) } match {
            case Some(nextServer) => send(request, nextServer, nextTriedServers, retry, remainingTimeout, Option(err))
            case None => Future.failed(RedisIOException("No valid connection available.", err))
          }
      }
    }
  }

  private def updateHashMisses(slot: Int, newServer: Server): Unit = this.synchronized {
    // I believe it is safe to synchronize only updates to hashSlots.
    // If another concurrent read is outdated it will be redirected anyway and potentially repeat the update.
    hashMisses += 1

    // do this update no matter what, updating cache may take a bit
    hashSlots = hashSlots.updated(slot, Option(newServer))

    if (hashMisses > maxHashMisses) {
      hashMisses = 0
      updateCache(maxRetries)
    }
  }

  /** Called when `server` has an error to increase its error counter and remove when errors exceed a threshold. */
  private def updateServerErrors(server: Server): Unit = this.synchronized {
    for {
      (con, errors) <- connections.get(server)
    } {
      val nextConnections =
        if (errors >= maxConnectionMisses) removeServerFromConnectionsUnsafe(con, server)
        else connections.updated(server, (con, errors + 1))

      this.connections =
        if (nextConnections.isEmpty) {
          // TODO how quickly will this happen when a node is failing?
          // will we have a constant slew of re-init attempts when all is kaput?
          logger.warn(
            "No cluster node connection alive. " +
            s"Attempting to recover with initial node configuration: $nodes")
          initialConnections
        }
        else nextConnections
    }
  }

  /** Should be called only within a synchronized block
   * Marks the connection to be closed later.
   * Returns new [[CONNECTIONS]] with the server removed
   * */
  private def removeServerFromConnectionsUnsafe(connToRemove: AkkaNonBlockingConnection, server: Server): CONNECTIONS = {
    closeConnection(connToRemove).onComplete { result =>
      logger.info(s"Connection to ${connToRemove.host}:${connToRemove.port} closed with result $result")
    }
    connections - server
  }

  private def closeConnection(connToRemove: NonBlockingConnection): Future[Unit] = {
    logger.info("closing connection {}", connToRemove)
    val req = Quit()
    connToRemove.send(req)
    req.future
  }

  /** Create a new connection if required, and update connections.
    * Protects against race conditions leading to same connection being created multiple times
    * TODO: It is still possible to have race conditions when creating connections
    */
  private def createConnectionIfMissing(server: Server): (NonBlockingConnection, Int) =
    this.synchronized {
      connections.get(server) match {
        case Some(conn) => conn
        case None =>
          val con = (makeConnection(server, system), 0)
          connections = connections.updated(server, con)
          con
      }
    }

  override protected[scredis] def send[A](request: Request[A]): Future[A] = request match {
      case req @ ClusterCountKeysInSlot(slot) =>
        // special case handling for slot counting in clusters: redirect to the proper cluster node
        if (slot >= Protocol.CLUSTER_HASHSLOTS || slot < 0)
          Future.failed(RedisInvalidArgumentException(s"Invalid slot number: $slot"))
        else hashSlots(slot.toInt) match {
          case None => Future.failed(RedisIOException(s"No cluster slot information available for $slot"))
          case Some(server) => send(req, server)
        }

      case keyReq: Request[A] with Key =>
        hashSlots(ClusterCRC16.getSlot(keyReq.key)) match {
          case None =>
            if (connections.isEmpty) Future.failed(RedisIOException("No cluster node connection available"))
            else send(keyReq, connections.head._1) // when we can't get a cached server, just get the first connection
          case Some(server) =>
            send(keyReq, server)
        }

      case clusterReq: Request[A] with Cluster =>
        // requests that are valid with any cluster node
        val server = connections.head._1
        send(clusterReq, server)
      case _ =>
        // TODO what to do about non-key requests? they would be valid for any individual cluster node,
        // but arbitrary choice is probably not what's intended..
        Future.failed(RedisInvalidArgumentException("This command is not supported for clusters"))
    }

  def quit(): Future[Unit] = {
    val toCloseConnections = this.synchronized {
      val cons = connections.values.map(_._1)
      connections = Map.empty
      cons
    }
    val result = Future.traverse(toCloseConnections) { connection =>
      val f = closeConnection(connection)
      connection.awaitTermination()
      f
    }.flatMap(_ => {
      if (systemState.shouldTerminate) {
        systemState.system.terminate()
        systemState.system.whenTerminated.map(_ => ())
      } else {
        Future.successful(())
      }
    })
    result.onComplete(r => logger.info("RedisCluster stopped with result {}", r))
    result
  }

  // TODO at init: fetch all hash slot-node associations: CLUSTER SLOTS
}

case class ActorSystemState(system: ActorSystem, shouldTerminate: Boolean)