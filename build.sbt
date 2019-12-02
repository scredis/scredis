
name := "scredis"
organization := "com.github.scredis"

scalaVersion := "2.13.1"
crossScalaVersions := Seq("2.11.12", "2.12.10", scalaVersion.value)

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-Xfatal-warnings",
  "-Ywarn-dead-code"
)

scalacOptions in (Compile,doc) := Seq("-no-link-warnings")
autoAPIMappings := true

enablePlugins(BuildInfoPlugin)
enablePlugins(GhpagesPlugin)
enablePlugins(SiteScaladocPlugin)

buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion)
buildInfoPackage := "scredis"
buildInfoOptions += BuildInfoOption.BuildTime

git.remoteRepo := "git@github.com:scredis/scredis.git"

val akkaV = "2.5.26"
val loggingV = "3.9.2"
val configV = "1.4.0"
val collectionCompatV = "2.1.2"

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % loggingV,
  "com.typesafe" % "config" % "1.3.3",
  "com.typesafe.akka" %% "akka-actor" % akkaV,

  "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatV,

  "org.scalatest" %% "scalatest" % "3.1.0" % Test,
  "org.scalatestplus" %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test,
  "org.scalacheck" %% "scalacheck" % "1.14.2" % Test,
//  "com.storm-enroute" %% "scalameter" % "0.8.2" % Test,  /* only used for ClientBenchmark testing */
  "org.slf4j" % "slf4j-simple" % "1.7.29" % Test
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

// Documentation
enablePlugins(ParadoxPlugin)
paradoxTheme := Some(builtinParadoxTheme("generic"))
