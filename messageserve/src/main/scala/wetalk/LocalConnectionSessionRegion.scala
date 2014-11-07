package wetalk

import akka.actor._
import wetalk.ConnectionSession.SendPackage
import wetalk.data.User

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

  def receive = {
    case ConnectionSession.CreateSession(sessionId: String, user: User, serverConnection: ActorRef) =>
      context.child(sessionId) match {
        case Some(_) =>
        case None =>
          sessions += (user.id.toString -> sessionId)
          val connectSession = context.actorOf(TransientConnectionSession.props(sessionId, user, serverConnection, databaseActor, cacheActor), name = sessionId)
          context.watch(connectSession)
      }

    case cmd: ConnectionSession.Command =>
      context.child(cmd.sessionId) match {
        case Some(ref) => ref forward cmd
        case None      => log.warning("Failed to select actor {}", cmd.sessionId)
      }

    case cmd: ConnectionSession.DispatchPackage =>
      sessions.get(cmd.userId).map { sessionId =>
        context.child(sessionId) match {
          case Some(ref) => ref forward cmd.wtPackage
          case None      => log.warning("Failed to select actor {}", sessionId)
        }
      }

    case Terminated(ref) =>
  }

}
