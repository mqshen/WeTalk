package wetalk.data

import java.sql.DriverManager

import akka.actor.Actor.Receive
import akka.actor.{ Actor, Props }
import com.redis.{ RedisClient }
import com.typesafe.config.{ Config, ConfigFactory }
import scala.collection.JavaConversions._

/**
 * Created by goldratio on 11/5/14.
 */

case class UnreadMessageCount(userId: Int)
case class UnreadGroupMessageCount(userId: Int)

object CacheManager {
  def props() = Props(classOf[DataManager])
}

class CacheManager extends Actor {

  val config = ConfigFactory.load().getConfig("wetalk.database")
  val Settings = new Settings(config)

  class Settings(config: Config) {
    val connects = {
      config.getConfigList("redis").map { redis =>
        val name = redis.getString("name")
        val host = redis.getString("host")
        val port = redis.getInt("port")
        val index = redis.getInt("index")
        name -> new RedisClient(host, port, index)
      } toMap
    }
  }

  override def receive: Receive = {
    case r: UnreadMessageCount =>
      val currentSender = sender()
      Settings.connects.get("unread").map { redis =>
        redis.hgetall(r.userId).map { result =>
          val unread: List[(String, Int)] = result.map { entry =>
            (entry._1, entry._2.toInt)
          }.toList
          currentSender ! unread
        }.getOrElse {
          currentSender ! NotFound
        }
      }.getOrElse {
        currentSender ! NotFound
      }
    case r: UnreadGroupMessageCount =>
      val currentSender = sender()
      Settings.connects.get("group_counter").map { redis =>
        redis.hgetall(r.userId).map { result =>
          val unread: List[(String, Int)] = result.map { entry =>
            (entry._1, entry._2.toInt)
          }.toList
          currentSender ! unread
        }.getOrElse {
          currentSender ! NotFound
        }
      }.getOrElse {
        currentSender ! NotFound
      }
  }

}