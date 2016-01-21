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
import org.scalajs.dom.experimental.mediastream._
import scala.scalajs.js.annotation.JSExport
import scala.scalajs.js.|
import scala.concurrent.ExecutionContext.Implicits.global


class WebSocketSignaler extends Peer.ModelTransformPeerSignaler[m.RTCSignal]{
  val id = (Math.random() * 1000).toInt.toString
  val `type` = "video"

  val localPeer = PeerInfo(id, `type`)

  var ws = new dom.WebSocket(s"ws://${dom.document.location.hostname}:${dom.document.location.port}/ws/${id}")
  ws.onmessage = { x: MessageEvent =>
    //println(s"WS onmessage ${x.data.toString}")
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
    case m.Signaling.Offer(r, l, offer, room) =>
      //println(s"toPeerSignaling offer ${offer}")
      Peer.Offer(r, l, new RTCSessionDescription(RTCSessionDescriptionInit(offer.`type`.asInstanceOf[RTCSdpType], offer.sdp)), room)
    case m.Signaling.Candidate(r, l, c) =>
      Peer.Candidate(r, l, new RTCIceCandidate(RTCIceCandidateInit(c.candidate, c.sdpMid, c.sdpMLineIndex.toDouble)))
    case m.Signaling.Answer(r, l, answer) =>
      Peer.Answer(r, l, new RTCSessionDescription(RTCSessionDescriptionInit(answer.`type`.asInstanceOf[RTCSdpType], answer.sdp)))
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
    case Peer.Answer(r, l, answer) =>
      m.Signaling.Answer(r, l, m.Signaling.RTCSessionDescription(answer.`type`.toString, answer.sdp))
    case Peer.Offer(r, l, offer, room) =>
      m.Signaling.Offer(r, l, m.Signaling.RTCSessionDescription(offer.`type`.toString, offer.sdp), room)
    case Peer.Candidate(r, l, c) =>
      m.Signaling.Candidate(r, l, m.Signaling.RTCIceCandidate(c.candidate, c.sdpMLineIndex.toInt, c.sdpMid))
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


    val iceServers: String | js.Array[String] = "turn:turn.conversant.im:443"

    val rtcConfiguration = RTCConfiguration(
        iceServers = js.Array[RTCIceServer](
            //RTCIceServer(urls = "stun:stun.l.google.com:19302"),
            //RTCIceServer(urls = "turn:turn.conversant.im:443", username="turnuser", credential = "turnpass")
            RTCIceServer(urls = iceServers)
          )
      )


    val webRTC = new SimpleWebRTC[m.RTCSignal,WebSocketSignaler](signaler, rtcConfiguration)
    webRTC.peerStreamAdded = { peer =>
      println("TODO: add the remote video to the page")
      val remoteVideoElm = dom.document.createElement("video").asInstanceOf[dom.html.Video]
      peer.streams.headOption.foreach{ s =>
        println(s"peerStreamAdded ADDING STREAM ${s}")
        val remoteDyn = (remoteVideoElm.asInstanceOf[js.Dynamic])
        remoteDyn.srcObject = s
        remoteDyn.play()
      }
      dom.document.getElementById("playground").appendChild(remoteVideoElm)
    }

    val constraintTrue: Boolean | MediaTrackConstraints = true
    val bCall = dom.document.getElementById("bCall").asInstanceOf[dom.html.Button]
    bCall.onclick = { me: MouseEvent =>
      webRTC.startLocalVideo(MediaStreamConstraints(constraintTrue, constraintTrue),local).foreach { s =>
        webRTC.joinRoom("test").foreach { room: Peer.Room =>
          println(s"You have joined the room... ${room.name}")
        }
      }
    }
  }


  @JSExport
  def advanced(): Unit = {
    println("ADVANCED...")
    val signaler = new WebSocketSignaler

    val local = dom.document.getElementById("local").asInstanceOf[dom.html.Video]

    val txtPeerId = dom.document.getElementById("peerId")
    txtPeerId.innerHTML = signaler.id


    val iceServers: String | js.Array[String] = "turn:turn.conversant.im:443"

    val rtcConfiguration = RTCConfiguration(
      iceServers = js.Array[RTCIceServer](
            //RTCIceServer(urls = "stun:stun.l.google.com:19302"),
            //RTCIceServer(urls = "turn:turn.conversant.im:443", username="turnuser", credential = "turnpass")
            RTCIceServer(urls = iceServers)
          )
    )


    val webRTC = new SMWebRTC[m.RTCSignal,WebSocketSignaler](signaler, rtcConfiguration)
    webRTC.peerStreamAdded = { peer =>
      println("TODO: add the remote video to the page")
      val remoteVideoElm = dom.document.createElement("video").asInstanceOf[dom.html.Video]
      peer.streams.headOption.foreach{ s =>
        println(s"peerStreamAdded ADDING STREAM ${s}")
        val remoteDyn = (remoteVideoElm.asInstanceOf[js.Dynamic])
        remoteDyn.srcObject = s
        remoteDyn.play()
      }
      dom.document.getElementById("playground").appendChild(remoteVideoElm)
    }

    val constraintTrue: Boolean | MediaTrackConstraints = true
    val bJoin = dom.document.getElementById("bJoin").asInstanceOf[dom.html.Button]
    bJoin.onclick = { me: MouseEvent =>
      val txtRoom = dom.document.getElementById("room").asInstanceOf[dom.html.Input]
      webRTC.joinRoom(txtRoom.value)
    }

    val bCall = dom.document.getElementById("bCall").asInstanceOf[dom.html.Button]
    bCall.onclick = { me: MouseEvent =>
      val txtRoom = dom.document.getElementById("room").asInstanceOf[dom.html.Input]
      webRTC.call(txtRoom.value)
    }

    webRTC.onJoinRoom = { (name, peers) =>
      println(s"Joined Room ${name}")
      println("members ============")
      peers.foreach(println)
      println("====================")
    }

    webRTC.onRing = { (name, peer) =>
      println("Ring Ring !!!")
      println(s"Call coming from room ${name}")
      println("Auto Answer")
      webRTC.call(name)
    }
  }
}