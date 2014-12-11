package wetalk.engine.rendering

import akka.stream.Transformer
import akka.stream.scaladsl.Source
import akka.util.{ByteStringBuilder, ByteString}
import wetalk.model.MessageResponse

/**
 * Created by goldratio on 11/22/14.
 */


private[wetalk] class ByteStringRendering(sizeHint: Int) {
  private[this] val builder = new ByteStringBuilder
  builder.sizeHint(sizeHint)

  def get: ByteString = builder.result

  def ~~(char: Char): this.type = {
    builder += char.toByte
    this
  }

  def ~~(bytes: Array[Byte]): this.type = {
    if (bytes.length > 0) builder.putBytes(bytes)
    this
  }

  def ~~(bytes: ByteString): this.type = {
    if (bytes.length > 0) builder ++= bytes
    this
  }
}

class MessageResponseRender  extends Transformer[ResponseRenderingContext, Source[ByteString]] {

  def onNext(ctx: ResponseRenderingContext): List[Source[ByteString]] = {
    val r = new ByteStringRendering(10)
    val messageStart = Source(r.get :: Nil)
    List(messageStart)

  }
}

private[wetalk] final case class ResponseRenderingContext( response: MessageResponse, closeAfterResponseCompletion: Boolean = false)

