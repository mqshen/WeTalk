package wetalk.processor

/**
 * Created by goldratio on 1/10/15.
 */
import akka.actor.Actor.Receive
import akka.actor.ActorRef
import wetalk.data.{User, Database}
import wetalk.parser._

object UserOperateProcessor extends Processor {
  import wetalk._

  def processor(user: User, connection: ActorRef, sessionRegion: ActorRef): Receive = {
    case userOperate: UserOperateRequest =>
      Database.withConnection { connect =>
        getUser(connect, userOperate.id).map { friend =>
          if (userOperate.operate == FriendOperate.Add) {
            val message = UserOperateResponse(userOperate.seqNo, userOperate.id, user, FriendOperate.ReceiveAdd, userOperate.greeting)
            sessionRegion ! DispatchMessage(userOperate.seqNo, userOperate.id, message)
            connection ! message.copy(operate = FriendOperate.Add, user = friend)
          }
          else if (userOperate.operate == FriendOperate.Accept) {
            val message = UserOperateResponse(userOperate.seqNo, userOperate.id, user, FriendOperate.ReceiveAccept, userOperate.greeting)
            sessionRegion ! DispatchMessage(userOperate.seqNo, userOperate.id, message)
            connection ! message.copy(operate = FriendOperate.Accept, user = friend)
          }
        }
      }
  }

}
