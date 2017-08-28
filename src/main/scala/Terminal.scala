import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}
import scala.concurrent.Future

object Terminal extends App {
  implicit val system = ActorSystem("QuickStart")
  implicit val materializer = ActorMaterializer()

  val helloWorldStream: RunnableGraph[NotUsed] = Source.single("Hello world")
    .via(Flow[String].map(s => s.toUpperCase()))
    .to(Sink.foreach(println))

  helloWorldStream.run

  // in, out
  // ls, cat
  sealed trait Cmd
  case object LS extends Cmd
  case class Cat(fileName: String) extends Cmd

  val ls: Source[LS.type, NotUsed] = Source.single(LS)
  val cat: Source[Cat, NotUsed] = Source.single(Cat("cv.pdf"))


  import scala.collection.{immutable => immut}
  val in: Source[Cmd, NotUsed] = Source(immut.Seq[Cmd](LS, Cat("cv.pdf"), LS, Cat("resume.pdf")))
  val out: Sink[String, Future[Done]] = Sink.foreach(println)

  val flow: Flow[Cmd, String, NotUsed] = Flow[Cmd].map {
    case LS =>
      "cv.pdf, resume.pdf"
    case Cat(fileName) =>
      fileName match {
        case "cv.pdf" =>
          "Mr. Yuriy Susuk"
        case "resume.pdf" =>
          "Herr Yuriy Susuk"
      }
  }

  in
    .via(flow)
    .to(out)
    .run
}
