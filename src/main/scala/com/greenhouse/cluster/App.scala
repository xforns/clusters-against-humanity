package com.greenhouse.cluster

object App {

  def main(args : Array[String]): Unit = {
    Player.main(Seq("2551").toArray)
    Player.main(Seq("2552").toArray)
    Czar.main(Array.empty)
  }

}
