package irc


/**
 * Created by goldratio on 11/18/14.
 */
case class MessagePrefix (nick : String,
                          user : String,
                          host : String) {
  override def toString = s":$nick!$user@$host"
}

case class UserIrcMessage(prefix: Option[MessagePrefix],
                          command: String,
                          arguments: List[String]) extends IrcMessage {
  //TODO add : for trailing or add a separate field just for that
  override def toString = prefix.map(_.toString + " ").getOrElse("") + command + arguments.mkString(" ")
}
