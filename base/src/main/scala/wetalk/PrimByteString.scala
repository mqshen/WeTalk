package wetalk

import java.nio.ByteOrder

import akka.util.{ByteStringBuilder, ByteIterator, ByteString}

/**
 * Created by goldratio on 11/3/14.
 */
class PrimByteIterator(underlying: ByteIterator) {

  def getString()(implicit byteOrder: ByteOrder): String = {
    val length = underlying.getInt
    val bytes = new Array[Byte](length)
    underlying.getBytes(bytes)
    new String(bytes)
  }

}

class PrimByteStringBuilder(underlying: ByteStringBuilder) {

  def putString(str: String)(implicit byteOrder: ByteOrder): Unit = {
    val bytes = str.getBytes
    underlying.putInt(bytes.length)
    underlying.putBytes(bytes)
  }

}
