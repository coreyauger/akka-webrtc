package io.surfkit.server


/**
 * Created by suroot on 04/11/15.
 */

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.{ Message, TextMessage, BinaryMessage }
import akka.stream.stage._
import akka.http.scaladsl.server.Route
import com.typesafe.config.ConfigFactory
import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Flow}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{Await, Future}


class Webservice(implicit fm: Materializer, system: ActorSystem) extends Directives {

  val configuration = ConfigFactory.load()

  val wsFlow = WebSocket.create(system)

  // Frontend
  def index = (path("") | pathPrefix("index.html")) {
      getFromResource("index.html")
    }
  def advanced =(path("") | pathPrefix("advanced.html")) {
      getFromResource("advanced.html")
    }
  def css = (pathPrefix("css") & path(Segment)) { resource => getFromResource(s"css/$resource") }
  def fonts = (pathPrefix("fonts") & path(Segment)) { resource => getFromResource(s"fonts/$resource") }
  def img = (pathPrefix("img") & path(Segment)) { resource => getFromResource(s"img/$resource") }
  def js = (pathPrefix("js") & path(Segment)) { resource => getFromResource(s"js/$resource") }

  def route: Route =
    get {
      index ~ advanced ~ css ~ fonts ~ img ~ js
    } ~
        path( "ws" / """\d+""".r) {  id =>
          println("Hanndling WS connection")
          handleWebsocketMessages(websocketFlow(sender = id))
        }

  def websocketFlow(sender: String): Flow[Message, Message, Unit] =
    Flow[Message]
      .mapConcat {
        case TextMessage.Strict(msg) =>
          //println(s"mapConcat: ${msg}")
          TextMessage.Strict(msg) :: Nil

        case other: TextMessage =>
          //println(s"Got other text ^^^^^^^^^^^^^^^^")
          //other.textStream.runWith(Sink.ignore)
          // FIXME: how do we do this without blocking :( ?
          val txt = Await.result(other.textStream.runWith(Sink.fold("")( (a, b) => a+b )).map{ msg =>
            TextMessage.Strict(msg)
          }, 1 second)
          txt :: Nil

        case other: BinaryMessage =>
          println(s"Got other binary $other")
          other.dataStream.runWith(Sink.ignore)
          Nil
      }
      .collect {
        case TextMessage.Strict(msg) =>
          //println(s"collect: ${msg}")
          msg // unpack incoming WS text messages...
      }
      .via(wsFlow.wsFlow(sender))
      .map {
        case c:m.Model => {
          TextMessage.Strict(upickle.default.write(c)) // ... pack outgoing messages into WS JSON messages ...
        }
      }
      .via(reportErrorsFlow) // ... then log any processing errors on stdin

  def reportErrorsFlow[T]: Flow[T, T, Unit] =
    Flow[T]
      .transform(() ⇒ new PushStage[T, T] {
        def onPush(elem: T, ctx: Context[T]): SyncDirective = ctx.push(elem)
        override def onUpstreamFailure(cause: Throwable, ctx: Context[T]): TerminationDirective = {
          println(s"WS stream failed with $cause")
          super.onUpstreamFailure(cause, ctx)
        }
      })
}
