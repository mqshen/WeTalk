package wetalk

import java.net.InetSocketAddress
import java.nio.ByteOrder

import akka.actor.FSM.Event
import akka.actor._
import akka.event.slf4j.SLF4JLogging
import akka.io.IO
import akka.io.Tcp.Write
import akka.stream.io.StreamTcp
import akka.stream.io.StreamTcp.IncomingTcpConnection
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl._
import akka.stream.{ FlowMaterializer, MaterializerSettings }
import akka.util.{ ByteString, Timeout }
import org.reactivestreams.{ Subscription, Subscriber, Publisher }
import play.api.libs.json.Json
import wetalk.WeTalkTestClient.{ MessageArrived, SendMessage, OnOpen }
import wetalk.data.{ UserAuth, DataManager }
import wetalk.protocol.Command._
import wetalk.protocol._
import wetalk.server.{ IncomingMessageHandler, JsonFormat, ConnectionActor, Connection }
import wetalk.server.Connection._
import wetalk.user.UserActor
import scala.collection.immutable.Queue
import scala.concurrent.duration._
/**
 * Created by goldratio on 11/18/14.
 */

trait ClientConnectionActor extends Actor with ActorLogging with FSM[Connection.ConnectionState, Connection.ConnectionData] {

  private class ConnectionSubscription extends Subscription {
    override def cancel() = self ! OutgoingFlowClosed

    override def request(n: Long) = self ! SubscriptionRequest(n.toInt)

  }

  protected def remoteAddress: InetSocketAddress

  protected def messageHandler: ClientMessageHandler

  startWith(Open, Uninitialized(messages = Queue.empty))

  when(Open, stateTimeout = 1.second) {
    case Event(IncomingFlowClosed, _) =>
      log.info(s"$remoteAddress closed connection")
      stop()
    case Event(message: UserMessage, Uninitialized(messages)) =>
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
    case Event(message: UserMessage, _) =>
      messageHandler.handleIncomingIrcMessage(message)
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
    case Event(message: ServerMessage, SubscriptionData(requested, subscriber, pendingMessages)) =>
      if (requested == 0) {
        stay() using SubscriptionData(requested, subscriber, pendingMessages enqueue message)
      } else {
        subscriber.onNext(message)
        stay() using SubscriptionData(requested = requested - 1, subscriber, pendingMessages)
      }

  }
}

class ClientMessageHandler(connection: ActorRef, commander: ActorRef, remoteAddress: InetSocketAddress, serverName: String) extends SLF4JLogging {
  import JsonFormat._

  implicit val sender = connection

  def handleIncomingIrcMessage(message: UserMessage) = {
    //log.debug(s"$remoteAddress: $message")
    Command(message.prefix.opCode).fold(connection ! UnknownCommandMessage(serverName, message.prefix.opCode))({
      case Disconnect =>
      case Connect =>
        commander ! OnOpen
      case Heartbeat =>
        commander ! HeartbeatMessage
      case Message =>
        val m = Json.parse(message.jsonData)
        val timestamp = (m \ "content" \ "timestamp").as[Long]
        val messageArrivedAt = System.currentTimeMillis - timestamp
        commander ! MessageArrived(messageArrivedAt)
      case GroupMessage =>
      case UserCommand  =>
      case Ack          =>
      case Error        =>
    })
  }

}

class ClientConnection(_remoteAddress: InetSocketAddress, commander: ActorRef) extends ClientConnectionActor {

  override protected val remoteAddress = _remoteAddress

  override protected val messageHandler = new ClientMessageHandler(connection = self, commander = commander, remoteAddress = _remoteAddress, serverName = "todo")
}

object ClientConnection {

  def props(remoteAddress: InetSocketAddress, commander: ActorRef) = {
    Props(classOf[ClientConnection], remoteAddress, commander)
  }
}
case class ChatMessageSend(from: String, to: String, content: String, attach: Option[String] = None) extends ServerMessage {

  val resultCode = 0
  val errorMessage = ""
  val jsonContent = Json.obj("sdf" -> "")

  override def toString = s"3:1:$json"

}

case class SendChatMessage(seqNo: Long, from: String, to: String, content: String, attach: Option[String] = None, timestamp: Long) extends ServerMessage {

  val resultCode = 0
  val errorMessage = ""

  val jsonContent = Json.obj("from" -> from,
    "to" -> to,
    "content" -> content,
    "timestamp" -> timestamp)

  override def toString = s"3:$seqNo:$jsonContent"

}

class ChatClient(commander: ActorRef) extends Actor with ActorLogging {

  private implicit val system = context.system

  private val serverAddress = new InetSocketAddress(context.system.settings.config.getString("wetalk.server.host"),
    context.system.settings.config.getInt("wetalk.server.port"))

  private val settings = MaterializerSettings(system)

  private implicit val materializer = FlowMaterializer(settings)

  private implicit val timeout = Timeout(5.seconds)

  private val delimiter = ByteString("\r\n".getBytes("UTF-8"))

  private implicit val executionContext = context.dispatcher

  override def preStart() = {
    IO(StreamTcp) ! StreamTcp.Connect(serverAddress)
  }

  private var _connection: ActorRef = _

  def connection = _connection

  def receive: Receive = {
    case serverBinding: StreamTcp.IncomingTcpConnection =>
      log.info("Server started, listening on: " + serverBinding.remoteAddress)
      _connection = sender()
    case Status.Failure(e) =>
      log.error(e, s"Server could not bind to $serverAddress: ${e.getMessage}")
    case connection: StreamTcp.OutgoingTcpConnection =>
      _connection = context.actorOf(ClientConnection.props(connection.remoteAddress, commander))
      createIncomingFlow(connection, _connection)
      createOutgoingFlow(connection, _connection)
      context.become(worker)
      _connection ! protocol.LoginRequest(1, "test@126.com", "f2720188fc444312d7dec4c3bb82f438")
  }

  //  private def createConnectionFlow(connection: StreamTcp.OutgoingTcpConnection) {
  //    Source(connection.inputStream)
  //      .foreach { connection ⇒
  //      log.info(s"Client connected from: ${connection.remoteAddress}")
  //      handleNewConnection(connection)
  //    }
  //  }

  def worker: Receive = {
    case e: ChatMessageSend =>
      _connection ! protocol.LoginResponse(2)
    case SendMessage =>
      _connection ! SendChatMessage(2, "t", "3", "test", timestamp = System.currentTimeMillis())
  }

  private def createIncomingFlow(connection: StreamTcp.OutgoingTcpConnection, connectionActor: ActorRef) {
    val delimiterFraming = new DelimiterFraming(maxSize = 1000, delimiter = delimiter)
    Source(connection.inputStream)
      .mapConcat(delimiterFraming.apply)
      .map(_.utf8String)
      .map(MessageParser.parse)
      .filter(_.isSuccess)
      .map(_.get)
      .foreach(connectionActor ! _)
      .onComplete { case _ => connectionActor ! Connection.IncomingFlowClosed }
  }

  private def createOutgoingFlow(connection: StreamTcp.OutgoingTcpConnection, connectionActor: ActorRef) {
    val publisher = new Publisher[Message] {

      override def subscribe(s: Subscriber[_ >: Message]): Unit = {
        connectionActor ! Connection.Subscribe(s)
      }
    }
    Source(publisher)
      .map(_.toString + "\r\n")
      .map(ByteString.apply).to(Sink(connection.outputStream)).run()
  }
}

