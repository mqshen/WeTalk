import java.sql.{ResultSet, PreparedStatement, Connection}

import com.typesafe.config.{Config, ConfigFactory}

/**
 * Created by goldratio on 1/3/15.
 */
package object wetalk {

  val config = ConfigFactory.load().getConfig("wetalk")
  val Settings = new Settings(config)

  implicit def date2timestamp(date: java.util.Date) = new java.sql.Timestamp(date.getTime)

  class Settings(config: Config) {
    val heartbeatInterval = config.getInt("server.heartbeat-interval")
    val HeartbeatTimeout = config.getInt("server.heartbeat-timeout")
    val CloseTimeout = config.getInt("server.close-timeout")
    val IdleTimeout = config.getInt("server.idle-timeout")
  }


  implicit class ExtendedConnection(connection: Connection) {
    def withStatement[T](sql: String)(f: PreparedStatement => T) = {
      var ok= false
      val s = connection.prepareStatement(sql)
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
  }

  implicit class ExtendedStatement(statement: PreparedStatement) {
    def withResult[T](f: ResultSet => T) = {
      var ok= false
      val rs = statement.executeQuery
      try {
        val res = f(rs)
        ok = true
        res
      }
      finally {
        if(ok) rs.close() // Let exceptions propagate normally
        else {
          // f(s) threw an exception, so don't replace it with an Exception from close()
          try rs.close() catch { case _: Throwable => }
        }
      }
    }
  }

}
