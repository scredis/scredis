package scredis.io

import java.util.concurrent.{CountDownLatch, TimeUnit}

import akka.actor._
import com.typesafe.scalalogging.LazyLogging
import scredis.protocol.{AuthConfig, Request}
import scredis.protocol.requests.ConnectionRequests.{Auth, Quit, Select}
import scredis.protocol.requests.ServerRequests.{ClientSetName, Shutdown}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

abstract class AbstractAkkaConnection(
  protected val system: ActorSystem,
  val host: String,
  val port: Int,
  @volatile protected var authOpt: Option[AuthConfig],
  @volatile protected var database: Int,
  @volatile protected var nameOpt: Option[String],
  protected val decodersCount: Int,
  protected val receiveTimeoutOpt: Option[FiniteDuration],
  protected val connectTimeout: FiniteDuration,
  protected val maxWriteBatchSize: Int,
  protected val tcpSendBufferSizeHint: Int,
  protected val tcpReceiveBufferSizeHint: Int,
  protected val akkaListenerDispatcherPath: String,
  protected val akkaIODispatcherPath: String,
  protected val akkaDecoderDispatcherPath: String
) extends Connection with LazyLogging {
  
  private val shutdownLatch = new CountDownLatch(1)
  
  @volatile protected var isShuttingDown = false
  
  override implicit val dispatcher: ExecutionContext = system.dispatcher
  
  protected val listenerActor: ActorRef
  
  protected def updateState(request: Request[_]): Unit = request match {
    case Auth(password, username) => if (password.isEmpty) {
      authOpt = None
    } else {
      authOpt = Some(AuthConfig(username, password))
    }
    case Select(db) => database = db
    case ClientSetName(name) => if (name.isEmpty) {
      nameOpt = None
    } else {
      nameOpt = Some(name)
    }
    case Quit() | Shutdown(_) =>
      logger.info(s"Shutting down connection to ${host}:${port}")
      isShuttingDown = true
    case _            =>
  }
  
  protected def getAuthOpt: Option[AuthConfig] = authOpt
  protected def getDatabase: Int = database
  protected def getNameOpt: Option[String] = nameOpt
  
  protected def watchTermination(): Unit = system.actorOf(
    Props(
      classOf[WatchActor],
      listenerActor,
      shutdownLatch
    )
  )
  
  /**
   * Waits for all the internal actors to be shutdown.
   * 
   * @note This method is usually called after issuing a QUIT command
   * 
   * @param timeout amount of time to wait
   */
  def awaitTermination(timeout: Duration = Duration.Inf): Unit = {
    if (timeout.isFinite) {
      shutdownLatch.await(timeout.toMillis, TimeUnit.MILLISECONDS)
    } else {
      shutdownLatch.await()
    }
  }

  def isTerminated: Boolean =
    shutdownLatch.getCount == 0
}

class WatchActor(actor: ActorRef, shutdownLatch: CountDownLatch) extends Actor with ActorLogging {
  context.watch(actor)

  def receive: Receive = {
    case Terminated(_) =>
      log.info("AkkaConnection actor terminated {}", actor)
      shutdownLatch.countDown()
      context.stop(self)
  }

}