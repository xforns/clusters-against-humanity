package com.humanity.cluster

import akka.actor.{ActorNotFound, ActorRef, Props}
import akka.cluster.Cluster
import akka.remote.testkit.{MultiNodeConfig, MultiNodeSpec}
import akka.testkit.ImplicitSender
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
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

  val deckAddress = node(deck).address
  val player1Address = node(player1).address
  val player2Address = node(player2).address
  val czarAddress = node(czar).address

  "The game" must {

    "illustrate how to start the deck" in within(4 seconds) {
      runOn(deck) {
        Cluster(system) join deckAddress
        val deckActor = getOrCreateDeckActor.get
        deckActor ! RestartGame
        deckActor ! DeckQuestion(null)

        import scala.concurrent.duration._
        expectMsgClass(2.seconds, classOf[Question])

        Cluster(system).leave(deckAddress)
      }
      testConductor.enter("deck-started")
    }

    "illustrate how to start the first player" in within(4 seconds) {
      runOn(player1) {
        Cluster(system) join player1Address
        val player = getOrCreatePlayerActor("1").get
        player ! RestartGame
        player ! StartGameRound(null)

        import scala.concurrent.duration._
        expectNoMessage(2.seconds)

        Cluster(system).leave(player1Address)
      }
      testConductor.enter("player1-started")
    }

    "illustrate how the czar automatically registers" in within(4 seconds) {
      runOn(czar) {
        Cluster(system) join czarAddress
        getOrCreateCzarActor().get
      }
      testConductor.enter("czar-started")

      runOn(player1) {
        val player = getOrCreatePlayerActor("1").get
        player ! RestartGame
        player ! StartGameRound(null)

        import scala.concurrent.duration._
        expectNoMessage(2.seconds)

        Cluster(system).leave(czarAddress)
      }

      testConductor.enter("player-czar-ok")
    }

    "illustrate how more nodes register" in within(4 seconds) {
      runOn(player1) {
        Cluster(system) join player1Address
        getOrCreatePlayerActor("1").get
      }
      testConductor.enter("player1-entered")

      runOn(player2) {
        Cluster(system) join player2Address
        getOrCreatePlayerActor("2").get
      }
      testConductor.enter("player2-entered")

      runOn(player1,player2) {
        val player = getOrCreatePlayerActor("1").get

        val player2 = getOrCreatePlayerActor("2").get

        player ! RestartGame
        player ! StartGameRound(null)

        import scala.concurrent.duration._
        expectNoMessage(2.seconds)

        player2 ! RestartGame
        player2 ! StartGameRound(null)

        //import scala.concurrent.duration._
        //expectNoMessage(2.seconds)

        Cluster(system).leave(player1Address)
        Cluster(system).leave(player2Address)
      }
      testConductor.enter("all-ok")
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

  def getOrCreateCzarActor(): Option[ActorRef] = {
    implicit val duration: Timeout = 200 millis
    val actorRefFuture = system.actorSelection("akka://" + system.name + "/user/czar").resolveOne()
    var ref:Option[ActorRef] = None
    try {
      ref = Some(Await.result(actorRefFuture,200.millis))
    } catch{
      case an:ActorNotFound => ref = Some(system.actorOf(Props(classOf[Czar],2), name = "czar"))
    }

    return ref
  }

}
