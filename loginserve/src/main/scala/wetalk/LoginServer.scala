package wetalk

import java.net.InetSocketAddress

import akka.actor.Actor.Receive
import akka.actor.{ ActorSystem, Props, Actor, ActorLogging }
import akka.io.{ Tcp, IO }
import akka.io.Tcp._

/**
 * Created by goldratio on 11/2/14.
 */
object LoginActor {
  def props() = Props(classOf[LoginActor])
}
class LoginActor extends Actor with ActorLogging {
  override def receive: Receive = {
    case b @ Bound(localAddress) =>
      println(s"localAddress:${localAddress}")

    case CommandFailed(_: Bind) =>
      context stop self

    case c @ Connected(remote, local) =>
      val handler = context.actorOf(Props[LoginHandler])
      val connection = sender()
      connection ! Register(handler)
  }
}

object LoginServer extends App {

  implicit val system = ActorSystem()

  val loginServer = system.actorOf(LoginActor.props(), name = "login-server")

  IO(Tcp) ! Bind(loginServer, new InetSocketAddress("0.0.0.0", 8008))

}
