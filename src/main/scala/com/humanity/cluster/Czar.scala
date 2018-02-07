package com.humanity.cluster

import akka.actor.{Actor, ActorLogging, ActorSystem, Props, ReceiveTimeout}
import akka.cluster.Cluster
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try


class Czar(repeat: Boolean) extends Actor with ActorLogging {

  val player = context.actorOf(FromConfig.props(), name = "playerRouter")
  val answers:mutable.ListBuffer[String] = mutable.ListBuffer()
  val questions = Seq(
    "When all else fails, I can always masturbate to.. ",
    "An Oedipus complex. ",
    "Incest. ",
    "What would grandma find disturbing, yet oddly charming? ",
    "What brought the orgy to a grinding halt? ",
    "Daddy, why is mommy crying? ")

  override def preStart(): Unit = {
    sendQuestions()
    if (repeat) {
      context.setReceiveTimeout(10.seconds)
    }
  }

  def receive = {
    case (answer: Answer) =>
      log.info("{}",answer.content)
      answers += answer.content
      if (answers.size == questions.size) {
        if (repeat) sendQuestions()
        else context.stop(self)
      }
    case ReceiveTimeout =>
      log.info("Timeout")
      sendQuestions()
  }

  def sendQuestions(): Unit = {
    log.info("Sending questions..")
    questions foreach {player ! Question(_)}
  }
}

object Czar {
  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.parseString("akka.cluster.roles = [czar]").
      withFallback(ConfigFactory.load("game"))

    val system = ActorSystem("ClusterSystem", config)
    system.log.info("Game will start when there is at least 1 czar and 1 player in the cluster.")

    Cluster(system) registerOnMemberUp {
      system.actorOf(Props(classOf[Czar],true), name = "czar")
    }

    Cluster(system).registerOnMemberRemoved {
      // exit JVM when ActorSystem has been terminated
      system.registerOnTermination(System.exit(0))
      // shut down ActorSystem
      system.terminate()

      // In case ActorSystem shutdown takes longer than 10 seconds,
      // exit the JVM forcefully anyway.
      // We must spawn a separate thread to not block current thread,
      // since that would have blocked the shutdown of the ActorSystem.
      new Thread {
        override def run(): Unit = {
          if (Try(Await.ready(system.whenTerminated, 10.seconds)).isFailure)
            System.exit(-1)
        }
      }.start()
    }
  }
}

