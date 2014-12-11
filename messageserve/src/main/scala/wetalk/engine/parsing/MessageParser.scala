package wetalk.engine.parsing

import java.nio.ByteOrder

import akka.stream.Transformer
import akka.util.ByteString
import wetalk.engine.parsing.ParserOutput.{UserMessage, MessageEnd, MessageOutput}

import scala.annotation.tailrec
import scala.collection.immutable
import scala.collection.mutable.ListBuffer

/**
 * Created by goldratio on 11/21/14.
 */
class MessageParser extends Transformer[ByteString, UserMessage] {
  implicit val byteOrder = ByteOrder.BIG_ENDIAN

  val delimiter: ByteString = ByteString("\r\n".getBytes("UTF-8"))
  val includeDelimiter: Boolean = false
  val maxSize: Int = 1000

  private val singleByteDelimiter: Boolean = delimiter.size == 1

  private var buffer: ByteString = ByteString.empty

  private var delimiterFragment: Option[ByteString] = None

  private val firstByteOfDelimiter = delimiter.head

  private[this] var terminated = false
  override def isComplete = terminated

  var lastTime: Long = 0

  def onNext(fragment: ByteString): List[UserMessage] = {
    val currentTimestamp = System.currentTimeMillis()
    val parts = extractParts(fragment, Nil)
    buffer = buffer.compact
    val delimiterTime = currentTimestamp - lastTime
    if (delimiterTime > 5)
      println(s"delimiter time:${delimiterTime}" )
    val parseTime = System.currentTimeMillis() - currentTimestamp
    if (parseTime > 5)
      println(s"delimiter time:${parseTime }" )

    this.lastTime = currentTimestamp

    parts
  }

  @tailrec
  private def extractParts(nextChunk: ByteString, acc: List[UserMessage]): List[UserMessage] = {

    delimiterFragment match {
      case Some(fragment) if nextChunk.size < fragment.size && fragment.startsWith(nextChunk) =>
        buffer ++= nextChunk
        delimiterFragment = Some(fragment.drop(nextChunk.size))
        acc
      // We got the missing parts of the delimiter
      case Some(fragment) if nextChunk.startsWith(fragment) ⇒
        val decoded = if (includeDelimiter) buffer ++ fragment else buffer.take(buffer.size - delimiter.size + fragment.size)
        val message = InputMessageParser.parse(decoded.utf8String)
        //val test = new MessageOutput()
        buffer = ByteString.empty
        delimiterFragment = None
        if(message.isSuccess)
          extractParts(nextChunk.drop(fragment.size), message.get :: acc)
        else
          extractParts(nextChunk.drop(fragment.size), acc)
      case _ =>
        val matchPosition = nextChunk.indexOf(firstByteOfDelimiter)
        if (matchPosition == -1) {
          delimiterFragment = None
          val minSize = buffer.size + nextChunk.size
          if (minSize > maxSize) throw new IllegalArgumentException(
            s"Received too large frame of size $minSize (max = $maxSize)")
          buffer ++= nextChunk
          acc
        } else if (matchPosition + delimiter.size > nextChunk.size) {
          val delimiterMatchLength = nextChunk.size - matchPosition
          if (nextChunk.drop(matchPosition) == delimiter.take(delimiterMatchLength)) {
            buffer ++= nextChunk
            // we are expecting the other parts of the delimiter
            delimiterFragment = Some(delimiter.drop(nextChunk.size - matchPosition))
            acc
          } else {
            // false positive
            delimiterFragment = None
            buffer ++= nextChunk.take(matchPosition + 1)
            extractParts(nextChunk.drop(matchPosition + 1), acc)
          }
        } else {
          delimiterFragment = None
          val missingBytes: Int = if (includeDelimiter) matchPosition + delimiter.size else matchPosition
          val expectedSize = buffer.size + missingBytes
          if (expectedSize > maxSize) throw new IllegalArgumentException(
            s"Received frame already of size $expectedSize (max = $maxSize)")

          if (singleByteDelimiter || nextChunk.slice(matchPosition, matchPosition + delimiter.size) == delimiter) {
            val decoded = buffer ++ nextChunk.take(missingBytes)
            buffer = ByteString.empty
            val message = InputMessageParser.parse(decoded.utf8String)
            if(message.isSuccess)
              extractParts(nextChunk.drop(matchPosition + delimiter.size), message.get :: acc)
            else
              extractParts(nextChunk.drop(matchPosition + delimiter.size), acc)

          } else {
            buffer ++= nextChunk.take(matchPosition + 1)
            extractParts(nextChunk.drop(matchPosition + 1), acc)
          }
        }

    }
  }


  sealed trait StateResult

  def terminate(): StateResult = {
    terminated = true
    done()
  }

  def done(): StateResult = null
}
