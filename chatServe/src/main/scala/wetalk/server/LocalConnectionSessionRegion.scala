package wetalk.server

import java.util.concurrent.ThreadFactory

import akka.actor._
import akka.dispatch.MonitorableThreadFactory
import akka.event.LoggingAdapter
import com.typesafe.config.{Config, ConfigFactory}
import wetalk.data.{OfflineMessage, User}
import wetalk.parser._

import scala.collection.{immutable, mutable}

/**
 * Created by goldratio on 11/6/14.
 */

object LocalConnectionSessionRegion {
  def props(databaseActor: ActorRef, cacheActor: ActorRef) =
    Props(classOf[LocalConnectionSessionRegion], databaseActor, cacheActor)


}

class LocalConnectionSessionRegion(databaseActor: ActorRef, cacheActor: ActorRef) extends Actor with ActorLogging {

  val sessions = new mutable.HashMap[String, String]
  val sessionActor = new mutable.HashMap[String, ActorRef]

  def receive = {
    case CreateSession(sessionId: String, user: User, userActor: ActorRef) =>
      sessions.get(sessionId) match {
        case Some(_) =>
        case None =>
          sessions += (user.id.toString -> sessionId)
          sessionActor += (user.id.toString -> userActor)
      }

    case cmd: DispatchMessage =>
      sessionActor.get(cmd.to) match {
        case Some(userActor) =>
          userActor ! cmd
        case None =>
          processOfflineMessage(cmd.to.toInt, cmd.message)
      }
    case cmd: GroupDispatchPackage =>
      cmd.users.filter(userId => userId != cmd.userId).foreach { userId =>
        sessionActor.get(userId) match {
          case Some(ref) =>
            ref forward cmd.message
          case None      =>
            log.warning("Failed to select actor {}", userId)
            //processOfflineMessage(userId.toInt, cmd.message)
        }
      }
    case CloseSession(useId: String, sessionId: String) =>
      sessions.get(useId) match {
        case Some(_) =>
          sessions -= useId
          sessionActor -= useId
        case None =>
      }

    case Terminated(ref) =>
  }

  def processOfflineMessage(userId: Int, message: ResponseMessage): Unit = {
    databaseActor ! OfflineMessage(userId, message)
  }
}

