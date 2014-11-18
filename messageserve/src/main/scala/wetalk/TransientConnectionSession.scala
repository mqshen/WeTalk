package wetalk

import akka.actor.{ ActorLogging, Actor, Props, ActorRef }
import wetalk.data.User

/**
 * Created by goldratio on 11/6/14.
 */
object TransientConnectionSession {
  def props(sessionId: String, user: User, connection: ActorRef, databaseActor: ActorRef, cacheActor: ActorRef): Props =
    Props(classOf[TransientConnectionSession], sessionId, user, connection, databaseActor, cacheActor)
}

final class TransientConnectionSession(val sessionId: String, val user: User, val connection: ActorRef,
                                       val databaseActor: ActorRef, val cacheActor: ActorRef)
    extends ConnectionSession with Actor with ActorLogging {

  def recoveryFinished: Boolean = true
  def recoveryRunning: Boolean = false

  def receive: Receive = working

}
