
name := "scredis"
organization := "com.github.scredis"

scalaVersion := "2.13.7"
crossScalaVersions := Seq("2.11.12", "2.12.15", scalaVersion.value)

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

val akkaV = "2.5.32"
val loggingV = "3.9.4"
val configV = "1.4.0"
val collectionCompatV = "2.5.0"
val typesafeConfigV = "1.3.3"

libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % loggingV,
  "com.typesafe" % "config" % typesafeConfigV,
  "com.typesafe.akka" %% "akka-actor" % akkaV,

  "org.scala-lang.modules" %% "scala-collection-compat" % collectionCompatV,

  "org.scalatest" %% "scalatest" % "3.2.10" % Test,
  "org.scalatestplus" %% "scalacheck-1-14" % "3.2.2.0" % Test,
  "org.scalacheck" %% "scalacheck" % "1.15.2" % Test,
  "com.storm-enroute" %% "scalameter" % "0.19" % Test,
  "org.slf4j" % "slf4j-simple" % "1.7.32" % Test
)

Test / testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-F", sys.props.getOrElse("F", "1.0"))

// required so that test actor systems don't get messed up
Test / fork := true
Test / parallelExecution := false

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

val scalaMeterFramework = new TestFramework("org.scalameter.ScalaMeterFramework")
testFrameworks += scalaMeterFramework

concurrentRestrictions in Global += Tags.limit(Tags.Test, 1)

// Documentation
enablePlugins(ParadoxPlugin)
paradoxTheme := Some(builtinParadoxTheme("generic"))


// Scalameter Benchmark tests
lazy val Benchmark = config("bench") extend Test
configs(Benchmark)
inConfig(Benchmark)(Defaults.testSettings)
