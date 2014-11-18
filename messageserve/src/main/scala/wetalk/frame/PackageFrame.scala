package wetalk.frame

import wetalk.{ ConnectingContext, ConnectionSession }

/**
 * Created by goldratio on 11/17/14.
 */
object PackageFrame {
  def unapply(frame: TextFrame)(implicit ctx: ConnectingContext): Option[Boolean] = {
    // ctx.sessionId should have been set during wsConnected
    //ctx.log.debug("Got TextFrame: {}", frame.payload.utf8String)
    ctx.sessionRegion ! ConnectionSession.OnFrame(ctx.sessionId, frame.payload)
    Some(true)
  }
}
