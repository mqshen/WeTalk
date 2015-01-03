package wetalk.database

import scala.concurrent.duration.Duration
import java.sql.{Driver, Connection}
import java.util.Properties
import java.util.concurrent.TimeUnit

import com.typesafe.config.{ConfigFactory, Config}
import wetalk.WeTalkException

class ConfigExtensionMethods(val c: Config) extends AnyVal {
  import scala.collection.JavaConverters._

  def getBooleanOr(path: String, default: => Boolean = false) = if(c.hasPath(path)) c.getBoolean(path) else default
  def getIntOr(path: String, default: => Int = 0) = if(c.hasPath(path)) c.getInt(path) else default
  def getStringOr(path: String, default: => String = null) = if(c.hasPath(path)) c.getString(path) else default
  def getConfigOr(path: String, default: => Config = ConfigFactory.empty()) = if(c.hasPath(path)) c.getConfig(path) else default

  def getMillisecondsOr(path: String, default: => Long = 0L) = if(c.hasPath(path)) c.getDuration(path, TimeUnit.MILLISECONDS) else default
  def getDurationOr(path: String, default: => Duration = Duration.Zero) =
    if(c.hasPath(path)) Duration(c.getDuration(path, TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS) else default

  def getPropertiesOr(path: String, default: => Properties = null) =
    if (!c.hasPath(path)) default
    else {
      val props = new Properties(null)
      c.getObject(path).asScala.foreach { case (k, v) => props.put(k, v.unwrapped.toString) }
      props
    }

  def getBooleanOpt(path: String): Option[Boolean] = if(c.hasPath(path)) Some(c.getBoolean(path)) else None
  def getIntOpt(path: String): Option[Int] = if(c.hasPath(path)) Some(c.getInt(path)) else None
  def getStringOpt(path: String) = Option(getStringOr(path))
  def getPropertiesOpt(path: String) = Option(getPropertiesOr(path))
}

object ConfigExtensionMethods {
  @inline implicit def configExtensionMethods(c: Config): ConfigExtensionMethods = new ConfigExtensionMethods(c)
}

import ConfigExtensionMethods._

/** A JdbcDataSource for a HikariCP connection pool */
class HikariCPJdbcDataSource(val ds: com.zaxxer.hikari.HikariDataSource, val hconf: com.zaxxer.hikari.HikariConfig) extends JdbcDataSource {
  def createConnection(): Connection = ds.getConnection()
  def close(): Unit = ds.close()
}

object HikariCPJdbcDataSource extends JdbcDataSourceFactory {
  import com.zaxxer.hikari._

  def forConfig(c: Config, driver: Driver, name: String): HikariCPJdbcDataSource = {
    if(driver ne null)
      throw new WeTalkException("An explicit Driver object is not supported by HikariCPJdbcDataSource")
    val hconf = new HikariConfig()

    // Connection settings
    hconf.setDataSourceClassName(c.getStringOr("dataSourceClass", null))
    Option(c.getStringOr("driverClassName", c.getStringOr("driver"))).map(hconf.setDriverClassName _)
    hconf.setJdbcUrl(c.getStringOr("url", null))
    c.getStringOpt("user").foreach(hconf.setUsername)
    c.getStringOpt("password").foreach(hconf.setPassword)
    c.getPropertiesOpt("properties").foreach(hconf.setDataSourceProperties)

    // Pool configuration
    hconf.setConnectionTimeout(c.getMillisecondsOr("connectionTimeout", 1000))
    hconf.setIdleTimeout(c.getMillisecondsOr("idleTimeout", 600000))
    hconf.setMaxLifetime(c.getMillisecondsOr("maxLifetime", 1800000))
    hconf.setLeakDetectionThreshold(c.getMillisecondsOr("leakDetectionThreshold", 0))
    hconf.setInitializationFailFast(c.getBooleanOr("initializationFailFast", false))
    c.getStringOpt("connectionTestQuery").foreach { s =>
      //hconf.setJdbc4ConnectionTest(false)
      hconf.setConnectionTestQuery(s)
    }
    c.getStringOpt("connectionInitSql").foreach(hconf.setConnectionInitSql)
    val numThreads = c.getIntOr("numThreads", 20)
    hconf.setMaximumPoolSize(c.getIntOr("maxConnections", numThreads * 5))
    hconf.setMinimumIdle(c.getIntOr("minConnections", numThreads))
    hconf.setPoolName(name)
    hconf.setRegisterMbeans(c.getBooleanOr("registerMbeans", false))

    // Equivalent of ConnectionPreparer
    hconf.setReadOnly(c.getBooleanOr("readOnly", false))
    c.getStringOpt("isolation").map("TRANSACTION_" + _).foreach(hconf.setTransactionIsolation)
    hconf.setCatalog(c.getStringOr("catalog", null))

    val ds = new HikariDataSource(hconf)
    new HikariCPJdbcDataSource(ds, hconf)
  }
}