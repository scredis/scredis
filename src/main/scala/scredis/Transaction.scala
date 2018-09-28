package scredis

import scredis.protocol.Request
import scredis.protocol.requests.TransactionRequests.{Exec, Multi}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

private[scredis] final case class Transaction (requests: Seq[Request[_]]) {
  val execRequest = Exec(requests.map(_.decode))
  private val future = execRequest.future
  
  future.onComplete {
    case Success(results) =>
      var i = 0
      requests.foreach { request =>
        if (!request.future.isCompleted) {
          results.apply(i) match {
            case Success(x) => request.success(x)
            case Failure(e) => request.failure(e)
          }
        }
        i += 1
      }
    case Failure(e) => requests.foreach { request =>
      if (!request.future.isCompleted) {
        request.failure(e)
      }
    }
  }
  
  override def toString: String = requests.mkString("Transaction(", ", ", ")")
}

private[scredis] object Transaction {
  val MultiRequest = Multi()
}