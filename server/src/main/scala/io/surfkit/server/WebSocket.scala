package io.surfkit.server

import akka.actor._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl._
import akka.util.Timeout
import m.Signaling.{Room, Join, PeerInfo}
import m.{Model, ApiMessage}
import scala.util._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

trait WebSocket {
  def wsFlow(sender: String): Flow[String, m.Model, Unit]
  def injectMessage(message: m.Model): Unit
}

object WebSocket {

  var rooms = Map.empty[String,Set[PeerInfo]]

  def create(system: ActorSystem): WebSocket = {
    val serverActor =
      system.actorOf(Props(new Actor {
        var subscribers = Map.empty[String, ActorRef]
        def receive: Receive = {
          case Connect(id, subscriber) =>
            context.watch(subscriber)
            subscribers += id -> subscriber
            // create user actor as a child of the subscriber
            println(s"$id joined!")
          case msg: ReceivedMessage =>
            println(s"Got message ${msg}")
            // broadcast to all...
            val api = msg.toApiMessage
            println(s"Api ${api}")
            api.data match{
              case Join(name, peer) =>
                println(s"Join ${peer}")
                rooms += name -> (rooms.get(name).getOrElse(Set.empty[PeerInfo]) + peer)
                val r = Room(name = name, peer = peer, config = Set(), rooms(name))
                subscribers.get(msg.id)foreach(_ ! r)
              case _ =>
                subscribers.values.foreach(_ ! api.data)
            }
          case Disconnect(id) =>
            // FIXME:..
            val peer = m.Signaling.PeerInfo(id, "video")
            rooms += "test" -> (rooms.get("test").getOrElse(Set.empty[PeerInfo]) - peer)

            println(s"$id left!")
            println(s"Kill actor ${id}")
            subscribers -= id
          case Terminated(sub) =>
            println("Terminated")

        }
      }))

    def wsInSink(uuid: String) = Sink.actorRef[SocketEvent](serverActor, Disconnect(uuid))

    new WebSocket {
      def wsFlow(id: String): Flow[String, m.Model, Unit] = {
        val in =
          Flow[String]
            .map(ReceivedMessage(id, _))
            .to(wsInSink(id))
        val out =
          Source.actorRef[ApiMessage](5, OverflowStrategy.fail)
            .mapMaterializedValue(serverActor ! Connect(id, _))

        Flow.wrap(in, out)(Keep.none)
      }
      def injectMessage(message: m.Model): Unit = serverActor ! message // non-streams interface
    }
  }

  private sealed trait SocketEvent
  private case class Connect(id: String, subscriber: ActorRef) extends SocketEvent
  private case class Disconnect(id: String) extends SocketEvent
  private case class ReceivedMessage(id: String, data: String) extends SocketEvent {
    def toApiMessage: ApiMessage = ApiMessage(id, upickle.default.read[m.Model](data))
  }
}
