package wetalk

import java.net.InetSocketAddress

import akka.actor.{ActorLogging, Actor, Props, ActorSystem}
import akka.io.Tcp._
import akka.io.{Tcp, IO}
import wetalk.data.{CacheManager, DataManager}

/**
 * Created by goldratio on 11/3/14.
 */

object MessageActor {
  def props() = Props(classOf[MessageActor])
}

class MessageActor extends Actor with ActorLogging {
  val databaseActor = context.actorOf(DataManager.props())
  val cacheActor = context.actorOf(CacheManager.props())

  override def receive: Receive = {
    case b @ Bound(localAddress) =>
      println(s"localAddress:${localAddress}")

    case CommandFailed(_: Bind) =>
      context stop self

    case c @ Connected(remote, local) =>
      val handler = context.actorOf(MessageHandler.props(databaseActor, cacheActor))
      val connection = sender()
      connection ! Register(handler)
  }
}

object MessageServer extends App {

  implicit val system = ActorSystem()

  val messageServer = system.actorOf(MessageActor.props(), name = "message-server")

  IO(Tcp) ! Bind(messageServer, new InetSocketAddress("0.0.0.0", 8100))

}