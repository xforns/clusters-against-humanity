package com.humanity.cluster

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props, ReceiveTimeout, RootActorPath}
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent._
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try


class Czar(totalPlayers: Int) extends Actor with ActorLogging {

  var deck:Option[Address] = None
  var players = Set.empty[Address]
  var question:Option[Question] = None

  override def preStart(): Unit = {
    Cluster(context.system).subscribe(self, InitialStateAsEvents, classOf[MemberEvent])
  }

  def receive = {

    case RestartGame =>
      deck = None
      players = Set.empty[Address]
      question = None

    case (question: Question) =>
      this.question = Some(question)
      log.info("")
      log.info("Q: {}",question.content)
      tryStartGame()

    case (answer: Answer) =>
      log.info("A: {}",answer.content)

    case ReceiveTimeout =>
      question = None
      tryRetrieveQuestion()

    case NoQuestionsLeft() =>
      log.info("Stopping game (no more questions left)")
      tryStopGame()

    case NoAnswersLeft() =>
      log.info("No answers left for player")

    case MemberUp(member) =>
      log.debug("Member up: {}", member.address)
      updateDeckStatus(member,true)
      updatePlayersStatus(member,true)
      tryStartGame()

    case MemberRemoved(member, _) =>
      log.debug("Member removed: {}", member.address)
      updateDeckStatus(member,false)
      updatePlayersStatus(member,false)
      tryStopGame()
  }

  private def canGameRun(): Boolean = {
    !question.isEmpty && players.size==totalPlayers
  }

  private def updatePlayersStatus(member: Member, memberUp: Boolean): Unit = {
    if(!member.hasRole("player")) return
    memberUp match {
      case true => players += member.address
      case false => players -= member.address
    }
  }

  private def updateDeckStatus(member: Member, memberUp: Boolean): Unit = {
    if(!member.hasRole("deck")) return
    memberUp match {
      case true => {
        deck = Some(member.address)
        tryRetrieveQuestion()
      }
      case false => deck = None
    }
  }

  private def tryStartGame(): Unit = {
    if(!canGameRun()) return
    players.foreach(address => context.actorSelection(RootActorPath(address) / "user" / "player") ! StartGameRound(deck get) )
    context.setReceiveTimeout(5.seconds)
  }

  private def tryStopGame(): Unit = {
    context.setReceiveTimeout(Duration.Undefined)
  }

  private def tryRetrieveQuestion(): Unit = {
    if(deck.isEmpty) return
    log.debug("Retrieving question..")

    context.actorSelection(RootActorPath(deck get) / "user" / "deck") ! DeckQuestion("")
  }
}

object Czar {
  def main(args: Array[String]): Unit = {
    val systemName = AppArgs.systemName()

    val config = ConfigFactory.parseString("akka.cluster.roles = [czar]").
      withFallback(ConfigFactory.load("game"))

    val system = ActorSystem(systemName, config)
    system.log.info("Game will start when there is at least 1 czar, 1 deck and 1 player in the cluster.")

    Cluster(system) registerOnMemberUp {
      system.actorOf(Props(classOf[Czar],2), name = "czar")
    }

    Cluster(system) registerOnMemberRemoved {
      system.registerOnTermination(System.exit(0))
      system.terminate()
      new Thread {
        override def run(): Unit = {
          if (Try(Await.ready(system.whenTerminated, 10.seconds)).isFailure)
            System.exit(-1)
        }
      }.start()
    }
  }
}

