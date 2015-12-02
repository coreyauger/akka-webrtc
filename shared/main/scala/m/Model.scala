package m


sealed trait Model
case class ApiMessage(id:String, data:Model) extends Model

sealed trait RTCSignal extends Model{
  def remote: Signaling.PeerInfo
  def local: Signaling.PeerInfo
}

object Signaling{

  case class PeerInfo(id:String, `type`: String) extends Model

  case class Join(remote: PeerInfo, local: PeerInfo, room:String) extends RTCSignal
  case class Room(remote: PeerInfo, local: PeerInfo, name:String, members:Set[PeerInfo]) extends RTCSignal

  case class RTCIceServer(url:String,username:String,credential:String) extends Model
  case class RTCSessionDescription(`type`: String,sdp: String) extends Model
  case class RTCIceCandidate(candidate: String, sdpMLineIndex: Int, sdpMid: String ) extends Model

  case class Offer(remote: PeerInfo, local: PeerInfo, offer: RTCSessionDescription, room: String) extends RTCSignal
  case class Answer(remote: PeerInfo, local: PeerInfo, answer: RTCSessionDescription) extends RTCSignal
  case class Candidate(remote: PeerInfo, local: PeerInfo, candidate: RTCIceCandidate) extends RTCSignal
  case class Error(remote: PeerInfo, local: PeerInfo, reason: String) extends RTCSignal
}


