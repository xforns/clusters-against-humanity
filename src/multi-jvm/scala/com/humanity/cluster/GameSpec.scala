package com.humanity.cluster

import akka.actor.Props
import akka.cluster.Cluster
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps

object GameSpecConfig extends MultiNodeConfig {

  val deck = role("deck")
  val player1 = role("player1")
  val player2 = role("player2")
  val czar = role("czar")

  def nodeList = Seq(deck, player1, player2, czar)

  // Extract individual sigar library for every node.
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

  // this configuration will be used for all nodes
  // note that no fixed host names and ports are used
  commonConfig(ConfigFactory.parseString("""
    akka.actor.provider = cluster
    # not using Artery in test due small /dev/shm in Travis
    akka.remote.artery.enabled = off
    """))

  nodeConfig(deck)(ConfigFactory.parseString("akka.cluster.roles=[deck]").withFallback(ConfigFactory.load("game")))

  nodeConfig(player1, player2)(
    ConfigFactory.parseString("akka.cluster.roles=[player]").withFallback(ConfigFactory.load("game")))

  nodeConfig(czar)(ConfigFactory.parseString("akka.cluster.roles=[czar]").withFallback(ConfigFactory.load("game")))
}

class GameSpecMultiJvmNode1 extends GameSpec
class GameSpecMultiJvmNode2 extends GameSpec
class GameSpecMultiJvmNode3 extends GameSpec
class GameSpecMultiJvmNode4 extends GameSpec

abstract class GameSpec extends MultiNodeSpec(GameSpecConfig)
  with WordSpecLike with Matchers with BeforeAndAfterAll with ImplicitSender {

  import GameSpecConfig._

  override def initialParticipants = roles.size

  override def beforeAll() = multiNodeSpecBeforeAll()

  override def afterAll() = multiNodeSpecAfterAll()

  "The game" must {

    "illustrate how to start the deck" in within(4 seconds) {
      runOn(deck) {
        Cluster(system) join node(deck).address
        val deckActor = system.actorOf(Props[Deck], name = "deck")
        deckActor ! DeckQuestion(null)
        import scala.concurrent.duration._
        expectMsgClass(2.seconds, classOf[Question])
      }
      testConductor.enter("deck-started")
    }

    "illustrate how to start the first player" in within(4 seconds) {
      runOn(player1) {
        Cluster(system) join node(player1).address
        val player = system.actorOf(Props[Player], name = "player")
        player ! StartGameRound(null)
        import scala.concurrent.duration._
        expectNoMessage(2.seconds)
      }
      testConductor.enter("player1-started")
    }

    "illustrate how the czar automatically registers" in within(4 seconds) {
      runOn(czar) {
        Cluster(system) join node(player1).address
        system.actorOf(Props(classOf[Czar],2), name = "czar")
      }
      testConductor.enter("czar-started")

      runOn(player1) {
        assertServiceOk()
      }

      testConductor.enter("player-czar-ok")
    }

    "illustrate how more nodes register" in within(4 seconds) {
      runOn(player2) {
        Cluster(system) join node(player1).address
        system.actorOf(Props[Player], name = "player")
      }
      testConductor.enter("player2-started")
      testConductor.enter("all-started")

      runOn(player1, player2) {
        assertServiceOk()
      }
      testConductor.enter("all-ok")
    }

  }

  def assertServiceOk(): Unit = {
    val player = system.actorSelection("akka://" + system.name + "/user/player")
    awaitAssert {
      player ! StartGameRound(null)
      import scala.concurrent.duration._
      expectNoMessage(2.seconds)
    }
  }

}
