package com.humanity.cluster

import akka.actor.{PoisonPill, Props}
import akka.cluster.Cluster
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender

import scala.concurrent.duration._
import scala.language.postfixOps
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}


object DeckSpecConfig extends MultiNodeConfig {
  val deck = role("deck")

  def nodeList = Seq(deck)

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
}

class DeckSpecMultiJvmNode1 extends DeckSpec

class DeckSpec extends MultiNodeSpec(DeckSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender  {

  import DeckSpecConfig._


  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  "The deck" must {

    "illustrate how to start it" in within(2 seconds) {
      runOn(deck) {
        Cluster(system) join node(deck).address
        val deckActor = system.actorOf(Props[Deck], name = "deck")
        deckActor ! DeckQuestion(null)
        import scala.concurrent.duration._
        expectMsgClass(1.seconds, classOf[Question])

        deckActor ! PoisonPill
      }
      testConductor.enter("deck-started")
    }

    "answer with as many questions as it has left" in within(15 seconds) {
      runOn(deck) {
        Cluster(system) join node(deck).address
        val deckActor = system.actorOf(Props[Deck], name = "deck")
        1 to totalNumberOfQuestions() foreach  {
          _ =>
            deckActor ! DeckQuestion(null)
            import scala.concurrent.duration._
            expectMsgClass(1.seconds, classOf[Question])
        }

        deckActor ! PoisonPill
      }
      testConductor.enter("deck-send-all-questions")
    }

    "answer with as many questions as it has left and then tell there is nothing left" in within(15 seconds) {
      runOn(deck) {
        Cluster(system) join node(deck).address
        val deckActor = system.actorOf(Props[Deck], name = "deck")
        1 to totalNumberOfQuestions() foreach  {
          _ =>
            deckActor ! DeckQuestion(null)
            import scala.concurrent.duration._
            expectMsgClass(1.seconds, classOf[Question])
        }
        deckActor ! DeckQuestion(null)
        import scala.concurrent.duration._
        expectMsgClass(1.seconds, classOf[NoQuestionsLeft])

        deckActor ! PoisonPill
      }
      testConductor.enter("deck-send-all-questions-and-answer-its-out")
    }

    "answer with as many answers as it has left" in within(15 seconds) {
      runOn(deck) {
        Cluster(system) join node(deck).address
        val deckActor = system.actorOf(Props[Deck], name = "deck")
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

        deckActor ! PoisonPill
      }
      testConductor.enter("deck-send-all-questions")
    }

    "answer with as many answers as it has left and then tell there is nothing left" in within(15 seconds) {
      runOn(deck) {
        Cluster(system) join node(deck).address
        val deckActor = system.actorOf(Props[Deck], name = "deck")
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

        deckActor ! PoisonPill
      }
      testConductor.enter("deck-send-all-questions")
    }

  }

  def totalNumberOfQuestions(): Int = {
    DeckContents.questions.size
  }

  def totalNumberOfAnswers(): Int = {
    DeckContents.answers.size
  }

}
