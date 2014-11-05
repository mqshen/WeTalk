package wetalk

import akka.util.{ByteStringBuilder, ByteIterator}

package object util {
  implicit def primByteIterator_(s: ByteIterator): PrimByteIterator = new PrimByteIterator(s)
  implicit def primByteStringBuilder_(s: ByteStringBuilder): PrimByteStringBuilder = new PrimByteStringBuilder(s)
}