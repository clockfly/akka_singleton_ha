package com.github.clockfly

import akka.actor._
import akka.cluster.Cluster
import akka.contrib.pattern.ClusterSingletonManager
import com.typesafe.config.ConfigFactory

class Master extends Actor with ActorLogging {

  log.info(s"Starting singleton master....")

  override def receive: Actor.Receive = {
    case _=>
  }

  override def postStop() : Unit = {
    log.info("Stopping master....")
  }
}

object Master {
  def main(args: Array[String]): Unit = {
    startup(args.headOption.getOrElse("0"))
  }

  def startup(port: String): Unit = {

    val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port)
      .withFallback(ConfigFactory.load())

    // Create an Akka system
    val system = ActorSystem("system", config)

    val singleton = ClusterSingletonManager.props(
      singletonProps = Props(classOf[Master]),
      singletonName = "master",
      terminationMessage = PoisonPill,
      role = None
    )

    system.actorOf(singleton, "singleton")

    val mainThread = Thread.currentThread();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      override def run() : Unit = {
        System.out.println("Triggering shutdown hook....")
        val cluster = Cluster(system)
        cluster.leave(cluster.selfAddress)
        //sleep for a interval so that the leave request is listened before we shutdown ourself.
        Thread.sleep(3000)
        system.shutdown()
        mainThread.join();
      }
    });

    system.awaitTermination()
  }
}