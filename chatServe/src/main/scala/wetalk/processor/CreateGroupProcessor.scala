package wetalk.processor


import java.sql.Connection
import java.util.Date

import akka.actor.Actor.Receive
import akka.actor.ActorRef
import wetalk.data.Database
import wetalk.data._
import wetalk.parser._

/**
 * Created by goldratio on 1/10/15.
 */
object CreateGroupProcessor {
  import wetalk._

  def processor(userId: String, connection: ActorRef): Receive = {
    case createGroup: CreateGroupRequest =>
      val sql = "insert into IMGroup(name, avatar, description, create_user_id, type, status, count, created, updated)" +
        "values(?, ?, ?, ?, ?, ?, ?, ?, ?)"
      Database.withConnection { connect =>
        connect.withStatement(sql) { statement =>
          val date = new Date(System.currentTimeMillis())

          statement.setString(1, "")
          statement.setString(2, "")
          statement.setString(3, "")
          statement.setString(4, userId)
          statement.setInt(5, 0)
          statement.setInt(6, 0)
          statement.setInt(7, createGroup.members.size + 1)
          statement.setTimestamp(8, date)
          statement.setTimestamp(9, date)
          val result = statement.executeUpdate()
          if (result > 0) {
            getCurrentId(connect).map { id =>
              joinGroup(connect, id, userId)
              createGroup.members.foreach { userId =>
                joinGroup(connect, id, userId)
              }
              val group = Group(id, "", "", "", userId, 0, 0, createGroup.members.size + 1, date, date, createGroup.members)
              val response = CreateGroupResponse(createGroup.seqNo, group)
              connection ! response
            }
          }
          else {
            val response = ErrorMessage(createGroup.seqNo, "group create failed")
            connection ! response
          }
        }
      }
  }


  def getCurrentId(connect: Connection): Option[String] = {
    val sql = "SELECT LAST_INSERT_ID()"
    connect.withStatement(sql) { statement =>
      statement.withResult { rs =>
        if (rs.next()) {
          Some(rs.getString(1))
        }
        else {
          None
        }
      }
    }
  }

  def joinGroup(connect: Connection, groupId: String, userId: String): Unit = {
    val sql = "insert into IMGroupRelation(group_id, user_id) values(?, ?)"
    connect.withStatement(sql) { statement =>
      statement.withResult { rs =>
        statement.setString(1, groupId)
        statement.setString(2, userId)
        statement.executeUpdate()
      }
    }
  }


}
