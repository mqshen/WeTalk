package wetalk.protocol

import play.api.libs.json.{ JsObject, Json }

/**
 * Created by goldratio on 11/18/14.
 */
trait Message

sealed trait Command

object Command {

  case object Disconnect extends Command
  case object Connect extends Command
  case object Heartbeat extends Command
  case object Message extends Command
  case object GroupMessage extends Command
  case object UserCommand extends Command
  case object Ack extends Command
  case object Error extends Command
  case object Noop extends Command

  def apply(command: Int): Option[Command] = {
    command match {
      case 0 => Some(Disconnect)
      case 1 => Some(Connect)
      case 2 => Some(Heartbeat)
      case 3 => Some(Message)
      case 4 => Some(GroupMessage)
      case 5 => Some(UserCommand)
      case 6 => Some(Ack)
      case 7 => Some(Error)
      case 8 => Some(Noop)
      case _ => None
    }

  }
}

trait ServerMessage extends Message {
  def resultCode: Int
  def errorMessage: String
  def jsonContent: JsObject

  def json = Json.obj(
    "ec" -> resultCode,
    "em" -> errorMessage,
    "content" -> jsonContent)

}

case class ChatMessage(from: String, to: String, content: String, attach: Option[String], timestamp: Long) extends ServerMessage {

  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")

  override def toString = s"3:"

}

object HeartbeatMessage extends ServerMessage {
  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")

  override def toString = s"2:"
}

case class LoginRequest(seqNo: Long, userName: String, password: String) extends ServerMessage {
  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("userName" -> userName, "password" -> password)

  override def toString = s"1:$seqNo:$jsonContent"
}

case class LoginResponse(seqNo: Long) extends ServerMessage {
  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")

  override def toString = s"1:$seqNo:$json"
}

case class ChatAckResponse(seqNo: Long, timestamp: Long) extends ServerMessage {
  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("timestamp" -> timestamp)

  override def toString = s"3:$seqNo:$json"

}

case class NoticeMessage(serverName: String, nick: String, text: String) extends ServerMessage {

  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")
  override def toString = s":$serverName NOTICE :$nick $text"
}

case class ErrorMessage(seqNo: Long, message: String) extends ServerMessage {

  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")
  override def toString = s"7:$seqNo:$message"
}

case class NeedMoreParamsMessage(serverName: String, opCode: Int) extends ServerMessage {

  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")

  override def toString = s":$serverName 461 $opCode :Not enough parameters"
}

case class UnknownCommandMessage(serverName: String, opCode: Int) extends ServerMessage {

  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")

  override def toString = s":$serverName 421 $opCode :Unknown command"
}

case class AlreadyAuthenticatedMessage(serverName: String) extends ServerMessage {

  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")
  override def toString = s":$serverName NOTICE : Already authenticated"
}
