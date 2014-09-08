package com.github.clockfly

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object Client {
  def main(args: Array[String]): Unit = {
    startup()
  }

  def startup(): Unit = {
    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + 0)
      .withFallback(ConfigFactory.load())

    // Create an Akka system
    val system = ActorSystem("client", config)
    val role = "client"

    val master = system.actorSelection("akka.tcp://system@127.0.0.1:2551/user/singleton/masterwatcher/master")

    master ! SubmitApplication

    master ! SubmitApplication

    system.awaitTermination()
  }

}
