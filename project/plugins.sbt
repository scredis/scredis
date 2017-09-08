externalResolvers ++= Seq (
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/",
  "Artifactory Releases"  at "http://artifactory.service.iad1.consul:8081/artifactory/libs-release/",
  "Artifactory Snapshots" at "http://artifactory.service.iad1.consul:8081/artifactory/libs-snapshot/"
)

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.0.0")
addSbtPlugin("com.typesafe.sbt" % "sbt-ghpages" % "0.5.4")
