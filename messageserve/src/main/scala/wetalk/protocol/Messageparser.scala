package wetalk.protocol

import scala.util.Try
import scala.util.parsing.combinator._

/**
 * Created by goldratio on 11/18/14.
 */
object MessageParser extends RegexParsers {

  override protected val whiteSpace = """[ ]+""".r

  private val opCode: Parser[String] = """[0-9]+""".r

  private val id: Parser[String] = """[0-9]+""".r

  private val host: Parser[String] = """[^\s@]+""".r

  private val arg: Parser[String] = """[^:\s][\S]+""".r

  private val data: Parser[String] = """.*$""".r

  private val prefix: Parser[MessagePrefix] = opCode ~ ":" ~ id ^^ {
    case opCode ~ _ ~ id => new MessagePrefix(opCode = opCode.toInt, id = id.toLong)
  }

  private val command: Parser[String] = """[a-zA-Z]+""".r

  private val message = prefix ~ ":" ~ data ^^ {
    case p ~ _ ~ d =>
      UserMessage(prefix = p, jsonData = d)
  }

  def parse(text: String): Try[UserMessage] = {
    parseAll(message, text) match {
      case Success(message, _) =>
        //val currentTime = System.currentTimeMillis()
        //println(s"message parse time:${currentTime - message.prefix.id}")
        scala.util.Success(message)
      case NoSuccess(msg, _) =>
        scala.util.Failure(new IllegalArgumentException(msg))
    }
  }

}