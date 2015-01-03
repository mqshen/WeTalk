package wetalk.user

import java.util.{Date, UUID}
import java.util.concurrent.TimeUnit

import akka.contrib.pattern.ShardRegion.Passivate
import scala.concurrent.duration._

import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import wetalk.parser._
import wetalk.data._

import scala.concurrent.forkjoin.ThreadLocalRandom

/**
 * Created by goldratio on 11/18/14.
 */

class UserActor(connection: ActorRef, databaseActor: ActorRef, sessionRegion: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher
  import UserActor._

  private val serverPassword = context.system.settings.config.getString("wetalk.server.salt")

  implicit val timeout = Timeout(120, TimeUnit.SECONDS)

  var user: User = null
  var sessionId: String = null

  private lazy val scheduler = UserExtension(context.system).scheduler

  private var heartbeatTask: Option[Cancellable] = None
  private var closeTimeoutTask: Option[Cancellable] = None
  private var idleTimeoutTask: Option[Cancellable] = None

  def enableHeartbeat() {
    log.debug("enabled heartbeat, will repeatly send heartbeat every {} seconds", wetalk.Settings.heartbeatInterval.seconds)
    heartbeatTask foreach { _.cancel } // it better to confirm previous heartbeatTask was cancled
    heartbeatTask = Some(scheduler.schedule(heartbeatDelay, wetalk.Settings.heartbeatInterval.seconds, self, HeartbeatTick))
  }

  def disableHeartbeat() {
    log.debug("disabled heartbeat")
    heartbeatTask foreach { _.cancel }
    heartbeatTask = None
  }

  def enableCloseTimeout() {
    log.debug("enabled close-timeout, will disconnect in {} seconds", wetalk.Settings.CloseTimeout)
    closeTimeoutTask foreach { _.cancel } // it better to confirm previous closeTimeoutTask was cancled
    if (context != null) {
      closeTimeoutTask = Some(scheduler.scheduleOnce(wetalk.Settings.CloseTimeout.seconds, self, CloseTimeout))
    }
  }

  def disableCloseTimeout() {
    log.debug("disabled close-timeout")
    closeTimeoutTask foreach { _.cancel }
    closeTimeoutTask = None
  }


  def enableIdleTimeout() {
    log.debug("enabled idle-timeout, will stop/exit in {} seconds", wetalk.Settings.IdleTimeout)
    idleTimeoutTask foreach { _.cancel } // it better to confirm previous idleTimeoutTask was cancled
    if (context != null) {
      idleTimeoutTask = Some(scheduler.scheduleOnce(wetalk.Settings.IdleTimeout.seconds, self, IdleTimeout))
    }
  }

  def disableIdleTimeout() {
    log.debug("disabled idle-timeout")
    idleTimeoutTask foreach { _.cancel }
    idleTimeoutTask = None
  }

  def deactivate() {
    log.debug("deactivated.")
    disableHeartbeat()
    disableCloseTimeout()
  }

  def doStop() {
    deactivate()
    disableIdleTimeout()

    if (UserExtension(context.system).Settings.isCluster) {
      context.parent ! Passivate(stopMessage = PoisonPill)
    }
    else {
      self ! PoisonPill
    }
  }

  def receive = {
    case userAuth: UserAuth =>
      val f = databaseActor ? userAuth
      f onSuccess {
        case user: User =>
          val sessionId = UUID.randomUUID().toString
          sessionRegion ! CreateSession(sessionId, user, self)
          this.user = user
          this.sessionId = sessionId
          enableHeartbeat()
          context.become(authenticated)
          connection ! LoginResponse(userAuth.seqNo, user)
        case e =>
          connection ! InvalidPassword
      }
      f onFailure {
        case t =>
          connection ! ErrorMessage("0", "system error")
      }
    case _ =>
      println("user have not login")

  }




  def authenticated: Receive = {
    case userSync: UserSync =>
      syncUser(userSync)
    case chatMessage: ChatMessage =>
      val index = chatMessage.to.indexOf("@room")
      if (index > 0) {
        val groupId = chatMessage.to.substring(0, index)
        val f = databaseActor ? CheckGroupRelationShip(chatMessage.from.toInt, groupId.toInt)
        f onSuccess {
          case users : List[String] =>
            users.map { user =>
              sessionRegion ! DispatchMessage(chatMessage.seqNo, user, ReceiveChatMessage(chatMessage))
            }
            connection ! ChatAckResponse(chatMessage.seqNo, chatMessage.timestamp)
          case _ =>
            connection ! ErrorMessage("0", "relationship not exist")
        }
        f onFailure {
          case t =>
            connection ! ErrorMessage("0", "system error")
        }
      }
      else {
        val f = databaseActor ? CheckRelationShip(chatMessage.from.toInt, chatMessage.to.toInt)
        f onSuccess {
          case 1 =>
            sessionRegion ! DispatchMessage(chatMessage.seqNo, chatMessage.to, ReceiveChatMessage(chatMessage))
            connection ! ChatAckResponse(chatMessage.seqNo, chatMessage.timestamp)
          case _ =>
            connection ! ErrorMessage("0", "system error")
        }
        f onFailure {
          case t =>
            connection ! ErrorMessage("0", "system error")
        }
      }

    case message: DispatchMessage =>
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
    case userSearchRequest: UserSearchRequest =>
      val f = databaseActor ? UserSearch(userSearchRequest.name, 0)
      f onSuccess {
        case users:  List[User] =>
          val response = ListSearchResponse(userSearchRequest.seqNo, users)
          connection ! response
        case e =>
          val response = ErrorMessage(userSearchRequest.seqNo, "not found")
          connection ! response
      }
      f onFailure {
        case t =>
          val response = ErrorMessage(userSearchRequest.seqNo, "not found")
          connection ! response
      }

    case request: FriendOperateRequest =>
      friendOperate(request)

    //TODO
    case userAddRequest: UserAddRequest =>
      addUserRequest(userAddRequest)
    case request: UserAddResponseRequest =>
      addUserResponse(request)

    case HeartbeatTick => // scheduled sending heartbeat
      log.debug("send heartbeat")
      connection ! HeartbeatResponse

      // keep previous close timeout. We may skip one closetimeout for this heartbeat, but we'll reset one at next heartbeat.
      if (closeTimeoutTask.fold(true)(_.isCancelled)) {
        enableCloseTimeout()
      }
    case HeartbeatRequest =>
      log.debug("got heartbeat")
      disableCloseTimeout()
    case DisconnectRequest =>
      connection ! UserActor.Quit
      doStop()
    case CloseTimeout =>
      connection ! UserActor.Quit
      sessionRegion ! CloseSession(user.id.toString, sessionId)
      log.info("CloseTimeout disconnect: {}, user: {}", sessionId, user)
      doStop()
    case _ =>
      println("tttt")
  }


  def friendOperate(request: FriendOperateRequest): Unit = {
    val f = databaseActor ? GetUser(request.id.toInt)

    f onSuccess {
      case friend: User =>
        if(request.operate == FriendOperate.Add) {
          val message = FriendOperateResponse(request.seqNo, request.id, user, FriendOperate.ReceiveAdd, request.greeting)
          sessionRegion ! DispatchMessage(request.seqNo, request.id, message)
          connection ! message.copy(operate = FriendOperate.Add, user = friend)
        }
        else  if(request.operate == FriendOperate.Accept) {
          val message = FriendOperateResponse(request.seqNo, request.id, user, FriendOperate.ReceiveAccept, request.greeting)
          sessionRegion ! DispatchMessage(request.seqNo, request.id, message)
          connection ! message.copy(operate = FriendOperate.Accept, user = friend)
        }
    }

  }

  def addUserRequest(userAddRequest: UserAddRequest): Unit = {
    val message = DispatchUserAdd(userAddRequest.id, user, userAddRequest.greeting)
    sessionRegion ! DispatchMessage(userAddRequest.seqNo, userAddRequest.id, message)
    connection ! UserAddResponse(userAddRequest.seqNo)
  }


  def addUserResponse(request: UserAddResponseRequest): Unit = {
    //TODO
//    val message = DispatchUserAddResponse(userAddRequest.id, user, userAddRequest.greeting)
//    sessionRegion ! DispatchMessage(userAddRequest.seqNo, userAddRequest.id, message)
//    connection ! UserAddResponse(userAddRequest.seqNo)
  }


  def syncUser(userSync: UserSync): Unit = {
    val f = databaseActor ? UserSync(userSync.seqNo, user.id, userSync.syncKey)
    f onSuccess {
      case messages:  List[OfflineMessageResponse] =>
        messages.map { message =>
          connection ! message
        }
      case e =>
        val response = ErrorMessage(userSync.seqNo, "not found")
        connection ! response
    }
    f onFailure {
      case t =>
        val response = ErrorMessage(userSync.seqNo, "not found")
        connection ! response
    }
  }

}

object UserActor {

  def props(connection: ActorRef, databaseActor: ActorRef, sessionRegion: ActorRef) = {
    Props(classOf[UserActor], connection, databaseActor, sessionRegion)
  }

  case object HeartbeatTick

  case object CloseTimeout

  case object IdleTimeout

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

  private def heartbeatDelay = ThreadLocalRandom.current.nextInt((math.min(wetalk.Settings.HeartbeatTimeout, wetalk.Settings.CloseTimeout) * 0.618).round.toInt).seconds
}
