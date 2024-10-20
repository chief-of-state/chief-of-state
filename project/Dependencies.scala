import sbt.*
import sbt.Test
import scalapb.compiler.Version.grpcJavaVersion
import scalapb.compiler.Version.scalapbVersion

object Dependencies {

  // Package versions
  object Versions {
    val ScalaVersion: String                = "2.13.15"
    val PekkoVersion: String                = "1.1.2"
    val ScalapbCommonProtoVersion: String   = "2.9.6-0"
    val SilencerVersion: String             = "1.7.19"
    val LogbackVersion: String              = "1.5.11"
    val ScalaTestVersion: String            = "3.2.19"
    val PekkoManagementVersion: String      = "1.1.0"
    val PekkoProjectionVersion: String      = "1.1.0"
    val PostgresDriverVersion: String       = "42.7.4"
    val SlickVersion: String                = "3.5.2"
    val PekkoPersistenceJdbcVersion: String = "1.1.0"
    val ScalaMockVersion: String            = "6.0.0"

    val JaninoVersion: String                          = "3.1.12"
    val LogstashLogbackVersion: String                 = "8.0"
    val OpenTelemetrySdkVersion: String                = "1.43.0"
    val TestContainers: String                         = "0.41.4"
    val OpenTelemetrySdkConfigVersion: String          = "1.43.0"
    val OpenTelemetryInstrumentationApiVersion: String = "2.8.0"
    val OpenTelemetrySdkTestingVersion: String         = "1.43.0"
    val OpenTelemetryExtensionVersion: String          = "1.43.0"
    val ScalaXmlVersion: String                        = "2.3.0"
    val JacksonVersion: String                         = "2.18.0"
    val NettyVersion: String                           = "4.1.114.Final"
  }

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
    "org.apache.pekko" %% "pekko-persistence-typed"            % PekkoVersion,
    "org.postgresql"    % "postgresql"                         % PostgresDriverVersion,
    "org.apache.pekko" %% "pekko-persistence-jdbc"             % PekkoPersistenceJdbcVersion,
    "org.apache.pekko" %% "pekko-persistence-query"            % PekkoVersion,
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
    "io.opentelemetry" % "opentelemetry-sdk" % OpenTelemetrySdkVersion,
    // Netty dependencies
    "io.netty" % "netty-all" % NettyVersion
  )

  val testJars: Seq[ModuleID] = Seq(
    "org.apache.pekko" %% "pekko-actor-testkit-typed" % PekkoVersion           % Test,
    "org.apache.pekko" %% "pekko-persistence-testkit" % PekkoVersion           % Test,
    "org.apache.pekko" %% "pekko-projection-testkit"  % PekkoProjectionVersion % Test,
    "org.apache.pekko" %% "pekko-stream-testkit"      % PekkoVersion           % Test,
    "org.scalatest"    %% "scalatest"                 % ScalaTestVersion       % Test,
    "org.scalamock"    %% "scalamock"                 % ScalaMockVersion       % Test,
    "io.grpc"           % "grpc-testing"              % grpcJavaVersion        % Test,
    "io.opentelemetry"  % "opentelemetry-sdk-testing" % OpenTelemetrySdkTestingVersion,
    "uk.org.webcompere" % "system-stubs-core"         % "2.1.7"                % Test,

    // test containers
    "com.dimafeng" %% "testcontainers-scala-scalatest"  % TestContainers % Test,
    "com.dimafeng" %% "testcontainers-scala-postgresql" % TestContainers % Test
  )
}
