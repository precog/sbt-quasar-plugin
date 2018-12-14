/*
 * Copyright 2014–2018 SlamData Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package quasar.sbtdatasource

import scala.Predef.{ArrowAssoc, String}
import scala.collection.Seq
import scala.concurrent.ExecutionContext

import cats.effect.IO
import coursier.interop.cats._
import sbt._, Keys._

object DatasourcePlugin extends AutoPlugin {

  private implicit val globalContextShift =
    IO.contextShift(ExecutionContext.Implicits.global)

  object autoImport {
    val datasourceName: SettingKey[String] = settingKey[String]("The short name of the datasource (e.g. 's3', 'azure').")
    val datasourceDependencies: SettingKey[Seq[ModuleID]] = settingKey[Seq[ModuleID]]("Declares the non-quasar managed dependencies.")
    val datasourceModuleFqcn: SettingKey[String] = settingKey[String]("The fully qualified class name of the datasource module.")
    val datasourceQuasarVersion: SettingKey[String] = settingKey[String]("Defines the version of Quasar to depend on.")

    val datasourceAssemblePlugin: TaskKey[File] = taskKey[File]("Produces the tarball containing the datasource plugin and all non-quasar dependencies.")
  }

  import autoImport._

  override def requires = plugins.JvmPlugin

  override def projectSettings = Seq(

    datasourceDependencies := Seq.empty,

    libraryDependencies := {
      libraryDependencies.value ++ datasourceDependencies.value ++ Seq(
        "com.slamdata" %% "quasar-connector" % datasourceQuasarVersion.value,
        "com.slamdata" %% "quasar-connector" % datasourceQuasarVersion.value % Test classifier "tests"
      )
    },

    packageOptions in (Compile, packageBin) +=
      Package.ManifestAttributes("Datasource-Module" -> datasourceModuleFqcn.value),

    datasourceAssemblePlugin := {
      val pluginPath =
        AssemblePlugin[IO, IO.Par](
          datasourceName.value,
          version.value,
          datasourceDependencies.value,
          (Keys.`package` in Compile).value.toPath,
          datasourceQuasarVersion.value,
          (scalaBinaryVersion in Compile).value,
          (crossTarget in Compile).value.toPath)

      pluginPath.map(_.toFile).unsafeRunSync()
    }
  )
}
