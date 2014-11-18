package wetalk

import spray.http.{ HttpOrigin, Uri }

/**
 * Created by goldratio on 11/6/14.
 */
class ConnectionContext(private var _sessionId: String = null) extends Serializable {
  def sessionId = _sessionId
  private[wetalk] def sessionId_=(sessionId: String) = {
    _sessionId = sessionId
    this
  }
  private var _isConnected: Boolean = _
  def isConnected = _isConnected
  private[wetalk] def isConnected_=(isConnected: Boolean) = {
    _isConnected = isConnected
    this
  }

  override def equals(other: Any) = {
    other match {
      case x: ConnectionContext => x.sessionId == this.sessionId
      case _ =>
        false
    }
  }

  override def toString(): String = {
    new StringBuilder().append(sessionId)
      .append(", isConnected=").append(isConnected)
      .toString
  }
}

