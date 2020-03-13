import scala.collection.Seq

performMavenCentralSync in ThisBuild := false   // basically just ignores all the sonatype sync parts of things

publishAsOSSProject in ThisBuild := true

ThisBuild / githubRepository := "sbt-quasar-plugin"

homepage in ThisBuild := Some(url("https://github.com/precog/sbt-quasar-plugin"))

scmInfo in ThisBuild := Some(ScmInfo(
  url("https://github.com/precog/sbt-quasar-plugin"),
  "scm:git@github.com:precog/sbt-quasar-plugin.git"))

name := "sbt-quasar-plugin"

sbtPlugin := true

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.12.0-RC1",
  "io.get-coursier" %% "coursier" % "2.0.0-RC3-1",
  "io.get-coursier" %% "coursier-cache" % "2.0.0-RC3-1",
  "io.get-coursier" %% "coursier-cats-interop" % "2.0.0-RC3-1")

addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.4.2")
