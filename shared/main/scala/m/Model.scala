package m

object aa

sealed trait Model
case class ApiMessage(id:String, data:Model) extends Model

sealed trait RTCSignal extends Model

object Signaling{

  case class RTCSessionDescription(`type`: String,sdp: String) extends RTCSignal
  case class RTCIceCandidate(candidate: String, sdpMLineIndex: Int, sdpMid: String ) extends RTCSignal

  case class Offer(offer: RTCSessionDescription) extends RTCSignal
  case class Answer(answer: RTCSessionDescription) extends RTCSignal
  case class Candidate(candidate: RTCIceCandidate) extends RTCSignal
  case class Error(reason: String) extends RTCSignal
}


