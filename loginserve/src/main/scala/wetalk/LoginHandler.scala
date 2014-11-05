package wetalk

import java.nio.ByteOrder

import akka.actor.Actor
import akka.io.Tcp
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.control.NonFatal
import scala.collection.JavaConversions._

/**
 * Created by goldratio on 11/2/14.
 */
class LoginHandler extends Actor {
  implicit val byteOrder = ByteOrder.BIG_ENDIAN

  val config = ConfigFactory.load().getConfig("wetalk")
  val Settings = new Settings(config)

  class Settings(config: Config) {
    val messageServer = {
      val hosts = config.getStringList("messageServer.host")
      //val host = config.getString("messageServer.host")
      val port = config.getInt("messageServer.port")
      hosts.map{ host =>
        (host, port.toShort)
      }.toList
    }
  }

  import Tcp._
  def receive = {
    case Received(data) =>
      try {
        val response = handleParsingResult(WTRequestParser(data))
        sender() ! Write(response.packageData())
      }
      catch {
        case e: ExceptionWithErrorInfo =>
          handleError(0, e.info)
        case NonFatal(e) =>
          handleError(1, ErrorInfo("Illegal request", e.getMessage))
        case e: Throwable =>
          e.printStackTrace()
      }
    case PeerClosed =>
      context stop self
  }


  def handleParsingResult(result: WTPackage): WTPackage = {
    result match {
      case request: MessageSeverRequest =>
        MessageServerResponse(Settings.messageServer, result.seqNo)
    }
  }


  def handleError(status: Int, info: ErrorInfo): Unit = {
    //log.warning("Illegal request, responding with status '{}': {}", status, info.formatPretty)
    println(info)
  }

}
