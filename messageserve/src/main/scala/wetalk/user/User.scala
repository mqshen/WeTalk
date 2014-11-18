package wetalk.user

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import wetalk.protocol._
import wetalk.user.UserActor.InvalidPassword
import wetalk.data.{ UserAuth, User }

/**
 * Created by goldratio on 11/18/14.
 */

class UserActor(connection: ActorRef, databaseActor: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  private val serverPassword = context.system.settings.config.getString("wetalk.server.salt")

  implicit val timeout = Timeout(120, TimeUnit.SECONDS)

  def receive = {
    case (userAuth: UserAuth, seqNo: Long) =>
      val f = databaseActor ? userAuth
      f onSuccess {
        case user: User =>
          //val sessionId = UUID.randomUUID().toString
          context.become(authenticated)
          connection ! LoginResponse(seqNo)
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
      connection ! ChatAckResponse(seqNo, chatMessage.timestamp)
    case _ =>
      println("tttt")
  }
  //
  //  startWith(AuthenticationPending, EmptyState)
  //
  //  when(AuhenticationPending) {
  //    case Event(Authenticate(password), _) => password match {
  //      case `serverPassword` =>
  //        goto(RegistrationPending)
  //      case _ =>
  //        sender ! InvalidPassword
  //        stay()
  //    }
  //  }
  //
  //  when(RegistrationPending) {
  //    case Event(Authenticate(_), _) =>
  //      sender ! new AlreadyAuthenticatedMessage("todo")
  //      stay()
  //    case Event(SetUsername(username), _) =>
  //      log.info(s"STUB: User wishes to change nickname to $username")
  //      stay()
  //  }
  //
  //  whenUnhandled {
  //    case Event(Quit(message), _) =>
  //      val quitMessage = message match {
  //        case Some(msg) => s"with message '$msg'"
  //        case None => "with no specific message"
  //      }
  //      log.info(s"User wishes to close connection $quitMessage.")
  //      sender ! Quit
  //      stop()
  //  }

}

object UserActor {

  def props(connection: ActorRef, databaseActor: ActorRef) = {
    Props(classOf[UserActor], connection, databaseActor)
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
