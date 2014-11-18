package irc

import java.net.InetSocketAddress

import akka.actor._
import irc.Connection._
import irc.parser.IrcCommand
import org.reactivestreams.{Subscription, Subscriber}
import scala.collection.immutable.Queue
import scala.concurrent.duration._
/**
 * Created by goldratio on 11/18/14.
 */
sealed trait ServerIrcMessage extends IrcMessage

case class NoticeMessage(serverName: String, nick: String, text: String) extends ServerIrcMessage {
  override def toString = s":$serverName NOTICE :$nick $text"
}

case class NeedMoreParamsMessage(serverName: String, command: IrcCommand) extends ServerIrcMessage {
  override def toString = s":$serverName 461 $command :Not enough parameters"
}

case class UnknownCommandMessage(serverName: String, command: String) extends ServerIrcMessage {
  override def toString = s":$serverName 421 $command :Unknown command"
}

case class AlreadyAuthenticatedMessage(serverName: String) extends ServerIrcMessage {
  override def toString = s":$serverName NOTICE : Already authenticated"
}

trait ConnectionActor extends Actor with ActorLogging with FSM[Connection.ConnectionState, Connection.ConnectionData] {

  private class ConnectionSubscription extends Subscription {
    override def cancel() = self ! OutgoingFlowClosed

    override def request(n: Long) = self ! SubscriptionRequest(n.toInt)

  }

  protected def remoteAddress: InetSocketAddress

  protected def user : ActorRef

  protected def messageHandler : IncomingMessageHandler

  startWith(Open, Uninitialized(messages = Queue.empty))

  when(Open, stateTimeout = 1.second) {
    case Event(IncomingFlowClosed, _) =>
      log.info(s"$remoteAddress closed connection")
      stop()
    case Event(message : UserIrcMessage, Uninitialized(messages)) =>
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
    case Event(message : UserIrcMessage, _) =>
      messageHandler.handleIncomingIrcMessage(message)
      stay()
    case Event(User.InvalidPassword, SubscriptionData(_, subscriber, _)) =>
      log.info(s"$remoteAddress close connection invalid password")
      subscriber.onComplete()
      stop()
    case Event(User.Quit, SubscriptionData(_, subscriber, _)) =>
      log.info(s"$remoteAddress closing connection.")
      subscriber.onComplete()
      stop()
    case Event(SubscriptionRequest(n), SubscriptionData(requested, subscriber, pendingMessages)) =>
      val messagesToSendImmediately = pendingMessages take n
      messagesToSendImmediately.foreach(subscriber.onNext)
      stay() using SubscriptionData(requested = n - messagesToSendImmediately.size, subscriber, pendingMessages drop messagesToSendImmediately.size)
    case Event(message : ServerIrcMessage, SubscriptionData(requested, subscriber, pendingMessages)) =>
      if (requested == 0) {
        stay() using SubscriptionData(requested, subscriber, pendingMessages enqueue message)
      }
      else {
        subscriber.onNext(message)
        stay() using SubscriptionData(requested = requested - 1, subscriber, pendingMessages)
      }

  }
}

class Connection(_remoteAddress: InetSocketAddress) extends ConnectionActor {

  override protected val remoteAddress = _remoteAddress

  override protected val user = context.actorOf(Props[User],"user")

  override protected val messageHandler = new IncomingMessageHandler(connection = self, user = user, remoteAddress = _remoteAddress, serverName = "todo")
}

object Connection {
  case class IncomingFlowClosed()
  case class OutgoingFlowClosed()
  case class Subscribe(subscriber : Subscriber[_ >: IrcMessage])
  case class SubscriptionRequest(n : Int)

  sealed trait ConnectionState
  case object Open extends ConnectionState
  case object Subscribed extends ConnectionState

  sealed trait ConnectionData
  case class Uninitialized(messages : Queue[UserIrcMessage]) extends ConnectionData
  case class SubscriptionData(requested : Int, subscriber : Subscriber[_ >: IrcMessage], pendingMessages : Queue[ServerIrcMessage]) extends ConnectionData

}
