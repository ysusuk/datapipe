import akka.{ Done, NotUsed }
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, scaladsl}
import scaladsl.{Broadcast, GraphDSL, Flow, Keep, RunnableGraph, Sink, Source}
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

  trait Eval[T <: Cmd] {
    def apply(cmd: T): String
  }

  implicit object evalLS extends Eval[LS.type] {
    def apply(lsCmd: LS.type): String =
      "cv.pdf, resume.pdf"
  }

  implicit object evalCat extends Eval[Cat] {
    def apply(catCmd: Cat): String = catCmd.fileName match {
      case "cv.pdf" =>
        "Mr. Yuriy Susuk"
      case "resume.pdf" =>
        "Herr Yuriy Susuk"
    }
  }

  // use summoner, e.g. eval[LS.type] returns evalLS
  def eval[T <: Cmd](cmd: T)(implicit evalF: Eval[T]): String = evalF(cmd)

  val flow: Flow[Cmd, String, NotUsed] = Flow[Cmd].map(eval)

  import scala.concurrent.ExecutionContext.Implicits.global

  // linear
  // ??? why this is Future[Done]
  val res1 = in
    .map(eval)
    .runForeach(println)

  res1.onComplete { _ => system.shutdown }

  // linear
  // ??? but this is not Future[Done]
  val res2 = in
    .via(flow)
    // shutdown condition
    .take(10)
    .to(out)
    .run

  // branching
  RunnableGraph.fromGraph(GraphDSL.create() { implicit b => 
    import GraphDSL.Implicits._

    val bcast = b.add(Broadcast[Cmd](2))
    in ~> bcast.in
    // would like Flow to be a Prism, so Flow[LS.type] and Flow[Cat]
    bcast.out(0) ~> Flow[Cmd].map(_ => "") ~> out
    bcast.out(1) ~> Flow[Cmd].map(_ => "") ~> out
    ClosedShape
  })

  // ??? sequence all futures with Future.sequence, and shutdown on complete
}
