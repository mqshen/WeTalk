package wetalk.user

import java.sql.Connection
import java.util.{Date, UUID}
import java.util.concurrent.TimeUnit

import akka.contrib.pattern.ShardRegion.Passivate
import wetalk.processor._
import scala.concurrent.duration._

import akka.pattern.ask
import akka.actor._
import akka.util.Timeout
import wetalk.parser._
import wetalk.data._

import scala.concurrent.forkjoin.ThreadLocalRandom

/**
 * Created by goldratio on 11/18/14.
 */

class UserActor(connection: ActorRef, databaseActor: ActorRef, sessionRegion: ActorRef) extends Actor with ActorLogging with Processor{
  import context.dispatcher
  import UserActor._
  import wetalk._

  private val serverPassword = context.system.settings.config.getString("wetalk.server.salt")

  implicit val timeout = Timeout(120, TimeUnit.SECONDS)

  var user: User = null
  var sessionId: String = null

  private lazy val scheduler = UserExtension(context.system).scheduler

  private var heartbeatTask: Option[Cancellable] = None
  private var closeTimeoutTask: Option[Cancellable] = None
  private var idleTimeoutTask: Option[Cancellable] = None

  def enableHeartbeat() {
    log.debug("enabled heartbeat, will repeatly send heartbeat every {} seconds", wetalk.Settings.heartbeatInterval.seconds)
    heartbeatTask foreach { _.cancel } // it better to confirm previous heartbeatTask was cancled
    heartbeatTask = Some(scheduler.schedule(heartbeatDelay, wetalk.Settings.heartbeatInterval.seconds, self, HeartbeatTick))
  }

  def disableHeartbeat() {
    log.debug("disabled heartbeat")
    heartbeatTask foreach { _.cancel }
    heartbeatTask = None
  }

  def enableCloseTimeout() {
    log.debug("enabled close-timeout, will disconnect in {} seconds", wetalk.Settings.CloseTimeout)
    closeTimeoutTask foreach { _.cancel } // it better to confirm previous closeTimeoutTask was cancled
    if (context != null) {
      closeTimeoutTask = Some(scheduler.scheduleOnce(wetalk.Settings.CloseTimeout.seconds, self, CloseTimeout))
    }
  }

  def disableCloseTimeout() {
    log.debug("disabled close-timeout")
    closeTimeoutTask foreach { _.cancel }
    closeTimeoutTask = None
  }


  def enableIdleTimeout() {
    log.debug("enabled idle-timeout, will stop/exit in {} seconds", wetalk.Settings.IdleTimeout)
    idleTimeoutTask foreach { _.cancel } // it better to confirm previous idleTimeoutTask was cancled
    if (context != null) {
      idleTimeoutTask = Some(scheduler.scheduleOnce(wetalk.Settings.IdleTimeout.seconds, self, IdleTimeout))
    }
  }

  def disableIdleTimeout() {
    log.debug("disabled idle-timeout")
    idleTimeoutTask foreach { _.cancel }
    idleTimeoutTask = None
  }

  def deactivate() {
    log.debug("deactivated.")
    disableHeartbeat()
    disableCloseTimeout()
  }

  def doStop() {
    deactivate()
    disableIdleTimeout()

    if (UserExtension(context.system).Settings.isCluster) {
      context.parent ! Passivate(stopMessage = PoisonPill)
    }
    else {
      self ! PoisonPill
    }
  }

  def receive = processorAuth()


  def processorAuth(): Receive = {
    case userAuth: UserAuth =>
      processUserAuth(userAuth)

    case userAuth: UserTokenAuth =>
      processUserTokenAuth(userAuth)

    case _ =>
      println("user have not login")
  }


