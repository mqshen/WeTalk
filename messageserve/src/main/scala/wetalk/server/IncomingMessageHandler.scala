package wetalk.server

import java.net.InetSocketAddress
import java.util.UUID

import akka.actor.ActorRef
import akka.event.slf4j.SLF4JLogging
import akka.io.Tcp.Write
import play.api.libs.json.Json
import wetalk.protocol.Command.Heartbeat
import wetalk.protocol.Command.Message
import wetalk.{ ErrorResponse, ConnectionSession, LoginResponse }
import wetalk.data.{ User, UserAuth }
import wetalk.protocol.Command._
import wetalk.protocol._
import wetalk.user.UserActor

/**
 * Created by goldratio on 11/18/14.
 */

object JsonFormat {
  implicit val userAuthFormat = Json.format[UserAuth]
  implicit val chatMessageFormat = Json.format[ChatMessage]
}

class IncomingMessageHandler(connection: ActorRef, userActor: ActorRef, remoteAddress: InetSocketAddress, serverName: String) extends SLF4JLogging {
  import JsonFormat._

  implicit val sender = connection

  def handleIncomingIrcMessage(message: UserMessage) = {
    //log.debug(s"$remoteAddress: $message")
    Command(message.prefix.opCode).fold(connection ! UnknownCommandMessage(serverName, message.prefix.opCode))({
      case Disconnect =>
      case Connect =>
        val userAuth = Json.parse(message.jsonData).as[UserAuth]
        userActor ! (userAuth, message.prefix.id)
      case Heartbeat =>
        userActor ! HeartbeatMessage
      case Message =>
        val chatMessage = Json.parse(message.jsonData).as[ChatMessage]
        userActor ! (chatMessage, message.prefix.id)
      case GroupMessage =>
      case UserCommand  =>
      case Ack          =>
      case Error        =>
    })
  }

}
