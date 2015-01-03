package wetalk.user

import java.util.concurrent.{ThreadFactory, TimeUnit}

import akka.actor._
import akka.dispatch.MonitorableThreadFactory
import akka.event.{LoggingAdapter, Logging}
import com.typesafe.config.{Config, ConfigFactory}

import scala.collection.immutable
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * Created by goldratio on 1/3/15.
 */


object UserExtension extends ExtensionId[UserExtension] with ExtensionIdProvider {
  override def get(system: ActorSystem): UserExtension = super.get(system)

  override def lookup(): ExtensionId[_ <: Extension] = UserExtension

  override def createExtension(system: ExtendedActorSystem): UserExtension = new UserExtension(system)

  val mediatorName: String = "socketioMediator"
  val mediatorSingleton: String = "socketiosession"

}

class UserExtension (system: ExtendedActorSystem) extends Extension {
    private val log = Logging(system, "SocketIO")

    /**
     * INTERNAL API
     */
    private[wetalk] object Settings {
      val sessionRole: String = "connectionSession"
      val config = system.settings.config.getConfig("wetalk")
      val isCluster: Boolean = config.getString("mode") == "cluster"
      val enableSessionPersistence: Boolean = config.getBoolean("server.enable-connectionsession-persistence")
      val schedulerTickDuration: FiniteDuration = Duration(config.getDuration("scheduler.tick-duration", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
      val schedulerTicksPerWheel: Int = config.getInt("scheduler.ticks-per-wheel")
      //val namespaceGroup = config.getString("server.namespace-group-name")
    }

    /**
     * No lazy, need to start immediately to accept subscriptions msg etc.
     * namespaceMediator is used by client outside of cluster.
     */


    lazy val scheduler: Scheduler = {
      import scala.collection.JavaConverters._
      log.info("Using a dedicated scheduler for socketio with 'spray.socketio.scheduler.tick-duration' [{} ms].", Settings.schedulerTickDuration.toMillis)

      val cfg = ConfigFactory.parseString(
        s"socketio.scheduler.tick-duration=${Settings.schedulerTickDuration.toMillis}ms").withFallback(system.settings.config)
      val threadFactory = system.threadFactory match {
        case tf: MonitorableThreadFactory => tf.withName(tf.name + "-socketio-scheduler")
        case tf                           => tf
      }
      system.dynamicAccess.createInstanceFor[Scheduler](system.settings.SchedulerClass, immutable.Seq(
        classOf[Config] -> cfg,
        classOf[LoggingAdapter] -> log,
        classOf[ThreadFactory] -> threadFactory)).get
    }

}
