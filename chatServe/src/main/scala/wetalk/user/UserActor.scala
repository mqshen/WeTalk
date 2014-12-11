package wetalk.user

import java.util.{Date, UUID}
import java.util.concurrent.TimeUnit

import akka.io.Tcp.Write
import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import wetalk.parser._
import wetalk.user.UserActor.InvalidPassword
import wetalk.data._

/**
 * Created by goldratio on 11/18/14.
 */

class UserActor(connection: ActorRef, databaseActor: ActorRef, sessionRegion: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  private val serverPassword = context.system.settings.config.getString("wetalk.server.salt")

  implicit val timeout = Timeout(120, TimeUnit.SECONDS)

  var user: User = null

  def receive = {
    case userAuth: UserAuth =>
      val f = databaseActor ? userAuth
      f onSuccess {
        case user: User =>
          val sessionId = UUID.randomUUID().toString
          sessionRegion ! CreateSession(sessionId, user, self)
          this.user = user
          connection ! LoginResponse(userAuth.seqNo, user)
          context.become(authenticated)
        case e =>
          connection ! InvalidPassword
      }
      f onFailure {
        case t =>
          connection ! ErrorMessage("0", "system error")
      }
    case _ =>
      println("user not found")

  }

  def authenticated: Receive = {
    case chatMessage: ChatMessage =>
      if (chatMessage.to.indexOf("@room") > 0) {

      }
      else {
        val f = databaseActor ? CheckRelationShip(chatMessage.from.toInt, chatMessage.to.toInt)
        f onSuccess {
          case 1 =>
            sessionRegion ! DispatchChatMessage(chatMessage.seqNo, chatMessage.to, ReceiveChatMessage(chatMessage))
            connection ! ChatAckResponse(chatMessage.seqNo, chatMessage.timestamp)
          case _ =>
            connection ! ErrorMessage("0", "system error")
        }
        f onFailure {
          case t =>
            connection ! ErrorMessage("0", "system error")
        }
      }

    case message: DispatchChatMessage =>
      connection ! message.message

    case listFriends: ListFriendRequest =>
      val f = databaseActor ? GetFriend(user.id)
      f onSuccess {
        case users: List[User] =>
          val response = ListFriendResponse(listFriends.seqNo, users)
          connection ! response
        case e =>
          val response = ErrorMessage(listFriends.seqNo, "not found")
          connection ! response
      }
      f onFailure {
        case t =>
          val response = ErrorMessage(listFriends.seqNo, "not found")
          connection ! response
      }

    case createGroup: CreateGroupRequest =>
      val date = new Date(System.currentTimeMillis())
      val f = databaseActor ? Group(0, "", "", "", user.id, 0, 0, createGroup.members.size + 1, date, date, createGroup.members)
      f onSuccess {
        case group: Group =>
          val response = CreateGroupResponse(createGroup.seqNo, group)
          connection ! response
        case e =>
          val response = ErrorMessage(createGroup.seqNo, "not found")
          connection ! response
      }
      f onFailure {
        case t =>
          val response = ErrorMessage(createGroup.seqNo, "not found")
          connection ! response
      }

    case listGroup: ListGroupRequest =>
      val f = databaseActor ? GetRecentGroupList(user.id)
      f onSuccess {
        case groups:  List[Group] =>
          val response = ListGroupResponse(listGroup.seqNo, groups)
          connection ! response
        case e =>
          val response = ErrorMessage(listGroup.seqNo, "not found")
          connection ! response
      }
      f onFailure {
        case t =>
          val response = ErrorMessage(listGroup.seqNo, "not found")
          connection ! response
      }
    case _ =>
      println("tttt")
  }

}

object UserActor {

  def props(connection: ActorRef, databaseActor: ActorRef, sessionRegion: ActorRef) = {
    Props(classOf[UserActor], connection, databaseActor, sessionRegion)
  }

  case class Authenticate(password: String)

  case class SetUsername(username: String)

  case class Quit(message: Option[String])

  case object InvalidPassword

  case object Quit

  sealed trait UserState

  case object AuthenticationPending extends UserState

  case object RegistrationPending extends UserState

  case object Authenticated extends UserState

  sealed trait UserData

  case object EmptyState extends UserData

}
