package scredis

import org.scalameter.api._
import org.scalameter.picklers.Implicits._
import akka.actor.ActorSystem
import org.scalameter.execution.SeparateJvmsExecutor

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
object ClientBenchmark extends Bench[Double] {
  
  private var system: ActorSystem = _
  private var client: Client = _
  
  /* configuration */
  override lazy val measurer = new Measurer.Default
  override lazy val executor = SeparateJvmsExecutor(
    new Executor.Warmer.Default,
    Aggregator.average,
    measurer
  )
  override lazy val reporter = Reporter.Composite(
    new RegressionReporter(
      RegressionReporter.Tester.Accepter(),
      RegressionReporter.Historian.Complete()
    ),
    HtmlReporter(embedDsv = true)
  )
  override lazy val persistor: Persistor = Persistor.None
  
  /* inputs */

  final val milion = 1_000_000
  final val sizes: Gen[Int] = Gen.range("size")(milion, 3 * milion, milion)

  /* tests */

  performance of "Client" in {
    measure method "PING" in {
      using(sizes) config {
        exec.maxWarmupRuns -> 3
        exec.benchRuns -> 3
        exec.independentSamples -> 3
      } setUp { _ =>
        system = ActorSystem()
        client = Client()(system)
      } tearDown { _ =>
        Await.result(client.quit(), 2.seconds)
        Await.result(system.terminate(), 10.seconds)
        client = null
        system = null
      } in { i =>
        implicit val ec = system.dispatcher
        val future = Future.traverse(1 to i) { _ =>
          client.ping()
        }
        Await.result(future, 30.seconds)
      }
    }
    
    measure method "GET" in {
      using(sizes) config {
        exec.maxWarmupRuns -> 3
        exec.benchRuns -> 3
        exec.independentSamples -> 3
      } setUp { _ =>
        system = ActorSystem()
        client = Client()(system)
        Await.result(client.set("foo", "bar"), 2.seconds)
      } tearDown { _ =>
        Await.result(client.del("foo"), 2.seconds)
        Await.result(client.quit(), 2.seconds)
        Await.result(system.terminate(), 10.seconds)
        client = null
        system = null
      } in { i =>
        implicit val ec = system.dispatcher
        val future = Future.traverse(1 to i) { _ =>
          client.get("foo")
        }
        Await.result(future, 30.seconds)
      }
    }
    
    measure method "SET" in {
      using(sizes) config {
        exec.maxWarmupRuns -> 3
        exec.benchRuns -> 3
        exec.independentSamples -> 3
      } setUp { _ =>
        system = ActorSystem()
        client = Client()(system)
      } tearDown { _ =>
        Await.result(client.del("foo"), 2.seconds)
        Await.result(client.quit(), 2.seconds)
        Await.result(system.terminate(), 10.seconds)
        client = null
        system = null
      } in { i =>
        implicit val ec = system.dispatcher
        val future = Future.traverse(1 to i) { _ =>
          client.set("foo", "bar")
        }
        Await.result(future, 30.seconds)
      }
    }
  }
}