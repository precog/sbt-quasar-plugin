import scala.collection.Seq

performMavenCentralSync in ThisBuild := false   // basically just ignores all the sonatype sync parts of things

publishAsOSSProject in ThisBuild := true

homepage in ThisBuild := Some(url("https://github.com/slamdata/sbt-quasar-plugin"))

scmInfo in ThisBuild := Some(ScmInfo(
  url("https://github.com/slamdata/sbt-quasar-plugin"),
  "scm:git@github.com:slamdata/sbt-quasar-plugin.git"))

lazy val root = project
  .in(file("."))
  .settings(name := "sbt-quasar-plugin")
  .settings(libraryDependencies ++= Seq(
    "io.circe" %% "circe-core" % "0.12.0-RC1",
    "io.get-coursier" %% "coursier" % "2.0.0-RC4",
    "io.get-coursier" %% "coursier-cache" % "2.0.0-RC4",
    "io.get-coursier" %% "coursier-cats-interop" % "2.0.0-RC4"
  ))
  .enablePlugins(SbtPlugin, AutomateHeaderPlugin)
