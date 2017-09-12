import akka.{ Done, NotUsed }
import akka.stream.UniformFanOutShape
import akka.stream.scaladsl.Merge
import akka.stream.javadsl.FramingTruncation
import akka.util.ByteString
import akka.stream.javadsl.Framing
import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, ClosedShape, scaladsl}
import scaladsl.{Broadcast, GraphDSL, Flow, Keep, RunnableGraph, Sink, Source}
import scala.concurrent.Future

object Terminal extends App {
  import scala.collection.{immutable => immut}

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

  object Cmd {
    def apply(str: String): Option[Cmd] = str match {
      case "ls" =>
        Some(LS)
      case cmdStr if str.startsWith("cat") =>
        Some(Cat(cmdStr.split(" ")(1)))
      case _ =>
        None
    }
  }

  // mix way
//  val ls: Source[LS.type, NotUsed] = Source.single(LS)
//  val cat: Source[Cat, NotUsed] = Source.single(Cat("cv.pdf"))
//  val mix = ls.concatMat(cat)(Keep.both)

  // poly way
  val in: Source[Cmd, NotUsed] = Source(immut.Seq[Cmd](LS, Cat("cv.pdf"), LS, Cat("resume.pdf")))
  val out: Sink[String, Future[Done]] = Sink.foreach(println)

  trait Evaluator[-T] {
    def eval(cmd: T): String
  }

  object Evaluator {
    // use summoner/materializer, e.g. eval[LS.type] returns evalLS
    def apply[T](implicit evaluator: Evaluator[T]): Evaluator[T] = evaluator

    implicit val evalLS = new Evaluator[LS.type] {
      def eval(lsCmd: LS.type): String =
        "cv.pdf, resume.pdf"
    }

    implicit val evalCat = new Evaluator[Cat] {
      def eval(catCmd: Cat): String = catCmd.fileName match {
        case "cv.pdf" =>
          "Mr. Yuriy Susuk"
        case "resume.pdf" =>
          "Herr Yuriy Susuk"
      }
    }
  }

  import Evaluator._
  
  // having a vay to return evaluator based on type for evaluaiton
  // next step would be to remove case construct
  def eval(cmd: Cmd): String = cmd match {
    case ls: LS.type =>
      Evaluator[LS.type].eval(ls)
    case cat: Cat =>
      Evaluator[Cat].eval(cat)
  }

  val flow: Flow[Cmd, String, NotUsed] = Flow[Cmd].map(cmd => eval(cmd))

  import scala.concurrent.ExecutionContext.Implicits.global

  // linear
  // ??? why this is Future[Done]
  val res1 = in
    .map(eval)
//    .runForeach(println)

//  res1.onComplete { _ => system.shutdown }

  // linear
  // ??? but this is not Future[Done]
  val res2 = in
    .via(flow)
    // shutdown condition
    .take(10)
    .to(out)
//    .run

  val sumOut: Sink[Int, Future[Int]] = Sink.fold[Int, Int](0)(_ + _)

  // branching
  val g = RunnableGraph.fromGraph(GraphDSL.create() { implicit b => 
    import GraphDSL.Implicits._

    val bcast = b.add(Broadcast[Cmd](2))
    in ~> bcast    // would like Flow to be a Prism, so Flow[LS.type] and Flow[Cat]
    bcast.out(0) ~> Flow[Cmd].map(cmd => eval(cmd) + " broadcast out 0" ) ~> out
    bcast.out(1) ~> Flow[Cmd].map(cmd => eval(cmd) + " broadcast out 1") ~> out
//    bcast.out(2) ~> Flow[Cmd].map(_ => 1) ~> sumOut
    ClosedShape
  })

//  g.run

  val strs: Source[String, NotUsed] = Source(immut.Seq[String]("ls", "cat cv.pdf", "ls", "cat resume.pdf"))

  val terminalGraph = RunnableGraph.fromGraph(GraphDSL.create() { implicit builder => 
    import GraphDSL.Implicits._

    val recognize: Flow[String, Option[Cmd], NotUsed] = Flow.fromFunction(str => Cmd(str))
//    val bcast = 

    // a Prism
    // val recognizeToLsFlow: Flow[String, LS.type, NotUsed] = recognize.collect {
    //  case Some(ls: LS.type) =>
    //    ls
    // }

    val lsFlow: Flow[Option[Cmd], LS.type, NotUsed] = Flow.fromFunction {
      case Some(ls: LS.type) =>
        ls
    }

    val catFlow: Flow[Option[Cmd], Cat, NotUsed] = Flow.fromFunction {
      case Some(cat: Cat) =>
        cat
    }

    val noneFlow: Flow[Option[Cmd], None.type, NotUsed] = Flow.fromFunction {
      case None =>
        None
    }

    val merge = builder.add(Merge[String](3))

    val broadcast = builder.add(Broadcast[Option[Cmd]](3))
    broadcast.out(0) ~> lsFlow
    broadcast.out(1) ~> catFlow
    broadcast.out(2) ~> noneFlow

    
//    merge.in(0) ~> stub
//    merge.in(1) ~> stub
//    merge.in(2) ~> stub
    
    // stub to transform everything to str
    val stub: Flow[Any, String, NotUsed] = Flow.fromFunction(_.toString)

    strs ~> recognize ~> broadcast ~> stub ~> out

    ClosedShape
  })

  terminalGraph.run

  // Framing.delimiter(ByteString("/n"), 1000, FramingTruncation.ALLOW)


  // ??? sequence all futures with Future.sequence, and shutdown on complete
//  system.shutdown
//  system.awaitTermination
}
