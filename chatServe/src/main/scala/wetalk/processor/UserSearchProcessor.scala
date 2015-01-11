package wetalk.processor

/**
 * Created by goldratio on 1/10/15.
 */
import akka.actor.Actor.Receive
import akka.actor.{ActorRef, ActorContext}
import wetalk.data.{User, Database, Group}
import wetalk.parser._

import scala.collection.mutable.ArrayBuffer

object UserSearchProcessor {
  import wetalk._

  def processor(userId: String, connection: ActorRef): Receive = {
    case userSearch: UserSearchRequest =>
      val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
        "created, updated from IMUsers where name like CONCAT('%', ?, '%')"
      Database.withConnection { connect =>
        connect.withStatement(sql) { statement =>
          statement.setString(1, userSearch.name)
          statement.withResult { rs =>
            var users = ArrayBuffer[User]()
            while (rs.next()) {
              val update = rs.getDate("updated")
              val updateData = if (update == null) None else Some(update)
              val user = User(rs.getString("id"), rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
                rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
                rs.getString("mail"), rs.getDate("created"), updateData)
              users += user
            }
            val response = ListSearchResponse(userSearch.seqNo, users.toList)
            connection ! response
          }
        }
      }
  }

}
