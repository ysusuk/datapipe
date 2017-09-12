import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.iuriisusuk",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "datapipe",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor"  % "2.4.14",
      "com.typesafe.akka" %% "akka-stream" % "2.4.14",
      "com.typesafe.akka" %% "akka-http"   % "10.0.10",
      scalaTest % Test
    )
  )
