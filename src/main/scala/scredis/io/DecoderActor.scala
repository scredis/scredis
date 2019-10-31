package scredis.io

import akka.actor.{Actor, ActorLogging}
import akka.util.ByteString
import scredis.PubSubMessage.Message
import scredis.exceptions.RedisProtocolException
import scredis.protocol.{ErrorResponse, Protocol, Request}
import scredis.{PubSubMessage, Subscription}

import scala.concurrent.{ExecutionContext, Future}

class DecoderActor(subscriptionOption: Option[Subscription]) extends Actor with ActorLogging {
  
  import DecoderActor._
  
  def receive: Receive = {
    case Partition(data, requests, skip) =>
      val buffer = data.asByteBuffer

      for (i <- 1 to skip) {
        try {
          Protocol.decode(buffer)
        } catch {
          case e: Throwable => log.error("Could not decode response", e)
        }
      }

      while (requests.hasNext) {
        val request = requests.next()
        try {
          val response = Protocol.decode(buffer)
          request.complete(response)
        } catch {
          case e: Throwable =>
            log.error("Could not decode response", e)
            request.failure(RedisProtocolException("Could not decode response", e))
        }
      }
    case SubscribePartition(data) =>
      val buffer = data.asByteBuffer
      while (buffer.remaining > 0) {
        try {
          val result = Protocol.decodePubSubResponse(Protocol.decode(buffer))
          result match {
            case Left(ErrorResponse(message)) =>
              sender ! SubscriberListenerActor.Fail(message)

            case Right(msgEither) => msgEither match {
              case Right(m: PubSubMessage.Subscribe) =>
                sender ! SubscriberListenerActor.Complete(m)

              case Right(m: PubSubMessage.PSubscribe) =>
                sender ! SubscriberListenerActor.Complete(m)

              case Right(m: PubSubMessage.Unsubscribe) =>
                sender ! SubscriberListenerActor.Complete(m)

              case Right(m: PubSubMessage.PUnsubscribe) =>
                sender ! SubscriberListenerActor.Complete(m)

              case Right(m: PubSubMessage.Error) =>
                sender ! SubscriberListenerActor.Complete(m)

              case Right(m: PubSubMessage.Message) =>
              case Right(m: PubSubMessage.PMessage) =>

              case Left(value) =>
                sender ! SubscriberListenerActor.Confirm(value)
            }
          }
          result match {
            case Right(Right(message)) =>
              subscriptionOption match {
                case Some(subscription) =>
                  Future {subscription.apply(message)}(ExecutionContext.global)
                case None =>
                  log.error("Received SubscribePartition without any subscription")
              }
            case _ =>
          }
        } catch {
          case e: Throwable =>
            val msg = data.decodeString("UTF-8").replace("\r\n", "\\r\\n")
            log.error(s"Could not decode PubSubMessage: $msg", e)
        }
      }
    case x => log.error(s"Received unexpected message: $x")
  }
  
}

object DecoderActor {
  case class Partition(data: ByteString, requests: Iterator[Request[_]], skip: Int)
  case class SubscribePartition(data: ByteString)
}
