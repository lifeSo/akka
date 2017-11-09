enablePlugins(UnidocRoot, TimeStampede, UnidocWithPrValidation, NoPublish)
disablePlugins(MimaPlugin)

import com.typesafe.sbt.SbtMultiJvm.MultiJvmKeys.MultiJvm
import com.typesafe.tools.mima.plugin.MimaPlugin
import spray.boilerplate.BoilerplatePlugin
import akka.AkkaBuild._
import akka.{AkkaBuild, Dependencies, GitHub, OSGi, Protobuf, SigarLoader, VersionGenerator}
import sbt.Keys.{initialCommands, parallelExecution}

initialize := {
  // Load system properties from a file to make configuration from Jenkins easier
  loadSystemProperties("project/akka-build.properties")
  initialize.value
}

akka.AkkaBuild.buildSettings
shellPrompt := { s => Project.extract(s).currentProject.id + " > " }
resolverSettings

lazy val aggregatedProjects: Seq[ProjectReference] = Seq(
  actor, actorTests,
  agent,
  benchJmh,
  camel,
  cluster, clusterMetrics, clusterSharding, clusterTools,
  contrib,
  distributedData,
  docs,
  multiNodeTestkit,
  osgi,
  persistence, persistenceQuery, persistenceShared, persistenceTck,
  protobuf,
  remote, remoteTests,
  slf4j,
  stream, streamTestkit, streamTests, streamTestsTck,
  testkit,
  typed, typedTests, typedTestkit
)

lazy val root = Project(
  id = "akka",
  base = file(".")
).aggregate(aggregatedProjects: _*)
 .settings(rootSettings: _*)
 .settings(unidocRootIgnoreProjects := Seq(remoteTests, benchJmh, protobuf, akkaScalaNightly, docs))

lazy val actor = akkaModule("akka-actor")
  .settings(Dependencies.actor)
  .settings(OSGi.actor)
  .settings(
    unmanagedSourceDirectories in Compile += {
      val ver = scalaVersion.value.take(4)
      (scalaSource in Compile).value.getParentFile / s"scala-$ver"
    }
  )
  .settings(VersionGenerator.settings)
  .enablePlugins(BoilerplatePlugin)

lazy val actorTests = akkaModule("akka-actor-tests")
  .dependsOn(testkit % "compile->compile;test->test")
  .settings(Dependencies.actorTests)
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin, WhiteSourcePlugin)

lazy val agent = akkaModule("akka-agent")
  .dependsOn(actor, testkit % "test->test")
  .settings(Dependencies.agent)
  .settings(OSGi.agent)
  .enablePlugins(ScaladocNoVerificationOfDiagrams)

lazy val akkaScalaNightly = akkaModule("akka-scala-nightly")
  // remove dependencies that we have to build ourselves (Scala STM)
  .aggregate(aggregatedProjects diff List[ProjectReference](agent, docs): _*)
  .disablePlugins(MimaPlugin)
  .disablePlugins(ValidatePullRequest, MimaPlugin)

lazy val benchJmh = akkaModule("akka-bench-jmh")
  .dependsOn(
    Seq(
      actor,
      stream, streamTests,
      persistence, distributedData,
      testkit
    ).map(_ % "compile->compile;compile->test;provided->provided"): _*
  )
  .settings(Dependencies.benchJmh)
  .enablePlugins(JmhPlugin, ScaladocNoVerificationOfDiagrams, NoPublish)
  .disablePlugins(MimaPlugin, WhiteSourcePlugin, ValidatePullRequest)

lazy val camel = akkaModule("akka-camel")
  .dependsOn(actor, slf4j, testkit % "test->test")
  .settings(Dependencies.camel)
  .settings(OSGi.camel)

lazy val cluster = akkaModule("akka-cluster")
  .dependsOn(remote, remoteTests % "test->test" , testkit % "test->test")
  .settings(Dependencies.cluster)
  .settings(OSGi.cluster)
  .settings(Protobuf.settings)
  .settings(
    parallelExecution in Test := false
  )
  .configs(MultiJvm)
  .enablePlugins(MultiNodeScalaTest)


