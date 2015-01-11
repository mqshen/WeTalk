package wetalk.processor


import akka.actor.Actor.Receive
import akka.actor.{ActorRef, ActorContext}
import wetalk.data._
import wetalk.parser._

/**
 * Created by goldratio on 1/10/15.
 */
object UserSyncProcessor {
  import wetalk._

  def processor(connection: ActorRef, context: ActorContext): Receive = {
    case userSync: UserSync =>
      val sql = "select id, message from IMOfflineMessage where user_id = ? and id > ?"
      Database.withConnection { connect =>
        connect.withStatement(sql) { statement =>
          statement.setString(1, userSync.userId)
          statement.setLong(2, userSync.syncKey)
          statement.withResult { rs =>
            while (rs.next()) {
              val id = rs.getLong(1)
              val message = rs.getString(2)
              val offlineMessage = OfflineMessageResponse(id, message)
              connection ! offlineMessage
            }
          }
        }
      }
  }

}
