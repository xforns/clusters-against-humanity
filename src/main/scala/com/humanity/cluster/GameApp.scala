package com.humanity.cluster

object GameApp {

  def main(args : Array[String]): Unit = {
    Deck.main(Seq("2551").toArray)
    Player.main(Seq("2552").toArray)
    Player.main(Seq("2553").toArray)
    Czar.main(Array.empty)
  }

}