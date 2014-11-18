package irc

/**
 * Created by goldratio on 11/18/14.
 */

import java.net.InetSocketAddress

import akka.actor._
import akka.io.IO
import akka.stream.io.StreamTcp
import akka.stream.io.StreamTcp.IncomingTcpConnection
import akka.stream.scaladsl.Flow
import akka.stream.{FlowMaterializer, MaterializerSettings}
import akka.util.{ByteString, Timeout}
import irc.parser.{DelimiterFraming, IrcMessageParser}
import org.reactivestreams.{Subscriber, Publisher}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

class IrcServer(shutdownSystemOnError : Boolean = false) extends Actor with ActorLogging {

  private implicit val system = context.system

  private val bindAddress = context.system.settings.config.getString("ircserver.socket.bind_adress")

  private val port = context.system.settings.config.getInt("ircserver.socket.port")

  private val serverAddress = new InetSocketAddress(bindAddress, port)

  private val settings = MaterializerSettings(system)

  private implicit val materializer = FlowMaterializer(settings)

  private implicit val timeout = Timeout(5.seconds)

  private val delimiter = ByteString("\r\n".getBytes("UTF-8"))

  private implicit val executionContext = context.dispatcher

  override def preStart() = {
    IO(StreamTcp) ! StreamTcp.Bind(serverAddress, Some(settings))
  }

  def receive: Receive = {
    case serverBinding: StreamTcp.TcpServerBinding =>
      log.info("Server started, listening on: " + serverBinding.localAddress)
      createConnectionFlow(serverBinding)
    case Status.Failure(e)  =>
      log.error(e, s"Server could not bind to $serverAddress: ${e.getMessage}")
      if(shutdownSystemOnError) system.shutdown()
  }

  private def createConnectionFlow(serverBinding: StreamTcp.TcpServerBinding) {
    Flow(serverBinding.connectionStream)
      .foreach { connection ⇒
      log.info(s"Client connected from: ${connection.remoteAddress}")
      handleNewConnection(connection)
    }
  }

  private def handleNewConnection(connection: IncomingTcpConnection) {
    val connectionActor = context.actorOf(Props(new Connection(connection.remoteAddress)))
    createIncomingFlow(connection, connectionActor)
    createOutgoingFlow(connection, connectionActor)
  }

  private def createIncomingFlow(connection: IncomingTcpConnection, connectionActor : ActorRef) {
    val delimiterFraming = new DelimiterFraming(maxSize = 1000, delimiter = delimiter)
    Flow(connection.inputStream)
      .mapConcat(delimiterFraming.apply)
      .map(_.utf8String)
      .map(IrcMessageParser.parse)
      .filter(_.isSuccess)
      .map(_.get)
      .foreach(connectionActor ! _)
      .onComplete{case _ => connectionActor ! Connection.IncomingFlowClosed}
  }

  private def createOutgoingFlow(connection: IncomingTcpConnection, connectionActor : ActorRef) {
    val publisher = new Publisher[IrcMessage] {

      override def subscribe(s: Subscriber[_ >: IrcMessage]): Unit = {
        connectionActor ! Connection.Subscribe(s)
      }
    }
    Flow(publisher)
      .map(_.toString + "\r\n")
      .map(ByteString.apply)
      .produceTo(connection.outputStream)
  }
}

import akka.actor.{Props, ActorSystem}

object Starter {

  def main(args: Array[String]): Unit = {
    val ircServerSystem = ActorSystem.create("ircserver-system")
    ircServerSystem.actorOf(Props(new IrcServer(shutdownSystemOnError = true)), "ircserver")
  }

}