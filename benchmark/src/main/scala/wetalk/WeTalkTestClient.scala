package wetalk

import java.net.InetSocketAddress
import java.nio.ByteOrder
import java.util.Date

import akka.actor.{ActorRef, Actor}
import akka.actor.Actor.Receive
import akka.io.{IO, Tcp}
import akka.io.Tcp.{Write, Received}
import akka.util.{ByteIterator, ByteString}
import wetalk.data.Message

import scala.concurrent.forkjoin.ThreadLocalRandom
import scala.util.control.NonFatal

import wetalk.data._

import wetalk.util._

/**
 * Created by goldratio on 11/10/14.
 */




object WTResponseParser {
  val headerLength = 12

  def apply(it: ByteIterator)(implicit byteOrder : java.nio.ByteOrder): WTPackage = {
    val sId = it.getShort
    val cId = it.getShort
    val version = it.getShort
    val seqNo = it.getShort
    //println(s"sid: ${sId} cId: $cId version: ${version} seqNo: ${seqNo}")
    sId match {
      case 1 => {
        cId match {
          case 4 =>
            val serverTime = it.getInt
            val result = it.getInt
            val onlineStatus = it.getInt
            val userId = it.getString
            val name = it.getString()
            val avatar = it.getString()
            val telphone = it.getString()
            val email = it.getString()
            val token = it.getString()

            val currentTime = new Date()
            LoginResponse(User(userId.toInt, name,"",avatar,"",0,0,0,telphone,email,currentTime, currentTime), seqNo)
        }
      }
      case 2 => {
        cId match {
          case 1 =>
            RecentContactRequest(seqNo)
          case 14 =>
            GetFriendRequest(seqNo)
          case 18 =>
            DepartmentRequest(seqNo)
        }
      }
      case 3 => {
        cId match {
          case 1 =>
            val msgNo = it.getInt
            val fromId = it.getString()
            val toId = it.getString()
            val temp = it.getInt
            val msgType = it.getByte
            val content = it.getString()
            val attachContent = it.getString()
            val timestamp = System.currentTimeMillis()
            val message =  Message(msgNo, fromId, toId, timestamp, msgType, content, attachContent)
            MessageSend(message, seqNo)
          case 2 =>
            val msgNo = it.getInt
            val timestamp = it.getLong
            MessageSendAckResponse(msgNo, timestamp, seqNo)
          case 7 =>
            UnreadMessageCountRequest(seqNo)
        }
      }
      case 5 => {
        cId match {
          case 1 =>
            GroupListRequest(seqNo)
          case 5 =>
            UnreadGroupMessageCountRequest(seqNo)
          case 12 =>
            val name = it.getString()
            val avatar = it.getString()
            val size = it.getInt
            val users = (0 until(size)).map(_ => it.getString()).toList

            CreateTempGroupRequest(name, avatar, users, seqNo)
          case 16 =>
            RecentGroupListRequest(seqNo)
        }
      }
      case 7 => {
        cId match {
          case 1 =>
            HeartbeatRequest(seqNo)
        }
      }
    }
  }

  def badProtocol = throw new Exception("protocol not support")

}

object WeTalkTestClient  {

