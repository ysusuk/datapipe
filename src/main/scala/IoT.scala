import akka.actor.{ Actor, ActorSystem, Props }

object IoT extends App {

  implicit val system = ActorSystem("IoT-Stream")

  case class Temperature(value: Int)

  sealed trait Device
  case class Thermostat(temprature: Temperature) extends Device

  class DeviceManager extends Actor {

    override def receive: Receive = {
      case Thermostat(temperature) =>
        println(temperature)
    }
  }

  val deviceManager = system.actorOf(Props[DeviceManager], "device-manager")
  deviceManager ! Thermostat(Temperature(123))

  system.shutdown
}
