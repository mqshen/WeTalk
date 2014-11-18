package wetalk

import java.nio.ByteOrder

import akka.io.Tcp.Command
import akka.util.{ ByteIterator, ByteString }
import wetalk.data._

import wetalk.util._

/**
 * Created by goldratio on 11/2/14.
 */
object ServiceType extends Enumeration {
  type ServiceType = Value
  val Login, Friend, Message, AllUser, User = Value
}

object WTRequestParser {
  val headerLength = 12

  def apply(it: ByteIterator)(implicit byteOrder: java.nio.ByteOrder): WTPackage = {
    val sId = it.getShort
    val cId = it.getShort
    val version = it.getShort
    val seqNo = it.getInt

    //println(s"sid: ${sId}, cId: ${cId} seqNo: ${seqNo.toInt}")
    sId match {
      case 1 => {
        cId match {
          case 1 =>
            MessageServerRequest(seqNo)
          case 2 =>
            MessageSeverAddressRequest(seqNo)
          case 3 =>
            val userId = it.getString()
            val password = it.getString()
            val status = it.getInt
            val clientType = it.getInt
            val clientVersion = it.getString()
            LoginRequest(userId, password, status, clientType, clientVersion, seqNo)
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
            val message = Message(msgNo, fromId, toId, timestamp, msgType, content, attachContent)
            MessageSend(message, seqNo)
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
            val users = (0 until (size)).map(_ => it.getString()).toList

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

trait WTPackage extends Command with Serializable {
  implicit val byteOrder = ByteOrder.BIG_ENDIAN
  def serverId: Int
  def commandId: Int
  def length: Int
  val version: Int = 1
  def seqNo: Int

  def packageData(): ByteString = {
    val payload = packageObject
    val length = 14 + payload.length
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(length)
    frameBuilder.putShort(serverId)
    frameBuilder.putShort(commandId)
    frameBuilder.putShort(version)
    frameBuilder.putInt(seqNo)
    frameBuilder.append(payload)
    frameBuilder.result()
  }

  def packageObject: ByteString
}

case class HeartbeatRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 7
  val commandId = 1

  def packageObject: ByteString = {
    ByteString("")
  }
}
// login request
case class MessageServerRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 1
  val commandId = 1

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class MessageSeverAddressRequest(seqNo: Int) extends WTPackage {
  val length = 14
  val serverId = 1
  val commandId = 2
  def packageObject: ByteString = {
    implicit val byteOrder = ByteOrder.BIG_ENDIAN
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(length)
    frameBuilder.putShort(1)
    frameBuilder.putShort(1)
    frameBuilder.putInt(seqNo)
    frameBuilder.result()
  }
}
// friend request
case class DepartmentRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 2
  val commandId = 18

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class GetFriendRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 2
  val commandId = 14

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class MessageSend(message: Message, seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 3
  val commandId = 1

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder

    frameBuilder.putInt(message.seqNo)
    frameBuilder.putString(message.fromId)
    frameBuilder.putString(message.toId)
    frameBuilder.putInt(0)
    frameBuilder.putByte(message.msgType)
    frameBuilder.putString(message.content)
    frameBuilder.putString(message.attachContent)
    frameBuilder.result()
  }
}

case class UnreadMessageCountRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 3
  val commandId = 7

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class UnreadMessageRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 2
  val commandId = 18

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class LoginRequest(userName: String, password: String, status: Int, clientType: Int, clientVersion: String, seqNo: Int)
    extends WTPackage {
  val length = 12
  val serverId = 1
  val commandId = 3
  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putString(userName)
    frameBuilder.putString(password)
    frameBuilder.putInt(status)
    frameBuilder.putInt(clientType)
    frameBuilder.putString(clientVersion)
    frameBuilder.result()
  }
}

case class RecentContactRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 2
  val commandId = 1

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class GroupListRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 1

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class UnreadGroupMessageCountRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 5

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class CreateTempGroupRequest(name: String, avatar: String, users: List[String], seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 12

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class CreateTempGroupResponse(group: Group, seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 13

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(0)
    frameBuilder.putString(group.id.toString)
    frameBuilder.putString(group.name)
    frameBuilder.putInt(group.users.size)
    group.users.foreach { id =>
      frameBuilder.putString(id)
    }
    frameBuilder.result()
  }
}

case class AddMemberTempGroupResponse(group: Group, seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 15

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(0)
    frameBuilder.putString(group.id.toString)
    frameBuilder.putString(group.name)
    frameBuilder.putInt(group.users.size)
    group.users.foreach { id =>
      frameBuilder.putString(id)
    }
    frameBuilder.result()
  }
}

case class RecentGroupListRequest(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 16

  def packageObject: ByteString = {
    ByteString("")
  }
}

//response
case class HeartbeatResponse(seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 7
  val commandId = 1

  def packageObject: ByteString = {
    ByteString("")
  }
}

case class MessageSendAckResponse(msgNo: Int, timestamp: Long, seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 3
  val commandId = 2

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(msgNo)
    frameBuilder.putLong(timestamp)
    frameBuilder.result()
  }
}

