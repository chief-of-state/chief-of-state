test / parallelExecution := false
Test / fork              := true

lazy val root: Project = project
  .in(file("."))
  .enablePlugins(NoPublish)
  .enablePlugins(UniversalPlugin)
  .enablePlugins(JavaAppPackaging)
  .enablePlugins(JavaAgent)
  .settings(
    headerLicense                             := None,
    Compile / mainClass                       := Some("com.github.chiefofstate.Node"),
    makeBatScripts                            := Seq(),
    executableScriptName                      := "entrypoint",
    javaAgents += "io.opentelemetry.javaagent" % "opentelemetry-javaagent" % "2.6.0" % "runtime",
    Universal / javaOptions ++= Seq(
      // Setting the OpenTelemetry java agent options
      // reference: https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#exporters
      "-Dotel.traces.exporter=otlp",
      "-Dotel.metrics.exporter=otlp",
      "-Dotel.exporter.otlp.protocol=grpc",
      "-Dotel.traces.sampler=parentbased_always_on",
      "-Dotel.javaagent.debug=false",
      "-Dotel.instrumentation.[hikaricp].enabled=false",
      "-Dotel.instrumentation.[jdbc].enabled=false",
      "-Dio.opentelemetry.javaagent.slf4j.simpleLogger.defaultLogLevel=error",
      // -J params will be added as jvm parameters
      "-J-XX:+UseContainerSupport",
      "-J-XX:MinRAMPercentage=60.0",
      "-J-XX:MaxRAMPercentage=90.0",
      "-J-XX:+HeapDumpOnOutOfMemoryError",
      "-J-XX:+UseG1GC"
    )
  )
  .dependsOn(chiefofstate)
  .aggregate(protogen, chiefofstate, protogenTest, migration)

lazy val chiefofstate: Project = project
  .in(file("code/service"))
  .enablePlugins(Common)
  .enablePlugins(BuildSettings)
  .enablePlugins(NoPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(name := "chiefofstate", headerLicenseStyle := HeaderLicenseStyle.SpdxSyntax)
  .dependsOn(protogen, protogenTest % "test->compile", migration)

lazy val migration = project
  .in(file("code/migration"))
  .enablePlugins(Common)
  .enablePlugins(BuildSettings)
  .enablePlugins(NoPublish)
  .enablePlugins(AutomateHeaderPlugin)
  .settings(
    name               := "migration",
    description        := "data migration tool",
    headerLicenseStyle := HeaderLicenseStyle.SpdxSyntax
  )
  .dependsOn(protogen)

lazy val protogen: Project = project
  .in(file("code/.protogen"))
  .enablePlugins(Common)
  .enablePlugins(BuildSettings)
  .enablePlugins(NoPublish)
  .settings(name := "protogen")
  .settings(headerLicense := None)
  .settings(
    inConfig(Compile)(
      Seq(
        PB.protoSources := Seq(
          // instruct scalapb to build all COS protos
          file("proto/chief-of-state-protos/chief_of_state"),
          file("proto/internal")
        ),
        PB.includePaths := Seq(
          // includes the protobuf source for imports
          file("proto/chief-of-state-protos"),
          file("proto/internal"),
          // includes external protobufs (like google dependencies)
          baseDirectory.value / "target/protobuf_external"
        ),
        PB.targets := Seq(
          scalapb
            .gen(
              flatPackage = false,
              javaConversions = false,
              grpc = true
            ) -> (Compile / sourceManaged).value / "scalapb"
        )
      )
    )
  )

lazy val protogenTest: Project = project
  .in(file("code/.protogen_test"))
  .enablePlugins(Common)
  .enablePlugins(BuildSettings)
  .enablePlugins(NoPublish)
  .settings(name := "protogen_test")
  .settings(headerLicense := None)
  .settings(
    inConfig(Compile)(
      Seq(
        PB.protoSources := Seq(file("proto/test")),
        PB.includePaths := Seq(
          file("proto/test"),
          // includes external protobufs (like google dependencies)
          baseDirectory.value / "target/protobuf_external"
        ),
        PB.targets := Seq(
          scalapb
            .gen(
              flatPackage = false,
              javaConversions = false,
              grpc = true
            ) -> (Compile / sourceManaged).value / "scalapb"
        )
      )
    )
  )
