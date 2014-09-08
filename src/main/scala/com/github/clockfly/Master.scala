package com.github.clockfly

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorLogging, Props, _}
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member, MemberStatus}
import akka.contrib.datareplication.Replicator._
import akka.contrib.pattern.ClusterSingletonManager
import com.typesafe.config.ConfigFactory

import scala.collection.immutable
import scala.concurrent.duration._

import scala.concurrent.duration._
import akka.actor.ActorLogging
import akka.cluster.Cluster
import akka.actor.Actor
import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import akka.actor.Props
import akka.contrib.datareplication.{GSet, DataReplication}
import scala.collection.JavaConverters._


case object SubmitApplication
case class ApplicationState(appId : Int, attemptId : Int, state : Any)


class Master extends Actor with ActorLogging with Stash {

  val STATE = "masterstate"
  val TIMEOUT = Duration(5, TimeUnit.SECONDS)
  log.info(s"Starting Master....")

  val replicator = DataReplication(context.system).replicator
  var state : Set[ApplicationState] = Set.empty[ApplicationState]
  var nextAppId = 0

  replicator ! Subscribe(STATE, self)


  override  def preStart : Unit = {
    replicator ! new Get(STATE, ReadTwo, TIMEOUT, None)
    log.info("Recoving application state....")
    context.become(waitForMasterState)
  }

  def waitForMasterState : Receive = {
    case GetSuccess(_, replicatedState : GSet, _) =>
      state = replicatedState.getValue().asScala.foldLeft(state) { (set, appState) =>
        set + appState.asInstanceOf[ApplicationState]
      }
      nextAppId = state.size
      log.info(s"Successfully recoeved states ${state}, nextAppId: ${nextAppId}....")
      context.become(waitForCommand)
      unstashAll()
    case x : GetFailure =>
      log.info("GetFailure We cannot find any exisitng state, start a fresh one...")
      context.become(waitForCommand)
      unstashAll()
    case x : NotFound =>
      log.info("We cannot find any exisitng state, start a fresh one...")
      context.become(waitForCommand)
      unstashAll()
    case msg =>
      log.info(s"Get information ${msg.getClass.getSimpleName} $msg")
      stash()
  }

  def waitForCommand : Receive = {
    case SubmitApplication =>

      log.info(s"submit new applicatin, new appId : $nextAppId")
      val appId = nextAppId
      nextAppId += 1

      replicator ! Update(STATE, GSet(), WriteTwo, TIMEOUT)(_ + ApplicationState(appId, 0, null))

    case update: UpdateResponse => log.info(s"we get update $update")

    case Changed(STATE, data: GSet) =>
      log.info("Current elements: {}", data.value)
  }

  override def receive: Actor.Receive = null

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