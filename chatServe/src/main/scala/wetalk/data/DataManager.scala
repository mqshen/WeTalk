package wetalk.data

import java.sql._
import java.util.Date

import akka.actor.{ Props, Actor }
import com.typesafe.config.{ Config, ConfigFactory }
import wetalk.database.HikariCPJdbcDataSource
import wetalk.parser.OfflineMessageResponse

import scala.collection.mutable.ArrayBuffer

/**
 * Created by goldratio on 11/3/14.
 */


object DataManager {
  def props() = Props(classOf[DataManager])
}

class DataManager extends Actor {

  implicit def date2timestamp(date: java.util.Date) = new java.sql.Timestamp(date.getTime)

  val config = ConfigFactory.load().getConfig("wetalk.database.mysql")
//  val Settings = new Settings(config)


  //val driver = Class.forName("com.mysql.jdbc.Driver").newInstance().asInstanceOf[Driver]
  val dataSource = HikariCPJdbcDataSource.forConfig(config, null, "")

  def getConnection = {
    dataSource.createConnection()
  }


  def withConnection[T](f: Connection => T): T = {
    val s = dataSource.createConnection()
    var ok = false
    try {
      val res = f(s)
      ok = true
      res
    }
    finally {
      if(ok) s.close() // Let exceptions propagate normally
      else {
        // f(s) threw an exception, so don't replace it with an Exception from close()
        try s.close() catch { case _: Throwable => }
      }
    }
  }

//  class Settings(config: Config) {
//    val connect = {
//      val url = config.getString("mysql.url")
//      val username = config.getString("mysql.username")
//      val password = config.getString("mysql.password")
//      Class.forName("com.mysql.jdbc.Driver").newInstance()
//      DriverManager.getConnection(url, username, password)
//    }
//  }

  def close(statement: PreparedStatement): Unit = {
    try {
      if (statement != null) {
        statement.close()
      }
    } catch {
      case e: Exception =>
        throw e
    }
  }

  def close(resultSet: ResultSet): Unit = {
    try {
      if (resultSet != null) {
        resultSet.close()
      }
    } catch {
      case e: Exception =>
        throw e
    }
  }

  override def receive: Receive = {
    case GetUser(id) =>
      getUser(id)
    case UserAuth(seqNo, userName, password) =>
      val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
        "created, updated from IMUsers where name = ? and password =?"
      var statement: PreparedStatement = null
      var rs: ResultSet = null
      withConnection { connect =>
        try {
          statement = connect.prepareStatement(sql)
          statement.setString(1, userName)
          statement.setString(2, password)
          rs = statement.executeQuery
          if (rs.next()) {
            val update = rs.getDate("updated")
            val updateData = if(update == null) None else Some(update)
            val user = User(rs.getString("id"), rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
              rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
              rs.getString("mail"), rs.getDate("created"), updateData)
            sender() ! user
          } else {
            sender() ! NotFound
          }
        }
        finally {
          close(rs)
          close(statement)
        }
      }
    case userSync: UserSync =>
      val messages = getUserSync(userSync)
      sender() ! messages
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
    case GetGroupInfo(groupId) =>
      getGroupInfo(groupId).map { group =>
        sender() ! group
      }.getOrElse {
        sender() ! None
      }
    case CheckRelationShip(userId, friendId) =>
      val hasRelation = getRelationShip(userId, friendId)
      sender() ! hasRelation
    case GetFriend(userId) =>
      val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
        "created, updated from IMUsers, IMFriend where IMUsers.id = IMFriend.friend_id and IMFriend.user_id = ?"
      var statement: PreparedStatement = null
      var rs: ResultSet = null
      withConnection { connect =>
        try {
          statement = connect.prepareStatement(sql)
          statement.setString(1, userId)
          rs = statement.executeQuery
          val users = ArrayBuffer[User]()
          while (rs.next()) {
            val update = rs.getDate("updated")
            val updateData = if (update == null) None else Some(update)
            val user = User(rs.getString("id"), rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
              rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
              rs.getString("mail"), rs.getDate("created"), updateData)
            users.append(user)
          }
          sender() ! users.toList
        }
        finally {
          close(rs)
          close(statement)
        }
      }
    case GetRecentContract(userId) =>
      val sql = "select id, friend_id, created, updated from IMRecentContact where id = ?"
      var statement: PreparedStatement = null
      var rs: ResultSet = null
      withConnection { connect =>
        try {
          statement = connect.prepareStatement(sql)
          statement.setInt(1, userId)
          rs = statement.executeQuery
          val users = ArrayBuffer[RecentContact]()
          while (rs.next()) {
            val user = RecentContact(rs.getInt("id"), rs.getString("user_id"), rs.getString("friend_id"),
              rs.getInt("status"), rs.getDate("created"), rs.getDate("updated"))
            users.append(user)
          }
          sender() ! users.toList
        } finally {
          close(rs)
          close(statement)
        }
      }
    case group: Group =>
      createGroup(group)
    case checkGroup: CheckGroupRelationShip =>
      checkGroupRelationShip(checkGroup)
    case offlineMessage : OfflineMessage =>
      storeOfflineMessage(offlineMessage)
    case userSearch : UserSearch =>
      searchUser(userSearch)

    case userToken: UserToken =>
      saveUserToken(userToken)
    case userToken: UserTokenAuth =>
      getUserByToken(userToken)

  }

