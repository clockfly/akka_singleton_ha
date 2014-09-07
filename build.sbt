import com.typesafe.sbt.SbtMultiJvm
import sbt._
import sbt.Keys._
import xerial.sbt.Pack._

packSettings

packMain := Map("main" -> "com.github.clockfly.Master")

val akkaVersion = "2.3.5"

val project = Project(
  id = "akka-sample-cluster-scala",
  base = file("."),
  settings = Project.defaultSettings ++ SbtMultiJvm.multiJvmSettings ++ Seq(
    name := "akka-sample-cluster-scala",
    version := "1.0",
    scalaVersion := "2.10.4",
    scalacOptions in Compile ++= Seq("-encoding", "UTF-8", "-target:jvm-1.6", "-deprecation", "-feature", "-unchecked", "-Xlog-reflective-calls", "-Xlint"),
    javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6", "-Xlint:unchecked", "-Xlint:deprecation"),
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
      "com.typesafe.akka" %% "akka-contrib" % akkaVersion
    )
  )
)

