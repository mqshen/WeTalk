package wetalk.parser

import play.api.libs.json.{JsObject, Json}
import wetalk.data.{Group, UserAuth}

/**
 * Created by goldratio on 11/24/14.
 */
case class CommandPrefix(serviceId: Int, commandId: Int) {

}

trait RequestMessage {
  def seqNo: String
}

trait ResponseMessage {

  def json: JsObject

}
case class Command(prefix: CommandPrefix, jsonData: String)


sealed trait ServiceId

object Command {

  case object Disconnect extends ServiceId
  case object Connect extends ServiceId
  case object Heartbeat extends ServiceId
  case object Message extends ServiceId
  case object GroupMessage extends ServiceId
  case object UserCommand extends ServiceId
  case object Ack extends ServiceId
  case object Error extends ServiceId
  case object Noop extends ServiceId

  def apply(serviceId: Int): Option[ServiceId] = {
    serviceId match {
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

import akka.actor.ActorRef
import play.api.libs.json.Json
import wetalk.data.User
import wetalk.parser.Command._

final case class CreateSession(sessionId: String, user: User, transportConnection: ActorRef)

final case class GroupDispatchPackage(seqNo: String, userId: String, users: List[String], message: RequestMessage) extends RequestMessage

case class ErrorMessage(seqNo: String, message: String) extends RequestMessage {

  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("sdf" -> "")
  override def toString = s"7:$seqNo:$message"
}

case class LoginResponse(seqNo: String, user: User) extends ResponseMessage {
  implicit val userFormat = Json.format[User]
  val resultCode = 0
  val errorMessage = ""

  lazy val json = Json.obj(
    "ec" -> 0,
    "em" -> "",
    "seqNo" -> seqNo,
    "user" -> user)

  override def toString = s"1:1:$json"
}

case class OfflineMessageResponse(seqNo: Long, message: String) extends ResponseMessage {
  val json = Json.obj("seqNo" -> seqNo,
    "ec" -> 0,
    "message" -> message)

  override def toString = s"1:2:$json"
}

object HeartbeatPrefix extends CommandPrefix(2, 2)

object HeartbeatMessage extends RequestMessage {
  val seqNo = "0"
}

case class DispatchMessage(seqNo: String, to: String, message: ResponseMessage) extends RequestMessage

object MessageType extends Enumeration {
  type MessageType = Value
  val Text, Image, Sound, Video = Value
}


import MessageType._

case class ChatMessage(seqNo: String,
                       messageType: MessageType,
                       from: String,
                       to: String,
                       content: String,
                       attach: Option[String],
                       timestamp: Long) extends RequestMessage


object ReceiveChatMessage {
  def apply(message: ChatMessage): ReceiveChatMessage = {
    new ReceiveChatMessage(message.seqNo, message.messageType, message.from, message.to, message.content, message.attach, message.timestamp)

  }
}
case class ReceiveChatMessage(seqNo: String,
                              messageType: MessageType,
                              from: String,
                              to: String,
                              content: String,
                              attach: Option[String],
                              timestamp: Long) extends ResponseMessage {
  val resultCode = 0
  val errorMessage = ""

  val json = Json.obj(
    "ec" -> 0,
    "messageType" -> messageType.id,
    "status" -> 1,
    "seqNo" -> seqNo,
    "from" -> from,
    "to" -> to,
    "content" -> content,
    "attach" -> attach,
    "timestamp" -> timestamp
  )

  override def toString = s"3:0:$json"
}

case class ChatAckResponse(seqNo: String, timestamp: Long) extends ResponseMessage {
  val resultCode = 0
  val errorMessage = ""

  val json = Json.obj("ec" -> 0, "seqNo" -> seqNo, "timestamp" -> timestamp)

  override def toString = s"3:1:$json"

}



//User
case class ListFriendRequest(seqNo: String) extends RequestMessage

case class ListFriendResponse(seqNo: String, friends: List[User]) extends ResponseMessage {
  implicit val userFormat = Json.format[User]

  lazy val json = Json.obj(
    "ec" -> 0,
    "em" -> "",
    "friends" -> friends)

  override def toString = s"5:1:$json"

}

case class ListGroupRequest(seqNo: String) extends RequestMessage

case class ListGroupResponse(seqNo: String, groups: List[Group]) extends ResponseMessage {
  implicit val groupFormat = Json.format[Group]

  lazy val json = Json.obj(
    "ec" -> 0,
    "em" -> "",
    "groups" -> groups)

  override def toString = s"5:5:$json"

}

case class CreateGroupRequest(seqNo: String, members: List[String]) extends RequestMessage


case class CreateGroupResponse(seqNo: String, group: Group) extends ResponseMessage {
  implicit val groupFormat = Json.format[Group]
  val resultCode = 0
  val errorMessage = ""

  val json = Json.obj("ec" -> 0, "em" -> "", "seqNo" -> seqNo, "group" -> group)

  override def toString = s"5:6:$json"
}

case class UserSearchRequest(seqNo: String, name: String) extends RequestMessage

case class ListSearchResponse(seqNo: String, users: List[User]) extends ResponseMessage {
  implicit val userFormat = Json.format[User]

  lazy val json = Json.obj(
    "ec" -> 0,
    "em" -> "",
    "seqNo" -> seqNo,
    "users" -> users)

  override def toString = s"5:5:$json"
}

case class DispatchUserAdd(to: String, user: User, greeting: String) extends ResponseMessage {
  implicit val userFormat = Json.format[User]
  val resultCode = 0
  val errorMessage = ""

  val json = Json.obj("ec" -> 0, "em" -> "", "user" -> user)

  override def toString = s"5:7:$json"

}

case class UserAddRequest(seqNo: String, id: String, greeting: String) extends RequestMessage


case class UserAddResponse(seqNo: String) extends ResponseMessage {
  implicit val groupFormat = Json.format[Group]
  val resultCode = 0
  val errorMessage = ""

  val json = Json.obj("ec" -> 0, "em" -> "", "seqNo" -> seqNo)

  override def toString = s"5:6:$json"

}


object NOOP extends RequestMessage with ResponseMessage {

  lazy val json = Json.obj( "ec" -> 0, "em" -> "")

  val seqNo: String = "0"

}