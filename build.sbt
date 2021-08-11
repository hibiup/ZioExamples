
// DEPENDENCIES
val ver = new{
    val logback = "1.2.3"
    val scalaLogging = "3.9.2"
    val scalaTest = "3.1.1"
    val zio = "1.0.7"
    val zioInteropCats = "2.4.1.0"
    val doobie = "0.13.4"
    val http4s = "0.21.4"
    val config = "1.4.0"
    val circe = "0.13.0"
    val akka = "2.6.5"
    val akkaHttp = "10.1.12"
}

lazy val testing = Seq(
    "org.scalatest" %% "scalatest" % ver.scalaTest,
    "dev.zio" %% "zio-test" % ver.zio
)

lazy val logging = Seq(
    "ch.qos.logback" % "logback-classic" % ver.logback,
    "com.typesafe.scala-logging" %% "scala-logging" % ver.scalaLogging
)

lazy val config = Seq(
    "com.typesafe" % "config" % ver.config
)

lazy val zio = Seq(
    "dev.zio" %% "zio" % ver.zio,
    "dev.zio" %% "zio-streams" % ver.zio,
    "dev.zio" %% "zio-interop-cats" % ver.zioInteropCats
)

lazy val jdbc = Seq(
    "org.tpolecat" %% "doobie-core" % ver.doobie,
    "org.tpolecat" %% "doobie-h2" % ver.doobie,
    "org.tpolecat" %% "doobie-hikari" % ver.doobie
)

lazy val http4s = Seq(
    "org.http4s" %% "http4s-blaze-server" % ver.http4s,
    "org.http4s" %% "http4s-circe" % ver.http4s,
    "org.http4s" %% "http4s-dsl" % ver.http4s
)

lazy val akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % ver.akka,
    "com.typesafe.akka" %% "akka-http" % ver.akkaHttp,
    "com.typesafe.akka" %% "akka-stream" % ver.akka,
    "de.heikoseeberger" %% "akka-http-circe" % "1.31.0"
)

lazy val circe = Seq(
    // Optional for auto-derivation of JSON codecs
    "io.circe" %% "circe-generic" % ver.circe,
    // Optional for string interpolation to JSON model
    "io.circe" %% "circe-literal" % ver.circe
)

lazy val ZioExamples = project.in(file(".")).settings(
    name := "ZioExamples",
    version := "0.1",
    scalaVersion := "2.13.2",
    libraryDependencies ++= testing.map(_ % Test) ++ logging ++ config ++ zio ++ jdbc ++ http4s ++ akka ++ circe
)