package wetalk.parser

/**
 * Created by goldratio on 11/24/14.
 */

import akka.util.ByteString

import scala.annotation.tailrec

class DelimiterFraming(maxSize: Int, delimiter: ByteString, includeDelimiter: Boolean = false) {

  require(maxSize > 0, "maxSize must be positive")
  require(delimiter.nonEmpty, "delimiter must not be empty")

  private val singleByteDelimiter: Boolean = delimiter.size == 1

  private var buffer: ByteString = ByteString.empty

  private var delimiterFragment: Option[ByteString] = None

  private val firstByteOfDelimiter = delimiter.head

  var lastTime: Long = 0

  def apply(fragment: ByteString): List[ByteString] = {
    val bytes = new Array[Byte](fragment.length)
    fragment.iterator.getBytes(bytes)
    println(new String(bytes))
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
  private def extractParts(nextChunk: ByteString, acc: List[ByteString]): List[ByteString] = {

    delimiterFragment match {
      case Some(fragment) if nextChunk.size < fragment.size && fragment.startsWith(nextChunk) =>
        buffer ++= nextChunk
        delimiterFragment = Some(fragment.drop(nextChunk.size))
        acc
      // We got the missing parts of the delimiter
      case Some(fragment) if nextChunk.startsWith(fragment) ⇒
        val decoded = if (includeDelimiter) buffer ++ fragment else buffer.take(buffer.size - delimiter.size + fragment.size)
        buffer = ByteString.empty
        delimiterFragment = None
        extractParts(nextChunk.drop(fragment.size), decoded :: acc)
      case _ =>
        val matchPosition = nextChunk.indexOf(firstByteOfDelimiter)
        if (matchPosition == -1) {
          delimiterFragment = None
          val minSize = buffer.size + nextChunk.size
          if (minSize > maxSize) throw new IllegalArgumentException(
            s"Received too large frame of size $minSize (max = $maxSize)")
          buffer ++= nextChunk
          acc
        }
        else if (matchPosition + delimiter.size > nextChunk.size) {
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
            extractParts(nextChunk.drop(matchPosition + delimiter.size), decoded :: acc)
          } else {
            buffer ++= nextChunk.take(matchPosition + 1)
            extractParts(nextChunk.drop(matchPosition + 1), acc)
          }
        }

    }
  }
}

