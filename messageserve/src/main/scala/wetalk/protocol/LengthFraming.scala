package wetalk.protocol

import akka.util.ByteString

import scala.annotation.tailrec

/**
 * Created by goldratio on 11/20/14.
 */
class LengthFraming {

  private var buffer: ByteString = ByteString.empty

  def apply(fragment: ByteString): List[ByteString] = {
    val parts = extractParts(fragment, Nil)
    buffer = buffer.compact

    parts
  }

  private def extractParts(nextChunk: ByteString, acc: List[ByteString]): List[ByteString] = {
    acc
  }

}
