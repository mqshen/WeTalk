package wetalk.processor

import akka.actor.Actor.Receive
import akka.actor.{ActorContext, ActorRef}
import wetalk.data.{Database}
import wetalk.parser._

/**
 * Created by goldratio on 1/10/15.
 */
object ChatMessageProcessor {
  import wetalk._

  def processor(connection: ActorRef, context: ActorContext, sessionRegion: ActorRef): Receive = {
    case chatMessage: ChatMessage =>
      val index = chatMessage.to.indexOf("@room")
      if (index > 0) {
        val groupId = chatMessage.to.substring(0, index)
        processGroupMessage(chatMessage, chatMessage.from, groupId, connection, sessionRegion)
      }
      else {
        processMessage(chatMessage, chatMessage.from, chatMessage.to, connection, sessionRegion)
      }

  }

  def processGroupMessage(chatMessage: ChatMessage, userId: String, groupId: String, connection: ActorRef, sessionRegion: ActorRef): Unit = {
    val sql = "select user_id from IMGroupRelation where group_id = ? and user_id = ?"
    Database.withConnection { connect =>
      connect.withStatement(sql) { statement =>
        statement.setString(1, userId)
        statement.setString(2, groupId)
        statement.withResult { rs =>
          if (rs.next()) {
            val sql = "select user_id from IMGroupRelation where group_id = ?"
            connect.withStatement(sql) { statement =>
              statement.setString(1, groupId)
              statement.withResult { rs =>
                while (rs.next()) {
                  val userId = rs.getString(1)
                  sessionRegion ! DispatchMessage(chatMessage.seqNo, userId, ReceiveChatMessage(chatMessage))
                }
              }
            }
            connection ! ChatAckResponse(chatMessage.seqNo, chatMessage.timestamp)
          }
          else {
            connection ! ErrorMessage("0", "no privilege to this group")
          }
        }
      }
    }
  }

  def processMessage(chatMessage: ChatMessage, userId: String, friendId: String, connection: ActorRef, sessionRegion: ActorRef): Unit = {
    val sql = "select count(*) from IMFriend where user_id = ? and friend_id = ?"
    Database.withConnection { connect =>
      connect.withStatement(sql) { statement =>
        statement.setString(1, userId)
        statement.setString(2, friendId)
        statement.withResult { rs =>
          if (rs.next()) {
            sessionRegion ! DispatchMessage(chatMessage.seqNo, chatMessage.to, ReceiveChatMessage(chatMessage))
            connection ! ChatAckResponse(chatMessage.seqNo, chatMessage.timestamp)
          }
          else {
            connection ! ErrorMessage("0", "no privilege to this user")
          }
        }
      }
    }
  }


}
