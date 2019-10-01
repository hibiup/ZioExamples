
// DEPENDENCIES
lazy val testing = Seq(
    "org.scalatest" %% "scalatest" % "3.0.8",
    "com.storm-enroute" %% "scalameter" % "0.19"
)

lazy val logging ={
    val logbackV = "1.2.3"
    val scalaLoggingV = "3.9.2"
    Seq(
        "ch.qos.logback" % "logback-classic" % logbackV,
        "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingV
    )
}

lazy val zio = {
    val ZioVersion = "1.0.0-RC13"
    Seq(
        "dev.zio" %% "zio" % ZioVersion,
        "dev.zio" %% "zio-streams" % ZioVersion
    )
}

lazy val ZioExamples = project.in(file(".")).settings(
    name := "ZioExamples",
    version := "0.1",
    scalaVersion := "2.13.1",
    libraryDependencies ++= testing.map(_ % Test) ++ logging ++ zio
)