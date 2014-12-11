package wetalk.engine.parsing

import akka.util.ByteString

import scala.annotation.tailrec

package object parsing {

  /**
   * INTERNAL API
   */
  private[wetalk] def escape(c: Char): String = c match {
    case '\t'                           ⇒ "\\t"
    case '\r'                           ⇒ "\\r"
    case '\n'                           ⇒ "\\n"
    case x if Character.isISOControl(x) ⇒ "\\u%04x" format c.toInt
    case x                              ⇒ x.toString
  }

  /**
   * INTERNAL API
   */
  private[wetalk] def byteChar(input: ByteString, ix: Int): Char = byteAt(input, ix).toChar

  /**
   * INTERNAL API
   */
  private[wetalk] def byteAt(input: ByteString, ix: Int): Byte =
    if (ix < input.length) input(ix) else throw NotEnoughDataException

  /**
   * INTERNAL API
   */
//  private[http] def asciiString(input: ByteString, start: Int, end: Int): String = {
//    @tailrec def build(ix: Int = start, sb: JStringBuilder = new JStringBuilder(end - start)): String =
//      if (ix == end) sb.toString else build(ix + 1, sb.append(input(ix).toChar))
//    if (start == end) "" else build()
//  }
}


package parsing {


/**
 * INTERNAL API
 */
private[parsing] class ParsingException extends RuntimeException() {
}

/**
 * INTERNAL API
 */
private[parsing] object NotEnoughDataException extends RuntimeException
}
