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
      val czar = sender()
      Future {
        val filteredQuestions = filterOutQuestions()
        if(filteredQuestions.size>0) {
          val nextRnd = rnd.nextInt(filteredQuestions.size)
          givenQuestions += filteredQuestions(nextRnd)._1
          filteredQuestions(nextRnd)._2
        }
        else {
          NoQuestionsLeft()
        }
      } pipeTo czar
    }

    case (answer: DeckAnswer) => {
      val player = sender()
      var answers:Map[UUID,Answer] = Map()
      Future {
        1 to answer.count foreach {
          _ => {
            val filteredAnswers = filterOutAnswers()
            if(filteredAnswers.size>0) {
              val nextRnd = rnd.nextInt(filteredAnswers.size)
              givenAnswers += filteredAnswers(nextRnd)._1
              answers += (filteredAnswers(nextRnd)._1 -> filteredAnswers(nextRnd)._2)
            }
          }
        }
        if(answers.size==0) NoAnswersLeft() else Answers(answers)
      } pipeTo player
    }
  }

  private def filterOutQuestions(): Seq[(UUID,Question)] = {
    DeckContents.questions.filter( o => !givenQuestions.contains(o._1) ).toSeq
  }

  private def filterOutAnswers(): Seq[(UUID,Answer)] = {
    DeckContents.answers.filter( o => !givenAnswers.contains(o._1) ).toSeq
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