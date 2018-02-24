package com.humanity.cluster

import java.util.concurrent.{TimeUnit, TimeoutException}

import akka.actor.{ActorNotFound, ActorRef, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent.{CurrentClusterState, MemberUp}
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.{ImplicitSender, TestProbe}
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.{Await, TimeoutException}
import scala.util.{Failure, Success}


object DeckSpecConfig extends MultiNodeConfig {
  val deck = role("deck")
  val player1 = role("player1")
  val player2 = role("player2")

  def nodeList = Seq(deck,player1,player2)

  nodeList foreach { role =>
    nodeConfig(role) {
      ConfigFactory.parseString(s"""
      # Enable metrics extension in akka-cluster-metrics.
      akka.extensions=["akka.cluster.metrics.ClusterMetricsExtension"]
      # Sigar native library extract location during tests.
      akka.cluster.metrics.native-library-extract-folder=target/native/${role.name}
      """)
    }
  }

  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = cluster
    # not using Artery in test due small /dev/shm in Travis
    akka.remote.artery.enabled = off
    """))

  nodeConfig(deck)(ConfigFactory.parseString("akka.cluster.roles=[deck]").withFallback(ConfigFactory.load("game")))
  nodeConfig(player1, player2)(
    ConfigFactory.parseString("akka.cluster.roles=[player]").withFallback(ConfigFactory.load("game")))
}

class DeckSpecMultiJvmNode1 extends DeckSpec
class DeckSpecMultiJvmNode2 extends DeckSpec
class DeckSpecMultiJvmNode3 extends DeckSpec

class DeckSpec extends MultiNodeSpec(DeckSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender  {

  import DeckSpecConfig._


  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  val deckAddress = node(deck).address
  val player1Address = node(player1).address
  val player2Address = node(player2).address

  "The deck" must {

    "illustrate how to start it" in within(2 seconds) {
      runOn(deck) {
        Cluster(system) join deckAddress
        val deckActor = getOrCreateDeckActor.get
        deckActor ! RestartGame

        deckActor ! DeckQuestion(null)
        import scala.concurrent.duration._
        expectMsgClass(1.seconds, classOf[Question])

        Cluster(system).leave(deckAddress)
      }
      testConductor.enter("deck-started")
    }

    "answer with as many questions as it has left" in within(15 seconds) {
      runOn(deck) {
        Cluster(system) join deckAddress
        val deckActor = getOrCreateDeckActor.get
        deckActor ! RestartGame

        1 to totalNumberOfQuestions() foreach  {
          _ =>
            deckActor ! DeckQuestion(null)
            import scala.concurrent.duration._
            expectMsgClass(1.seconds, classOf[Question])
        }

        Cluster(system).leave(deckAddress)
      }
      testConductor.enter("deck-send-all-questions")
    }

    "answer with as many questions as it has left and then tell there is nothing left" in within(15 seconds) {
      runOn(deck) {
        Cluster(system) join deckAddress
        val deckActor = getOrCreateDeckActor.get
        deckActor ! RestartGame

        1 to totalNumberOfQuestions() foreach  {
          _ =>
            deckActor ! DeckQuestion(null)
            import scala.concurrent.duration._
            expectMsgClass(1.seconds, classOf[Question])
        }
        deckActor ! DeckQuestion(null)
        import scala.concurrent.duration._
        expectMsgClass(1.seconds, classOf[NoQuestionsLeft])

        Cluster(system).leave(deckAddress)
      }
      testConductor.enter("deck-send-all-questions-and-answer-its-out")
    }

    "answer with as many answers as it has left" in within(15 seconds) {
      runOn(deck) {
        Cluster(system) join deckAddress
        val deckActor = getOrCreateDeckActor.get
        deckActor ! RestartGame

        val totals = totalNumberOfAnswers()
        val rem = totals % 2
        1 to Math.floor(totals/2).intValue foreach  {
          _ =>
            deckActor ! DeckAnswer(2)
            import scala.concurrent.duration._
            expectMsgClass(1.seconds, classOf[Answers])
        }
        if(rem>0) {
          deckActor ! DeckAnswer(rem)
          import scala.concurrent.duration._
          expectMsgClass(1.seconds, classOf[Answers])
        }

        Cluster(system).leave(deckAddress)
      }
      testConductor.enter("deck-send-all-questions")
    }

    "answer with as many answers as it has left and then tell there is nothing left" in within(15 seconds) {
      runOn(deck) {
        Cluster(system) join deckAddress
        val deckActor = getOrCreateDeckActor.get
        deckActor ! RestartGame

        val totals = totalNumberOfAnswers()
        val rem = totals % 2
        1 to Math.floor(totals/2).intValue foreach  {
          _ =>
            deckActor ! DeckAnswer(2)
            import scala.concurrent.duration._
            expectMsgClass(1.seconds, classOf[Answers])
        }
        if(rem>0) {
          deckActor ! DeckAnswer(rem)
          import scala.concurrent.duration._
          expectMsgClass(1.seconds, classOf[Answers])
        }

        deckActor ! DeckAnswer(2)
        import scala.concurrent.duration._
        expectMsgClass(1.seconds, classOf[NoAnswersLeft])

        Cluster(system).leave(deckAddress)
      }
      testConductor.enter("deck-send-all-questions")
    }

    "send as many answers as a player asks" in within(15 seconds) {
      runOn(player1,deck) {

        Cluster(system) join deckAddress
        val deckActor = getOrCreateDeckActor.get
        deckActor ! RestartGame

        Cluster(system) join player1Address
        val player = getOrCreatePlayerActor("1").get
        player ! RestartGame

        var answers:Map[Int,String] = Map()
        var totalAnswers = 0
        var stopAsking = false
        while(!stopAsking) {

          player ! StartGameRound(deckAddress)
          import scala.concurrent.duration._
          expectMsgPF(10.seconds) {
            case (answer: Answer) =>
              totalAnswers += 1
              answers += (answer.content.hashCode -> answer.content)
            case NoAnswersLeft() => stopAsking = true
          }
        }

        assert(answers.size==totalAnswers)

        Cluster(system).leave(deckAddress)
        Cluster(system).leave(player1Address)
      }
      testConductor.enter("player-get-answers")
    }

    "send as many answers as all players ask" in within(20 seconds) {
      runOn(player1,player2,deck) {

        Cluster(system) join deckAddress
        val deckActor = getOrCreateDeckActor.get
        deckActor ! RestartGame

        Cluster(system) join player1Address
        val player = getOrCreatePlayerActor("1").get
        player ! RestartGame

        Cluster(system) join player2Address
        val player2 = getOrCreatePlayerActor("1").get
        player2 ! RestartGame

        var answers:Map[Int,String] = Map()
        var totalAnswers = 0
        var stopAsking = false
        while(!stopAsking) {

          player ! StartGameRound(deckAddress)
          player2 ! StartGameRound(deckAddress)
          import scala.concurrent.duration._
          expectMsgPF(10.seconds) {
            case (answer: Answer) =>
              totalAnswers += 1
              answers += (answer.content.hashCode -> answer.content)
            case NoAnswersLeft() => stopAsking = true
          }
        }

        assert(answers.size==totalAnswers)

        Cluster(system).leave(deckAddress)
        Cluster(system).leave(player1Address)
        Cluster(system).leave(player2Address)
      }
      testConductor.enter("all-players-get-answers")
    }


  }

  def getOrCreateDeckActor(): Option[ActorRef] = {
    implicit val duration: Timeout = 200 millis
    val actorRefFuture = system.actorSelection("akka://" + system.name + "/user/deck").resolveOne()
    var ref:Option[ActorRef] = None
    try {
      ref = Some(Await.result(actorRefFuture,200.millis))
    } catch{
      case an:ActorNotFound => ref = Some(system.actorOf(Props[Deck], name = "deck"))
    }

    return ref
  }

  def getOrCreatePlayerActor(number:String): Option[ActorRef] = {
    implicit val duration: Timeout = 200 millis
    val actorRefFuture = system.actorSelection("akka://" + system.name + "/user/player"+number).resolveOne()
    var ref:Option[ActorRef] = None
    try {
      ref = Some(Await.result(actorRefFuture,200.millis))
    } catch{
      case an:ActorNotFound => ref = Some(system.actorOf(Props[Player], name = "player"+number))
    }

    return ref
  }

  def totalNumberOfQuestions(): Int = {
    DeckContents.questions.size
  }

  def totalNumberOfAnswers(): Int = {
    DeckContents.answers.size
  }

}
