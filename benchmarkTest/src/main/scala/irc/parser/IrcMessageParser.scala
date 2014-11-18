package irc.parser

/**
 * Created by goldratio on 11/18/14.
 */

import irc.{UserIrcMessage, MessagePrefix}

import scala.util.Try
import scala.util.parsing.combinator._

object IrcMessageParser extends RegexParsers {

  override protected val whiteSpace = """[ ]+""".r

  private val nick: Parser[String] = """[a-zA-Z0-9-\[\]\\`\^\{\}]+""".r

  private val user: Parser[String] = """[^\s@]+""".r

  private val host: Parser[String] = """[^\s@]+""".r

  private val arg: Parser[String] = """[^:\s][\S]+""".r

  private val trailing: Parser[String] = ":" ~ """[^\r\n]*""".r ^^ { case _ ~ s => s}

  private val prefix: Parser[MessagePrefix] = ":" ~ nick ~ "!" ~ user ~ """@""" ~ host ^^ {
    case _ ~ n ~ _ ~ u ~ _ ~ h => new MessagePrefix(nick = n, user = u, host = h)
  }

  private val command: Parser[String] = """[a-zA-Z]+""".r

  private val message = prefix.? ~ command ~ arg.* ~ trailing.? ^^ {
    case p ~ c ~ a ~ t =>
      val arguments = a ::: t.map(List(_)).getOrElse(List.empty)
      UserIrcMessage(prefix = p, command = c, arguments = arguments)
  }

  def parse(text: String): Try[UserIrcMessage] = parseAll(message, text) match {
    case Success(ircMessage, _) => scala.util.Success(ircMessage)
    case NoSuccess(msg, _)      => scala.util.Failure(new IllegalArgumentException(msg))
  }

}
