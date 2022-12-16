# Configuration options

This section describes the environment variables for configuration.

See the following deployment-specific guides for relevant configurations:

- [Docker Deployment](./docker-deployment.md)
- [Kubernetes Deployment](./kubernetes-deployment.md)

### Global environment variables

| environment variable              | description                                                                                                                                            | default                      |
|-----------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------|
| LOG_LEVEL                         | The possible values are: _**DEBUG**_, _**INFO**_, _**WARN**_, _**ERROR**_                                                                              | DEBUG                        |
| LOG_STYLE                         | Logging format: _**STANDARD**_, _**SIMPLE**_, _**JSON**_                                                                                               | _**JSON**_                   |
| COS_ADDRESS                       | container host                                                                                                                                         | 0.0.0.0                      |
| COS_PORT                          | container port                                                                                                                                         | 9000                         |
| COS_DEPLOYMENT_MODE               | "docker" or "kubernetes"                                                                                                                               | "docker"                     |
| COS_DB_USER                       | journal, snapshot and read side offsets store username                                                                                                 | postgres                     |
| COS_DB_PASSWORD                   | journal, snapshot and read side offsets store password                                                                                                 | changeme                     |
| COS_DB_HOST                       | journal, snapshot and read side offsets store host                                                                                                     | localhost                    |
| COS_DB_PORT                       | journal, snapshot and read side offsets store port                                                                                                     | 5432                         |
| COS_DB_NAME                       | journal, snapshot and read side offsets store db name                                                                                                  | postgres                     |
| COS_DB_SCHEMA                     | journal, snapshot and read side offsets store db schema                                                                                                | public                       |
| COS_DB_POOL_MAX_SIZE              | controls the maximum size that the pool is allowed to reach, including both idle and in-use connections. The default value should be ok for most apps. | 10                           |
| COS_DB_POOL_MIN_IDLE_CONNECTIONS  | controls the minimum number of idle connections to maintain in the pool. The default value should be ok for most apps.                                 | 3                            |
| COS_DB_POOL_IDLE_TIMEOUT_MS       | controls the maximum amount of time in milliseconds that a connection is allowed to sit idle in the pool. The default value should be ok for most apps | 30000                        |
| COS_DB_POOL_MAX_LIFETIME_MS       | controls the maximum lifetime of a connection in the pool. The default value should be ok for most apps.                                               | 60000                        |
| COS_SNAPSHOT_FREQUENCY            | Save snapshots automatically every Number of Events                                                                                                    | 100                          |
| COS_NUM_SNAPSHOTS_TO_RETAIN       | Number of Aggregate Snapshot to persist to disk for swift recovery                                                                                     | 2                            |
| COS_READ_SIDE_ENABLED             | turn on readside or not                                                                                                                                | false                        |
| COS_ENCRYPTION_CLASS              | java class to use for encryption                                                                                                                       | <none>                       |
| COS_WRITE_SIDE_HOST               | address of the gRPC writeSide handler service                                                                                                          | <none>                       |
| COS_WRITE_SIDE_PORT               | port for the gRPC writeSide handler service                                                                                                            | <none>                       |
| COS_WRITE_SIDE_USE_TLS            | use TLS for outbound gRPC calls to write side                                                                                                          | false                        |
| COS_WRITE_SIDE_PROTO_VALIDATION   | enable validation of the handler service states and events proto message FQN. If not set to `true` the validation will be skipped.                     | false                        |
| COS_WRITE_SIDE_STATE_PROTOS       | handler service states proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`. This will be a comma separated list of values   | <none>                       |
| COS_WRITE_SIDE_EVENT_PROTOS       | handler service events proto message FQN (fully qualified typeUrl). Format: `packagename.messagename`. This will be a comma separated list of values   | <none>                       |
| COS_SERVICE_NAME                  | service name                                                                                                                                           | chiefofstate                 |
| COS_WRITE_SIDE_PROPAGATED_HEADERS | CSV of gRPC headers to propagate to write side handler                                                                                                 | <none>                       |
| COS_WRITE_PERSISTED_HEADERS       | CSV of gRPC headers to persist to journal (experimental)                                                                                               | <none>                       |
| COS_JOURNAL_LOGICAL_DELETION      | Event deletion is triggered after saving a new snapshot. Old events would be deleted prior to old snapshots being deleted.                             | false                        |
| COS_COMMAND_HANDLER_TIMEOUT       | Timeout required for the Aggregate to process command and reply. The value is in seconds.                                                              | 5                            |

### Telemetry configuration

This library leverages the [io.opentelemetry](https://opentelemetry.io/docs/java/) library for both metrics and tracing
instrumentation. We only bundle in
the [OTLP](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md) gRPC
exporter which should be used to push metrics and traces to
an [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/)
that should then propagate the same to desired monitoring services. Collection of telemetry data will be enabled when the 
`OTEL_JAVAAGENT_ENABLED` is set to `true`. 

_Note: By default, telemetry data will be collected so set this env var to `false` when there is no need for telemetry data._

#### Basic configuration
The following options can be configured via environment variables. 

| Property                    | Required                                         | Description                                                                                                                                                                |
|-----------------------------|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| OTEL_SERVICE_NAME           | no(when `OTEL_JAVAAGENT_ENABLED` is set to true) | Name to be used to differentiate different chief of state deployments                                                                                                      |
| OTEL_EXPORTER_OTLP_ENDPOINT | no(when `OTEL_JAVAAGENT_ENABLED` is set to true) | The grpc endpoint to be use to connect to an [opentelemetry collector](https://opentelemetry.io/docs/collector/) eg.`http://otlp.collector:4317`                           |
| OTEL_JAVAAGENT_ENABLED      | yes                                              | Set to `false` will disable the telemetry instrumentation                                                                                                                  |
| OTEL_PROPAGATORS            | no (default value is `tracecontext,baggage`)     | More information can be found [here](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#propagator) on how to set it up |
  

#### Advanced configuration
Advanced users can use any of the following [environment variables](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#exporters) to tweak the OpenTelemetry Agent before starting CoS.

### Read side configurations
The CoS can handle as many as read sides one desires. CoS read side are configured using environment variables.
The following format defines how a CoS read side environment variable is configured:

| environment variable                                | description                     | default |
|-----------------------------------------------------|---------------------------------|---------|
| COS_READ_SIDE_CONFIG__<SETTING_NAME>__<READSIDE_ID> | readside configuration settings | <none>  |

- <SETTING_NAME> - Accepted values are:
    - **HOST** - Read side host
    - **PORT** - Read side port
    - **USE_TLS** - Use TLS for read side calls. The default value is set to `false`
    - **AUTO_START** - Set to `true` means that the Read side on start is ready to process events. However, when it set to `false` means that the Read side is paused on start or no not. One can use
      the [cli](https://github.com/chief-of-state/cos-cli) to resume processing. The default value is set to `true`
- <READSIDE_ID> - Unique id for the read side instance. Replace this placeholder with your actual ID.

#### Example

```shell
COS_READ_SIDE_CONFIG__HOST__DB_WRITER=db-writer
COS_READ_SIDE_CONFIG__PORT__DB_WRITER=50053
COS_READ_SIDE_CONFIG__USE_TLS__DB_WRITER=false
COS_READ_SIDE_CONFIG__AUTO_START__DB_WRITER=false
```
