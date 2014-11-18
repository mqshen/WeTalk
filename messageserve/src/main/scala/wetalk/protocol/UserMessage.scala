package wetalk.protocol

/**
 * Created by goldratio on 11/18/14.
 */
case class MessagePrefix(opCode: Int,
                         id: Long) {
  override def toString = s"$opCode:$id"
}

case class UserMessage(prefix: MessagePrefix,
                       jsonData: String) extends Message {
  //TODO add : for trailing or add a separate field just for that
  override def toString = prefix.toString + ":" + jsonData
}
