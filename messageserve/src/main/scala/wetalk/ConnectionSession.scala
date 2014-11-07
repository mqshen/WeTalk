package wetalk

import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef}
import akka.pattern.ask
import akka.event.LoggingAdapter
import akka.io.Tcp.Write
import akka.util.Timeout
import spray.http.{HttpOrigin, Uri}
import wetalk.data._

import scala.collection.immutable

/**
 * Created by goldratio on 11/6/14.
 */
object ConnectionSession {

  sealed trait Event extends Serializable

  sealed trait DispatchCommand extends Serializable {
    def userId: String
  }

  sealed trait Command extends Serializable {
    def sessionId: String
  }

  final case class CreateSession(sessionId: String, user: User, transportConnection: ActorRef) extends Command
  final case class Connecting(sessionId: String, transportConnection: ActorRef) extends Command with Event
  final case class SendPackage(sessionId: String, wtPackage: WTPackage) extends Command with Event

  final case class DispatchPackage(userId: String, wtPackage: WTPackage) extends DispatchCommand with Event


  final class State(var sessionId: String, var connection: ActorRef, var topics: immutable.Set[String]) extends Serializable {
    override def equals(other: Any) = {
      other match {
        case x: State => x.sessionId == this.sessionId && x.connection == this.connection && x.topics == this.topics
        case _        => false
      }
    }

    override def toString = {
      new StringBuilder().append("State(")
        .append("sessionId=").append(sessionId)
        .append(", transConn=").append(connection)
        .append(", topics=").append(topics).append(")")
        .toString
    }
  }
}

trait ConnectionSession { _: Actor =>
  import ConnectionSession._
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  implicit val timeout = Timeout(120, TimeUnit.SECONDS)
  import context.dispatcher

  def log: LoggingAdapter

  def databaseActor: ActorRef
  def cacheActor: ActorRef
  def recoveryFinished: Boolean
  def recoveryRunning: Boolean


  def sessionId: String
  def user: User
  def connection: ActorRef

  private var _state: State = _ // have to init it lazy
  def state = {
    if (_state == null) {
      _state = new State(sessionId, connection, immutable.Set())
    }
    _state
  }
  def state_=(state: State) {
    _state = state
  }

  def updateState(evt: Any, newState: State) {
    state = newState
  }

  def working: Receive = {
    case cmd @ Connecting(sessionId, connection) => // transport fired connecting command

      state.sessionId match {
        case null =>
          state.sessionId = sessionId
          state.connection = connection
          updateState(cmd, state)
        case existed =>
          state.connection = connection
          if (recoveryFinished) {
            updateState(cmd, state)
          }
      }

      if (recoveryFinished) {
        log.info("Connecting: {}, state: {}", sessionId, state)
      }

    case SendPackage(sessionId, wtPackage) =>
      handlerPackage(wtPackage)
    case request: MessageReceiveResponse =>
      connection ! Write(request.packageData())
  }


  def handlerPackage(result: WTPackage) = {
    val serverConnection = connection
    println(serverConnection)
    println(result)
    result match {
      case request: HeartbeatRequest =>
        val response = HeartbeatResponse(request.seqNo)
        serverConnection ! Write(response.packageData())
      case request: MessageSend =>
        val response = MessageSendAckResponse(request.message.seqNo, request.seqNo)
        val dispatchResponse = MessageReceiveResponse(request.message, 0)
        context.parent ! DispatchPackage(request.message.toId, dispatchResponse)
        serverConnection ! Write(response.packageData())
      case request: DepartmentRequest =>
        val f = databaseActor ? GetDepartment(1)
        f onSuccess {
          case department: Department =>
            val response = DepartResponse(department, result.seqNo)
            serverConnection ! Write(response.packageData())
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
      case request: RecentContactRequest =>
        val f = databaseActor ? GetRecentContract(user.id)
        f onSuccess {
          case contact: List[RecentContact] =>
            val response = RecentContactResponse(contact, result.seqNo)
            serverConnection ! Write(response.packageData())
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
      case request: GroupListRequest =>
        val f = databaseActor ? GetGroupList(user.id)
        f onSuccess {
          case groups: List[Group] =>
            val response = GroupListResponse(groups, result.seqNo)
            serverConnection ! Write(response.packageData())
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
      case request: RecentGroupListRequest =>
        val f = databaseActor ? GetRecentGroupList(user.id)
        f onSuccess {
          case groups: List[Group] =>
            val response = RecentGroupListResponse(groups, result.seqNo)
            serverConnection ! Write(response.packageData())
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
      case request: UnreadMessageCountRequest =>
        val f = cacheActor ? UnreadMessageCount(user.id)
        f onSuccess {
          case unread: List[(String, Int)] =>
            val response = UnreadMessageCountResponse(unread, result.seqNo)
            serverConnection ! Write(response.packageData())
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
      case request: UnreadGroupMessageCountRequest =>
        val f = cacheActor ? UnreadGroupMessageCount(user.id)
        f onSuccess {
          case unread: List[(String, Int)] =>
            val response = UnreadGroupMessageCountResponse(unread, result.seqNo)
            serverConnection ! Write(response.packageData())
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
      case request: GetFriendRequest =>
        val f = databaseActor ? GetFriend(user.id)
        f onSuccess {
          case users: List[User] =>
            val response = FriendsResponse(users, result.seqNo)
            serverConnection ! Write(response.packageData())
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
    }
  }
}