package com.humanity.cluster

import java.util.UUID

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import akka.pattern.pipe
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

class Deck extends Actor with ActorLogging {

  import context.dispatcher

  val givenQuestions:ListBuffer[UUID] = ListBuffer()
  val givenAnswers:ListBuffer[UUID] = ListBuffer()
  val rnd = scala.util.Random

  override def receive = {
    case (question: DeckQuestion) => {
      val sender = sender()
      val questions:ListBuffer[Question] = ListBuffer()
      Future {
        1 to question.count foreach {
          _ => {
            val filteredQuestions = filterOutQuestions()
            val nextRnd = rnd.nextInt(filteredQuestions.size)
            givenQuestions += filteredQuestions(nextRnd)._1
            questions += filteredQuestions(nextRnd)._2
          }
        }
        Questions(questions)
      } pipeTo sender
    }

    case (answer: DeckAnswer) => {

    }
  }

  private def filterOutQuestions(): Seq[(UUID,Question)] = {
    DeckContents.questions.filter( o => !givenQuestions.contains(o._1) ).toSeq
  }

}

object Deck {

  def main(args: Array[String]): Unit = {
    val port = if (args.isEmpty) "0" else args(0)
    val config = ConfigFactory.parseString(s"""
        akka.remote.netty.tcp.port=$port
        akka.remote.artery.canonical.port=$port
        """)
      .withFallback(ConfigFactory.parseString("akka.cluster.roles = [deck]"))
      .withFallback(ConfigFactory.load("game"))

    val system = ActorSystem("ClusterSystem", config)
    system.actorOf(Props[Deck], name = "deck")

    system.actorOf(Props[MetricsListener], name = "metricsListener")
  }

}