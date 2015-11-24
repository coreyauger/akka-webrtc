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

  val localPeer = PeerInfo(id, `type`)

  var ws = new dom.WebSocket(s"ws://${dom.document.location.hostname}:${dom.document.location.port}/ws/${id}")
  ws.onmessage = { x: MessageEvent =>
    println(s"WS onmessage ${x.data.toString}")
    val msg = upickle.default.read[m.Model](x.data.toString)
    receive(toPeerSignaling(msg.asInstanceOf[m.RTCSignal]))
  }
  ws.onopen = { x: Event =>
    println("WS connection connected")
  }
  ws.onerror = { x: ErrorEvent =>
    println("some error has occured " + x.message)
  }
  ws.onclose = { x: CloseEvent =>
    println("WS connection CLOSED !!")
  }

  implicit def modelToPeer(p:m.Signaling.PeerInfo):Peer.PeerInfo = Peer.PeerInfo(p.id, p.`type`)
  implicit def peerToModel(p:Peer.PeerInfo):m.Signaling.PeerInfo = m.Signaling.PeerInfo(p.id, p.`type`)

  override def toPeerSignaling(model:m.RTCSignal):Peer.Signaling = model match{
    case m.Signaling.Join(r, l,name) =>
      Peer.Join(r, l, name)
    case m.Signaling.Room(r, l, name, members) =>
      import js.JSConverters._
      val peers = members.map(p => Peer.PeerInfo(id = p.id, `type` = p.`type`))
      Peer.Room(r, l, name, peers.toJSArray)
    case m.Signaling.Offer(r, l, offer) =>
      //println(s"toPeerSignaling offer ${offer}")
      val o = RTCSessionDescription(offer.`type`, offer.sdp)
      Peer.Offer(r, l, RTCSessionDescription(offer.`type`, offer.sdp))
    case m.Signaling.Candidate(r, l, c) =>
      Peer.Candidate(r, l, RTCIceCandidate(c.candidate, c.sdpMLineIndex, c.sdpMid))
    case m.Signaling.Answer(r, l, answer) =>
      Peer.Answer(r, l, RTCSessionDescription(answer.`type`, answer.sdp))
    case m.Signaling.Error(r, l, error) =>
      Peer.Error(r, l, error)
    case _ =>
      Peer.Error(Peer.PeerInfo("", ""), Peer.PeerInfo("", ""), "Unknown signaling type")
  }
  override def fromPeerSignaling(s:Peer.Signaling):m.RTCSignal = s match{
    case Peer.Join(r, l, name) =>
      m.Signaling.Join(r, l, name)
    case Peer.Room(r, l, name, members) =>
      val peers = members.map(p => m.Signaling.PeerInfo(id = p.id, `type` = p.`type`))
      m.Signaling.Room(r, l, name, peers.toSet)
    case Peer.Offer(r, l, offer) =>
      m.Signaling.Offer(r, l, m.Signaling.RTCSessionDescription(offer.`type`, offer.sdp))
    case Peer.Candidate(r, l, c) =>
      m.Signaling.Candidate(r, l, m.Signaling.RTCIceCandidate(c.candidate, c.sdpMLineIndex, c.sdpMid))
    case Peer.Answer(r, l, answer) =>
      m.Signaling.Answer(r, l, m.Signaling.RTCSessionDescription(answer.`type`, answer.sdp))
    case Peer.Error(r, l, error) =>
      m.Signaling.Error(r, l, error)
    case _ =>
      m.Signaling.Error(m.Signaling.PeerInfo("", ""), m.Signaling.PeerInfo("", ""), "Unknown signaling type")
  }

  override def sendModel(s:m.RTCSignal) = ws.send(upickle.default.write(s))
}

object WebRTCMain extends js.JSApp {
  def main(): Unit = {

    val signaler = new WebSocketSignaler

    val local = dom.document.getElementById("local").asInstanceOf[dom.html.Video]

    val txtPeerId = dom.document.getElementById("peerId")
    txtPeerId.innerHTML = signaler.id

    val props = WebRTC.Props(
      rtcConfiguration = RTCConfiguration(
        iceServers = js.Array[RTCIceServer](
          RTCIceServer(url = "stun:stun.l.google.com:19302"),
          RTCIceServer(url = "turn:turn.conversant.im:443", username="turnuser", credential = "trunpass")
        )
      )
    )

    val webRTC = new SimpleWebRTC[m.RTCSignal,WebSocketSignaler](signaler, props)
    webRTC.peerStreamAdded = { peer =>
      println("TODO: add the remote video to the page")
      val remoteVideoElm = dom.document.createElement("video").asInstanceOf[dom.html.Video]
      peer.stream.foreach{ s =>
        println(s"peerStreamAdded ADDING STREAM ${s}")
        val remoteDyn = (remoteVideoElm.asInstanceOf[js.Dynamic])
        remoteDyn.srcObject = s
        remoteDyn.play()
      }
      dom.document.getElementById("playground").appendChild(remoteVideoElm)
    }

    val bGetMedia = dom.document.getElementById("bGetMedia").asInstanceOf[dom.html.Button]
    bGetMedia.onclick = { me:MouseEvent =>
      webRTC.startLocalVideo(MediaConstraints(true, true),local)
    }

    val bCall = dom.document.getElementById("bCall").asInstanceOf[dom.html.Button]
    bCall.onclick = { me: MouseEvent =>
      webRTC.joinRoom("test").foreach{ room: Peer.Room =>
        println(s"You have joined the room... ${room.name}")
      }
    }
  }
}