  def getUserByToken(userToken: UserTokenAuth) = {
    var statement: PreparedStatement = null
    var result: ResultSet = null
    withConnection { connect =>
      statement = connect.prepareStatement("select created from IMSession where id= ? and token = ?")
      try {
        statement.setString(1, userToken.account)
        statement.setString(2, userToken.token)
        result = statement.executeQuery()
        if (result.next()) {
          //val created = rs.getDate("created")
          getUser(userToken.account.toInt)
        }
      }
      finally {
        close(statement)
      }
    }
  }

  def saveUserToken(userToken: UserToken) = {
    var statement: PreparedStatement = null
    withConnection { connect =>
      statement = connect.prepareStatement("insert into IMSession(id, token, created) values (?, ?, ?)")
      try {
        statement.setString(1, userToken.account)
        statement.setString(2, userToken.token)
        val date = new Date(System.currentTimeMillis())
        statement.setTimestamp(3, date)
        statement.executeUpdate()
      }
      finally {
        close(statement)
      }
    }

  }

  def checkGroupRelationShip(checkGroup: CheckGroupRelationShip): Unit = {
    val sql = "select user_id from IMGroupRelation where group_id = ? and user_id = ?"
    var statement: PreparedStatement = null
    var result: ResultSet = null
    withConnection { connect =>
      statement = connect.prepareStatement(sql)
      try {
        statement.setInt(1, checkGroup.groupId)
        statement.setInt(2, checkGroup.userId)
        result = statement.executeQuery()
        if (result.next()) {
          val sql = "select user_id from IMGroupRelation where group_id = ?"
          var users = scala.collection.mutable.ArrayBuffer[String]()
          var rs: ResultSet = null
          var listStatement: PreparedStatement = null
          listStatement = connect.prepareStatement(sql)
          try {
            listStatement.setInt(1, checkGroup.groupId)
            rs = listStatement.executeQuery()
            while (rs.next()) {
              users += rs.getString(1)
            }
            sender() ! users.toList
          }
          finally {
            close(rs)
            close(listStatement)
          }
        }
        else {
          sender() ! List()
        }
      } finally {
        close(result)
        close(statement)
      }
    }
  }

  def joinGroup(groupId: String, userId: String): Unit = {
    val sql = "insert into IMGroupRelation(group_id, user_id)" +
      "values(?, ?)"
    var statement: PreparedStatement = null
    withConnection { connect =>
      statement = connect.prepareStatement(sql)
      try {
        statement.setString(1, groupId)
        statement.setString(2, userId)
        statement.executeUpdate()
      } finally {
        close(statement)
      }
    }
  }

