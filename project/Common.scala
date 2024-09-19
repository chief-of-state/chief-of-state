import Dependencies.Versions
import de.heikoseeberger.sbtheader.HeaderPlugin.autoImport.{
  headerLicense,
  headerLicenseStyle,
  HeaderLicense,
  HeaderLicenseStyle
}
import org.scalafmt.sbt.ScalafmtPlugin.autoImport.scalafmtOnCompile
import sbt.Keys._
import sbt._
import scoverage.ScoverageKeys.{
  coverageExcludedPackages,
  coverageFailOnMinimum,
  coverageMinimumStmtTotal
}

object Common extends AutoPlugin {

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger = allRequirements

  override def globalSettings =
    Seq(
      scalaVersion         := Versions.ScalaVersion,
      organization         := "com.github.chiefofstate",
      organizationName     := "Chief Of State.",
      organizationHomepage := Some(url("https://github.com/chief-of-state")),
      startYear            := Some(2020),
      licenses += ("MIT", new URL("https://opensource.org/licenses/MIT")),
      headerLicenseStyle := HeaderLicenseStyle.SpdxSyntax,
      headerLicense := Some(HeaderLicense.Custom("""|Copyright (c) 2022 ChiefOfState.
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
        "-target:8",
        "-Xfatal-warnings",
        "-deprecation",
        "-Xlint",
        "-P:silencer:checkUnused",
        "-P:silencer:pathFilters=.protogen[/].*",
        "-P:silencer:globalFilters=Unused import;deprecated",
        "-P:silencer:globalFilters=Marked as deprecated in proto file;Could not find any member to link;unbalanced or unclosed heading"
      ),
      resolvers ++= Resolver.sonatypeOssRepos("public"),
      resolvers ++= Resolver.sonatypeOssRepos("snapshots"),
      libraryDependencies ++= Seq(
        compilerPlugin(
          ("com.github.ghik" % "silencer-plugin" % Versions.SilencerVersion)
            .cross(CrossVersion.full)
        ),
        ("com.github.ghik" % "silencer-lib" % Versions.SilencerVersion % Provided)
          .cross(CrossVersion.full)
      ),
      scalafmtOnCompile := true,
      // require test coverage
      coverageMinimumStmtTotal := 65,
      coverageFailOnMinimum    := true,
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
