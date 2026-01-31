import Dependencies.Versions
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.HeaderLicense
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.HeaderLicenseStyle
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.headerLicense
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.headerLicenseStyle
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.*
import sbt.Keys.*
import scoverage.ScoverageKeys.coverageExcludedPackages
import scoverage.ScoverageKeys.coverageFailOnMinimum
import scoverage.ScoverageKeys.coverageMinimumStmtTotal

object Common extends AutoPlugin {

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger = allRequirements

  override def globalSettings =
    Seq(
      // Let Slick 3.6.1 be used even when pekko-persistence-jdbc declares 3.5.1 (same major, early-semver)
      libraryDependencySchemes += "com.typesafe.slick" %% "slick"         % "early-semver",
      libraryDependencySchemes += "com.typesafe.slick" %% "slick-hikaricp" % "early-semver",
      scalaVersion         := Versions.ScalaVersion,
      organization         := "com.github.chiefofstate",
      organizationName     := "Chief Of State.",
      organizationHomepage := Some(url("https://github.com/chief-of-state")),
      startYear            := Some(2020),
      licenses += ("MIT", url("https://opensource.org/licenses/MIT")),
      headerLicenseStyle := HeaderLicenseStyle.SpdxSyntax,
      headerLicense := Some(HeaderLicense.Custom("""|Copyright (c) 2020 ChiefOfState.
                                                    |
                                                    |""".stripMargin)),
      developers += Developer(
        "contributors",
        "Contributors",
        "",
        url("https://github.com/chief-of-state/chief-of-state/graphs/contributors")
      )
    )

  override def projectSettings =
    Seq(
      javaOptions ++= Seq(
        "--illegal-access=deny",
        "--add-opens",
        "java.base/java.util=ALL-UNNAMED",
        "--add-opens",
        "java.base/java.lang=ALL-UNNAMED"
      ),
      scalacOptions ++= Seq(
        "-Xtarget:11",
        "-nowarn",
        "-Xfatal-warnings",
        "-deprecation",
        "-explain"
      ),
      resolvers ++= Resolver.sonatypeOssRepos("public"),
      resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
      scalafmtOnCompile := true,
      // require test coverage
      coverageMinimumStmtTotal := 50,
      coverageFailOnMinimum    := false,
      // show full stack traces and test case durations
      Test / testOptions += Tests.Argument("-oDF"),
      Test / logBuffered := false,
      coverageExcludedPackages := "<empty>;com.github.chiefofstate.protobuf.*;" +
        "com.github.chiefofstate.test.helloworld.*;" +
        "com.github.chiefofstate.Entrypoint;" +
        "com.github.chiefofstate.ServiceStarter;" +
        "com.github.chiefofstate.CosBehavior;",
      Test / fork := true
    )
}