  def createGroup(group: Group) = {
    val sql = "insert into IMGroup(name, avatar, description, create_user_id, type, status, count, created, updated)" +
      "values(?, ?, ?, ?, ?, ?, ?, ?, ?)"
    var statement: PreparedStatement = null
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setString(1, group.name)
        statement.setString(2, group.avatar)
        statement.setString(3, group.description)
        statement.setString(4, group.creator)
        statement.setInt(5, group.groupType)
        statement.setInt(6, group.status)
        statement.setInt(7, group.count)
        statement.setTimestamp(8, group.created)
        statement.setTimestamp(9, group.created)
        val result = statement.executeUpdate()
        if (result > 0) {
          getCurrentId(connect).map {id =>
            joinGroup(id, group.creator)
            group.users.foreach { userId =>
              joinGroup(id, userId)
            }
            sender() ! group.copy(id = id)
          }
        } else
          sender() ! None
      } finally {
        close(statement)
      }
    }
  }

  def getGroupUserIds(groupId: String): List[String] = {
    val sql = "select user_id from IMGroupRelation where group_id = ?"
    var statement: PreparedStatement = null
    var rs: ResultSet = null
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setString(1, groupId)
        rs = statement.executeQuery
        val users = ArrayBuffer[String]()
        while (rs.next()) {
          users.append(rs.getString("user_id"))
        }
        users.toList
      } finally {
        close(rs)
        close(statement)
      }
    }
  }

  def getGroupInfo(groupId: String): Option[Group] = {
    val sql = "select name, avatar, description, create_user_id, type, status, count, created, updated from IMGroup where id = ?"
    var statement: PreparedStatement = null
    var rs: ResultSet = null
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setString(1, groupId)
        rs = statement.executeQuery
        if (rs.next()) {
          val userIds = getGroupUserIds(groupId)
          val group = Group(groupId, rs.getString("name"), rs.getString("avatar"), rs.getString("description"),
            rs.getString("create_user_id"), rs.getInt("type"), rs.getInt("status"), rs.getInt("count"), rs.getDate("created"),
            rs.getDate("updated"), userIds)
          Some(group)
        } else {
          None
        }
      } finally {
        close(rs)
        close(statement)
      }
    }
  }

  def getCurrentId(connect: Connection): Option[String] = {
    val sql = "SELECT LAST_INSERT_ID()"
    var statement: PreparedStatement = null
    var rs: ResultSet = null
      try {
        statement = connect.prepareStatement(sql)
        rs = statement.executeQuery
        if (rs.next()) {
          Some(rs.getString(1))
        } else {
          None
        }
      } finally {
        close(rs)
        close(statement)
      }
  }

  def getGroupList(userId: String, isRecent: Boolean): List[Group] = {
    val condition = if (isRecent) {
      " and status = 1"
    } else {
      ""
    }
    val sql = "select a.id, name, avatar, description, create_user_id, type, status, count, a.created, a.updated from " +
      "IMGroup a, IMGroupRelation b where a.id = b.group_id and b.user_id = ?" + condition
    var statement: PreparedStatement = null
    var rs: ResultSet = null
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setString(1, userId)
        rs = statement.executeQuery
        val groups = ArrayBuffer[Group]()
        while (rs.next()) {
          val groupId = rs.getString("id")
          val userIds = getGroupUserIds(groupId)
          val group = Group(groupId, rs.getString("name"), rs.getString("avatar"),
            rs.getString("description"), rs.getString("create_user_id"), rs.getInt("type"), rs.getInt("status"), rs.getInt("count"),
            rs.getDate("created"), rs.getDate("updated"), userIds)
          groups.append(group)
        }
        groups.toList
      } finally {
        close(rs)
        close(statement)
      }
    }
  }

  def getRelationShip(userId: Int, friend: Int): Int = {
    val sql = "select count(*) from IMFriend where user_id = ? and friend_id = ?"
    var statement: PreparedStatement = null
    var rs: ResultSet = null
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setInt(1, userId)
        statement.setInt(2, friend)
        rs = statement.executeQuery
        if (rs.next()) {
          rs.getInt(1)
        }
        else {
          0
        }
      }
      finally {
        close(rs)
        close(statement)
      }
    }
  }

  def storeOfflineMessage(offlineMessage: OfflineMessage): Unit = {
    val sql = "insert into IMOfflineMessage(user_id, create_date, message) values (?, ?, ?)"
    var statement: PreparedStatement = null
    val date = new Date(System.currentTimeMillis())
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setString(1, offlineMessage.userId)
        statement.setTimestamp(2, date)
        statement.setString(3, offlineMessage.message.toString)
        statement.executeUpdate()
      }
      finally {
        close(statement)
      }
    }
  }

  def getUserSync(userSync: UserSync): List[OfflineMessageResponse] = {
    val sql = "select id, message from IMOfflineMessage where user_id = ? and id > ?"
    var statement: PreparedStatement = null
    var rs: ResultSet = null
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setString(1, userSync.userId)
        statement.setLong(2, userSync.syncKey)
        rs = statement.executeQuery()
        var messages = scala.collection.mutable.ArrayBuffer[OfflineMessageResponse]()
        while (rs.next()) {
          val id = rs.getLong(1)
          val message = rs.getString(2)
          val offlineMessage = OfflineMessageResponse(id, message)
          messages += offlineMessage
        }
        messages.toList
      }
      finally {
        close(statement)
      }
    }
  }

  def searchUser(userSearch: UserSearch) = {
    val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
      "created, updated from IMUsers where name like CONCAT('%', ?, '%')"
    var statement: PreparedStatement = null
    var rs: ResultSet = null
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setString(1, userSearch.name)
        rs = statement.executeQuery
        var users = scala.collection.mutable.ArrayBuffer[User]()
        while (rs.next()) {
          val update = rs.getDate("updated")
          val updateData = if (update == null) None else Some(update)
          val user = User(rs.getString("id"), rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
            rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
            rs.getString("mail"), rs.getDate("created"), updateData)
          users += user
        }
        sender() ! users.toList
      }
      finally {
        close(rs)
        close(statement)
      }
    }
  }

  def getUser(id: Int): Unit = {
    val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
      "created, updated from IMUsers where id = ? "
    var statement: PreparedStatement = null
    var rs: ResultSet = null
    withConnection { connect =>
      try {
        statement = connect.prepareStatement(sql)
        statement.setLong(1, id)
        rs = statement.executeQuery
        if (rs.next()) {
          val update = rs.getDate("updated")
          val updateData = if (update == null) None else Some(update)
          val user = User(rs.getString("id"), rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
            rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
            rs.getString("mail"), rs.getDate("created"), updateData)
          sender() ! user
        } else {
          sender() ! NotFound
        }
      } finally {
        close(rs)
        close(statement)
      }
    }
  }
}
