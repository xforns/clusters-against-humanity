package com.greenhouse.cluster

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import akka.pattern.pipe
import scala.concurrent.Future

class Player extends Actor with ActorLogging {

  import context.dispatcher

  val rnd = scala.util.Random
  val answers = Seq(
    "Grandpa's ashes",
    "Kid-tested, mother-approved",
    "High five, bro",
    "The biggest, blackest dick",
    "My worthless son",
    "Friction")

  def receive = {
    case (question: Question) =>
      Future(retrieveAnswer(question).get) pipeTo sender()
  }

  def retrieveAnswer(question: Question): Option[Answer] = {
    val nextRnd = rnd.nextInt(answers.size)
    Some(Answer(question.content+answers(nextRnd)))
  }

}

object Player {
  def main(args: Array[String]): Unit = {
    // Override the configuration of the port when specified as program argument
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"""
        akka.remote.netty.tcp.port=$port
        akka.remote.artery.canonical.port=$port
        """)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [player]"))
      .withFallback(ConfigFactory.load("game"))

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props[Player], name = "player")

    system.actorOf(Props[MetricsListener], name = "metricsListener")
  }
}
