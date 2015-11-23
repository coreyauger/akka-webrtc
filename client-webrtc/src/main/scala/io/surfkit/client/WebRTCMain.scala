package io.surfkit.client

import java.util.UUID

import io.surfkit.clientlib.webrtc.Peer.PeerInfo
import org.scalajs.dom.raw.MouseEvent

import scala.scalajs.js
import org.scalajs.dom
import org.scalajs.dom.{ErrorEvent, CloseEvent, MessageEvent, Event}
import org.scalajs.dom.raw.DOMError

import scala.scalajs.js
import io.surfkit.clientlib.webrtc._

import org.scalajs.dom.experimental.webrtc._
import scala.concurrent.ExecutionContext.Implicits.global


class WebSocketSignaler extends Peer.ModelTransformPeerSignaler[m.RTCSignal]{
  val id = (Math.random() * 1000).toInt.toString
  val `type` = "video"

  val peerInfo = PeerInfo(id, `type`)

  var ws = new dom.WebSocket(s"ws://localhost:8080/ws/${id}")
  ws.onmessage = { x: MessageEvent =>
    println("WS onmessage")
    val msg = upickle.default.read[m.Model](x.data.toString)
    println(s"MODEL ... ${msg}")
    receive(toPeerSignaling(msg.asInstanceOf[m.RTCSignal]))
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
    case m.Signaling.Join(name, p) =>
      Peer.Join(name,Peer.PeerInfo(p.id, p.`type`))
    case m.Signaling.Room(name, p, config, members) =>
      import js.JSConverters._
      val servers = config.map(s => RTCIceServer(url = s.url, username = s.username, credential = s.credential))
      val peers = members.map(p => Peer.PeerInfo(id = p.id, `type` = p.`type`))
      Peer.Room(name, Peer.PeerInfo(p.id, p.`type`), RTCConfiguration(servers.toJSArray), peers.toJSArray)
    case m.Signaling.Offer(p, offer) =>
      //println(s"toPeerSignaling offer ${offer}")
      val o = RTCSessionDescription(offer.`type`, offer.sdp)
      println("toPeerSignaling")
      println(s"toPeerSignaling offer.type : ${o.`type`}")
      println(s"toPeerSignaling offer.sdp : ${o.`sdp`}")
      Peer.Offer(Peer.PeerInfo(p.id, p.`type`), RTCSessionDescription(offer.`type`, offer.sdp))
    case m.Signaling.Candidate(p, c) =>
      Peer.Candidate(Peer.PeerInfo(p.id, p.`type`), RTCIceCandidate(c.candidate, c.sdpMLineIndex, c.sdpMid))
    case m.Signaling.Answer(p, answer) =>
      Peer.Answer(Peer.PeerInfo(p.id, p.`type`), RTCSessionDescription(answer.`type`, answer.sdp))
    case m.Signaling.Error(p, error) =>
      Peer.Error(Peer.PeerInfo(p.id, p.`type`), error)
    case _ =>
      Peer.Error(Peer.PeerInfo("", ""), "Unknown signaling type")
  }
  override def fromPeerSignaling(s:Peer.Signaling):m.RTCSignal = s match{
    case Peer.Join(name, p) =>
      m.Signaling.Join(name,m.Signaling.PeerInfo(p.id, p.`type`))
    case Peer.Room(name, p, config, members) =>
      val servers = config.iceServers.map(s => m.Signaling.RTCIceServer(url = s.url, username = s.username, credential = s.credential))
      val peers = members.map(p => m.Signaling.PeerInfo(id = p.id, `type` = p.`type`))
      m.Signaling.Room(name, m.Signaling.PeerInfo(p.id, p.`type`), servers.toSet, peers.toSet)
    case Peer.Offer(p, offer) =>
      m.Signaling.Offer(m.Signaling.PeerInfo(p.id, p.`type`), m.Signaling.RTCSessionDescription(offer.`type`, offer.sdp))
    case Peer.Candidate(p, c) =>
      m.Signaling.Candidate(m.Signaling.PeerInfo(p.id, p.`type`), m.Signaling.RTCIceCandidate(c.candidate, c.sdpMLineIndex, c.sdpMid))
    case Peer.Answer(p, answer) =>
      m.Signaling.Answer(m.Signaling.PeerInfo(p.id, p.`type`), m.Signaling.RTCSessionDescription(answer.`type`, answer.sdp))
    case Peer.Error(p, error) =>
      m.Signaling.Error(m.Signaling.PeerInfo(p.id, p.`type`), error)
    case _ =>
      m.Signaling.Error(m.Signaling.PeerInfo("", ""), "Unknown signaling type")
  }

  override def sendModel(s:m.RTCSignal) = {
    println("SEND USING WS")
    ws.send(upickle.default.write(s))
  }
}

object WebRTCMain extends js.JSApp {
  def main(): Unit = {

    val signaler = new WebSocketSignaler

    val local = dom.document.getElementById("local").asInstanceOf[dom.html.Video]
    //dom.document.getElementById("playground").appendChild(video)

    val txtPeerId = dom.document.getElementById("peerId")
    txtPeerId.innerHTML = signaler.id

    val webRTC = new SimpleWebRTC[m.RTCSignal,WebSocketSignaler](signaler)

    val bGetMedia = dom.document.getElementById("bGetMedia").asInstanceOf[dom.html.Button]
    bGetMedia.onclick = { me:MouseEvent =>
      webRTC.startLocalVideo(MediaConstraints(true, true),local)
    }

    val bCall = dom.document.getElementById("bCall").asInstanceOf[dom.html.Button]
    bCall.onclick = { me: MouseEvent =>
     // webRTC.initiatCall
      webRTC.joinRoom("test").foreach{ room: Peer.Room =>
        println("You have joined the room...")
      }
    }


  }
}