lazy val clusterMetrics = akkaModule("akka-cluster-metrics")
  .dependsOn(cluster % "compile->compile;test->test;multi-jvm->multi-jvm", slf4j % "test->compile")
  .settings(OSGi.clusterMetrics)
  .settings(Dependencies.clusterMetrics)
  .settings(Protobuf.settings)
  .settings(SigarLoader.sigarSettings)
  .settings(
    parallelExecution in Test := false
  )
  .configs(MultiJvm)
  .enablePlugins(MultiNodeScalaTest)

lazy val clusterSharding = akkaModule("akka-cluster-sharding")
  // TODO akka-persistence dependency should be provided in pom.xml artifact.
  //      If I only use "provided" here it works, but then we can't run tests.
  //      Scope "test" is alright in the pom.xml, but would have been nicer with
  //      provided.
  .dependsOn(
    cluster % "compile->compile;test->test;multi-jvm->multi-jvm",
    distributedData,
    persistence % "compile->compile;test->provided",
    clusterTools
  )
  .settings(Dependencies.clusterSharding)
  .settings(OSGi.clusterSharding)
  .settings(Protobuf.settings)
  .configs(MultiJvm)
  .enablePlugins(MultiNode, ScaladocNoVerificationOfDiagrams)

lazy val clusterTools = akkaModule("akka-cluster-tools")
  .dependsOn(cluster % "compile->compile;test->test;multi-jvm->multi-jvm")
  .settings(Dependencies.clusterTools)
  .settings(OSGi.clusterTools)
  .settings(Protobuf.settings)
  .configs(MultiJvm)
  .enablePlugins(MultiNode, ScaladocNoVerificationOfDiagrams)

lazy val contrib = akkaModule("akka-contrib")
  .dependsOn(remote, remoteTests % "test->test", cluster, clusterTools, persistence % "compile->compile;test->provided")
  .settings(Dependencies.contrib)
  .settings(OSGi.contrib)
  .settings(
    description := """|
                      |This subproject provides a home to modules contributed by external
                      |developers which may or may not move into the officially supported code
                      |base over time. A module in this subproject doesn't have to obey the rule
                      |of staying binary compatible between minor releases. Breaking API changes
                      |may be introduced in minor releases without notice as we refine and
                      |simplify based on your feedback. A module may be dropped in any release
                      |without prior deprecation. The Lightbend subscription does not cover
                      |support for these modules.
                      |""".stripMargin
  )
  .configs(MultiJvm)
  .enablePlugins(MultiNode, ScaladocNoVerificationOfDiagrams)
  .disablePlugins(MimaPlugin)

lazy val distributedData = akkaModule("akka-distributed-data")
  .dependsOn(cluster % "compile->compile;test->test;multi-jvm->multi-jvm")
  .settings(Dependencies.distributedData)
  .settings(OSGi.distributedData)
  .settings(Protobuf.settings)
  .configs(MultiJvm)
  .enablePlugins(MultiNodeScalaTest)

