package scredis.io

import java.net.InetSocketAddress

import akka.Done
import akka.actor._
import akka.io.Tcp.SO
import akka.stream.QueueOfferResult.Enqueued
import akka.stream.TLSProtocol.NegotiateNewSession
import akka.stream.scaladsl.{Sink, Source, Tcp}
import akka.stream.{ActorMaterializer, OverflowStrategy, QueueOfferResult}
import akka.util.ByteString
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import scredis.exceptions.RedisIOException
import scredis.protocol.{Protocol, Request}

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class IOActor(
  listenerActor: ActorRef,
  remote: InetSocketAddress,
  connectTimeout: FiniteDuration,
  maxWriteBatchSize: Int,
  tcpSendBufferSizeHint: Int,
  tcpReceiveBufferSizeHint: Int,
  sslSettings: Option[(String, String)]
) extends Actor with ActorLogging {

  implicit val mat: ActorMaterializer = ActorMaterializer()

  import IOActor._
  import context.system

  val maxBuffSize: Int = maxWriteBatchSize + (0.25 * maxWriteBatchSize).toInt

  private val bufferPool = new scredis.util.BufferPool(
    maxCapacity = 1,
    maxBufferSize = maxBuffSize
  )

  private var batch: Seq[Request[_]] = Nil
  private var canWrite = false

  protected val requests = new java.util.LinkedList[Request[_]]()

  protected val sink = Sink.foreach[ByteString](data => listenerActor ! akka.io.Tcp.Received(data))
  protected val (queue, source) = Source.queue[ByteString](maxBuffSize, OverflowStrategy.fail).preMaterialize()

  protected def createSSLContext(keystore: String, password: String): SSLContext = {
    import java.io.{FileInputStream, IOException}
    import java.security.KeyStore
    try {
      val passphrase = password.toCharArray
      val ctx = SSLContext.getInstance("TLS")
      val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      val ks = KeyStore.getInstance("JKS")
      ks.load(new FileInputStream(keystore), passphrase)
      kmf.init(ks, passphrase)

      val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
      tmf.init(ks)

      ctx.init(kmf.getKeyManagers, tmf.getTrustManagers, null)


      ctx
    } catch {
      case e: Exception =>
        log.error(e, "Unable to create proper SSL Context")
        throw new IOException(e.getMessage)
    }
  }

  protected def connect(): Unit = {
    log.info(s"Connecting to $remote")

    val soptions = List[akka.io.Inet.SocketOption](
      SO.KeepAlive(true),
      SO.TcpNoDelay(true),
      SO.ReuseAddress(true),
      SO.SendBufferSize(tcpSendBufferSizeHint),
      SO.ReceiveBufferSize(tcpReceiveBufferSizeHint)
    )

    val connection = sslSettings match {
      case Some((keystore, password)) =>
        val sslContext = createSSLContext(keystore, password)
        Tcp().outgoingTlsConnection(remote, sslContext, NegotiateNewSession, options = soptions, connectTimeout = 2.seconds)
      case None =>
        Tcp().outgoingConnection(remote, options = soptions, connectTimeout = 2.seconds)
    }

    val t = connection.runWith(source, sink)
    t._2.onComplete {
      case Success(Done) => self ! ConnectCompleted
      case Failure(exception) => self ! ConnectFailure(exception)
    }(context.dispatcher)

    listenerActor ! ListenerActor.Connected
    canWrite = true
    write()
    become(connected)
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
    val qresult: Future[QueueOfferResult] = queue.offer(data)
    import akka.pattern.pipe
    implicit val ec = context.dispatcher
    qresult.pipeTo(self)
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
  }
  
  def receive: Receive = fail

  def connected: Receive = {
    case request: Request[_] =>
      requests.addLast(request)
      if (canWrite) {
        write()
      }
    case Enqueued =>
      batch = Nil
      write()

    case ConnectCompleted =>
      log.info(s"Connection stream closed.")

    case ConnectFailure(ex) =>
      log.error(ex, s"Connection stream encountered error, aborting")
      abort()
  }
  
  def awaitingShutdown: Receive = {
    case request: Request[_] =>
      request.failure(RedisIOException("Connection is being shutdown"))
      listenerActor ! ListenerActor.Remove(1)
    case Enqueued =>
    case ShutdownAck => context.stop(self)
  }
  
  def awaitingAbort: Receive = {
    case request: Request[_] =>
      request.failure(RedisIOException("Connection is being reset"))
      listenerActor ! ListenerActor.Remove(1)
    case Enqueued =>
    case AbortAck =>
      queue.complete()
      become(aborting)
  }
  
  def aborting: Receive = {
    case Enqueued =>
    case Terminated(_) =>
      context.stop(self)
  }
}

object IOActor {
  case object ConnectCompleted
  case class ConnectFailure(ex: Throwable)


  case object AbortAck
  case object ShutdownAck
  case object Shutdown
}
