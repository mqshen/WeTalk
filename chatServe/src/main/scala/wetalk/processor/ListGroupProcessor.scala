package wetalk.processor

/**
 * Created by goldratio on 1/10/15.
 */

import java.sql.{Connection, ResultSet, PreparedStatement}

import akka.actor.Actor.Receive
import akka.actor.{ActorRef, ActorContext}
import wetalk.data._
import wetalk.parser._

import scala.collection.mutable.ArrayBuffer

object ListGroupProcessor {
  import wetalk._

  def processor(userId: String, connection: ActorRef): Receive = {
    case listGroup: ListGroupRequest =>
      val sql = "select a.id, name, avatar, description, create_user_id, type, status, count, a.created, a.updated from " +
        "IMGroup a, IMGroupRelation b where a.id = b.group_id and b.user_id = ? and status = 0"
      Database.withConnection { connect =>
        connect.withStatement(sql) { statement =>
          statement.setString(1, userId)
          statement.withResult { rs =>
            val groups = ArrayBuffer[Group]()
            while (rs.next()) {
              val groupId = rs.getString("id")
              val userIds = getGroupUserIds(connect, groupId)
              val group = Group(groupId, rs.getString("name"), rs.getString("avatar"),
                rs.getString("description"), rs.getString("create_user_id"), rs.getInt("type"), rs.getInt("status"), rs.getInt("count"),
                rs.getDate("created"), rs.getDate("updated"), userIds)
              groups.append(group)
            }
            val response = ListGroupResponse(listGroup.seqNo, groups.toList)
            connection ! response
          }
        }
      }
  }

  def getGroupUserIds(connect: Connection, groupId: String): List[String] = {
    val sql = "select user_id from IMGroupRelation where group_id = ?"
    connect.withStatement(sql) { statement =>
      statement.setString(1, groupId)
      statement.withResult { rs =>
        val users = ArrayBuffer[String]()
        while (rs.next()) {
          users.append(rs.getString("user_id"))
        }
        users.toList
      }
    }
  }
}
