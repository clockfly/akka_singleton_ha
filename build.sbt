import sbt.Keys._
import sbt._
import xerial.sbt.Pack._

name := "akka_cluster_ha"

version := "0.1-SNAPSHOT"

organization := "com.github.clockfly"

packSettings

packMain := Map("main" -> "com.github.clockfly.Master",
                "client" -> "com.github.clockfly.Client")

val akkaVersion = "2.3.5"

val scalaVersion = "2.10.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-remote" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-contrib" % akkaVersion,
  "com.typesafe.akka" %% "akka-agent" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.github.patriknw" %% "akka-data-replication" % "0.4")

resolvers ++= Seq(
  "maven-repo" at "http://repo.maven.apache.org/maven2",
  "maven1-repo" at "http://repo1.maven.org/maven2",
  "apache-repo" at "https://repository.apache.org/content/repositories/releases",
  "jboss-repo" at "https://repository.jboss.org/nexus/content/repositories/releases",
  "mqtt-repo" at "https://repo.eclipse.org/content/repositories/paho-releases",
  "cloudera-repo" at "https://repository.cloudera.com/artifactory/cloudera-repos",
  "mapr-repo" at "http://repository.mapr.com/maven",
  "spring-releases" at "http://repo.spring.io/libs-release",
  "clockfly" at "http://dl.bintray.com/clockfly/maven"
)


