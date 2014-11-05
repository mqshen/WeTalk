package wetalk.data

import java.sql.{ResultSet, PreparedStatement, Statement, DriverManager}
import java.util.Date

import akka.actor.{Props, Actor}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.mutable.ArrayBuffer

/**
 * Created by goldratio on 11/3/14.
 */
case class UserAuth(userName: String, password: String)
case class UserSync(userName: String, syncKey: Long)
case class UserInfo(userName: String)

object NotFound

case class GetDepartment(departmentId: Int)
case class GetFriend(userId: Int)
case class GetRecentContract(userId: Int)
case class GetGroupList(userId: Int)
case class GetRecentGroupList(userId: Int)

object DataManager
{
  def props() = Props(classOf[DataManager])
}

class DataManager extends Actor {

  val config = ConfigFactory.load().getConfig("wetalk.database")
  val Settings = new Settings(config)

  class Settings(config: Config) {
    val connect =  {
      val url = config.getString("mysql.url")
      val username = config.getString("mysql.username")
      val password = config.getString("mysql.password")
      Class.forName("com.mysql.jdbc.Driver").newInstance()
      DriverManager.getConnection(url, username, password)
    }
  }

  def close(statement: PreparedStatement): Unit = {
    try {
      if(statement != null) {
        statement.close()
      }
    }
    catch {
      case e: Exception =>
        throw e
    }
  }


  def close(resultSet: ResultSet): Unit = {
    try {
      if(resultSet != null) {
        resultSet.close()
      }
    }
    catch {
      case e: Exception =>
        throw e
    }
  }

  override def receive: Receive = {
    case UserAuth(userName, password) =>
      val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
        "created, updated from IMUsers where name = ? and password =?"
      var statement: PreparedStatement = null
      var rs: ResultSet = null
      try {
        statement = Settings.connect.prepareStatement(sql)
        statement.setString(1, userName)
        statement.setString(2, password)
        rs = statement.executeQuery
        if(rs.next()) {
          val user = User( rs.getInt("id"), rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
            rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
            rs.getString("mail"), rs.getDate("created"), rs.getDate("updated"))
          sender() ! user
        }
        else {
          sender() ! NotFound
        }
      }
      finally {
        close(rs)
        close(statement)
      }
    case GetDepartment(departmentId) =>
      val date = new Date(System.currentTimeMillis())
      val department = Department(1, "1", "2", "1", "1", 0, date, date)
      sender() ! department
    case GetRecentGroupList(userId) =>
      val groups = getGroupList(userId, false)
      sender() ! groups
    case GetGroupList(userId) =>
      val groups = getGroupList(userId, true)
      sender() ! groups
    case GetFriend(userId) =>
      val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
        "created, updated from IMUsers, IMFriend where IMUsers.id = IMFriend.friend_id and IMFriend.user_id = ?"
      var statement: PreparedStatement = null
      var rs: ResultSet = null
      try {
        statement = Settings.connect.prepareStatement(sql)
        statement.setInt(1, userId)
        rs = statement.executeQuery
        val users = ArrayBuffer[User]()
        while (rs.next()) {
          val user = User( rs.getInt("id"), rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
            rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
            rs.getString("mail"), rs.getDate("created"), rs.getDate("updated"))
          users.append(user)
        }
        sender() ! users.toList
      }
      finally {
        close(rs)
        close(statement)
      }
    case GetRecentContract(userId) =>
      val sql = "select id, friend_id, created, updated from IMRecentContact where id = ?"
      var statement: PreparedStatement = null
      var rs: ResultSet = null
      try {
        statement = Settings.connect.prepareStatement(sql)
        statement.setInt(1, userId)
        rs = statement.executeQuery
        val users = ArrayBuffer[RecentContact]()
        while (rs.next()) {
          val user = RecentContact( rs.getInt("id"), rs.getInt("user_id"), rs.getInt("friend_id"),
            rs.getInt("status"), rs.getDate("created"), rs.getDate("updated"))
          users.append(user)
        }
        sender() ! users.toList
      }
      finally {
        close(rs)
        close(statement)
      }
  }

  def getGroupList(userId: Int, isRecent: Boolean): List[Group] = {
    val condition = if(isRecent) {
      " and status = 1"
    }
    else {
      ""
    }
    val sql = "select a.id, name, avatar, description, create_user_id, type, status, count, a.created, a.updated from " +
      "IMGroup a, IMGroupRelation b where a.id = b.group_id and b.user_id = ?" + condition
    var statement: PreparedStatement = null
    var rs: ResultSet = null
    try {
      statement = Settings.connect.prepareStatement(sql)
      statement.setInt(1, userId)
      rs = statement.executeQuery
      val groups = ArrayBuffer[Group]()
      while (rs.next()) {
        val group = Group( rs.getInt("id"), rs.getString("name"), rs.getString("avatar"),
          rs.getString("description"), rs.getInt("create_user_id"), rs.getInt("type"), rs.getInt("status"), rs.getInt("count"),
          rs.getDate("created"), rs.getDate("updated"))
        groups.append(group)
      }
      groups.toList
    }
    finally {
      close(rs)
      close(statement)
    }
  }
}
