import com.typesafe.config.{Config, ConfigFactory}

/**
 * Created by goldratio on 1/3/15.
 */
package object wetalk {

  val config = ConfigFactory.load().getConfig("wetalk")
  val Settings = new Settings(config)

  class Settings(config: Config) {
    val heartbeatInterval = config.getInt("server.heartbeat-interval")
    val HeartbeatTimeout = config.getInt("server.heartbeat-timeout")
    val CloseTimeout = config.getInt("server.close-timeout")
    val IdleTimeout = config.getInt("server.idle-timeout")
  }

}
