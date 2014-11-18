package wetalk

import akka.actor.ActorRef

/**
 * Created by goldratio on 11/6/14.
 */
private[wetalk] final class ConnectingContext(var sessionId: String,
                                              val serverConnection: ActorRef,
                                              val sessionRegion: ActorRef)

