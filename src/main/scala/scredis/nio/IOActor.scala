package scredis.nio

import com.typesafe.scalalogging.Logging
import com.codahale.metrics.MetricRegistry

import akka.io.{ IO, Tcp }
import akka.actor.{ Actor, ActorRef }
import akka.util.ByteString

import scredis.protocol.{ Protocol, Request }

import scala.util.Failure
import scala.collection.mutable.{ Queue => MQueue, ListBuffer }

import java.net.InetSocketAddress

case object ClosingException extends Exception("Connection is being closed")

class IOActor(remote: InetSocketAddress) extends Actor with Logging {
  
  import Tcp._
  import IOActor._
  import context.system
  
  /*
  private val requestMeter = scredis.protocol.NioProtocol.metrics.meter(
    MetricRegistry.name(getClass, "requestMeter")
  )
  
  private val bytesMeter = scredis.protocol.NioProtocol.metrics.meter(
    MetricRegistry.name(getClass, "bytesMeter")
  )
  
  private val writeTimer = scredis.protocol.NioProtocol.metrics.timer(
    MetricRegistry.name(getClass, "writeTimer")
  )
  private val waitTimer = scredis.protocol.NioProtocol.metrics.timer(
    MetricRegistry.name(getClass, "waitTimer")
  )
  private val tellTimer = scredis.protocol.NioProtocol.metrics.timer(
    MetricRegistry.name(getClass, "tellTimer")
  )*/
  
  private val bufferPool = new scredis.util.BufferPool(1)
  private val requests = MQueue[Request[_]]()
  
  private var canWrite = false
  var written = 0
  private var connection: ActorRef = _
  private var partitionerActor: ActorRef = _
  private var waitTimerContext: com.codahale.metrics.Timer.Context = _
  
  private def stop(): Unit = {
    logger.trace("Stopping Actor...")
    context.stop(self)
  }
  
  private def write(): Unit = {
    if (requests.isEmpty) {
      canWrite = true
      return
    }
    
    //val ctx = writeTimer.time()
    var i = 0
    var length = 0
    val batch = ListBuffer[Request[_]]()
    while (!requests.isEmpty && i < 5000) {
      val request = requests.dequeue()
      request.encode()
      length += {
        request.encoded match {
          case Left(bytes) => bytes.length
          case Right(buffer) => buffer.remaining
        }
      }
      batch += request
      i += 1
    }
    val buffer = bufferPool.acquire(length)
    batch.foreach { r =>
      r.encoded match {
        case Left(bytes) => buffer.put(bytes)
        case Right(buff) => {
          buffer.put(buff)
          Protocol.releaseBuffer(buff)
        }
      }
    }
    buffer.flip()
    val data = ByteString(buffer)
    logger.trace(s"Writing data: ${data.decodeString("UTF-8")}")
    //requestMeter.mark(batch.size)
    //bytesMeter.mark(length)
    connection ! Write(data, WriteAck)
    bufferPool.release(buffer)
    canWrite = false
    //ctx.stop()
    //waitTimerContext = waitTimer.time()
  }
  
  def receive: Receive = {
    case partitionerActor: ActorRef => {
      this.partitionerActor = partitionerActor
      logger.trace(s"Connecting to $remote...")
      IO(Tcp) ! Connect(remote)
      context.become(connecting)
    }
  }
  
  def connecting: Receive = {
    case c @ Connected(remote, local) => {
      logger.trace(s"Connected to $remote")
      connection = sender
      connection ! Register(self)
      while (!requests.isEmpty) {
        self ! requests.dequeue()
      }
      canWrite = true
      context.watch(connection)
      context.become(ready)
    }
    case CommandFailed(_: Connect) => {
      logger.error(s"Could not connect to $remote")
      stop()
    }
    case request: Request[_] => requests.enqueue(request)
  }
  
  import context.dispatcher
  import scala.concurrent.duration._
  
  def ready: Receive = {
    case Received(data) => {
      logger.trace(s"Received data: ${data.decodeString("UTF-8")}")
      //val ctx = tellTimer.time()
      partitionerActor ! data
      //ctx.stop()
    }
    case WriteAck => {
      //waitTimerContext.stop()
      write()
    }
    case request: Request[_] => {
      //val data = ByteString(request.encoded)
      //logger.trace(s"Writing data: ${data.decodeString("UTF-8")}")
      requests.enqueue(request)
      if (canWrite) {
        write()
      }
    }
    case CommandFailed(x) => logger.error(s"Command failed: $x")
    case Close => {
      logger.trace(s"Closing connection...")
      connection ! Close
      context.become(closing)
    }
    case _: ConnectionClosed => {
      logger.debug(s"Connection has been closed by the server")
      stop()
    }
    case x => logger.error(s"Invalid message received from: $sender: $x")
  }
  
  def closing: Receive = {
    case CommandFailed(c: CloseCommand) => {
      logger.warn(s"Connection could not be closed. Aborting...")
      connection ! Tcp.Abort
      stop()
    }
    case _: ConnectionClosed => {
      logger.debug(s"Connection has been closed")
      stop()
    }
    case request: Request[_] => {
      request.failure(ClosingException)
    }
  }
  
}

object IOActor {
  object WriteAck extends Tcp.Event
  case class Batch(requests: Seq[Request[_]], length: Int)
}