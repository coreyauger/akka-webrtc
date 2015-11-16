package io.surfkit.server

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import akka.util.Timeout
import m.{Model, ApiMessage}
import scala.util._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

trait WebSocket {
  def wsFlow(sender: String): Flow[String, m.Model, Unit]
  def injectMessage(message: m.Model): Unit
}

object WebSocket {

  def create(system: ActorSystem): WebSocket = {
    val coreActor =
      system.actorOf(Props(new Actor {
        var subscribers = Set.empty[ActorRef]
        def receive: Receive = {
          case Connect(id, subscriber) =>
            context.watch(subscriber)
            subscribers += subscriber
            // create user actor as a child of the subscriber
            println(s"$id joined!")
          case msg: ReceivedMessage =>
            // broadcast to all...
            subscribers.foreach(_ ! msg.toCoreMessage)
          case Disconnect(id) =>
            println(s"$id left!")
            println(s"Kill actor ${id}")
          case Terminated(sub) =>
            println("Terminated")
            subscribers -= sub // clean up dead subscribers
        }
      }))

    def wsInSink(uuid: String) = Sink.actorRef[SocketEvent](coreActor, Disconnect(uuid))

    new WebSocket {
      def wsFlow(id: String): Flow[String, m.Model, Unit] = {
        val in =
          Flow[String]
            .map(ReceivedMessage(id, _))
            .to(wsInSink(id))
        val out =
          Source.actorRef[ApiMessage](5, OverflowStrategy.fail)
            .mapMaterializedValue(coreActor ! Connect(id, _))

        Flow.wrap(in, out)(Keep.none)
      }
      def injectMessage(message: m.Model): Unit = coreActor ! message // non-streams interface
    }
  }

  private sealed trait SocketEvent
  private case class Connect(id: String, subscriber: ActorRef) extends SocketEvent
  private case class Disconnect(id: String) extends SocketEvent
  private case class ReceivedMessage(id: String, data: String) extends SocketEvent {
    def toCoreMessage: ApiMessage = ApiMessage(id, upickle.default.read[Model](data))
  }
}
