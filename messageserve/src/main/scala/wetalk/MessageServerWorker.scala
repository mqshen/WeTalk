package wetalk

import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import akka.io.Tcp.{Write, PeerClosed, Received}
import akka.util.Timeout
import wetalk.ConnectionSession.SendPackage
import wetalk.data.{User, UserAuth}

import scala.util.control.NonFatal

/**
 * Created by goldratio on 11/6/14.
 */
object MessageServerWorker {
  def props(databaseActor: ActorRef, sessionRegion: ActorRef) = Props(classOf[MessageServerWorker], databaseActor, sessionRegion)
}

class MessageServerWorker(databaseActor: ActorRef, sessionRegion: ActorRef) extends Actor with ActorLogging{

  //val soConnContext = new ConnectingContext(null, sender(), sessionRegion)
  var sessionId: String = null
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  implicit val timeout = Timeout(120, TimeUnit.SECONDS)
  import context.dispatcher

  def receive = {
    case Received(data) =>
      try {
        handleParsingResult(WTRequestParser(data))
      }
      catch {
        case e: ExceptionWithErrorInfo =>
        case NonFatal(e) =>
        case e: Throwable =>
          e.printStackTrace()
      }
    case PeerClosed =>
      context stop self
  }


  def handleParsingResult(result: WTPackage) = {
    val serverConnection = sender()
    println(serverConnection)
    result match {
      case request: LoginRequest =>
        val f = databaseActor ? UserAuth(request.userName, request.password)
        f onSuccess {
          case user: User =>
            val response = LoginResponse(user, result.seqNo)
            sessionId = UUID.randomUUID().toString
            sessionRegion ! ConnectionSession.CreateSession(sessionId, user, serverConnection)
            serverConnection ! Write(response.packageData())

            context.become(handleMessage)
          case e =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
        f onFailure {
          case t =>
            val response = ErrorResponse(1, "not found", result.seqNo)
            serverConnection ! Write(response.packageData())
        }
      case _ =>
        //TODO close connection
    }
  }


  def handleMessage: Receive = {
    case Received(data) =>
      try {
        sessionRegion ! SendPackage(sessionId, WTRequestParser(data))
      }
      catch {
        case e: ExceptionWithErrorInfo =>
        case NonFatal(e) =>
        case e: Throwable =>
          e.printStackTrace()
      }
    case PeerClosed =>
      context stop self
  }
}
