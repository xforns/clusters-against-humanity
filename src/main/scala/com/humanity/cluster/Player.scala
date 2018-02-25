package com.humanity.cluster

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props, RootActorPath}
import akka.pattern.pipe
import com.typesafe.config.ConfigFactory

import scala.collection.mutable.ListBuffer
import scala.concurrent.Future


class Player extends Actor with ActorLogging {

  import context.dispatcher

  var czar: ActorRef = _
  val rnd = scala.util.Random
  var answers: Map[UUID,Answer] = Map.empty
  val givenAnswers:ListBuffer[UUID] = ListBuffer()

  def receive = {

    case RestartGame =>
      answers = Map.empty
      givenAnswers.clear()

    case startGame: StartGameRound =>
      czar = sender()
      context.actorSelection(RootActorPath(startGame.deck) / "user" / "deck") ! DeckAnswer(5)

    case answers: Answers =>
      this.answers ++= answers.obj
      Future(retrieveAnswer() get) pipeTo czar

    case NoAnswersLeft() =>
      Future(retrieveAnswer()) map {
        case Some(answer) => answer
        case None => NoAnswersLeft()
      } pipeTo czar

  }

  private def retrieveAnswer(): Option[Answer] = {
    val filteredAnswers = answers.filter( o => !givenAnswers.contains(o._1) ).toSeq
    if(filteredAnswers.isEmpty) return None
    val nextRnd = rnd.nextInt(filteredAnswers.size)
    givenAnswers += filteredAnswers(nextRnd)._1
    Some(filteredAnswers(nextRnd)._2)
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
