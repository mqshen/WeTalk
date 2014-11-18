package wetalk.serializer

import akka.serialization.Serializer
import akka.util.ByteString
import wetalk.frame.{ FrameParser, FrameRender, Frame }

/**
 * Created by goldratio on 11/17/14.
 */

object FrameSerializer extends FrameSerializer
class FrameSerializer extends Serializer {

  // This is whether "fromBinary" requires a "clazz" or not
  final def includeManifest: Boolean = false

  // Pick a unique identifier for your Serializer,
  // you've got a couple of billions to choose from,
  // 0 - 16 is reserved by Akka itself
  final def identifier: Int = 1000

  final def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case x: Frame => FrameRender(x).toArray
      case _        => Array[Byte]()
    }
  }

  final def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef = {
    var r: Frame = null
    new FrameParser().onReceive(ByteString(bytes).iterator) {
      case FrameParser.Success(frame) => r = frame
      case FrameParser.Failure(_, _)  =>
    }
    r
  }
}

