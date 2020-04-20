# sbt-quasar-plugin [![Discord](https://img.shields.io/discord/373302030460125185.svg?logo=discord)](https://discord.gg/QNjwCg6)

There are two different types of plugins: datasources and destinations. The former are responsible for connecting to read-only *sources* of data which can be loaded and processed by Precog, while the latter are responsible for connecting to write-only output *sinks* for data produced by Precog. These plugins are dynamically loaded at startup by [Quasar](https://github.com/precog/quasar), the core architectural layer of Precog, in a classloader-isolated environment (to ensure that plugins with conflicting dependencies can coexist).

All plugins must be compiled to JVM bytecode, compatible with OpenJDK 8. The exact JVM language in which the plugin is written is irrelevant, although the APIs are designed for use with Scala. It wouldn't be terribly difficult to wrap the API in some way such that it becomes easier to write plugins in Java, Kotlin, Clojure, or any other JVM language. This guide will be written assuming knowledge of Scala, though it will not assume knowledge of any more advanced topics or frameworks within Scala (such as Cats, Scalaz, or similar). Some relatively advanced techniques were employed in the design of the plugin APIs, but those techniques may be used in a fairly straightforward manner, without prerequisite knowledge of how they were derived.

If using Scala, some tooling does exist to assist in the building and packaging of plugins for subsequent embedding within Precog. This tooling is wrapped up in the sbt plugin, [sbt-quasar-plugin](https://github.com/precog/sbt-quasar-plugin). This can be added to your build by adding the following to your `project/plugins.sbt` file:

```scala
addSbtPlugin("com.precog" %% "sbt-quasar-plugin" % <version/>)
```

Replace `<version/>` with the latest version of the plugin, which can be determined by looking at the Git tags on the repository.

Once this is done, add the followint to your `build.sbt`:

```sbt
enablePlugins(QuasarPlugin)
```

Once the plugin is added to your build, a number of keys will be exposed for use in your primary plugin module:

- `quasarPluginName : String`
- `quasarPluginQuasarVersion : String`
- `quasarPluginDatasourceFqcn : Option[String]`
- `quasarPluginDestinationFqcn : Option[String]`
- `quasarPluginDependencies : Seq[ModuleID]`

The `quasarPluginDependencies` key is analogous to `libraryDependencies`, except it will be considered as part of the assembly and packaging process for your plugin. You should declare all of your non-`Test` dependencies using *this* key rather than `libraryDependencies`. `quasarPluginName` is relatively self-explanatory, as is `quasarPluginQuasarVersion` (you can find the latest quasar version by looking at [the GitHub repository](https://github.com/precog/quasar)).

The datasource/destination fully qualified class name keys are more complex. This declaration is necessary so that the plugin is able to add some metadata to the manifest of the JAR file produced by the plugin build. That metadata will be used by quasar to load the plugin. Thus, either the destination or datasource class name must be provided, and the class in question must be an `object` which extends either `DestinationModule` or `DatasourceModule`, respectively. Note that, in Scala, the name of the *class* which corresponds to an `object` is generally the name of the object with a suffix `$`.

You can see a reasonably simple example of an sbt build for a datasource [here](https://github.com/precog/quasar-datasource-url/blob/eb313806afcda4f44f35dd5dff6250e287c473c6/build.sbt). If you're using a JVM language other than Scala, the precise packaging requirements for plugins (sources and destinations) are described at the end of the guide.

The remainder of this guide will be split between datasource- and destination-type plugins, since their APIs are distinct.

### Datasources

A datasource module must extend either the `quasar.connector.LightweightDatasourceModule` trait or `quasar.connector.HeavyweightDatasourceModule`. This guide will not touch on `HeavyweightDatasourceModule`s as their implementation is extremely complex, though they are commensurately granted much much more power (including the ability to entirely replace the Precog evaluation runtime). All currently-available datasources *aside* from Precog's own internal runtime are implemented as `LightweightDatasourceModule`s. This trait defines several functions which must be implemented to define a datasource:

```scala
trait LightweightDatasourceModule {
  def kind: DatasourceType

  def sanitizeConfig(config: Json): Json

  def lightweightDatasource[
      F[_]: ConcurrentEffect: ContextShift: MonadResourceErr: Timer](
      config: Json)(
      implicit ec: ExecutionContext)
      : Resource[F, Either[InitializationError[Json], LightweightDatasourceModule.DS[F]]]
}
```

We'll go over these one at a time. The `kind` function simply defines the name and version of your datasource. For example:

```scala
val kind = DatasourceType("s3", 1L)
```

This is the `kind` of [the S3 datasource](https://github.com/precog/quasar-datasource-s3). Note that, due to the current use of [Refined](https://github.com/fthomas/refined) in these type signatures, you will need the following import somewhere in your file:

```scala
import eu.timepit.refined.auto._
```

If you get weird compile errors, try adding that.

The second important function is `sanitizeConfig`. This function takes a parameter of type `argonaut.Json` (from the [Argonaut JSON library](http://argonaut.io)) representing the configuration parameters for your plugin. You are free to define the config in whatever shape you see fit; quasar makes no assumptions about it. The `sanitizeConfig` function takes the config as a parameter and *returns* that same config, but with any sensitive information redacted. For example, if your config contains authentication information for your datasource, you should redact this from the returned `Json` value. This function will be used by quasar before the config is ever produced or logged in any form.

The final function is `lightweightDatasource`. This function is considerably more imposing at first glance, but its basic function is very simple: take the config as a parameter and return a `Resource` which manages the lifecycle of the datasource. The configuration would be expected to be something like a set of database credentials and an address, and the lifecycle of the datasource would start by establishing a connection to that database and authenticating using the supplied credentials. The lifecycle would end by closing the connection to the database and freeing any scarce resources that may have been allocated.

The `Resource` type comes from [Cats Effect](https://typelevel.org/cats-effect/datatypes/resource.html) and it safely encapsulates initializing and freeing your datasource. It is possible to create a `Resource` using the `Resource.make` function. For example:

```scala
val initializeConnection: F[Connection] = Sync[F] delay {
  val c = new Connection(address)
  c.connect()
  c.authenticate(credentials)   // we probably got the credentials from the config
  c
}

val r: Resource[F, Connection] = 
  Resource.make(initializeConnection) { conn =>
    Sync[F] delay {
      conn.close()
    }
  }
```

This creates a `Resource` for a hypothetical `Connection`. You'll note the use of the `Sync[F] delay { ... }` syntax. You'll end up using this a lot in any plugin. You can find a high-level description of the meaning of this construct [here](https://typelevel.org/cats-effect/datatypes/io.html#synchronous-effects--ioapply), but broadly speaking, it takes a block of code and wraps it up as an *effect* which will be evaluated at some later point in time. This syntax allows the quasar framework to fully control the lifecycle of your plugin, despite that lifecycle being defined *in* your plugin. In this case, we're defining what it means to initialize a `Connection` (and later on, we define what it means to `close()` that connection). These effects are safely captured by `delay`, and you don't need to worry about carefully ordering your statements or accidentally leaking resources.

Speaking of resources, the `Resource.make` function takes two effects, one which *creates* a resource and another which *releases* that resource, and returns a `Resource` instance which safely encapsulates that resource's lifecycle. The `lightweightConnector` function must produce a `Resource[F, LightweightDatasourceModule.DS[F]]`. This `DS` type expands to the following imposing signature:

```scala
Datasource[F, Stream[F, ?], InterpretedRead[ResourcePath], QueryResult[F], ResourcePathType.Physical]
```

Or, in a more simplified and useful form:

```scala
LightweightDatasource[F, Stream[F, ?], QueryResult[F]]
```

The `Stream` type in question here is from [fs2](https://fs2.io), a purely functional streaming library for Scala. We'll be seeing a lot more of this library later on, but suffice it to say that this is the mechanism by which it is possible to safely, *incrementally* load large amounts of data from a datasource with high performance.

Remember that we need to return a `Resource` which encapsulates one of these things, so it may be prudent to examine exactly what this is.

A `Datasource` (or in this case, a `LightweightDatasource`) is a *running* instance of your plugin. While the `DatasourceModule` represents a constructor which is capable of building instances of your plugin, the `Datasource` itself is just such an instance. Note that your plugin may be instantiated multiple times with different configurations. For example, if someone needs to load data from two different databases of the same type. Any running instance of your datasource plugin will be responsible for performing the actual loading of data via whatever mechanism is exposed by the target data source. More specifically, it will need to define the following three functions:

```scala
/** The type of this datasource. */
def kind: DatasourceType

def pathIsResource(path: ResourcePath): F[Boolean]

def prefixedChildPaths(
    prefixPath: ResourcePath)
    : F[Option[Stream[F, (ResourceName, P)]]]

def evaluate(query: InterpretedRead[ResourcePath]): F[QueryResult[F]]
```

Let's go through these one at a time. The first function, `kind` is exactly the same as `kind` in your module, and should return the same value. It's here to simplify some things in the quasar runtime. 

`pathIsResource` is simply the equivalent of checking whether a given path 1) exists, and 2) is a "file". Obviously, not all datasources have a notion of files (for example, most NoSQL databases have some notion of a *collection*, which is kind of like a file, but exists at the top level). The definition of a file for the purposes of the datasource API is simple: if you can read the contents of a given path, it's a file; if a given path has sub-paths, it's not a file. This is kind of like `[ -f ... ]` in common Unix shells.

`prefixedChildPaths` is sort of like a recursive `ls`. Given a path, enumerate all of the paths *under* that path. These can be directories or files. This is a bit like `find ...` in Unix shells.

`evaluate` is the function which actually *reads* data from a given path. This is where most of the functionality of your datasource will live. It takes a file path in the form of an `InterpretedRead` (more on this in a moment) and produces a data stream representing the contents of that file in the form of a `QueryResult`. Conceptually, this is just `cat ...` from Unix shells.

`InterpretedRead` contains more than just the path to the resource being loaded. In fact, its definition looks like this:

```scala
final case class InterpretedRead[A](path: A, stages: ScalarStages)
```

`ScalarStages` are important for semantically-rich sources such as Mongo, but most datasources will be able to simply ignore them. `path` is what is actually interesting in most cases, and in our `LightweightDatasource` as defined above, `path` will be a `ResourcePath`. A `ResourcePath` is either a `Leaf` (which is defined by a [Pathy](https://github.com/precog/scala-pathy) file path of type `Path[Abs, Sandboxed, Sandboxed]`) or `Root`, which simply indicates the root path of the virtual filesystem (i.e. `/`).

`QueryResult` is what we're trying to *produce* from an evaluation, and it can take on one of three different forms:

- `Parsed` – Indicating data that is already in memory. This would be useful for a datasource loading data stored in formats like Avro or Protobuf, where you will need to apply a custom parser to produce objects in memory, one for each row. This is very inefficient, and you will need to provide an instance of `QDataDecode` for whatever your in-memory row type is, but this case does make it possible to easily support datasources which already have JVM client libraries. **Use this if you have a pre-existing client library for your data source which produces *Java objects* as results**
- `Typed` – Indicating data that is of a known format, but which is being loaded as raw bytes. This is the most general case and will signal to quasar that the data stream must be processed and parsed according to any of the supported methodologies (defined by `DataFormat`, currently including JSON, CSV, and GZIP compression applied to either of those two). All you're responsible for is providing the bytes themselves! **Use this if your data source contains raw data in CSV or JSON format, optionally compressed with GZIP**
- `Stateful` – A much more complex variant of `Typed` which provides support for staged loading of data based on information determined during the parsing process. This is unlikely to be what you want... ever. **Use this if you *really* know what you're doing**

All of the `QueryResult` cases also contain a value of type `ScalarStages`, which we obtain from the `InterpretedRead`. If we were implementing push-down semantics for operations (i.e. pushing limited evaluation operations to an underlying database), then we would take a *prefix* of the `ScalarStages` provided to us in the `InterpretedRead`, push those down to the underlying datastore, and then return the remainder of the stages to quasar via the `QueryResult` along with the data stream which represents the results of interpreting that prefix. This prefix/suffix behavior is to allow support for datasources which can push down certain operations, but not all operations. Any prefix may be chosen, including the empty prefix (which would mean returning *all* of the `ScalarStages` unmodified to quasar). This is the simplest to implement, and also what we will do most of the time.

Taking a step back... `evaluate` takes a `InterpretedRead`, which contains a `path` and some `stages`, and expects us to produce a `QueryResult`. The `QueryResult` contains a data `Stream` consisting of either objects, for which we must define an implementation of `QDataDecode`, or raw bytes, for which we must declare a `DataFormat`. The `DataFormat` may be either `DataFormat.Json` or `DataFormat.SeparatedValues`, and *either* of these formats may be wrapped in `DataFormat.Compressed`, which currently only supports the `CompressionScheme.Gzip` as a configuration option. `QueryResult` also contains the `stages` we were handed in the `InterpretedRead`. This is very important! *If you do not pass along `stages` from the `InterpretedRead` through to the `QueryResult`, tables will not evaluate correctly against your datasource, and Precog will probably crash whenever you load any complex data.*

#### Simple Example

The best way to understand the datasource API is to attempt to implement a datasource for some simple source of unparsed JSON data. In this case, we will be loading JSON files from the local filesystem.

*TODO*

#### Production Examples

All of the production datasources distributed with Precog are open source under a permissive license and may be liberally used as examples and templates in the construction of new datasources.

- [S3](https://github.com/precog/quasar-datasource-s3)
  + A relatively straightforward example of a *blobstore* datasource, which is a particular type of datasource that loads binary data out of a virtual filesystem
  + Datasources which are similar to this may benefit from the [async-blobstore](https://github.com/precog/async-blobstore) utility library, which exposes a `BlobstoreDatasource` definition which is simpler to implement for this restricted case
- [Azure](https://github.com/precog/quasar-datasource-azure)
- [HTTP REST APIs](https://github.com/precog/quasar-datasource-url)
- [MongoDB](https://github.com/precog/quasar-datasource-mongo)
  + This is a notable example as it is the only one which produces a `QueryResult.Parsed`, and also the only example of a push-down datasource, where the `ScalarStages` are passed to the underlying database (in this case MongoDB) for evaluation.

### Destinations

*TODO*

### Packaging

Every plugin must contain a primary module which is a class with the following properties:

- Must extend either `quasar.connector.LightweightDatasourceModule` or `quasar.connector.HeavyweightDatasourceModule` (for datasources), or `quasar.connector.DestinationModule` (for destinations). Note that for datasources, you *almost* always want to extend `LightweightDatasourceModule`.
- Must define a `static` field, `MODULE$`, which contains the singleton instance of the class. If using Scala, this can be achieved by defining the primary module as an `object`.

An example of such a module in Java would be the following:

```java
package com.company.example;

public final class ExampleDatasourceModule implements LightweightDatasourceModule {
  public static final ExampleDatasourceModule MODULE$ = new ExampleDatasourceModule();

  private ExampleDatasourceModule() {}

  // implement abstract things here
}
```

The fully qualified name of this class must be added to a manifest entry in the `MANIFEST.MF` file within the JAR file which corresponds to the plugin. This entry must be `Datasource-Module` for datasources, or `Destination-Module` for destinations. The entry for the above example would look like the following:

```
Datasource-Module: com.company.example.ExampleDatasourceModule
```

You may distribute multiple plugins within a single JAR by whitespace-separating their fully qualified names. It is conventional though to only include a single plugin per JAR.

Once this JAR is prepared, you must additionally create a `.plugin` file which describes the main JAR and classpath entries for your plugin. This file is in JSON format and should consist of a map with two keys: `mainJar` and `classPath`. The `mainJar` should be the path (relative to the `.plugin` file itself) to the JAR file `MANIFEST.MF` entry for the plugin. The `classPath` should be a JSON array of strings, each of which is a path (relative to the `.plugin` file) to a JAR file. These JARs will be added to the classpath of the plugin when loaded. In this way, it is not necessary to create fat jars for plugin distribution.

Additionally, the plugin classpath will always include Quasar itself and its dependencies. While it will not *hurt* to include the Quasar JARs (and everything upstream of it) in the plugin `classPath`, it will just end up taking extra space on disk (the runtime will ignore those paths). Fat JARs will also *work*, they're just less modular and take somewhat longer to load (if using a fat JAR, then `classPath` will be `[]`).

An example `.plugin` file for our simple plugin defined above:

```json
{
  "mainJar": "example-datasource-0.1.0.jar",
  "classPath": [
    "lib/netty-4.1.9.jar",
    "lib/apache-commons-collections-4.4.jar",
  ]
}
```

Place the `.plugin` file (and referenced JAR files as appropriate) in the directory referenced as the plugins directory by the Precog config file. For example, the bundled Docker container for Precog contains a config file with the following subsection:

```
precog {
  # The path to the Precog license file.
  license = "/var/lib/precog/docker.lic"

  # The directory Precog will store its data in, must be writable by the UID
  # of the Precog process.
  data-directory = "/var/lib/precog/data"

  # The directory Precog will load Datasource plugins from, must be readable
  # by the UID of the Precog process.
  plugin-directory = "/var/lib/precog/lib"

  # The number of rows sampled from a dataset when generating an SST.
  #
  # Presence: OPTIONAL
  sst-sample-size = 100000

  # The number of rows to process at a time when generating an SST.
  #
  # Presence: OPTIONAL
  sst-chunk-size = 250

  # The number of chunks to process in parallel when generating an SST.
  #
  # Presence: OPTIONAL
  sst-parallelism = 2

  # The maximum amount of time allowed when generating an SST.
  #
  # Presence: OPTIONAL
  sst-time-limit = 120 seconds
}
```

The plugins directory in this case is `/var/lib/precog/lib/`. Placing your `.plugin` file (and its referenced dependencies) within that directory will cause it to be loaded automatically by Precog the next time it is started. Any errors encountered while loading your plugin will be logged.
