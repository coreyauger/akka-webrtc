package io.surfkit.server


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.stream.ActorMaterializer
import akka.util.Timeout
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App {
  import system.dispatcher
  implicit val system = ActorSystem("webrtc")
  implicit val materializer = ActorMaterializer()

  println("AkkaCoreService Started.")
  val service = new Webservice

  println("Binding address")
  val binding = Http().bindAndHandle(interface = "0.0.0.0", port = 8080, handler = service.route)
  binding.onComplete {
    case Success(binding) ⇒
      val localAddress = binding.localAddress
      println(s"Server is listening on ${localAddress.getHostName}:${localAddress.getPort}")
    case Failure(e) ⇒
      println(s"Binding failed with ${e.getMessage}")
      system.terminate()
  }

}
