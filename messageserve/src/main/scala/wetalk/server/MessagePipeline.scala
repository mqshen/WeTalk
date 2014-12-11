package wetalk.server

import java.net.InetSocketAddress

import akka.event.LoggingAdapter
import akka.stream.{Transformer, FlattenStrategy, FlowMaterializer}
import akka.stream.io.StreamTcp
import akka.stream.scaladsl._
import akka.util.ByteString
import org.reactivestreams.{Subscriber, Publisher}
import wetalk.engine.parsing.MessageParser
import wetalk.engine.parsing.ParserOutput.{UserMessage, MessageStart, RequestOutput}
import wetalk.engine.rendering.{ResponseRenderingContext, MessageResponseRender}
import wetalk.model.{MessageResponse, MessageRequest}

/**
 * Created by goldratio on 11/21/14.
 */
final case class IncomingConnection(remoteAddress: InetSocketAddress,
                                    requestPublisher: Publisher[UserMessage],
                                    responseSubscriber: Subscriber[MessageResponse])
class MessagePipeline()(implicit fm: FlowMaterializer) extends (StreamTcp.IncomingTcpConnection => IncomingConnection) {

  def apply(tcpConn: StreamTcp.IncomingTcpConnection) = {
    import FlowGraphImplicits._

    val networkIn = Source(tcpConn.inputStream)
    val networkOut = Sink(tcpConn.outputStream)

    val userIn = Sink.publisher[UserMessage]
    val userOut = Source.subscriber[MessageResponse]


    val rootParser = new MessageParser()
    val rootRender = new MessageResponseRender()

    val pipeline = FlowGraph { implicit b =>
      val bypassFanout = Broadcast[Source[UserMessage]]("bypassFanout")
      val bypassFanin = Merge[Any]("merge")

      val rootParsePipeline =
        Flow[ByteString]
          .transform("rootParser", () => rootParser).splitWhen{
          x =>
            println(x)
            true
        }


      println(rootParsePipeline)

//
//      val parse = rootParsePipeline.splitWhen{x =>
//        println(x)
//        x.isInstanceOf[ByteString]
//      }
//        .map {
//        case t =>
//          println(t)
//          t.prefixAndTail(1).map {
//            case (prefix, tail) =>
//              (prefix.head, tail)
//          }
//      }
//        .flatten(FlattenStrategy.concat)
//      println(parse)

      val rendererPipeline =
        Flow[Any]
          .transform("applyApplicationBypass", () => applyApplicationBypass)
          .transform("renderer", () => rootRender)
          .flatten(FlattenStrategy.concat)
          //.transform("errorLogger", () => errorLogger(log, "Outgoing response stream error"))


      val requestTweaking = Flow[Source[UserMessage]].collect {
        case t: UserMessage =>
          t
        case _ =>
          UserMessage(null, null)
      }


      val bypass =
        Flow[Source[UserMessage]]
          .collect[UserMessage] { case x: UserMessage => x }

      networkIn ~> rootParsePipeline ~>
        bypassFanout ~> requestTweaking ~> userIn
      bypassFanout ~> bypass ~> bypassFanin
      userOut ~> bypassFanin ~> rendererPipeline ~> networkOut

    }.run()

    IncomingConnection(tcpConn.remoteAddress, pipeline.get(userIn), pipeline.get(userOut))

  }

  private[wetalk] def errorLogger(log: LoggingAdapter, msg: String): Transformer[ByteString, ByteString] =
    new Transformer[ByteString, ByteString] {
      def onNext(element: ByteString) = element :: Nil
      override def onError(cause: Throwable): Unit = log.error(cause, msg)
    }

  def applyApplicationBypass =
    new Transformer[Any, ResponseRenderingContext] {

      def onNext(elem: Any) = elem match {
        case _ =>
          ResponseRenderingContext(new MessageResponse(),  false) :: Nil
      }

    }

}
