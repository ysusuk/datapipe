import akka.{ Done, NotUsed }
import akka.stream.KillSwitches
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}
import scala.concurrent.Future

object Terminal extends App {
  implicit val system = ActorSystem("Cmds-Stream")
  implicit val materializer = ActorMaterializer()

//  val helloWorldStream: RunnableGraph[NotUsed] = Source.single("Hello world")
//    .via(Flow[String].map(s => s.toUpperCase()))
//    .to(Sink.foreach(println))

//  helloWorldStream.run

  // in, out
  // ls, cat
  sealed trait Cmd
  case object LS extends Cmd
  case class Cat(fileName: String) extends Cmd

  // mix way
  val ls: Source[LS.type, NotUsed] = Source.single(LS)
  val cat: Source[Cat, NotUsed] = Source.single(Cat("cv.pdf"))
  val mix = ls.concatMat(cat)(Keep.both)

  // poly way
  import scala.collection.{immutable => immut}
  val in: Source[Cmd, NotUsed] = Source(immut.Seq[Cmd](LS, Cat("cv.pdf"), LS, Cat("resume.pdf")))
  val out: Sink[String, Future[Done]] = Sink.foreach(println)

  def eval(cmd: Cmd): String =  cmd match {
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

  val flow: Flow[Cmd, String, NotUsed] = Flow[Cmd].map(eval)

  import scala.concurrent.ExecutionContext.Implicits.global

  // ??? why this is Future[Done]
  val res1 = in
    .map(eval)
    .runForeach(println)

  res1.onComplete { _ => system.shutdown }

  // ??? but this is not Future[Done]
  val res2 = in
    .via(flow)
    // shutdown condition
    .take(10)
    .to(out)
    .run

  // ??? sequence all futures with Future.sequence, and shutdown on complete
}
