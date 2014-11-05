package wetalk

import java.nio.ByteOrder
import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.actor.{Props, ActorRef, Actor}
import akka.io.Tcp
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import wetalk.data._

import scala.util.control.NonFatal
import scala.collection.JavaConversions._

/**
 * Created by goldratio on 11/2/14.
 */
object MessageHandler
{
  def props(databaseActor: ActorRef, cacheActor: ActorRef) = Props(classOf[MessageHandler], databaseActor, cacheActor)
}

class MessageHandler(databaseActor: ActorRef, cacheActor: ActorRef) extends Actor {
  var user: Option[User] = None
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  implicit val timeout = Timeout(120, TimeUnit.SECONDS)
  import context.dispatcher



  val config = ConfigFactory.load().getConfig("wetalk")
  val Settings = new Settings(config)

  class Settings(config: Config) {
    val messageServer = {
      val hosts = config.getStringList("messageServer.host")
      //val host = config.getString("messageServer.host")
      val port = config.getInt("messageServer.port")
      hosts.map{ host =>
        (host, port.toShort)
      }.toList
    }
  }

  import Tcp._
  def receive = {
    case Received(data) =>
      try {
        handleParsingResult(WTRequestParser(data))
      }
      catch {
        case e: ExceptionWithErrorInfo =>
          handleError(0, e.info)
        case NonFatal(e) =>
          handleError(1, ErrorInfo("Illegal request", e.getMessage))
        case e: Throwable =>
          e.printStackTrace()
      }
    case PeerClosed =>
      context stop self
  }


  def handleParsingResult(result: WTPackage) = {
    val currentSender = sender()
    result match {
      case request: LoginRequest=>
        val f = databaseActor ? UserAuth(request.userName, request.password)
        f onSuccess {
          case user: User =>
            val response = LoginResponse(user, result.seqNo)
            this.user = Some(user)
            currentSender ! Write(response.packageData())
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            currentSender ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            currentSender ! Write(response.packageData())
        }
      case request: HeartbeatRequest =>
        val response = HeartbeatResponse(request.seqNo)
        currentSender ! Write(response.packageData())
      case request: DepartmentRequest =>
        user.map { u =>
          val f = databaseActor ? GetDepartment(1)
          f onSuccess {
            case department: Department =>
              val response = DepartResponse(department, result.seqNo)
              currentSender ! Write(response.packageData())
            case e =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
          f onFailure {
            case t =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
        }
      case request: RecentContactRequest =>
        user.map { u =>
          val f = databaseActor ? GetRecentContract(u.id)
          f onSuccess {
            case contact: List[RecentContact]=>
              val response = RecentContactResponse(contact, result.seqNo)
              currentSender ! Write(response.packageData())
            case e =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
          f onFailure {
            case t =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
        }
      case request: GroupListRequest =>
        user.map { u =>
          val f = databaseActor ? GetGroupList(u.id)
          f onSuccess {
            case groups: List[Group]=>
              val response = GroupListResponse(groups, result.seqNo)
              currentSender ! Write(response.packageData())
            case e =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
          f onFailure {
            case t =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
        }
      case request: RecentGroupListRequest =>
        user.map { u =>
          val f = databaseActor ? GetRecentGroupList(u.id)
          f onSuccess {
            case groups: List[Group]=>
              val response = RecentGroupListResponse(groups, result.seqNo)
              currentSender ! Write(response.packageData())
            case e =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
          f onFailure {
            case t =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
        }
      case request: UnreadMessageCountRequest=>
        user.map { u =>
          val f = cacheActor ? UnreadMessageCount(u.id)
          f onSuccess {
            case unread: List[(String, Int)] =>
              val response = UnreadMessageCountResponse(unread, result.seqNo)
              currentSender ! Write(response.packageData())
            case e =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
          f onFailure {
            case t =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
        }
      case request: GetFriendRequest =>
        user.map { u =>
          val f = databaseActor ? GetFriend(u.id)
          f onSuccess {
            case users: List[User]=>
              val response = FriendsResponse(users, result.seqNo)
              currentSender ! Write(response.packageData())
            case e =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
          f onFailure {
            case t =>
              val response = ErrorResponse(1, "not found", result.seqNo)
              currentSender ! Write(response.packageData())
          }
        }
    }
  }


  def handleError(status: Int, info: ErrorInfo): Unit = {
    //log.warning("Illegal request, responding with status '{}': {}", status, info.formatPretty)
    println(info)
  }

}
