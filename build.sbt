
organization := "com.github.scredis"

name := "scredis"

scalaVersion := "2.12.3"
crossScalaVersions := Seq("2.11.11", "2.12.3")

scalacOptions ++= Seq("-deprecation")

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
  "com.typesafe" % "config" % "1.3.1",
  "com.typesafe.akka" %% "akka-actor" % "2.5.3",
  "org.scalatest" %% "scalatest" % "3.0.3" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.5" % "test",
  "com.storm-enroute" %% "scalameter" % "0.8.2" % "test",
  "org.slf4j" % "slf4j-simple" % "1.7.25" % "test"
)

// required so that test actor systems don't get messed up
fork in Test := true

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }
licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))

publishTo := {
  val artifactory = "http://artifactory.service.iad1.consul:8081/artifactory/"
  val (name, url) = if (version.value.contains("-SNAPSHOT"))
    ("Artifactory Realm", artifactory + "libs-snapshot;build.timestamp=" + new java.util.Date().getTime)
  else
    ("Artifactory Realm", artifactory + "libs-release;build.timestamp=" + new java.util.Date().getTime)
  Some(Resolver.url(name, new URL(url)))
}

scalacOptions += "-feature"

pomExtra :=
  <url>https://github.com/scredis/scredis</url>
  <licenses>
    <license>
      <name>The Apache Software License, Version 2.0</name>
      <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/scredis/scredis.git</url>
    <connection>scm:https://github.com/scredis/scredis.git</connection>
  </scm>
  <developers>
    <developer>
      <id>curreli</id>
      <name>Alexandre Curreli</name>
      <url>https://github.com/curreli</url>
    </developer>
  </developers>

parallelExecution in Test := false

testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework")

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