  val userIDs = (1 until 17).map(i => i.toString).toArray
  val users = Array("mqshen@126.com" , "goldratio87@gmail.com", "test@126.com",
    "test1@126.com",
    "test2@126.com",
    "test3@126.com",
    "test4@126.com",
    "test5@126.com",
    "test6@126.com",
    "test7@126.com",
    "test8@126.com",
    "test9@126.com",
    "test10@126.com",
    "test11@126.com",
    "test12@126.com",
    "test13@126.com",
    "test14@126.com",
    "test15@126.com",
    "test16@126.com",
    "test17@126.com",
    "test18@126.com",
    "test19@126.com",
    "test20@126.com",
    "test21@126.com",
    "test22@126.com",
    "test23@126.com",
    "test24@126.com",
    "test25@126.com",
    "test26@126.com",
    "test27@126.com",
    "test28@126.com",
    "test29@126.com",
    "test30@126.com",
    "test31@126.com",
    "test32@126.com",
    "test33@126.com",
    "test34@126.com",
    "test35@126.com",
    "test36@126.com",
    "test37@126.com",
    "test38@126.com",
    "test39@126.com",
    "test40@126.com",
    "test41@126.com",
    "test42@126.com",
    "test43@126.com",
    "test44@126.com",
    "test45@126.com",
    "test46@126.com",
    "test47@126.com",
    "test48@126.com",
    "test49@126.com",
    "test50@126.com",
    "test51@126.com",
    "test52@126.com",
    "test53@126.com",
    "test54@126.com",
    "test55@126.com",
    "test56@126.com",
    "test57@126.com",
    "test58@126.com",
    "test59@126.com",
    "test60@126.com",
    "test61@126.com",
    "test62@126.com",
    "test63@126.com",
    "test64@126.com",
    "test65@126.com",
    "test66@126.com",
    "test67@126.com",
    "test68@126.com",
    "test69@126.com",
    "test70@126.com",
    "test71@126.com",
    "test72@126.com",
    "test73@126.com",
    "test74@126.com",
    "test75@126.com",
    "test76@126.com",
    "test77@126.com",
    "test78@126.com",
    "test79@126.com",
    "test80@126.com",
    "test81@126.com",
    "test82@126.com",
    "test83@126.com",
    "test84@126.com",
    "test85@126.com",
    "test86@126.com",
    "test87@126.com",
    "test88@126.com",
    "test89@126.com",
    "test90@126.com",
    "test91@126.com",
    "test92@126.com",
    "test93@126.com",
    "test94@126.com",
    "test95@126.com",
    "test96@126.com",
    "test97@126.com",
    "test98@126.com",
    "test99@126.com",
    "test100@126.com",
    "test101@126.com",
    "test102@126.com",
    "test103@126.com",
    "test104@126.com",
    "test105@126.com",
    "test106@126.com",
    "test107@126.com",
    "test108@126.com",
    "test109@126.com",
    "test110@126.com",
    "test111@126.com",
    "test112@126.com",
    "test113@126.com",
    "test114@126.com",
    "test115@126.com",
    "test116@126.com",
    "test117@126.com",
    "test118@126.com",
    "test119@126.com",
    "test120@126.com",
    "test121@126.com",
    "test122@126.com",
    "test123@126.com",
    "test124@126.com",
    "test125@126.com",
    "test126@126.com",
    "test127@126.com",
    "test128@126.com",
    "test129@126.com",
    "test130@126.com",
    "test131@126.com",
    "test132@126.com",
    "test133@126.com",
    "test134@126.com",
    "test135@126.com",
    "test136@126.com",
    "test137@126.com",
    "test138@126.com",
    "test139@126.com",
    "test140@126.com",
    "test141@126.com",
    "test142@126.com",
    "test143@126.com",
    "test144@126.com",
    "test145@126.com",
    "test146@126.com",
    "test147@126.com",
    "test148@126.com",
    "test149@126.com",
    "test150@126.com",
    "test151@126.com",
    "test152@126.com",
    "test153@126.com",
    "test154@126.com",
    "test155@126.com",
    "test156@126.com",
    "test157@126.com",
    "test158@126.com",
    "test159@126.com",
    "test160@126.com",
    "test161@126.com",
    "test162@126.com",
    "test163@126.com",
    "test164@126.com",
    "test165@126.com",
    "test166@126.com",
    "test167@126.com",
    "test168@126.com",
    "test169@126.com",
    "test170@126.com",
    "test171@126.com",
    "test172@126.com",
    "test173@126.com",
    "test174@126.com",
    "test175@126.com",
    "test176@126.com",
    "test177@126.com",
    "test178@126.com",
    "test179@126.com",
    "test180@126.com",
    "test181@126.com",
    "test182@126.com",
    "test183@126.com",
    "test184@126.com",
    "test185@126.com",
    "test186@126.com",
    "test187@126.com",
    "test188@126.com",
    "test189@126.com",
    "test190@126.com",
    "test191@126.com",
    "test192@126.com",
    "test193@126.com",
    "test194@126.com",
    "test195@126.com",
    "test196@126.com",
    "test197@126.com",
    "test198@126.com",
    "test199@126.com"
  )

  private var _nextId = 0
  private def nextId = {
    _nextId += 1
    _nextId
  }



  case object OnOpen
  case object OnClose
  case class MessageArrived(roundtrip: Long)

