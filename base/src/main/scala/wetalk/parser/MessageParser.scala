package wetalk.parser

/**
 * Created by goldratio on 11/24/14.
 */

import play.api.libs.json.Json
import wetalk.EnumUtils
import wetalk.data.{UserSync, UserAuth}
import wetalk.parser.MessageType.MessageType

import scala.util.Try
import scala.util.parsing.combinator._

/**
 * Created by goldratio on 11/18/14.
 */

object JsonFormat {
  implicit val enumAFormat = EnumUtils.enumFormat(MessageType)
  implicit val userAuthFormat = Json.format[UserAuth]
  implicit val chatMessageFormat = Json.format[ChatMessage]
  implicit val listFriendFormat = Json.format[ListFriendRequest]
  implicit val createGroupFormat = Json.format[CreateGroupRequest]
  implicit val listGroupFormat = Json.format[ListGroupRequest]
  implicit val userSyncFormat = Json.format[UserSync]
  implicit val userSearchRequestFormat = Json.format[UserSearchRequest]
  implicit val userAddRequestFormat = Json.format[UserAddRequest]
}

object MessageParser extends RegexParsers {
  import JsonFormat._

  override protected val whiteSpace = """[ ]+""".r

  private val serverId: Parser[String] = """[0-9]+""".r

  private val commandId: Parser[String] = """[0-9]+""".r

  private val host: Parser[String] = """[^\s@]+""".r

  private val arg: Parser[String] = """[^:\s][\S]+""".r

  private val data: Parser[String] = """.*$""".r

  private val prefix: Parser[CommandPrefix] = serverId ~ ":" ~ commandId ^^ {
    case serverId ~ _ ~ commandId => new CommandPrefix(serverId.toInt, commandId.toInt)
  }

  private val command: Parser[String] = """[a-zA-Z]+""".r

  private val message = prefix ~ ":" ~ data ^^ {
    case p ~ _ ~ d =>
      Command(prefix = p, jsonData = d)
  }

  def parse(text: String): Try[RequestMessage] = {
    parseAll(message, text) match {
      case Success(command:Command, _) => {
        val message: RequestMessage = command.prefix.serviceId match {
          case 0 =>
            null
          case 1 =>
            command.prefix.commandId match {
              case 1 =>
                Json.parse(command.jsonData).as[UserAuth]
              case 2 =>
                Json.parse(command.jsonData).as[UserSync]
            }
          case 2 =>
            HeartbeatMessage
          case 3 =>
            command.prefix.commandId match {
              case 1 =>
                Json.parse(command.jsonData).as[ChatMessage]
              case _ =>
                NOOP
            }
          case 4 =>
            null
          case 5 =>
            command.prefix.commandId match {
              case 1 =>
                Json.parse(command.jsonData).as[ListFriendRequest]
              case 5 =>
                Json.parse(command.jsonData).as[UserSearchRequest]
              case 6 =>
                Json.parse(command.jsonData).as[UserAddRequest]
            }
          case 6 =>
            command.prefix.commandId match {
              case 5 =>
                Json.parse(command.jsonData).as[ListGroupRequest]
              case 6 =>
                Json.parse(command.jsonData).as[CreateGroupRequest]
            }
          case 7 =>
            null
        }
        scala.util.Success(message)
      }
      case NoSuccess(msg, _) =>
        scala.util.Failure(new IllegalArgumentException(msg))
    }
  }

}
