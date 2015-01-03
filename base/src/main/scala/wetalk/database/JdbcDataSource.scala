package wetalk.database

import java.io.Closeable
import java.sql.{Driver, Connection}

import com.typesafe.config.Config

/**
 * Created by goldratio on 1/3/15.
 */
trait JdbcDataSource extends Closeable {
  /** Create a new Connection or get one from the pool */
  def createConnection(): Connection

  /** If this object represents a connection pool managed directly by Slick, close it.
    * Otherwise no action is taken. */
  def close(): Unit
}

trait JdbcDataSourceFactory {
  def forConfig(c: Config, driver: Driver, name: String): JdbcDataSource
}