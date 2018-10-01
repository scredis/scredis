
name := "scredis"
organization := "com.github.scredis"

scalaVersion := "2.12.7"
crossScalaVersions := Seq("2.11.12", scalaVersion.value)

scalacOptions ++= Seq("-deprecation", "-feature", "-Xfatal-warnings",
  "-Ywarn-dead-code", "-Ywarn-infer-any", "-Ywarn-unused-import")

scalacOptions in (Compile,doc) := Seq("-no-link-warnings")
autoAPIMappings := true

enablePlugins(BuildInfoPlugin)
enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "scredis"
buildInfoOptions += BuildInfoOption.BuildTime

git.remoteRepo := "git@github.com:scredis/scredis.git"

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
  "com.typesafe" % "config" % "1.3.3",
  "com.typesafe.akka" %% "akka-actor" % "2.5.17",

  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.scalacheck" %% "scalacheck" % "1.14.0" % Test,
  "com.storm-enroute" %% "scalameter" % "0.8.2" % Test,
  "org.slf4j" % "slf4j-simple" % "1.7.25" % Test
)

Test / testOptions += Tests.Argument("-F", sys.props.getOrElse("F", "1.0"))

// required so that test actor systems don't get messed up
fork in Test := true

publishMavenStyle := true
publishArtifact in Test := false
pomIncludeRepository := { _ => false }

licenses := Seq(("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")))

scmInfo := Some(ScmInfo(
  url("https://github.com/scredis/scredis"),
  "scm:git@github.com:scredis/scredis.git"
))

homepage := Some(url("https://github.com/scredis/scredis"))

developers := List(
  Developer(
    id="kpbochenek",
    name="kpbochenek",
    email="kpbochenek@gmail.com",
    url=url("https://github.com/kpbochenek")
  ))

bintrayVcsUrl := Some("https://github.com/scredis/scredis.git")
bintrayOrganization := Some("scredis")
bintrayRepository := "maven"
bintrayPackageLabels := Seq("redis")

parallelExecution in Test := false

testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework")

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)
