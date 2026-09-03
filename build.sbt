import sbtassembly.MergeStrategy

val scala3Version = "3.8.4"

val PekkoVersion = "1.1.5"
val PekkoHttpVersion = "1.3.0"
val kafkaClientsVersion = "3.9.0"
val circeVersion = "0.14.10"

lazy val root = project
  .in(file("."))
  .settings(
    name := "llm-service",
    version := "0.1.0-SNAPSHOT",

    scalaVersion := scala3Version,

    Compile / run / fork := true,

    libraryDependencies ++= Seq(
      "org.apache.kafka" % "kafka-clients" % kafkaClientsVersion,

      "org.apache.pekko" %% "pekko-actor-typed" % PekkoVersion,
      "org.apache.pekko" %% "pekko-stream" % PekkoVersion,
      "org.apache.pekko" %% "pekko-http" % PekkoHttpVersion,

      "io.circe" %% "circe-core" % circeVersion,
      "io.circe" %% "circe-parser" % circeVersion,
      "io.circe" %% "circe-generic" % circeVersion,

      "ch.qos.logback" % "logback-classic" % "1.6.0"
    ),

    assembly / mainClass := Some("Main"),
    assembly / assemblyMergeStrategy := {
      case "reference.conf"                      => MergeStrategy.concat
      case "application.conf"                    => MergeStrategy.concat
      case PathList("META-INF", "services", _*)  => MergeStrategy.concat
      case PathList("META-INF", _*)              => MergeStrategy.discard
      case _                                     => MergeStrategy.first
    }
  )
