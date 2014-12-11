package wetalk.engine.parsing

import akka.util.ByteString
import wetalk.protocol.Message

/**
 * Created by goldratio on 11/21/14.
 */

private[wetalk] sealed trait ParserOutput

private[wetalk] object ParserOutput {
  sealed trait RequestOutput extends ParserOutput
  sealed trait ResponseOutput extends ParserOutput
  sealed trait MessageStart extends ParserOutput
  sealed trait MessageOutput extends RequestOutput with ResponseOutput


  case object MessageEnd extends MessageOutput

  final case class EntityPart(data: ByteString) extends MessageOutput

  case class MessagePrefix(opCode: Int,
                           id: Long) {
    override def toString = s"$opCode:$id"
  }

  case class UserMessage(prefix: MessagePrefix,
                         jsonData: String) extends MessageOutput{
    //TODO add : for trailing or add a separate field just for that
    override def toString = prefix.toString + ":" + jsonData
  }
}
