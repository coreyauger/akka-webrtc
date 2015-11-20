package io.surfkit.client

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{ErrorEvent, CloseEvent, MessageEvent, Event}
import org.scalajs.dom.raw.DOMError

import scala.scalajs.js
import io.surfkit.clientlib.webrtc._
import org.scalajs.dom.experimental.webrtc._
import scala.concurrent.ExecutionContext.Implicits.global


class WebSocketSignaler extends Peer.ModelTransformPeerSignaler[m.RTCSignal]{
  var ws = new dom.WebSocket(s"ws://localhost:8080/ws/1")
  ws.onmessage = { x: MessageEvent =>
    println("WS onmessage")
    val msg = upickle.default.read[m.ApiMessage](x.data.toString)
    println(s"MODEL ... ${msg}")
    receive(toPeerSignaling(msg.data.asInstanceOf[m.RTCSignal]))
  }
  ws.onopen = { x: Event =>
    println("WS connection connected")
  }
  ws.onerror = { x: ErrorEvent =>
    println("some error has   occured " + x.message)
  }
  ws.onclose = { x: CloseEvent =>
    println("WS connection CLOSED !!")
  }

  override def toPeerSignaling(model:m.RTCSignal):Peer.Signaling = model match{
    case m.Signaling.Offer(offer) =>
      //println(s"toPeerSignaling offer ${offer}")
      val o = RTCSessionDescription(offer.`type`, offer.sdp)
      println("toPeerSignaling")
      println(s"toPeerSignaling offer.type : ${o.`type`}")
      println(s"toPeerSignaling offer.sdp : ${o.`sdp`}")
      Peer.Offer(RTCSessionDescription(offer.`type`, offer.sdp))
    case m.Signaling.Candidate(c) =>
      Peer.Candidate(RTCIceCandidate(c.candidate, c.sdpMLineIndex, c.sdpMid))
    case m.Signaling.Answer(answer) =>
      Peer.Answer(RTCSessionDescription(answer.`type`, answer.sdp))
    case m.Signaling.Error(error) =>
      Peer.Error(error)
    case _ =>
      Peer.Error("Unknown signaling type")
  }
  override def fromPeerSignaling(s:Peer.Signaling):m.RTCSignal = s match{
    case Peer.Offer(offer) =>
      m.Signaling.Offer(m.Signaling.RTCSessionDescription(offer.`type`, offer.sdp))
    case Peer.Candidate(c) =>
      m.Signaling.Candidate(m.Signaling.RTCIceCandidate(c.candidate, c.sdpMLineIndex, c.sdpMid))
    case Peer.Answer(answer) =>
      m.Signaling.Answer(m.Signaling.RTCSessionDescription(answer.`type`, answer.sdp))
    case Peer.Error(error) =>
      m.Signaling.Error(error)
    case _ =>
      m.Signaling.Error("Unknown signaling type")
  }

  override def sendModel(s:m.RTCSignal) = {
    println("SEND USING WS")
    ws.send(upickle.default.write(s))
  }
}

object WebRTCMain extends js.JSApp {
  def main(): Unit = {

    val signaler = new WebSocketSignaler

    val video = dom.document.createElement("video").asInstanceOf[dom.html.Video]
    dom.document.getElementById("playground").appendChild(video)

    val webRTC = new WebRTC[m.RTCSignal,WebSocketSignaler](signaler)
    webRTC.start(MediaConstraints(true, true)).foreach{ stream =>
      (video.asInstanceOf[js.Dynamic]).srcObject = stream
    }

  }
}