lazy val docs = akkaModule("akka-docs")
  .dependsOn(
    actor, cluster, clusterMetrics, slf4j, agent, camel, osgi, persistenceTck, persistenceQuery, distributedData, stream,
    clusterTools % "compile->compile;test->test",
    clusterSharding % "compile->compile;test->test",
    testkit % "compile->compile;test->test",
    remote % "compile->compile;test->test",
    persistence % "compile->compile;provided->provided;test->test",
    typed % "compile->compile;test->test",
    typedTests % "compile->compile;test->test",
    streamTestkit % "compile->compile;test->test"
  )
  .settings(Dependencies.docs)
  .settings(
    name in (Compile, paradox) := "Akka",
    paradoxProperties ++= Map(
      "akka.canonical.base_url" -> "http://doc.akka.io/docs/akka/current",
      "github.base_url" -> GitHub.url(version.value), // for links like this: @github[#1](#1) or @github[83986f9](83986f9)
      "extref.akka.http.base_url" -> "http://doc.akka.io/docs/akka-http/current/%s",
      "extref.wikipedia.base_url" -> "https://en.wikipedia.org/wiki/%s",
      "extref.github.base_url" -> (GitHub.url(version.value) + "/%s"), // for links to our sources
      "extref.samples.base_url" -> "https://github.com/akka/akka-samples/tree/2.5/%s",
      "extref.ecs.base_url" -> "https://example.lightbend.com/v1/download/%s",
      "scaladoc.akka.base_url" -> "https://doc.akka.io/api/akka/2.5",
      "scaladoc.akka.http.base_url" -> "https://doc.akka.io/api/akka-http/current",
      "javadoc.akka.base_url" -> "https://doc.akka.io/japi/akka/2.5",
      "javadoc.akka.http.base_url" -> "http://doc.akka.io/japi/akka-http/current",
      "scala.version" -> scalaVersion.value,
      "scala.binary_version" -> scalaBinaryVersion.value,
      "akka.version" -> version.value,
      "sigar_loader.version" -> "1.6.6-rev002",
      "algolia.docsearch.api_key" -> "543bad5ad786495d9ccd445ed34ed082",
      "algolia.docsearch.index_name" -> "akka_io",
      "google.analytics.account" -> "UA-21117439-1",
      "google.analytics.domain.name" -> "akka.io",
      "snip.code.base_dir" -> (sourceDirectory in Test).value.getAbsolutePath,
      "snip.akka.base_dir" -> (baseDirectory in ThisBuild).value.getAbsolutePath,
      "fiddle.code.base_dir" -> (sourceDirectory in Test).value.getAbsolutePath
    ),
    paradoxGroups := Map("Language" -> Seq("Scala", "Java")),
    resolvers += Resolver.jcenterRepo,
    deployRsyncArtifact := List((paradox in Compile).value -> s"www/docs/akka/${version.value}"),
  )
  .enablePlugins(AkkaParadoxPlugin, DeployRsync, NoPublish, ParadoxBrowse, ScaladocNoVerificationOfDiagrams)
  .disablePlugins(MimaPlugin, WhiteSourcePlugin)

lazy val multiNodeTestkit = akkaModule("akka-multi-node-testkit")
  .dependsOn(remote, testkit)
  .settings(Protobuf.settings)
  .settings(AkkaBuild.mayChangeSettings)

lazy val osgi = akkaModule("akka-osgi")
  .dependsOn(actor)
  .settings(Dependencies.osgi)
  .settings(OSGi.osgi)
  .settings(
    parallelExecution in Test := false
  )

lazy val persistence = akkaModule("akka-persistence")
  .dependsOn(actor, testkit % "test->test", protobuf)
  .settings(Dependencies.persistence)
  .settings(OSGi.persistence)
  .settings(Protobuf.settings)
  .settings(
    fork in Test := true
  )

lazy val persistenceQuery = akkaModule("akka-persistence-query")
  .dependsOn(
    stream,
    persistence % "compile->compile;provided->provided;test->test",
    streamTestkit % "test"
  )
  .settings(Dependencies.persistenceQuery)
  .settings(OSGi.persistenceQuery)
  .settings(
    fork in Test := true
  )
  .enablePlugins(ScaladocNoVerificationOfDiagrams)

lazy val persistenceShared = akkaModule("akka-persistence-shared")
  .dependsOn(persistence % "test->test", testkit % "test->test", remote % "test", protobuf)
  .settings(Dependencies.persistenceShared)
  .settings(
    fork in Test := true
  )
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin, WhiteSourcePlugin)

lazy val persistenceTck = akkaModule("akka-persistence-tck")
  .dependsOn(persistence % "compile->compile;provided->provided;test->test", testkit % "compile->compile;test->test")
  .settings(Dependencies.persistenceTck)
//.settings(OSGi.persistenceTck) TODO: we do need to export this as OSGi bundle too?
  .settings(
    fork in Test := true
  )
  .disablePlugins(MimaPlugin)

lazy val protobuf = akkaModule("akka-protobuf")
  .settings(OSGi.protobuf)
  .enablePlugins(ScaladocNoVerificationOfDiagrams)
  .disablePlugins(MimaPlugin)

