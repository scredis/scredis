package scredis.io

import akka.actor._
import scredis.Transaction
import scredis.exceptions._
import scredis.protocol._
import scredis.util.UniqueNameGenerator

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

/**
 * This trait represents a non-blocking connection to a `Redis` server.
 */
abstract class AkkaNonBlockingConnection(
  system: ActorSystem,
  host: String,
  port: Int,
  authOpt: Option[AuthConfig],
  database: Int,
  nameOpt: Option[String],
  decodersCount: Int,
  receiveTimeoutOpt: Option[FiniteDuration],
  connectTimeout: FiniteDuration,
  maxWriteBatchSize: Int,
  tcpSendBufferSizeHint: Int,
  tcpReceiveBufferSizeHint: Int,
  akkaListenerDispatcherPath: String,
  akkaIODispatcherPath: String,
  akkaDecoderDispatcherPath: String,
  failCommandOnConnecting: Boolean
) extends AbstractAkkaConnection(
  system = system,
  host = host,
  port = port,
  authOpt = authOpt,
  database = database,
  nameOpt = nameOpt,
  decodersCount = decodersCount,
  receiveTimeoutOpt = receiveTimeoutOpt,
  connectTimeout = connectTimeout,
  maxWriteBatchSize = maxWriteBatchSize,
  tcpSendBufferSizeHint = tcpSendBufferSizeHint,
  tcpReceiveBufferSizeHint = tcpReceiveBufferSizeHint,
  akkaListenerDispatcherPath = akkaListenerDispatcherPath,
  akkaIODispatcherPath = akkaIODispatcherPath,
  akkaDecoderDispatcherPath = akkaDecoderDispatcherPath
) with NonBlockingConnection with TransactionEnabledConnection {
  
  protected val listenerActor: ActorRef = system.actorOf(
    Props(
      classOf[ListenerActor],
      host,
      port,
      authOpt,
      database,
      nameOpt,
      decodersCount,
      receiveTimeoutOpt,
      connectTimeout,
      maxWriteBatchSize,
      tcpSendBufferSizeHint,
      tcpReceiveBufferSizeHint,
      akkaIODispatcherPath,
      akkaDecoderDispatcherPath,
      failCommandOnConnecting
    ).withDispatcher(akkaListenerDispatcherPath),
    UniqueNameGenerator.getUniqueName(s"${nameOpt.getOrElse(s"$host-$port")}-listener-actor")
  )
  
  override protected[scredis] def send[A](request: Request[A]): Future[A] = {
    if (isShuttingDown) {
      Future.failed(RedisIOException("Connection has been shutdown"))
    } else {
      logger.debug(s"Sending request: $request")
      updateState(request)
      Protocol.send(request, listenerActor)
    }
  }
  
  override protected[scredis] def send(transaction: Transaction): Future[Vector[Try[Any]]] = {
    if (isShuttingDown) {
      Future.failed(RedisIOException("Connection has been shutdown"))
    } else {
      logger.debug(s"Sending transaction: $transaction")
      transaction.requests.foreach(updateState)
      Protocol.send(transaction, listenerActor)
    }
  }
}