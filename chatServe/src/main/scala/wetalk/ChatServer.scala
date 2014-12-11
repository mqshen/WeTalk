package wetalk

import java.net.InetSocketAddress

import akka.actor._
import akka.io.IO
import akka.stream.io.StreamTcp
import akka.stream.io.StreamTcp.IncomingTcpConnection
import akka.stream.scaladsl.{ Source, Flow, Sink }
import akka.stream.{ FlowMaterializer, MaterializerSettings }
import akka.util.{ ByteString, Timeout }
import org.reactivestreams.{ Subscriber, Publisher }
import wetalk.data.{CacheManager, DataManager}
import wetalk.parser.{DelimiterFraming, MessageParser, ResponseMessage}
import wetalk.server.{LocalConnectionSessionRegion, Connection}

import scala.concurrent.duration._
import scala.util.{ Failure, Success }

/**
 * Created by goldratio on 11/18/14.
 */
class ChatServer(shutdownSystemOnError: Boolean = false) extends Actor with ActorLogging {

  private implicit val system = context.system

  private val serverAddress = new InetSocketAddress(context.system.settings.config.getString("wetalk.server.host"),
    context.system.settings.config.getInt("wetalk.server.port"))

  private val settings = MaterializerSettings(system)

  private implicit val materializer = FlowMaterializer(settings)

  private implicit val timeout = Timeout(5.seconds)

  private val delimiter = ByteString("\r\n".getBytes("UTF-8"))

  private implicit val executionContext = context.dispatcher

  val databaseActor = context.actorOf(DataManager.props())

  val cacheActor = context.actorOf(CacheManager.props())

  val sessionRegion = context.actorOf(LocalConnectionSessionRegion.props(databaseActor, cacheActor))

  override def preStart() = {
    IO(StreamTcp) ! StreamTcp.Bind(serverAddress, Some(settings))
  }

  def receive: Receive = {
    case serverBinding: StreamTcp.TcpServerBinding =>
      log.info("Server started, listening on: " + serverBinding.localAddress)
      createConnectionFlow(serverBinding)
    case Status.Failure(e) =>
      log.error(e, s"Server could not bind to $serverAddress: ${e.getMessage}")
      if (shutdownSystemOnError)
        system.shutdown()
  }

  private def createConnectionFlow(serverBinding: StreamTcp.TcpServerBinding) {
    Source(serverBinding.connectionStream).foreach { connection =>
      log.info(s"Client connected from: ${connection.remoteAddress}")
      handleNewConnection(connection)
    }
  }

  private def handleNewConnection(connection: IncomingTcpConnection) {
    val connectionActor = context.actorOf(Connection.props(connection.remoteAddress, databaseActor, sessionRegion))
    createIncomingFlow(connection, connectionActor)
    createOutgoingFlow(connection, connectionActor)
  }

  private def createIncomingFlow(connection: IncomingTcpConnection, connectionActor: ActorRef) {
    val delimiterFraming = new DelimiterFraming(maxSize = 51200, delimiter = delimiter)
    Source(connection.inputStream)
      .mapConcat(delimiterFraming.apply)
      .map(_.utf8String)
      .map(MessageParser.parse)
      .filter(_.isSuccess)
      .map(_.get)
      .foreach(connectionActor ! _)
      .onComplete { case _ => connectionActor ! Connection.IncomingFlowClosed }
  }

  private def createOutgoingFlow(connection: IncomingTcpConnection, connectionActor: ActorRef) {
    val publisher = new Publisher[ResponseMessage] {

      override def subscribe(s: Subscriber[_ >: ResponseMessage]): Unit = {
        connectionActor ! Connection.Subscribe(s)
      }
    }
    Source(publisher)
      .map{ t =>
        println(t)
        t.toString + "\r\n"
      }
      .map(ByteString.apply).to(Sink(connection.outputStream)).run()
  }
}

import akka.actor.{ Props, ActorSystem }

object ChatServer {

  def main(args: Array[String]): Unit = {
    val ircServerSystem = ActorSystem.create("message-system")
    ircServerSystem.actorOf(Props(new ChatServer(shutdownSystemOnError = true)), "messageServer")
  }

}