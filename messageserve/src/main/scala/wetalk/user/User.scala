package wetalk.user

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import wetalk.ConnectionSession
import wetalk.protocol._
import wetalk.user.UserActor.InvalidPassword
import wetalk.data.{ UserAuth, User }

/**
 * Created by goldratio on 11/18/14.
 */

class UserActor(connection: ActorRef, databaseActor: ActorRef, sessionRegion: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  private val serverPassword = context.system.settings.config.getString("wetalk.server.salt")

  implicit val timeout = Timeout(120, TimeUnit.SECONDS)

  def receive = {
    case (userAuth: UserAuth, seqNo: Long) =>
      val f = databaseActor ? userAuth
      f onSuccess {
        case user: User =>
          val sessionId = UUID.randomUUID().toString
          sessionRegion ! ConnectionSession.CreateSession(sessionId, user, self)
          connection ! LoginResponse(seqNo)
          context.become(authenticated)
        case e =>
          connection ! InvalidPassword
      }
      f onFailure {
        case t =>
          connection ! ErrorMessage(seqNo, "system erro")
      }
    case _ =>
      println("tttt")

  }

  def authenticated: Receive = {

    case (chatMessage: ChatMessage, seqNo: Long) =>
      sessionRegion ! DispatchChatMessage(chatMessage.to, chatMessage)
      connection ! ChatAckResponse(seqNo, chatMessage.timestamp)

    case message: DispatchChatMessage =>
      connection ! message.message

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