lazy val remote = akkaModule("akka-remote")
  .dependsOn(actor, stream, actorTests % "test->test", testkit % "test->test", streamTestkit % "test", protobuf)
  .settings(Dependencies.remote)
  .settings(OSGi.remote)
  .settings(Protobuf.settings)
  .settings(
    parallelExecution in Test := false
  )

lazy val remoteTests = akkaModule("akka-remote-tests")
  .dependsOn(actorTests % "test->test", remote % "test->test", streamTestkit % "test", multiNodeTestkit)
  .settings(Dependencies.remoteTests)
  .settings(Protobuf.settings)
  .settings(
    parallelExecution in Test := false
  )
  .configs(MultiJvm)
  .enablePlugins(MultiNodeScalaTest, NoPublish)
  .disablePlugins(MimaPlugin, WhiteSourcePlugin)

lazy val slf4j = akkaModule("akka-slf4j")
  .dependsOn(actor, testkit % "test->test")
  .settings(Dependencies.slf4j)
  .settings(OSGi.slf4j)

lazy val stream = akkaModule("akka-stream")
  .dependsOn(actor)
  .settings(Dependencies.stream)
  .settings(OSGi.stream)
  .enablePlugins(BoilerplatePlugin)

lazy val streamTestkit = akkaModule("akka-stream-testkit")
  .dependsOn(stream, testkit % "compile->compile;test->test")
  .settings(Dependencies.streamTestkit)
  .settings(OSGi.streamTestkit)
  .disablePlugins(MimaPlugin)

lazy val streamTests = akkaModule("akka-stream-tests")
  .dependsOn(streamTestkit % "test->test", stream)
  .settings(Dependencies.streamTests)
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin, WhiteSourcePlugin)

lazy val streamTestsTck = akkaModule("akka-stream-tests-tck")
  .dependsOn(streamTestkit % "test->test", stream)
  .settings(Dependencies.streamTestsTck)
  .settings(
    // These TCK tests are using System.gc(), which
    // is causing long GC pauses when running with G1 on
    // the CI build servers. Therefore we fork these tests
    // to run with small heap without G1.
    fork in Test := true
  )
  .enablePlugins(NoPublish)
  .disablePlugins(MimaPlugin, WhiteSourcePlugin)

lazy val testkit = akkaModule("akka-testkit")
  .dependsOn(actor)
  .settings(Dependencies.testkit)
  .settings(OSGi.testkit)
  .settings(
    initialCommands += "import akka.testkit._"
  )

lazy val typed = akkaModule("akka-typed")
  .dependsOn(
    testkit % "compile->compile;test->test",
    persistence % "provided->compile",
    cluster % "provided->compile",
    clusterTools % "provided->compile",
    clusterSharding % "provided->compile",
    distributedData % "provided->compile"
  )
  .settings(AkkaBuild.mayChangeSettings)
  .settings(
    initialCommands := """
      import akka.typed._
      import akka.typed.scaladsl.Actor
      import scala.concurrent._
      import scala.concurrent.duration._
      import akka.util.Timeout
      implicit val timeout = Timeout(5.seconds)
    """
  )
  .disablePlugins(MimaPlugin)

lazy val typedTestkit = akkaModule("akka-typed-testkit")
  .dependsOn(typed, testkit % "compile->compile;test->test")
  .disablePlugins(MimaPlugin)

lazy val typedTests = akkaModule("akka-typed-tests")
  .dependsOn(
    typed,
    typedTestkit % "compile->compile;test->provided;test->test",
    // the provided dependencies
    persistence % "compile->compile;test->test",
    cluster % "test->test",
    clusterTools,
    clusterSharding,
    distributedData
  )
  .settings(AkkaBuild.mayChangeSettings)
  .disablePlugins(MimaPlugin)


def akkaModule(name: String): Project =
  Project(id = name, base = file(name))
    .settings(akka.AkkaBuild.buildSettings)
    .settings(akka.AkkaBuild.defaultSettings)
    .settings(akka.Formatting.formatSettings)
    .enablePlugins(BootstrapGenjavadoc)
