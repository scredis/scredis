package scredis.io

import java.net.InetSocketAddress

import akka.actor._
import akka.io.{IO, Tcp}
import akka.util.ByteString
import scredis.exceptions.RedisIOException
import scredis.protocol.{Protocol, Request}

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._
import scala.language.postfixOps

class IOActor(
  listenerActor: ActorRef,
  remote: InetSocketAddress,
  connectTimeout: FiniteDuration,
  maxWriteBatchSize: Int,
  tcpSendBufferSizeHint: Int,
  tcpReceiveBufferSizeHint: Int
) extends Actor with ActorLogging {
  
  import IOActor._
  import Tcp._
  import context.{dispatcher, system}
  
  private val scheduler = context.system.scheduler
  
  private val bufferPool = new scredis.util.BufferPool(
    maxCapacity = 1,
    maxBufferSize = maxWriteBatchSize + (0.25 * maxWriteBatchSize).toInt
  )
  
  private var batch: Seq[Request[_]] = Nil
  private var canWrite = false
  private var timeoutCancellableOpt: Option[Cancellable] = None
  
  protected val requests = new java.util.LinkedList[Request[_]]()
  protected var connection: ActorRef = _
  
  protected def connect(): Unit = {
    log.info(s"Connecting to $remote")
    IO(Tcp) ! Connect(
      remoteAddress = remote,
      options = List[akka.io.Inet.SocketOption](
        SO.KeepAlive(true),
        SO.TcpNoDelay(true),
        SO.ReuseAddress(true),
        SO.SendBufferSize(tcpSendBufferSizeHint),
        SO.ReceiveBufferSize(tcpReceiveBufferSizeHint)
      ),
      timeout = Some(connectTimeout)
    )
    timeoutCancellableOpt = Some {
      scheduler.scheduleOnce(2 seconds, self, ConnectTimeout)
    }
  }
  
  protected def requeueBatch(): Unit = {
    batch.foreach(requests.push)
    batch = Nil
  }
  
  protected def abort(): Unit = {
    listenerActor ! ListenerActor.Abort
    become(awaitingAbort)
  }
  
  protected def encode(request: Request[_]): Int = {
    request.encode()
    request.encoded match {
      case Left(bytes) => bytes.length
      case Right(buffer) => buffer.remaining
    }
  }
  
  protected def write(requests: Seq[Request[_]], lengthOpt: Option[Int] = None): Unit = {
    val length = lengthOpt.getOrElse {
      requests.foldLeft(0)((length, request) => length + encode(request))
    }
    val buffer = bufferPool.acquire(length)
    requests.foreach { request =>
      request.encoded match {
        case Left(bytes) => buffer.put(bytes)
        case Right(buff) =>
          buffer.put(buff)
          Protocol.releaseBuffer(buff)
      }
    }
    buffer.flip()
    val data = ByteString(buffer)
    log.debug(s"Writing data: ${data.decodeString("UTF-8")}")
    connection ! Write(data, WriteAck)
    bufferPool.release(buffer)
    canWrite = false
    this.batch = requests
  }
  
  protected def write(): Unit = {
    if (this.batch.nonEmpty) {
      requeueBatch()
    }
    if (requests.isEmpty) {
      canWrite = true
      return
    }
    
    var length = 0
    val batch = ListBuffer[Request[_]]()
    while (!requests.isEmpty && length < maxWriteBatchSize) {
      val request = requests.pop()
      length += encode(request)
      batch += request
    }
    write(batch.toList, Some(length))
  }
  
  protected def always: Receive = {
    case Shutdown => abort()
    case Terminated(_) =>
      listenerActor ! ListenerActor.Shutdown
      become(awaitingShutdown)
  }
  
  protected def fail: Receive = {
    case x => log.error(s"Received unhandled message: $x")
  }
  
  protected def become(state: Receive): Unit = context.become(state orElse always orElse fail)
  
  override def preStart(): Unit = {
    connect()
    become(connecting)
  }
  
  def receive: Receive = fail
  
  def connecting: Receive = {
    case Connected(remoteConnected, localAddress) =>
      log.info(s"Connected to $remoteConnected from $localAddress")
      connection = sender()
      connection ! Register(listenerActor)
      context.watch(connection)
      listenerActor ! ListenerActor.Connected
      timeoutCancellableOpt.foreach(_.cancel())
      canWrite = true
      requeueBatch()
      write()
      become(connected)
    case CommandFailed(_: Connect) =>
      log.error(s"Could not connect to $remote: Command failed")
      timeoutCancellableOpt.foreach(_.cancel())
      context.stop(self)
    case ConnectTimeout =>
      log.error(s"Could not connect to $remote: Connect timeout")
      context.stop(self)
  }
  
  def connected: Receive = {
    case request: Request[_] =>
      requests.addLast(request)
      if (canWrite) {
        write()
      }
    case WriteAck =>
      batch = Nil
      write()
    case CommandFailed(_: Write) =>
      log.error(s"Write failed")
      write()
  }
  
  def awaitingShutdown: Receive = {
    case request: Request[_] =>
      request.failure(RedisIOException("Connection is being shutdown"))
      listenerActor ! ListenerActor.Remove(1)
    case WriteAck =>
    case CommandFailed(_: Write) =>
    case ShutdownAck => context.stop(self)
  }
  
  def awaitingAbort: Receive = {
    case request: Request[_] =>
      request.failure(RedisIOException("Connection is being reset"))
      listenerActor ! ListenerActor.Remove(1)
    case WriteAck =>
    case CommandFailed(_: Write) =>
    case AbortAck =>
      connection ! Abort
      timeoutCancellableOpt = Some {
        scheduler.scheduleOnce(3 seconds, self, AbortTimeout)
      }
      become(aborting)
  }
  
  def aborting: Receive = {
    case WriteAck =>
    case CommandFailed(_: Write) =>
    case Aborted =>
    case Terminated(_) =>
      timeoutCancellableOpt.foreach(_.cancel())
      context.stop(self)
    case AbortTimeout =>
      log.error(s"A timeout occurred while resetting the connection")
      context.stop(connection)
  }
  
}

object IOActor {
  object WriteAck extends Tcp.Event
  case object ConnectTimeout
  case object AbortTimeout
  case object AbortAck
  case object ShutdownAck
  case object Shutdown
}
