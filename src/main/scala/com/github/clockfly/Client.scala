package com.github.clockfly

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

object Client {
  def main(args: Array[String]): Unit = {

    System.out.println("Trigger an application submission. \n" +
      "Client <ip of master> <port of master node>")

    val ip = args(0)
    val port = args(1).toInt
    startup(ip, port)
  }

  def startup(ip : String, port : Int): Unit = {
    val config = ConfigFactory.parseString(
      """
        |akka.remote.netty.tcp.port=0
        |akka.cluster.roles=["client"]
        |""".stripMargin)
      .withFallback(ConfigFactory.load())

    // Create an Akka system
    val system = ActorSystem("client", config)

    val master = system.actorSelection(s"akka.tcp://system@$ip:$port/user/singleton/masterwatcher/master")

    master ! SubmitApplication
    System.out.println(s"Submitting new applicatin to master ${master.pathString}")

    Thread.sleep(1000) //sleep 1s
    system.shutdown()
  }

}
