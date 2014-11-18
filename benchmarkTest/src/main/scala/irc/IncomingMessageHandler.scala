package irc

import java.net.InetSocketAddress

import akka.actor.ActorRef
import akka.event.slf4j.SLF4JLogging
import irc.parser.IrcCommand
import irc.parser.IrcCommand._

/**
 * Created by goldratio on 11/18/14.
 */

class IncomingMessageHandler(connection : ActorRef, user : ActorRef, remoteAddress: InetSocketAddress, serverName : String) extends SLF4JLogging {

  implicit val sender = connection

  def handleIncomingIrcMessage(message : UserIrcMessage) = {
    log.debug(s"$remoteAddress: $message")
    IrcCommand(message.command).fold(connection ! UnknownCommandMessage(serverName, message.command))({
      case PASS => message.arguments match {
        case List(password) => user ! User.Authenticate(password = password)
        case _ => connection ! NeedMoreParamsMessage(serverName, PASS)
      }
      case NICK => message.arguments match {
        case List(username) => user ! User.SetUsername(username = username)
        case _ => connection ! NeedMoreParamsMessage(serverName, NICK)
      }
      case QUIT => message.arguments match {
        case Nil => user ! User.Quit(None)
        case quitMessageAsList => user ! User.Quit(Some(quitMessageAsList.mkString(" ")))
      }
    })
  }

}
