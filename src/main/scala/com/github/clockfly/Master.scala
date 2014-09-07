package com.github.clockfly

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, Props, _}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.contrib.pattern.ClusterSingletonManager
import com.typesafe.config.ConfigFactory

import scala.collection.immutable
import scala.concurrent.duration._

class Master extends Actor with ActorLogging {

  log.info(s"Starting Master....")

  override def receive: Actor.Receive = {
    case _=>
  }

  override def postStop() : Unit = {
    log.info("Stopping master....")
  }
}

object Shutdown

class MasterWatcher(role: String) extends Actor  with ActorLogging {
  import context.dispatcher

  val cluster = Cluster(context.system)

  val config = context.system.settings.config
  val masters = config.getList("akka.cluster.seed-nodes")
  val quorum = masters.size() / 2 + 1

  val system = context.system

  // sort by age, oldest first
  val ageOrdering = Ordering.fromLessThan[Member] { (a, b) => a.isOlderThan(b) }
  var membersByAge: immutable.SortedSet[Member] = immutable.SortedSet.empty(ageOrdering)

  def receive : Receive = null

  // subscribe to MemberEvent, re-subscribe when restart
  override def preStart(): Unit = {
    cluster.subscribe(self, classOf[MemberEvent])
    context.become(waitForInit)
  }
  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  def matchingRole(member: Member): Boolean = member.hasRole(role)

  def waitForInit : Receive = {
    case state: CurrentClusterState => {
      membersByAge = immutable.SortedSet.empty(ageOrdering) ++ state.members.filter(m =>
        m.status == MemberStatus.Up && matchingRole(m))

      if (membersByAge.size < quorum) {
        membersByAge.iterator.mkString(",")
        log.info(s"We cannot get a quorum, $quorum, shutting down...${membersByAge.iterator.mkString(",")}")
        context.become(waitForShutdown)
        self ! Shutdown
      } else {
        context.actorOf(Props(classOf[Master]), "master")
        context.become(waitForClusterEvent)
      }
    }
  }

  def waitForClusterEvent : Receive = {
    case MemberUp(m) if matchingRole(m)  => {
      membersByAge += m
    }
    case mEvent: MemberEvent if (mEvent.isInstanceOf[MemberExited] || mEvent.isInstanceOf[MemberRemoved]) && matchingRole(mEvent.member) => {
      log.info(s"member removed ${mEvent.member}")
      val m = mEvent.member
      membersByAge -= m
      if (membersByAge.size < quorum) {
        log.info(s"We cannot get a quorum, $quorum, shutting down...${membersByAge.iterator.mkString(",")}")
        context.become(waitForShutdown)
        self ! Shutdown
      }
    }
  }

  def waitForShutdown : Receive = {
    case Shutdown => {
      cluster.unsubscribe(self)
      cluster.leave(cluster.selfAddress)
      context.stop(self)
      system.scheduler.scheduleOnce(Duration.Zero) {
        try {
          system.awaitTermination(Duration(3, TimeUnit.SECONDS))
        } catch {
          case ex : Exception => //ignore
        }
        system.shutdown()
      }
    }
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
    val role = "master"

    val singleton = system.actorOf(ClusterSingletonManager.props(
      singletonProps = Props(classOf[MasterWatcher], role),
      singletonName = "masterwatcher",
      terminationMessage = PoisonPill,
      role = Some(role)
    ), "singleton")

    val mainThread = Thread.currentThread();
    Runtime.getRuntime().addShutdownHook(new Thread() {
      override def run() : Unit = {
        if (!system.isTerminated) {
         System.out.println("Triggering shutdown hook....")
          val cluster = Cluster(system)
          cluster.leave(cluster.selfAddress)
          try {
            system.awaitTermination(Duration(3, TimeUnit.SECONDS))
          } catch {
            case ex : Exception => //ignore
          }
          system.shutdown()
          mainThread.join();
        }
      }
    });

    system.awaitTermination()
  }
}