  def processUserAuth(userAuth: UserAuth): Unit = {
    val sql = "select id, name, nick, avatar, address, status, sex, type, phone, mail, " +
      "created, updated from IMUsers where name = ? and password =?"
    Database.withConnection { connect =>
      connect.withStatement(sql) { statement =>
        statement.setString(1, userAuth.userName)
        statement.setString(2, userAuth.password)
        statement.withResult { rs =>
          if (rs.next()) {
            val update = rs.getDate("updated")
            val updateData = if(update == null) None else Some(update)
            val user = User(rs.getInt("id").toString, rs.getString("name"), rs.getString("nick"), rs.getString("avatar"),
              rs.getString("address"), rs.getInt("status"), rs.getInt("sex"), rs.getInt("type"), rs.getString("phone"),
              rs.getString("mail"), rs.getDate("created"), updateData)

            val sessionId = UUID.randomUUID().toString
            sessionRegion ! CreateSession(sessionId, user, self)
            this.user = user
            this.sessionId = sessionId

            enableHeartbeat()
            context.become(authenticated)
            databaseActor ! UserToken(user.id, sessionId)
            connection ! LoginResponse(userAuth.seqNo, user, sessionId)
          }
          else {
            connection ! InvalidPassword
          }
        }
      }
    }
  }


  def processUserTokenAuth(userToken: UserTokenAuth): Unit = {
    val sql = "select created from IMSession where id= ? and token = ?"
    Database.withConnection { connect =>
      connect.withStatement(sql) { statement =>
        statement.setString(1, userToken.account)
        statement.setString(2, userToken.token)
        statement.withResult { rs =>
          if (rs.next()) {
            getUser(connect, userToken.account).map { user =>
              val sessionId = UUID.randomUUID().toString
              sessionRegion ! CreateSession(sessionId, user, self)
              this.user = user
              this.sessionId = sessionId

              enableHeartbeat()
              context.become(authenticated)
              databaseActor ! UserToken(user.id, sessionId)
              connection ! LoginResponse(userToken.seqNo, user, sessionId)
            }
          }
          else {
            connection ! InvalidPassword
          }
        }
      }
    }
  }



  def authenticated: Receive = UserSyncProcessor.processor(connection, context) orElse
    ChatMessageProcessor.processor(connection, context, sessionRegion) orElse
    ListFriendProcessor.processor(user.id, connection) orElse
    CreateGroupProcessor.processor(user.id, connection) orElse
    ListGroupProcessor.processor(user.id, connection) orElse
    UserOperateProcessor.processor(user, connection, sessionRegion) orElse heartbeatProcess


  def heartbeatProcess: Receive = {
    case message: DispatchMessage =>
      connection ! message.message

    case HeartbeatTick => // scheduled sending heartbeat
      log.debug("send heartbeat")
      connection ! HeartbeatResponse

      if (closeTimeoutTask.fold(true)(_.isCancelled)) {
        enableCloseTimeout()
      }
    case HeartbeatRequest =>
      log.debug("got heartbeat")
      disableCloseTimeout()
    case DisconnectRequest =>
      connection ! UserActor.Quit
      doStop()
    case CloseTimeout =>
      connection ! UserActor.Quit
      sessionRegion ! CloseSession(user.id.toString, sessionId)
      log.info("CloseTimeout disconnect: {}, user: {}", sessionId, user)
      doStop()
    case _ =>
      println("tttt")
  }

}

object UserActor {

  def props(connection: ActorRef, databaseActor: ActorRef, sessionRegion: ActorRef) = {
    Props(classOf[UserActor], connection, databaseActor, sessionRegion)
  }

  case object HeartbeatTick

  case object CloseTimeout

  case object IdleTimeout

  case class Authenticate(password: String)

  case class SetUsername(username: String)

  case class Quit(message: Option[String])

  case object InvalidPassword

  case object Quit

  sealed trait UserState

  case object AuthenticationPending extends UserState

  case object RegistrationPending extends UserState

  case object Authenticated extends UserState

  sealed trait UserData

  case object EmptyState extends UserData

  private def heartbeatDelay = ThreadLocalRandom.current.nextInt((math.min(wetalk.Settings.HeartbeatTimeout, wetalk.Settings.CloseTimeout) * 0.618).round.toInt).seconds
}
