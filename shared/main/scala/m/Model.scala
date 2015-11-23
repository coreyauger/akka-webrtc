package m

object aa

sealed trait Model
case class ApiMessage(id:String, data:Model) extends Model

sealed trait RTCSignal extends Model

object Signaling{

  case class PeerInfo(id:String, `type`: String) extends Model

  case class Join(room:String, peer:PeerInfo) extends RTCSignal
  case class Room(name:String, peer:PeerInfo, config:Set[RTCIceServer], members:Set[PeerInfo]) extends RTCSignal

  case class RTCIceServer(url:String,username:String,credential:String)
  case class RTCSessionDescription(`type`: String,sdp: String) extends RTCSignal
  case class RTCIceCandidate(candidate: String, sdpMLineIndex: Int, sdpMid: String ) extends RTCSignal

  case class Offer(peer: PeerInfo, offer: RTCSessionDescription) extends RTCSignal
  case class Answer(peer: PeerInfo, answer: RTCSessionDescription) extends RTCSignal
  case class Candidate(peer: PeerInfo, candidate: RTCIceCandidate) extends RTCSignal
  case class Error(peer: PeerInfo, reason: String) extends RTCSignal
}


