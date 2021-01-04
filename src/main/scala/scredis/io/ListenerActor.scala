package scredis.io

import java.net.InetSocketAddress
import java.util

import akka.actor._
import akka.io.Tcp
import akka.routing._
import akka.util.ByteString
import scredis.{Subscription, Transaction}
import scredis.exceptions.RedisIOException
import scredis.protocol.requests.ConnectionRequests.{Auth, Quit, Select}
import scredis.protocol.requests.ServerRequests
import scredis.protocol.{AuthConfig, Protocol, Request}
import scredis.util.UniqueNameGenerator

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scredis.Transaction._

class ListenerActor(
  host: String,
  port: Int,
  var authOpt: Option[AuthConfig],
  var database: Int,
  var nameOpt: Option[String],
  var decodersCount: Int,
  receiveTimeoutOpt: Option[FiniteDuration],
  connectTimeout: FiniteDuration,
  maxWriteBatchSize: Int,
  tcpSendBufferSizeHint: Int,
  tcpReceiveBufferSizeHint: Int,
  akkaIODispatcherPath: String,
  akkaDecoderDispatcherPath: String,
  failCommandOnConnecting: Boolean
) extends Actor with ActorLogging {
  
  import ListenerActor._
  import context.dispatcher
  
  override val supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case e: Exception => SupervisorStrategy.Stop
  }
  
  private val remote = new InetSocketAddress(host, port)
  
  private var remainingByteStringOpt: Option[ByteString] = None
  private var initializationRequestsCount = 0
  private var isConnecting = false
  private var isShuttingDown = false
  private var isShuttingDownBeforeConnected = false
  private var isReceiveTimeout = false
  private var timeoutCancellableOpt: Option[Cancellable] = None
  
  protected val queuedRequests = new util.LinkedList[Request[_]]()
  protected val requests = new util.LinkedList[Request[_]]()
  protected var ioActor: ActorRef = _
  protected var decoders: Router = _

  protected val decodingSubscription: Option[Subscription] = None

  private def createIOActor(): ActorRef = context.actorOf(
    Props(
      classOf[IOActor],
      self,
      remote,
      connectTimeout,
      maxWriteBatchSize,
      tcpSendBufferSizeHint,
      tcpReceiveBufferSizeHint
    ).withDispatcher(akkaIODispatcherPath),
    UniqueNameGenerator.getUniqueName(s"${nameOpt.getOrElse(s"$host-$port")}-io-actor")
  )
  
  private def createDecodersRouter(): Router = {
    val routees = Vector.fill(decodersCount) {
      val ref = context.actorOf(
        Props(classOf[DecoderActor], decodingSubscription).withDispatcher(akkaDecoderDispatcherPath),
        UniqueNameGenerator.getNumberedName(s"${nameOpt.getOrElse(s"$host-$port")}-decoder-actor")
      )
      context.watch(ref)
      ActorRefRoutee(ref)
    }
    Router(SmallestMailboxRoutingLogic(), routees)
  }
  
  protected def doSend(request: Request[_]): Unit = {
    receiveTimeoutOpt.foreach { receiveTimeout =>
      if (timeoutCancellableOpt.isEmpty) {
        timeoutCancellableOpt = Some {
          context.system.scheduler.scheduleOnce(receiveTimeout, self, ReceiveTimeout)
        }
      }
    }
    requests.addLast(request)
    ioActor ! request
  }
  
  protected def send(requests: Request[_]*): Unit = {
    var isShuttingDown = false
    requests.foreach { request =>
      if (isShuttingDown) {
        request.failure(RedisIOException("Shutting down"))
      } else {
        request match {
          case auth @ Auth(password, username) =>
            authOpt = Some(AuthConfig(username, password))
            doSend(auth)
          case select @ Select(db) =>
            database = db
            doSend(select)
          case setName @ ServerRequests.ClientSetName(name) => if (name.isEmpty) {
            nameOpt = None
            doSend(setName)
          } else {
            nameOpt = Some(name)
            doSend(setName)
          }
          case request @ (Quit() | ServerRequests.Shutdown(_)) =>
            isShuttingDown = true
            this.isShuttingDown = true
            doSend(request)
            become(shuttingDown)
            // FIXME shutdown request does not complete on success
          case _ => doSend(request)
        }
      }
    }
  }
  
  protected def sendAllQueuedRequests(): Unit ={
    while (!queuedRequests.isEmpty) {
      send(queuedRequests.pop())
    }
  }
  
  protected def failAllQueuedRequests(throwable: Throwable): Unit = {
    while (!queuedRequests.isEmpty) {
      queuedRequests.pop().failure(throwable)
    }
  }
  
  protected def failAllSentRequests(throwable: Throwable): Unit = {
    while (!requests.isEmpty) {
      requests.pop().failure(throwable)
    }
  }
  
  protected def handleData(data: ByteString, responsesCount: Int): Unit = {
    val requestsCount = this.requests.size
    val (count, skip) = if (responsesCount > requestsCount) {
      (requestsCount, responsesCount - requestsCount)
    } else {
      (responsesCount, 0)
    }
    val requests = ListBuffer[Request[_]]()
    for (i <- 1 to count) {
      requests += this.requests.pop()
    }
    decoders.route(DecoderActor.Partition(data, requests.toList.iterator, skip), self)
  }
  
  protected def receiveData(data: ByteString): Int = {
    log.debug(s"Received data: ${data.decodeString("UTF-8").replace("\r\n", "\\r\\n")}")
    
    timeoutCancellableOpt.foreach(_.cancel())
    timeoutCancellableOpt = None
    
    val completedData = remainingByteStringOpt match {
      case Some(remains) => remains ++ data
      case None => data
    }
    
    val buffer = completedData.asByteBuffer
    val responsesCount = Protocol.count(buffer)
    val position = buffer.position()
    
    if (buffer.remaining > 0) {
      remainingByteStringOpt = Some(ByteString(buffer))
    } else {
      remainingByteStringOpt = None
    }
    
    if (responsesCount > 0) {
      val trimmedData = if (remainingByteStringOpt.isDefined) {
        completedData.take(position)
      } else {
        completedData
      }
      handleData(trimmedData, responsesCount)
    }
    responsesCount
  }
  
  protected def unhandled: Receive = {
    case x => log.error(s"Received unexpected message: $x")
  }
  
  protected def always: Receive = {
    case Remove(count) =>
      (1 to count).foreach(_ => requests.pop())
    case Abort =>
      ioActor ! IOActor.AbortAck
      become(reconnecting)
    case Shutdown =>
      ioActor ! IOActor.ShutdownAck
      become(reconnecting)
    case _: Tcp.ConnectionClosed =>
  }
  
  protected def fail: Receive = {
    case request: Request[_] => request.failure(RedisIOException("Shutting down"))
    case transaction: Transaction => transaction.execRequest.failure(
      RedisIOException("Shutting down")
    )
  }
  
  protected def queue: Receive = {
    case request: Request[_] => queuedRequests.addLast(request)
    case t @ Transaction(requests) =>
      queuedRequests.addLast(MultiRequest)
      requests.foreach { request =>
        queuedRequests.addLast(request)
      }
      queuedRequests.addLast(t.execRequest)
  }
  
  protected def sendReceive: Receive = {
    case request: Request[_] =>
      send(request)
    case t @ Transaction(requests) =>
      send(MultiRequest)
      send(requests: _*)
      send(t.execRequest)
  }
  
  protected def become(state: Receive): Unit = context.become(state orElse always orElse unhandled)
  
  protected def reconnect(): Unit = {
    ioActor = createIOActor()
    context.watch(ioActor)
    isConnecting = true
    isReceiveTimeout = false
    become(connecting)
  }
  
  protected def handleReceiveTimeout(): Unit = {
    log.error("Receive timeout")
    isReceiveTimeout = true
    remainingByteStringOpt = None
    timeoutCancellableOpt = None
    ioActor ! IOActor.Shutdown
    become(reconnecting)
  }
  
  protected def onConnect(): Unit = ()
  protected def onInitialized(): Unit = ()
  
  protected def shutdown(): Unit = {
    decoders.route(Broadcast(PoisonPill), self)
    become(awaitingDecodersShutdown)
  }
  
  override def preStart(): Unit = {
    ioActor = createIOActor()
    decoders = createDecodersRouter()
    context.watch(ioActor)
    isConnecting = true
    become(connecting)
  }
  
  def receive: Receive = unhandled
  
  def connecting: Receive = {
    case request: Quit =>
      request.success(())
      failAllQueuedRequests(RedisIOException("Connection has been shutdown by QUIT command"))
      isShuttingDownBeforeConnected = true
      shutdown()
    case request: Request[_] =>
      if(failCommandOnConnecting) {
        request.failure(RedisIOException("Trying to connect..."))
      } else{
        queuedRequests.addLast(request)
      }
      if (!isConnecting) {
        reconnect()
      }
    case t @ Transaction(requests) =>
      queuedRequests.addLast(MultiRequest)
      requests.foreach { request =>
        queuedRequests.addLast(request)
      }
      queuedRequests.addLast(t.execRequest)
      if (!isConnecting) {
        reconnect()
      }
    case Connected =>
      isConnecting = false

      if (isShuttingDownBeforeConnected) {
        shutdown()
      } else {
        onConnect()

        val authRequestOpt = authOpt.map { auth =>
          Auth(auth.password, auth.username)
        }
        val selectRequestOpt = if (database > 0) {
          Some(Select(database))
        } else {
          None
        }
        val setNameRequestOpt = nameOpt.map { name =>
          ServerRequests.ClientSetName(name)
        }

        val authFuture = authRequestOpt match {
          case Some(request) => request.future
          case None => Future.successful(())
        }
        val selectFuture = selectRequestOpt match {
          case Some(request) => request.future
          case None => Future.successful(())
        }
        val setNameFuture = setNameRequestOpt match {
          case Some(request) => request.future
          case None => Future.successful(())
        }

        val requests = List[Option[Request[Unit]]](
          authRequestOpt, selectRequestOpt, setNameRequestOpt
        ).flatten

        initializationRequestsCount = requests.size

        if (initializationRequestsCount > 0) {
          send(requests: _*)
          become(initializing)
        } else {
          onInitialized()
          sendAllQueuedRequests()
          if (isShuttingDown) {
            become(shuttingDown)
          } else {
            become(initialized)
          }
        }

        authFuture.failed.foreach { e =>
          log.error(e, s"Could not authenticate to $remote")
        }
        selectFuture.failed.foreach { e =>
          log.error(e, s"Could not select database '$database' in $remote")
        }
        setNameFuture.failed.foreach { e =>
          log.error(e, s"Could not set client name in $remote")
        }
      }
    case ReceiveTimeout =>
    case Terminated(_) =>
      isConnecting = false
      failAllQueuedRequests(RedisIOException(s"Could not connect to $remote"))
  }
  
  def initializing: Receive = queue orElse {
    case Tcp.Received(data) =>
      val responsesCount = receiveData(data)
      initializationRequestsCount -= responsesCount
      if (initializationRequestsCount == 0) {
        onInitialized()
        sendAllQueuedRequests()
        if (isShuttingDown) {
          become(shuttingDown)
        } else {
          become(initialized)
        }
      }
    case ReceiveTimeout => handleReceiveTimeout()
    case Terminated(_) =>
      log.error(s"Could not initialize connection to $remote")
      failAllQueuedRequests(RedisIOException(s"Could not initialize connection to $remote"))
      reconnect()
  }
  
  def initialized: Receive = sendReceive orElse {
    case Tcp.Received(data) => receiveData(data)
    case ReceiveTimeout => handleReceiveTimeout()
    case Terminated(_) =>
      log.info("Connection has been shutdown abruptly")
      failAllSentRequests(RedisIOException("Connection has been shutdown abruptly"))
      reconnect()
  }
  
  def reconnecting: Receive = queue orElse {
    case Tcp.Received(_) =>
    case ReceiveTimeout =>
    case Terminated(_) =>
      if (isReceiveTimeout) {
        log.info(s"Connection has been reset due to receive timeout")
        failAllSentRequests(RedisIOException("Receive timeout"))
      } else {
        log.info(s"Connection has been shutdown abruptly")
        failAllSentRequests(RedisIOException("Connection has been shutdown abruptly"))
      }
      reconnect()
  }
  
  def shuttingDown: Receive = fail orElse {
    case Tcp.Received(data) => receiveData(data)
    case ReceiveTimeout => handleReceiveTimeout()
    case Shutdown => ioActor ! IOActor.ShutdownAck
    case Terminated(_) => shutdown()
  }
  
  def awaitingDecodersShutdown: Receive = fail orElse {
    case Terminated(_) =>
      decodersCount -= 1
      if (decodersCount == 0) {
        log.info("Connection has been shutdown gracefully")
        context.stop(self)
      }
  }
  
}

object ListenerActor {
  case object Connected
  case object Abort
  case object Shutdown
  case class Remove(count: Int)
}