  case object SendMessage

}
class WeTalkTestClient(remote: InetSocketAddress, commander: ActorRef) extends Actor {
  import WeTalkTestClient._
  implicit val byteOrder = ByteOrder.BIG_ENDIAN

  val user = users(nextId)

  private var _connection: ActorRef = _

  def connection = _connection

  import Tcp._
  import context.system

  IO(Tcp) ! Connect(remote)
  var count = 0

  override def receive: Receive = {
    case Received(data) =>
      try {
        val it = data.iterator

        it.getInt
      }
      catch {
        case e: ExceptionWithErrorInfo =>
        case NonFatal(e) =>
        case e: Throwable =>
          e.printStackTrace()
      }


    case c @ Connected(remote, local) =>
      val connection = sender()
      connection ! Register(self)
      val login = LoginRequest(user, "f2720188fc444312d7dec4c3bb82f438" , 0, 0, "1.0" ,0).packageData
      connection ! Write(login)
      _connection = sender()
      context become worker
  }

  var hasRemainData = false
  var remainData: ByteString = null
  var lastData: ByteString = null
  var lastLastData: ByteString = null

  def worker: Receive = {
    case Received(data) =>
      //println("data:" + data)
      val remainLength = data.length
      try {
        var start = 0
        if(hasRemainData) {
          val dataLength = remainData.length
          val tempLength = data.length
          if(dataLength + tempLength < 4) {
            val frameBuilder = ByteString.newBuilder
            frameBuilder.append(remainData)
            frameBuilder.append(data)
            remainData = frameBuilder.result()
            start = data.length
          }
          else {
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
            if(dataLength + tempLength < lastLength) {
              val frameBuilder = ByteString.newBuilder
              frameBuilder.append(remainData)
              frameBuilder.append(data)
              remainData = frameBuilder.result()
              start = data.length
            }
            else {
              val frameBuilder = ByteString.newBuilder
              if(dataLength > 4) {
                frameBuilder.append(remainData.slice(4, dataLength ))
              }
              start = Math.max(0, 4 - dataLength)

              val tempData = data.slice(start, lastLength - dataLength )
              frameBuilder.append(tempData)
              val result = frameBuilder.result()
              try {
                val receive = WTResponseParser(result.iterator)
                handleParsingResult(receive)
              }
              catch {
                case e =>
                  e.printStackTrace()
              }
              hasRemainData = false
              remainData = null
              start = lastLength - dataLength
            }
          }
        }
        while(start <= remainLength - 1) {
          if (start + 4 > remainLength) {
            hasRemainData = true
            remainData = data.slice(start, remainLength)
            start = remainLength
          }
          else {
            val packageLength = data.slice(start, start + 4).iterator.getInt

            val end = start + packageLength
            if(end > remainLength) {
              hasRemainData = true
              remainData = data.slice(start, remainLength)
              //println("remainData:" + remainData)
              start = remainLength
              lastLastData = lastData
              lastData = data
            }
            else {
              try {
                val packageData = data.slice(start + 4, end)
                val receive = WTResponseParser(packageData.iterator)
                handleParsingResult(receive)
                start = end
                lastData = data
              }
              catch {
                case e =>
                  e.printStackTrace()
              }
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
    case SendMessage =>
      val toId = userIDs(ThreadLocalRandom.current.nextInt(userIDs.size))
      val message = Message(count, user, toId, System.currentTimeMillis(), 1, "test", "")
      count = count + 1

      _connection ! Write(MessageSend(message, count).packageData())

    case p: WTPackage =>
      _connection ! Write(p.packageData())
    case PeerClosed =>
      context stop self
    case data: ByteString =>
      connection ! Write(data)
    case CommandFailed(w: Write) =>
      // O/S buffer was full
      println( "write failed")
  }

  def handleParsingResult(result: WTPackage) = {
    result match {
      case request: LoginResponse =>
        commander ! OnOpen
      case response: MessageSendAckResponse =>
        val messageArrivedAt = System.currentTimeMillis - response.timestamp
        commander ! MessageArrived(messageArrivedAt)
      case t =>
        //println(t)
    }
  }

}

object Test extends App {
  12 until 200 foreach { i => println(s"test${ i }@126.com,")}
}