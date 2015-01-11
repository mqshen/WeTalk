package wetalk.server

import java.net.InetSocketAddress

import akka.actor.FSM.Event
import akka.actor._
import org.reactivestreams.{ Subscription, Subscriber }
import wetalk.parser.{ResponseMessage, RequestMessage}
import wetalk.server.Connection._
import wetalk.user.UserActor

import scala.collection.immutable.Queue

import scala.concurrent.duration._

/**
 * Created by goldratio on 11/18/14.
 */

trait ConnectionActor extends Actor with ActorLogging with FSM[Connection.ConnectionState, Connection.ConnectionData] {

  private class ConnectionSubscription extends Subscription {
    override def cancel() = self ! OutgoingFlowClosed

    override def request(n: Long) = self ! SubscriptionRequest(n.toInt)

  }

  protected def remoteAddress: InetSocketAddress

  def userActor: ActorRef

  startWith(Open, Uninitialized(messages = Queue.empty))

  //  var lastTime: Long = 0

  when(Open, stateTimeout = 1.second) {
    case Event(IncomingFlowClosed, _) =>
      log.info(s"$remoteAddress closed connection")
      stop()
    case Event(message: RequestMessage, Uninitialized(messages)) =>
      log.debug("{}: buffered message before subscription", remoteAddress)
      stay() using Uninitialized(messages = messages enqueue message)
    case Event(Subscribe(subscriber), Uninitialized(messages)) =>
      subscriber.onSubscribe(new ConnectionSubscription)
      messages.foreach(self ! _)
      goto(Subscribed) using SubscriptionData(requested = 0, subscriber, Queue.empty)
    case Event(StateTimeout, _) =>
      log.warning("{} connection wasn't subscribed", remoteAddress)
      stop()
  }

  when(Subscribed) {
    case Event(IncomingFlowClosed, _) =>
      log.info(s"$remoteAddress closed connection")
      stop()
    case Event(OutgoingFlowClosed, _) =>
      log.info(s"$remoteAddress outgoing flow closed")
      stop()
    case Event(message: RequestMessage, _) =>
      userActor ! message
      stay()
    case Event(UserActor.InvalidPassword, SubscriptionData(_, subscriber, _)) =>
      log.info(s"$remoteAddress close connection invalid password")
      subscriber.onComplete()
      stop()
    case Event(UserActor.Quit, SubscriptionData(_, subscriber, _)) =>
      log.info(s"$remoteAddress closing connection.")
      subscriber.onComplete()
      stop()
    case Event(SubscriptionRequest(n), SubscriptionData(requested, subscriber, pendingMessages)) =>
      val messagesToSendImmediately = pendingMessages take n
      messagesToSendImmediately.foreach(subscriber.onNext)
      stay() using SubscriptionData(requested = n - messagesToSendImmediately.size, subscriber, pendingMessages drop messagesToSendImmediately.size)
    case Event(message: ResponseMessage, SubscriptionData(requested, subscriber, pendingMessages)) =>
      if (requested == 0) {
        stay() using SubscriptionData(requested, subscriber, pendingMessages enqueue message)
      } else {
        subscriber.onNext(message)
        stay() using SubscriptionData(requested = requested - 1, subscriber, pendingMessages)
      }

  }
}

class Connection(_remoteAddress: InetSocketAddress, databaseActor: ActorRef, sessionRegion: ActorRef) extends ConnectionActor {

  override protected val remoteAddress = _remoteAddress

  val userActor = context.actorOf(UserActor.props(self, databaseActor, sessionRegion), "user")

}

object Connection {

  def props(remoteAddress: InetSocketAddress, databaseActor: ActorRef, sessionRegion: ActorRef) = {
    Props(classOf[Connection], remoteAddress, databaseActor, sessionRegion)
  }

  case class IncomingFlowClosed()
  case class OutgoingFlowClosed()
  case class Subscribe(subscriber: Subscriber[_ >: ResponseMessage])
  case class SubscriptionRequest(n: Int)

  sealed trait ConnectionState
  case object Open extends ConnectionState
  case object Subscribed extends ConnectionState

  sealed trait ConnectionData
  case class Uninitialized(messages: Queue[RequestMessage]) extends ConnectionData
  case class SubscriptionData(requested: Int, subscriber: Subscriber[_ >: ResponseMessage], pendingMessages: Queue[ResponseMessage]) extends ConnectionData

}
