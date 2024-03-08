import sbt.{Test, _}
import scalapb.compiler.Version.{grpcJavaVersion, scalapbVersion}

object Dependencies {

  // Package versions
  object Versions {
    val ScalaVersion: String               = "2.13.11"
    val AkkaVersion: String                = "2.6.20"
    val ScalapbCommonProtoVersion: String  = "2.9.6-0"
    val SilencerVersion: String            = "1.17.13"
    val LogbackVersion: String             = "1.5.0"
    val ScalapbValidationVersion: String   = "0.1.4"
    val ScalaTestVersion: String           = "3.2.18"
    val AkkaManagementVersion: String      = "1.1.4"
    val AkkaProjectionVersion: String      = "1.2.5"
    val PostgresDriverVersion: String      = "42.7.2"
    val SlickVersion: String               = "3.3.3"
    val AkkaPersistenceJdbcVersion: String = "5.1.0"
    val ScalaMockVersion: String           = "5.2.0"

    val JaninoVersion: String                          = "3.1.12"
    val LogstashLogbackVersion: String                 = "6.3"
    val OpenTelemetrySdkVersion: String                = "1.36.0"
    val TestContainers: String                         = "0.41.3"
    val OpenTelemetrySdkConfigVersion: String          = "1.36.0"
    val OpenTelemetryInstrumentationApiVersion: String = "2.1.0"
    val OpenTelemetrySdkTestingVersion: String         = "1.36.0"
    val OpenTelemetryExtensionVersion: String          = "1.36.0"
    val OpenTelemetryGRPCVersion: String               = "1.0.1-alpha"
    val ScalaXmlVersion: String                        = "2.2.0"
    val JacksonVersion: String                         = "2.16.1"
  }

  import Dependencies.Versions._

  val excludeGRPC = ExclusionRule(organization = "io.grpc")
  val jars: Seq[ModuleID] = Seq(
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % ScalapbCommonProtoVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime"      % scalapbVersion % "protobuf",
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % ScalapbCommonProtoVersion % "protobuf",
    "io.grpc"                        % "grpc-netty"                        % grpcJavaVersion,
    "com.typesafe.akka"             %% "akka-persistence-typed"            % AkkaVersion,
    "com.typesafe.akka"             %% "akka-serialization-jackson"        % AkkaVersion,
    "com.typesafe.akka"             %% "akka-cluster-sharding-typed"       % AkkaVersion,
    "ch.qos.logback"                 % "logback-classic"                   % LogbackVersion,
    "com.typesafe.akka"             %% "akka-cluster-typed"                % AkkaVersion,
    "com.typesafe.akka"             %% "akka-stream-typed"                 % AkkaVersion,
    "com.typesafe.akka"             %% "akka-discovery"                    % AkkaVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % AkkaManagementVersion,
    "com.lightbend.akka.discovery"  %% "akka-discovery-kubernetes-api"     % AkkaManagementVersion,
    "com.lightbend.akka.management" %% "akka-management-cluster-http"      % AkkaManagementVersion,
    "com.lightbend.akka"            %% "akka-projection-core"              % AkkaProjectionVersion,
    "com.lightbend.akka"            %% "akka-projection-kafka"             % AkkaProjectionVersion,
    "com.lightbend.akka"     %% "akka-projection-eventsourced" % Versions.AkkaProjectionVersion,
    "com.lightbend.akka"     %% "akka-projection-jdbc"         % Versions.AkkaProjectionVersion,
    "com.typesafe.akka"      %% "akka-persistence-typed"       % AkkaVersion,
    "org.postgresql"          % "postgresql"                   % Versions.PostgresDriverVersion,
    "com.lightbend.akka"     %% "akka-persistence-jdbc"        % AkkaPersistenceJdbcVersion,
    "com.typesafe.akka"      %% "akka-persistence-query"       % AkkaVersion,
    "com.typesafe.slick"     %% "slick"                        % SlickVersion,
    "com.typesafe.slick"     %% "slick-hikaricp"               % SlickVersion,
    "net.logstash.logback"    % "logstash-logback-encoder"     % Versions.LogstashLogbackVersion,
    "org.codehaus.janino"     % "janino"                       % Versions.JaninoVersion,
    "org.scala-lang"          % "scala-reflect"                % Versions.ScalaVersion,
    "org.scala-lang.modules" %% "scala-xml"                    % Versions.ScalaXmlVersion,
    // Jackson
    "com.fasterxml.jackson.core"       % "jackson-core"            % Versions.JacksonVersion,
    "com.fasterxml.jackson.core"       % "jackson-annotations"     % Versions.JacksonVersion,
    "com.fasterxml.jackson.core"       % "jackson-databind"        % Versions.JacksonVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % Versions.JacksonVersion,
    "com.fasterxml.jackson.module"    %% "jackson-module-scala"    % Versions.JacksonVersion,
    // Opentelemetry
    "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % OpenTelemetryExtensionVersion,
    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-api" % OpenTelemetryInstrumentationApiVersion,
    "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % OpenTelemetrySdkConfigVersion,
    "io.opentelemetry.instrumentation" % "opentelemetry-grpc-1.5" % OpenTelemetryGRPCVersion,
    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-annotations" % OpenTelemetryInstrumentationApiVersion,
    "io.opentelemetry" % "opentelemetry-sdk" % OpenTelemetrySdkVersion
  )

  val testJars: Seq[ModuleID] = Seq(
    "com.typesafe.akka"  %% "akka-actor-testkit-typed"  % AkkaVersion           % Test,
    "com.typesafe.akka"  %% "akka-persistence-testkit"  % AkkaVersion           % Test,
    "com.lightbend.akka" %% "akka-projection-testkit"   % AkkaProjectionVersion % Test,
    "com.typesafe.akka"  %% "akka-stream-testkit"       % AkkaVersion           % Test,
    "org.scalatest"      %% "scalatest"                 % ScalaTestVersion      % Test,
    "org.scalamock"      %% "scalamock"                 % ScalaMockVersion      % Test,
    "io.grpc"             % "grpc-testing"              % grpcJavaVersion       % Test,
    "io.opentelemetry"    % "opentelemetry-sdk-testing" % OpenTelemetrySdkTestingVersion,
    // test containers
    "com.dimafeng" %% "testcontainers-scala-scalatest"  % Versions.TestContainers % Test,
    "com.dimafeng" %% "testcontainers-scala-postgresql" % Versions.TestContainers % Test
  )
}
