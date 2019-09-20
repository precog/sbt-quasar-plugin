/*
 * Copyright 2014–2019 SlamData Inc.
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

package quasar.sbt

import scala.{None, Option}
import scala.Predef.String
import scala.collection.Seq
import scala.concurrent.ExecutionContext

import cats.effect.IO
import coursier.interop.cats._
import sbt._, Keys._

object QuasarPlugin extends AutoPlugin {

  private implicit val globalContextShift =
    IO.contextShift(ExecutionContext.Implicits.global)

  object autoImport {
    val quasarPluginName: SettingKey[String] = settingKey[String]("The short name of the plugin (e.g. 's3', 'azure').")
    val quasarPluginDependencies: SettingKey[Seq[ModuleID]] = settingKey[Seq[ModuleID]]("Declares the non-quasar managed dependencies.")
    val quasarPluginDatasourceFqcn: SettingKey[Option[String]] = settingKey[Option[String]]("The fully qualified class name of the datasource module.")
    val quasarPluginDestinationFqcn: SettingKey[Option[String]] = settingKey[Option[String]]("The fully qualified class name of the destination module.")
    val quasarPluginQuasarVersion: SettingKey[String] = settingKey[String]("Defines the version of Quasar to depend on.")

    val quasarPluginAssemble: TaskKey[File] = taskKey[File]("Produces the tarball containing the quasar plugin and all non-quasar dependencies.")
  }

  import autoImport._

  override def requires = plugins.JvmPlugin

  override def projectSettings = {
    val srcFqcn = quasarPluginDatasourceFqcn
    val dstFqcn = quasarPluginDestinationFqcn

    Seq(
      quasarPluginDependencies := Seq.empty,

      quasarPluginDatasourceFqcn := None,

      quasarPluginDestinationFqcn := None,

      libraryDependencies := {
        val quasarDependencies =
          if (srcFqcn.value.isDefined || dstFqcn.value.isDefined)
            Seq(
              "com.slamdata" %% "quasar-connector" % quasarPluginQuasarVersion.value,
              "com.slamdata" %% "quasar-connector" % quasarPluginQuasarVersion.value % Test classifier "tests")
          else
            Seq()

        libraryDependencies.value ++ quasarPluginDependencies.value ++ quasarDependencies
      },

      packageOptions in (Compile, packageBin) += {
        val attrs =
          srcFqcn.value.toList.map(("Datasource-Module", _)) ++
          dstFqcn.value.toList.map(("Destination-Module", _))

        Package.ManifestAttributes(attrs: _*),
      },

      quasarPluginAssemble := {
        val pluginPath =
          AssemblePlugin[IO, IO.Par](
            quasarPluginName.value,
            version.value,
            quasarPluginDependencies.value,
            (Keys.`package` in Compile).value.toPath,
            quasarPluginQuasarVersion.value,
            (scalaBinaryVersion in Compile).value,
            (crossTarget in Compile).value.toPath)

        pluginPath.map(_.toFile).unsafeRunSync()
      })
  }
}
