package wetalk.data

import java.sql.Connection

import com.typesafe.config.ConfigFactory
import wetalk.database.HikariCPJdbcDataSource

/**
 * Created by goldratio on 1/10/15.
 */
object Database {

  val config = ConfigFactory.load().getConfig("wetalk.database.mysql")

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
      if(ok) s.close()
      else {
        try s.close() catch { case _: Throwable => }
      }
    }
  }

}
