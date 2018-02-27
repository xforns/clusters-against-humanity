# Clusters Against Humanity

Pet project that implements Akka Cluster stack to automatically play the game of Cards Against Humanity.
Completely based off [https://github.com/akka/akka-samples/tree/2.5/akka-sample-cluster-scala](akka-sample-cluster-scala)

## Prerequisites

Based on Scala 2.12.4 and sbt 0.13.13. Uses Akka 2.5.8.

## Setup

Following environment variables are required on runtime:

```
actorSystemName: name of the cluster system
```

## Running

There are 2 ways to execute this:

* Single terminal window running the predetermined actors within the cluster (as specified by App.scala):
```
sbt "runMain com.humanity.cluster.App"
```

* Multiple terminal windows running one actor per window (preferred!):

```
sbt "runMain com.humanity.cluster.Deck 2551"
sbt "runMain com.humanity.cluster.Player 2552"
sbt "runMain com.humanity.cluster.Player 2553"
sbt "runMain com.humanity.cluster.Czar 0"
```

Add as many players as you want, but only one Czar and one Deck should exist! (It is not enforced.)
To stop any of the nodes, Ctrl+c should do it.

## Deployment

* This project uses a Docker image found in `xforns/cah-openjdk`
 


### Using (or publishing) your own image

If you want to make modifications and use your own base image, you can either build it locally: `docker build -t local/<image-name>:latest - < Dockerfile`
Or build it and push it to your own Hub repository:
* Build: `docker build -t <your-hub-username>/<image-name> - < Dockerfile`
* Push: `docker push <your-hub-username>/<image-name>`

Changes required in other parts of this project:
* In `build.sbt`, modify dockerRepository with your group, and dockerBaseImage with the <image-name> of the base image you created, otherwise comment dockerBaseImage
* In `kube-config.yaml`, modify the image name in the StatefulSet resource


## Testing

Test can be run with `sbt multi-jvm:test`.
