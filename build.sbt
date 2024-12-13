
import scoverage.ScoverageKeys
import play.sbt.routes.RoutesKeys

val appName = "one-stop-shop-returns"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin) //Required to prevent https://github.com/scalatest/scalatest/issues/1427
  .settings(
    majorVersion                     := 0,
    scalaVersion                     := "3.5.2",
    libraryDependencies              ++= AppDependencies.compile ++ AppDependencies.test,

    ScoverageKeys.coverageExcludedFiles := "<empty>;Reverse.*;" +
      ".*Routes.*;",
    ScoverageKeys.coverageMinimumStmtTotal := 78,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := true,
    RoutesKeys.routesImport ++= Seq(
      "java.time.LocalDate",
      "models._",
      "models.Binders._"
    )
  )
  .settings(PlayKeys.playDefaultPort := 10205)
  .configs(IntegrationTest)
  .settings(inConfig(IntegrationTest)(itSettings)*)
  .configs(Test)
  .settings(inConfig(Test)(testSettings)*)
  .settings(resolvers += Resolver.jcenterRepo)
  .settings(scalacOptions ++= Seq("-Wconf:src=routes/.*:s", "-Wconf:msg=Flag.*repeatedly:s"))

lazy val itSettings = Defaults.itSettings ++ Seq(
  unmanagedSourceDirectories := Seq(
    baseDirectory.value / "it",
    baseDirectory.value / "test" / "generators"
  ),
  unmanagedResourceDirectories := Seq(
    baseDirectory.value / "it" / "resources"
  ),
  parallelExecution := false,
  fork := true,
  javaOptions ++= Seq(
    "-Dlogger.resource=logback-it.xml"
  )
)

lazy val testSettings = Defaults.testSettings ++ Seq(
  unmanagedSourceDirectories := Seq(
    baseDirectory.value / "test"
  ),
  parallelExecution := false,
  fork := true,
  javaOptions ++= Seq(
    "-Dlogger.resource=logback-test.xml"
  )
)
