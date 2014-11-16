package wetalk

import java.nio.ByteOrder
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.pattern.ask
import akka.actor.{Props, ActorRef, Actor, ActorLogging}
import akka.io.Tcp.{Write, PeerClosed, Received}
import akka.util.{ByteString, Timeout}
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
        val it = data.iterator
        it.getInt
        handleParsingResult(WTRequestParser(it))
      }
      catch {
        case e: ExceptionWithErrorInfo =>
          e.printStackTrace()
        case NonFatal(e) =>
          e.printStackTrace()
        case e: Throwable =>
          e.printStackTrace()
      }
    case PeerClosed =>
      context stop self
    case t =>
      println(s"do know type${t}")
  }


  def handleParsingResult(result: WTPackage) = {
    val serverConnection = sender()
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

  var count = 0

  var hasRemainData = false
  var remainData: ByteString = null
  var lastData: ByteString = null

  def handleMessage: Receive = {
    case Received(data) =>
      count = count + 1
      val remainLength = data.length
      try {
        var start = 0
        if(hasRemainData) {
          val dataLength = remainData.length
          val lastLength = if (dataLength < 4) {
            val length = data.slice(0, 4 - dataLength)
            val frameBuilder = ByteString.newBuilder
            frameBuilder.append(remainData)
            frameBuilder.append(length)
            frameBuilder.result().iterator.getInt
          }
          else {
            remainData.iterator.getInt
          }
          val frameBuilder = ByteString.newBuilder
          if(dataLength > 4) {
            frameBuilder.append(remainData.slice(4, dataLength ))
          }
          start = Math.max(0, 4 - dataLength)

          val tempData = data.slice(start, lastLength - dataLength )
          frameBuilder.append(tempData)
          val result = frameBuilder.result()
          try {
            val tempPackage = WTRequestParser(result.iterator)
            if (tempPackage.isInstanceOf[MessageServerRequest] ) {
              //println("remainData" + remainData)
              //println("result" + result)
              //println("lastData" + lastData)
              //println("data" + data)
              println("test")
            }
            sessionRegion ! SendPackage(sessionId, tempPackage)
          }
          catch {
            case e =>
              //println("remainData" + remainData)
              //println("result" + result)
              //println("lastData" + lastData)
              //println("data" + data)
              e.printStackTrace()
          }
          hasRemainData = false
          remainData = null
          start = lastLength - dataLength
        }
        while(start <= remainLength - 1) {
          if (start + 4 > remainLength) {
            hasRemainData = true
            remainData = data.slice(start , remainLength)
            if(remainData.length > 100) {
              println("test")
            }
            start = remainLength
          }
          else {
            val packageLength = data.slice(start, start + 4).iterator.getInt

            val end = start + packageLength
            if(end > remainLength) {
              hasRemainData = true
              remainData = data.slice(start, remainLength)
              if(remainData.length > 100) {
                println("test")
              }
              start = remainLength
              lastData = data
            }
            else {
              val packageData = data.slice(start + 4, end)
              val tempPackage = WTRequestParser(packageData.iterator)
              if (tempPackage.isInstanceOf[MessageServerRequest] ) {
                //println(data)
                println("test")
              }
              sessionRegion ! SendPackage(sessionId, tempPackage)
              start = end
            }
          }
        }
      }
      catch {
        case e: ExceptionWithErrorInfo =>
          e.printStackTrace()
        case NonFatal(e) =>
          e.printStackTrace()
        case e: Throwable =>
          e.printStackTrace()
      }
    case PeerClosed =>
      context stop self

    case t =>
      println(s"do know type${t}")
  }
}
