import akka.actor.{ Actor, ActorLogging, ActorSystem, Props }

object IoT extends App {

  implicit val system = ActorSystem("IoT-Stream")

  object Device {
    def props(groupId: String, deviceId: String): Props = Props(new Device(groupId, deviceId))

    final case class RequestId(value: Long)
    // write
    final case class Temperature(value: Double)
    final case class Update(requestId: RequestId, temperature: Temperature)
    final case class Updated(requestId: RequestId)

    // query
    final case class RequestTemperature(requestId: RequestId)
    final case class Response(requestId: RequestId, maybeTemperature: Option[Temperature])
  }

  class Device(groupId: String, deviceId: String) extends Actor with ActorLogging {
    import Device._

    // todo: rather give temperature together with groupId and deviceId
    // then no need for option value, and generally write protocol
    var maybeTemperature: Option[Temperature] = None

    override def preStart(): Unit = log.info("Device actor {}-{} started", groupId, deviceId)
    override def postStop(): Unit = log.info("Device actor {}-{} stopped", groupId, deviceId)

    def receive: Receive = {
      case Update(requestId, temperature) =>
        log.info("Recorded temperature reading {} with {}", temperature.value, requestId.value)
        maybeTemperature = Some(temperature)
        sender ! Updated(requestId)
      case RequestTemperature(requestId) =>
        log.info("Requested temperature {} with {}", maybeTemperature, requestId.value)
        sender ! Response(requestId, maybeTemperature)
    }
  }

  val device = system.actorOf(Device.props("group", "device"))

  device ! Device.Update(Device.RequestId(1), Device.Temperature(26))
  device ! Device.RequestTemperature(Device.RequestId(1))

  system.shutdown
}