case class MessageReceiveResponse(message: Message, seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 3
  val commandId = 1

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(seqNo)
    frameBuilder.putString(message.fromId)
    frameBuilder.putString(message.toId)
    frameBuilder.putInt(message.timestamp.toInt)
    frameBuilder.putByte(message.msgType)
    frameBuilder.putString(message.content)
    frameBuilder.putString(message.attachContent)
    frameBuilder.result()
  }
}
case class ErrorResponse(errorCode: Int, errorMsg: String, seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 1
  val commandId = 2
  override def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(errorCode)
    frameBuilder.putString(errorMsg)
    frameBuilder.result()
  }
}
case class MessageServerResponse(messageServer: List[(String, Short)], seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 1
  val commandId = 2
  def packageObject: ByteString = {
    implicit val byteOrder = ByteOrder.BIG_ENDIAN
    var length = 12
    length = length + 4
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(0)
    messageServer.foreach { server =>
      val bytes = server._1.getBytes
      frameBuilder.putInt(bytes.length)
      frameBuilder.putBytes(bytes)
    }
    frameBuilder.putShort(messageServer(0)._2)
    frameBuilder.result()
  }
}

case class LoginResponse(user: User, seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 1
  val commandId = 4

  override def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(1)
    frameBuilder.putInt(0)
    frameBuilder.putInt(0)
    frameBuilder.putString(user.id.toString)
    frameBuilder.putString(user.nick)
    frameBuilder.putString(user.avatar)
    frameBuilder.putString(user.phone)
    frameBuilder.putString(user.mail)
    frameBuilder.putString("sss")
    frameBuilder.result()
  }
}

case class DepartResponse(department: Department, seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 2
  val commandId = 18

  override def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(1)
    frameBuilder.putString(department.id.toString)
    frameBuilder.putString(department.title)
    frameBuilder.putString(department.description)
    frameBuilder.putString(department.parentID)
    frameBuilder.putString(department.leader)
    frameBuilder.putInt(0)
    frameBuilder.result()
  }
}

case class FriendsResponse(users: List[User], seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 2
  val commandId = 15

  override def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(users.size)
    users.foreach { u =>
      frameBuilder.putString(u.id.toString)
      frameBuilder.putString(u.name)
      frameBuilder.putString(u.nick)
      frameBuilder.putString(u.avatar)
      frameBuilder.putInt(u.status)
      frameBuilder.putInt(u.sex)
      frameBuilder.putString(u.phone)
      frameBuilder.putString(u.mail)
    }
    frameBuilder.result()
  }
}

case class RecentContactResponse(users: List[RecentContact], seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 2
  val commandId = 3

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(users.size)
    users.foreach { u =>
      frameBuilder.putString(u.id.toString)
      frameBuilder.putInt(u.status)
    }
    frameBuilder.result()
  }
}

case class UnreadMessageCountResponse(unread: List[(String, Int)], seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 3
  val commandId = 8

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(unread.size)
    unread.foreach { u =>
      frameBuilder.putString(u._1)
      frameBuilder.putInt(u._2)
    }
    frameBuilder.result()
  }
}

case class GroupListResponse(groups: List[Group], seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 2

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(groups.size)
    groups.foreach { g =>
      frameBuilder.putString(g.id.toString)
      frameBuilder.putString(g.name)
      frameBuilder.putString(g.avatar)
      frameBuilder.putString(g.creator.toString)
      frameBuilder.putInt(g.groupType)
      frameBuilder.putLong(g.updated.getTime)
      frameBuilder.putLong(g.count)
    }
    frameBuilder.result()
  }
}

case class UnreadGroupMessageCountResponse(unread: List[(String, Int)], seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 6

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(unread.size)
    unread.foreach { u =>
      frameBuilder.putString(u._1)
      frameBuilder.putInt(u._2)
    }
    frameBuilder.result()
  }
}

case class RecentGroupListResponse(groups: List[Group], seqNo: Int) extends WTPackage {
  val length = 12
  val serverId = 5
  val commandId = 17

  def packageObject: ByteString = {
    val frameBuilder = ByteString.newBuilder
    frameBuilder.putInt(groups.size)
    groups.foreach { g =>
      frameBuilder.putString(g.id.toString)
      frameBuilder.putString(g.name)
      frameBuilder.putString(g.avatar)
      frameBuilder.putString(g.creator.toString)
      frameBuilder.putInt(g.groupType)
      frameBuilder.putInt(g.updated.getTime.toInt)
      frameBuilder.putInt(g.count)
      g.users.foreach { userId =>
        frameBuilder.putString(userId)
      }
    }
    frameBuilder.result()
  }
}