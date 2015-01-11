package wetalk.processor

import java.sql.{PreparedStatement, ResultSet, Statement, Connection}

import akka.actor.Actor.Receive
import wetalk.data.{Database, User}

/**
 * Created by goldratio on 1/10/15.
 */
trait Processor {
  import wetalk._


  def getUser(connection: Connection, id: String): Option[User] = {
    val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
      "created, updated from IMUsers where id = ? "
    Database.withConnection { connect =>
      connect.withStatement(sql) { statement =>
        statement.setString(1, id)
        statement.withResult { rs =>
          if (rs.next()) {
            val update = rs.getDate("updated")
            val updateData = if (update == null) None else Some(update)
            val user = User(rs.getInt("id").toString, rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
              rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
              rs.getString("mail"), rs.getDate("created"), updateData)
            Some(user)
          } else {
            None
          }
        }
      }
    }
  }

}
