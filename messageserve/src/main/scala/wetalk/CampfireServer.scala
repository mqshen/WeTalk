package wetalk

import java.net.InetSocketAddress

import akka.actor.{Status, ActorLogging, Actor}
import akka.io.IO
import akka.stream.io.StreamTcp
import akka.stream.scaladsl.{Sink, Source}
import akka.util.{ByteString, Timeout}
import wetalk.data.{CacheManager, DataManager}
import wetalk.engine.parsing.ParserOutput.UserMessage
import wetalk.model.{MessageResponse, MessageRequest}
import wetalk.server.{IncomingConnection, MessagePipeline}
import akka.stream.{ FlowMaterializer, MaterializerSettings }

import scala.concurrent.Future
import scala.concurrent.duration._
/**
 * Created by goldratio on 11/21/14.
 */
class CampfireServer extends Actor with ActorLogging {

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
    case serverBinding @ StreamTcp.TcpServerBinding(localAddress, connectionStream) =>
      log.info("Server started, listening on: " + localAddress)
      val httpServerPipeline = new MessagePipeline()
      val httpConnectionStream = Source(connectionStream)
        .map(httpServerPipeline)
        .runWith(Sink.publisher)
      //createConnectionFlow(serverBinding)
      Source(httpConnectionStream).foreach {
        case IncomingConnection(remoteAddress, requestPublisher, responseSubscriber) ⇒
          Source(requestPublisher)
            .mapAsync(handleRequest(remoteAddress, _))
      }
    case Status.Failure(e) =>
      log.error(e, s"Server could not bind to $serverAddress: ${e.getMessage}")
  }


  private def handleRequest(remoteAddress: InetSocketAddress, request: UserMessage): Future[MessageResponse] = {
    println(request)
    null
  }

}

import akka.actor.{ Props, ActorSystem }

object CampfireServer {

  def main(args: Array[String]): Unit = {
    val ircServerSystem = ActorSystem.create("message-system")
    ircServerSystem.actorOf(Props(new CampfireServer()), "messageServer")
  }

}