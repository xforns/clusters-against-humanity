# Clusters Against Humanity

Pet project that implements Akka Cluster stack to automatically play the game of Cards Against Humanity.
Completely based off [https://github.com/akka/akka-samples/tree/2.5/akka-sample-cluster-scala](akka-sample-cluster-scala)

## Prerequisites

Based on Scala 2.12.4 and sbt 0.13.13. Uses Akka 2.5.8.

## Running

There are 2 ways to execute this:

* Single terminal window running the predetermined actors within the cluster (as specified by App.scala):
```
sbt "runMain com.humanity.cluster.App"
```

* Multiple terminal windows running one actor per window (preferred!):

```
sbt "runMain com.humanity.cluster.Player 2551"
sbt "runMain com.humanity.cluster.Player 2552"
sbt "runMain com.humanity.cluster.Czar 0"
```

Add as many players as you want, but only one Czar can exist!
To stop any of the actors, Ctrl+c should do it.

## Testing

Test can be run with `sbt multi-jvm:test`.
