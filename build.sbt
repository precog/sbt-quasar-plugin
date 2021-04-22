import scala.collection.Seq

ThisBuild / scalaVersion := "2.12.12"

ThisBuild / publishAsOSSProject in ThisBuild := true

ThisBuild / homepage := Some(url("https://github.com/precog/sbt-quasar-plugin"))

ThisBuild / scmInfo := Some(ScmInfo(
  url("https://github.com/precog/sbt-quasar-plugin"),
  "scm:git@github.com:precog/sbt-quasar-plugin.git"))

ThisBuild / githubOwner := "precog"
ThisBuild / githubRepository := "sbt-quasar-plugin"

name := "sbt-quasar-plugin"

sbtPlugin := true

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core" % "0.12.0-RC1",
  "io.get-coursier" %% "coursier" % "2.0.0-RC3-1",
  "io.get-coursier" %% "coursier-cache" % "2.0.0-RC3-1",
  "io.get-coursier" %% "coursier-cats-interop" % "2.0.0-RC3-1")

addSbtPlugin("com.codecommit" % "sbt-github-packages" % "0.5.0")
