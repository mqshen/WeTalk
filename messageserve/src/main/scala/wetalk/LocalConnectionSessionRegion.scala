package wetalk

import akka.actor._
import wetalk.ConnectionSession.SendPackage
import wetalk.data.User
import wetalk.protocol.DispatchChatMessage

import scala.collection.mutable

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
    case ConnectionSession.CreateSession(sessionId: String, user: User, userActor: ActorRef) =>
      sessions.get(sessionId) match {
        case Some(_) =>
        case None =>
          sessions += (user.id.toString -> sessionId)
          sessionActor += (user.id.toString -> userActor)
      }

    case cmd: DispatchChatMessage =>
      sessionActor.get(cmd.to) match {
        case Some(userActor) =>
          userActor ! cmd
        case None =>
      }
    case cmd: ConnectionSession.GroupDispatchPackage =>
      cmd.users.filter(userId => userId != cmd.userId).foreach { userId =>
        sessions.get(userId).map { sessionId =>
          context.child(sessionId) match {
            case Some(ref) => ref forward cmd.wtPackage
            case None      => log.warning("Failed to select actor {}", sessionId)
          }
        }
      }

    case Terminated(ref) =>
  }

}
