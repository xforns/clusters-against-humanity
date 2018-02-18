package com.humanity.cluster

import akka.actor.{Actor, ActorLogging, ActorSystem, Address, Props, ReceiveTimeout, RootActorPath}
import akka.cluster.{Cluster, Member}
import akka.cluster.ClusterEvent._
import akka.routing.FromConfig
import akka.pattern.{ask,pipe}
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try


class Czar(repeat: Boolean, totalPlayers: Int) extends Actor with ActorLogging {

  import context.dispatcher

  val player = context.actorOf(FromConfig.props(), name = "playerRouter")
  var deck:Option[Address] = None
  var playersUp = Set.empty[Address]
  var questions:Seq[Question] = Seq.empty[Question]



  override def preStart(): Unit = {
    Cluster(context.system).subscribe(self, InitialStateAsEvents, classOf[MemberEvent])
  }

  private def canGameRun(): Boolean = {
    !questions.isEmpty && playersUp.size==totalPlayers
  }

  private def updatePlayersStatus(member: Member, memberUp: Boolean): Unit = {
    if(!member.hasRole("player")) return
    memberUp match {
      case true => playersUp += member.address
      case false => playersUp -= member.address
    }
  }

  private def updateDeckStatus(member: Member): Unit = {
    if(member.hasRole("deck")) deck = Some(member.address)
  }

  private def tryStartGame(): Unit = {
    if(!canGameRun()) return
    player ! StartGame
    if (repeat) {
      context.setReceiveTimeout(10.seconds)
    }
  }

  private def tryStopGame(): Unit = {
    if(!canGameRun()) return
    context.setReceiveTimeout(Duration.Undefined)
  }

  def receive = {

    case (questions: Questions) =>
      this.questions = questions.obj
      tryStartGame()

    case (playersReady: PlayersReady) =>
      questions foreach { player ! _ }

    case (answer: Answer) =>
      log.info("{}",answer.content)
      /*answers += answer.content
      if (answers.size == questions.size) {
        if (repeat) sendQuestions()
        else context.stop(self)
      }*/
    case ReceiveTimeout =>
      log.info("Timeout")
      //retrieveQuestions()

    case MemberUp(member) =>
      log.debug("Member up: {}", member.address)
      updateDeckStatus(member)
      updatePlayersStatus(member,true)
      tryRetrieveQuestions()
      tryStartGame()

    case MemberRemoved(member, _) =>
      log.debug("Member removed: {}", member.address)
      updateDeckStatus(member)
      updatePlayersStatus(member,false)
      tryStopGame()
  }

  def tryRetrieveQuestions(): Unit = {
    if(deck.isEmpty) return
    log.info("Retrieving questions..")

    context.actorSelection(RootActorPath(deck get) / "user" / "deck") ! DeckQuestion(totalPlayers)
  }
}

object Czar {
  def main(args: Array[String]): Unit = {

    val config = ConfigFactory.parseString("akka.cluster.roles = [czar]").
      withFallback(ConfigFactory.load("game"))

    val system = ActorSystem("ClusterSystem", config)
    system.log.info("Game will start when there is at least 1 czar and 2 players in the cluster.")

    Cluster(system) registerOnMemberUp {
      system.actorOf(Props(classOf[Czar],true,3), name = "czar")
    }

    Cluster(system) registerOnMemberRemoved {
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

