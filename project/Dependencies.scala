import sbt.*
import sbt.Test
import scalapb.compiler.Version.grpcJavaVersion
import scalapb.compiler.Version.scalapbVersion

object Dependencies {

  // Package versions
  object Versions {
    val ScalaVersion: String                = "3.3.6"
    val PekkoVersion: String                = "1.4.0"
    val PekkoHttpVersion: String            = "1.1.0"
    val ScalapbCommonProtoVersion: String   = "2.9.6-0"
    val SilencerVersion: String             = "1.7.19"
    val LogbackVersion: String              = "1.5.26"
    val ScalaTestVersion: String            = "3.2.19"
    val PekkoManagementVersion: String      = "1.2.0"
    val PekkoProjectionVersion: String      = "1.1.0"
    val PostgresDriverVersion: String       = "42.7.9"
    val SlickVersion: String                = "3.6.1"
    val PekkoPersistenceJdbcVersion: String = "1.1.1"
    val ScalaMockVersion: String            = "7.4.1"

    val JaninoVersion: String                          = "3.1.12"
    val LogstashLogbackVersion: String                 = "8.1"
    val OpenTelemetrySdkVersion: String                = "1.58.0"
    val TestContainers: String                         = "0.44.1"
    val OpenTelemetrySdkConfigVersion: String          = "1.58.0"
    val OpenTelemetryInstrumentationApiVersion: String = "2.19.0"
    val OpenTelemetrySdkTestingVersion: String         = "1.58.0"
    val OpenTelemetryExtensionVersion: String          = "1.58.0"
    val OpenTelemetryApiVersion: String                = "1.58.0"
    val ScalaXmlVersion: String                        = "2.4.0"
    val JacksonVersion: String                         = "2.19.2"
    val NettyVersion: String                           = "4.2.9.Final"
  }

  // include the dependencies
  import Dependencies.Versions._

  val excludeGRPC = ExclusionRule(organization = "io.grpc")
  val jars: Seq[ModuleID] = Seq(
    // Protocol buffers dependencies
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % ScalapbCommonProtoVersion,
    "com.thesamet.scalapb" %% "scalapb-runtime"      % scalapbVersion % "protobuf",
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapbVersion,
    "com.thesamet.scalapb.common-protos" %% "proto-google-common-protos-scalapb_0.11" % ScalapbCommonProtoVersion % "protobuf",
    "io.grpc" % "grpc-netty" % grpcJavaVersion,
    // Pekko dependencies
    "org.apache.pekko" %% "pekko-persistence-typed"            % PekkoVersion,
    "org.apache.pekko" %% "pekko-serialization-jackson"        % PekkoVersion,
    "ch.qos.logback"    % "logback-classic"                    % LogbackVersion,
    "org.apache.pekko" %% "pekko-cluster-typed"                % PekkoVersion,
    "org.apache.pekko" %% "pekko-stream-typed"                 % PekkoVersion,
    "org.apache.pekko" %% "pekko-cluster-sharding-typed"       % PekkoVersion,
    "org.apache.pekko" %% "pekko-management-cluster-bootstrap" % PekkoManagementVersion,
    "org.apache.pekko" %% "pekko-cluster"                      % PekkoVersion,
    "org.apache.pekko" %% "pekko-cluster-sharding"             % PekkoVersion,
    "org.apache.pekko" %% "pekko-discovery"                    % PekkoVersion,
    "org.apache.pekko" %% "pekko-management-cluster-http"      % PekkoManagementVersion,
    "org.apache.pekko" %% "pekko-discovery-kubernetes-api"     % PekkoManagementVersion,
    "org.apache.pekko" %% "pekko-projection-core"              % PekkoProjectionVersion,
    "org.apache.pekko" %% "pekko-projection-kafka"             % PekkoProjectionVersion,
    "org.apache.pekko" %% "pekko-projection-eventsourced"      % PekkoProjectionVersion,
    "org.apache.pekko" %% "pekko-projection-jdbc"              % PekkoProjectionVersion,
    "org.postgresql"    % "postgresql"                         % PostgresDriverVersion,
    "org.apache.pekko" %% "pekko-persistence-jdbc"             % PekkoPersistenceJdbcVersion,
    "org.apache.pekko" %% "pekko-persistence-query"            % PekkoVersion,
    // Pekko HTTP dependencies
    "org.apache.pekko" %% "pekko-http"            % PekkoHttpVersion,
    "org.apache.pekko" %% "pekko-http-spray-json" % PekkoHttpVersion,
    // Slick dependencies
    "com.typesafe.slick"     %% "slick"                    % SlickVersion,
    "com.typesafe.slick"     %% "slick-hikaricp"           % SlickVersion,
    "net.logstash.logback"    % "logstash-logback-encoder" % LogstashLogbackVersion,
    "org.codehaus.janino"     % "janino"                   % JaninoVersion,
    "org.scala-lang.modules" %% "scala-xml"                % ScalaXmlVersion,
    // Jackson dependencies
    "com.fasterxml.jackson.core"       % "jackson-core"            % JacksonVersion,
    "com.fasterxml.jackson.core"       % "jackson-annotations"     % JacksonVersion,
    "com.fasterxml.jackson.core"       % "jackson-databind"        % JacksonVersion,
    "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % JacksonVersion,
    "com.fasterxml.jackson.module"    %% "jackson-module-scala"    % JacksonVersion,
    // Open-Telemetry dependencies
    "io.opentelemetry" % "opentelemetry-extension-trace-propagators" % OpenTelemetryExtensionVersion,
    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-api" % OpenTelemetryInstrumentationApiVersion,
    "io.opentelemetry" % "opentelemetry-sdk-extension-autoconfigure" % OpenTelemetrySdkConfigVersion,
    "io.opentelemetry.instrumentation" % "opentelemetry-instrumentation-annotations" % OpenTelemetryInstrumentationApiVersion,
    "io.opentelemetry" % "opentelemetry-sdk"         % OpenTelemetrySdkVersion,
    "io.opentelemetry" % "opentelemetry-sdk-trace"   % OpenTelemetrySdkVersion,
    "io.opentelemetry" % "opentelemetry-sdk-metrics" % OpenTelemetrySdkVersion,
    "io.opentelemetry" % "opentelemetry-api"         % OpenTelemetryApiVersion,
    // Netty dependencies
    "io.netty" % "netty-all" % NettyVersion
  )

  val testJars: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % PekkoVersion           % Test,
    "org.apache.pekko" %% "pekko-persistence-testkit" % PekkoVersion           % Test,
    "org.apache.pekko" %% "pekko-projection-testkit"  % PekkoProjectionVersion % Test,
    "org.apache.pekko" %% "pekko-stream-testkit"      % PekkoVersion           % Test,
    "org.apache.pekko" %% "pekko-http-testkit"        % PekkoHttpVersion       % Test,
    "org.scalatest"    %% "scalatest"                 % ScalaTestVersion       % Test,
    "org.scalamock"    %% "scalamock"                 % ScalaMockVersion       % Test,
    "io.grpc"           % "grpc-testing"              % grpcJavaVersion        % Test,
    "io.opentelemetry"  % "opentelemetry-sdk-testing" % OpenTelemetrySdkTestingVersion,
    "uk.org.webcompere" % "system-stubs-core"         % "2.1.8"                % Test,

    // test containers
    "com.dimafeng" %% "testcontainers-scala-scalatest"  % TestContainers % Test,
    "com.dimafeng" %% "testcontainers-scala-postgresql" % TestContainers % Test
  )
}
