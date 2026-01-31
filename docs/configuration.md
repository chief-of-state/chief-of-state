# ⚙️ Configuration Options

This section describes the environment variables used to configure Chief of State.

See the deployment-specific guides for relevant configurations:

- [Docker Deployment](./docker-deployment.md)
- [Kubernetes Deployment](./kubernetes-deployment.md)

When using HTTP (`COS_SERVER_PROTOCOL` = `http` or `both`), see the [HTTP API documentation](./http.md) for endpoints and JSON format.

## 🌐 Global Environment Variables

| Environment Variable                 | Description                                                                                                                                                    | Default      |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------|
| LOG_LEVEL                            | Log level: _**DEBUG**_, _**INFO**_, _**WARN**_, _**ERROR**_                                                                                                    | DEBUG        |
| LOG_STYLE                            | Log format: _**STANDARD**_, _**SIMPLE**_, _**JSON**_                                                                                                           | _**JSON**_   |
| COS_SERVER_PROTOCOL                  | Server protocol: _**grpc**_, _**http**_, or _**both**_                                                                                                         | grpc         |
| COS_ADDRESS                          | gRPC server host                                                                                                                                               | 0.0.0.0      |
| COS_PORT                             | gRPC server port                                                                                                                                               | 9000         |
| COS_HTTP_ADDRESS                     | HTTP server host (when HTTP enabled)                                                                                                                           | 0.0.0.0      |
| COS_HTTP_PORT                        | HTTP server port (when HTTP enabled)                                                                                                                           | 9001         |
| COS_DEPLOYMENT_MODE                  | `"docker"` or `"kubernetes"`                                                                                                                                   | `"docker"`   |
| COS_DB_USER                          | Journal, snapshot, and read-side offsets store username                                                                                                        | postgres     |
| COS_DB_PASSWORD                      | Journal, snapshot, and read-side offsets store password                                                                                                        | changeme     |
| COS_DB_HOST                          | Journal, snapshot, and read-side offsets store host                                                                                                            | localhost    |
| COS_DB_PORT                          | Journal, snapshot, and read-side offsets store port                                                                                                            | 5432         |
| COS_DB_NAME                          | Journal, snapshot, and read-side offsets store database name                                                                                                   | postgres     |
| COS_DB_SCHEMA                        | Journal, snapshot, and read-side offsets store schema                                                                                                          | public       |
| COS_DB_POOL_MAX_SIZE                 | Maximum pool size (idle + in-use connections). Default is fine for most apps.                                                                                  | 5            |
| COS_DB_POOL_MIN_IDLE_CONNECTIONS     | Minimum number of idle connections in the pool. Default is fine for most apps.                                                                                 | 1            |
| COS_DB_POOL_IDLE_TIMEOUT_MS          | Maximum time (ms) a connection can sit idle in the pool. Default is fine for most apps.                                                                        | 60000        |
| COS_DB_POOL_MAX_LIFETIME_MS          | Maximum lifetime (ms) of a connection in the pool. Default is fine for most apps.                                                                              | 120000       |
| COS_SNAPSHOT_FREQUENCY               | Save snapshots automatically every N events                                                                                                                    | 100          |
| COS_DISABLE_SNAPSHOT                 | Disable snapshots. Use with care.                                                                                                                              | false        |
| COS_NUM_SNAPSHOTS_TO_RETAIN          | Number of aggregate snapshots to persist for swift recovery                                                                                                    | 2            |
| COS_READ_SIDE_ENABLED                | Enable or disable read sides                                                                                                                                   | false        |
| COS_SUBSCRIPTION_ENABLED             | Enable real-time event streaming (Subscribe / SubscribeAll gRPC streams)                                                                                       | false        |
| COS_READ_SIDE_BREAKER_ENABLED        | Enable circuit breaker for read-side calls. When open, event processing returns without calling the remote handler.                                            | false        |
| COS_READ_SIDE_BREAKER_MAX_FAILURES   | Maximum failures before opening the circuit (1–100 when enabled)                                                                                               | 5            |
| COS_READ_SIDE_BREAKER_CALL_TIMEOUT   | Timeout for each read-side call; use a duration (e.g. `10s`).                                                                                                  | 10s          |
| COS_READ_SIDE_BREAKER_RESET_TIMEOUT  | Time before attempting to close an open circuit; use a duration (e.g. `1m`).                                                                                   | 1m           |
| COS_WRITE_SIDE_PROTOCOL              | Protocol for write-side handler: _**grpc**_ or _**http**_                                                                                                      | grpc         |
| COS_WRITE_SIDE_HOST                  | Host of the write-side handler service                                                                                                                         | (none)       |
| COS_WRITE_SIDE_PORT                  | Port of the write-side handler service                                                                                                                         | (none)       |
| COS_WRITE_SIDE_USE_TLS               | Use TLS for outbound calls (gRPC: TLS; HTTP: https)                                                                                                            | false        |
| COS_WRITE_SIDE_PROTO_VALIDATION      | Validate handler service states and events proto FQN. If not `true`, validation is skipped.                                                                    | false        |
| COS_WRITE_SIDE_STATE_PROTOS          | Handler state proto FQN (fully qualified typeUrl). Format: `packagename.messagename`. Comma-separated list.                                                    | (none)       |
| COS_WRITE_SIDE_EVENT_PROTOS          | Handler event proto FQN (fully qualified typeUrl). Format: `packagename.messagename`. Comma-separated list.                                                    | (none)       |
| COS_SERVICE_NAME                     | Service name                                                                                                                                                   | chiefofstate |
| COS_WRITE_SIDE_PROPAGATED_HEADERS    | CSV of gRPC headers to propagate to the write-side handler                                                                                                     | (none)       |
| COS_WRITE_PERSISTED_HEADERS          | CSV of gRPC headers to persist to the journal (experimental)                                                                                                   | (none)       |
| COS_WRITE_SIDE_BREAKER_ENABLED       | Enable circuit breaker for write-side calls. When open, calls fail fast without calling the remote handler.                                                    | false        |
| COS_WRITE_SIDE_BREAKER_MAX_FAILURES  | Maximum failures before opening the circuit (1–100 when enabled)                                                                                               | 5            |
| COS_WRITE_SIDE_BREAKER_CALL_TIMEOUT  | Timeout for each call; use a duration (e.g. `60s`). Should align with gRPC deadline.                                                                           | 60s          |
| COS_WRITE_SIDE_BREAKER_RESET_TIMEOUT | Time before attempting to close an open circuit; use a duration (e.g. `1m`).                                                                                   | 1m           |
| COS_JOURNAL_LOGICAL_DELETION         | Event deletion is triggered after saving a new snapshot. Old events are deleted before old snapshots.                                                          | false        |
| COS_COMMAND_HANDLER_TIMEOUT          | Timeout for the aggregate to process a command and reply (seconds)                                                                                             | 5            |
| COS_NUM_SHARDS                       | Number of shards for cluster sharding. Must be a **power of 2** (e.g. 8, 16, 32) for optimal distribution. Same value required on all nodes.                   | 16           |
| COS_GRPC_CALLS_TIMEOUT               | RPC deadline (milliseconds). Failure to meet this results in a timeout exception.                                                                              | 60000        |
| COS_GRPC_KEEPALIVE_ENABLED           | Enable HTTP/2 keepalive on outbound gRPC channels (write-side and read-side). Reduces "http2 exception" under load when connections are closed by LB/firewall. | false        |
| COS_GRPC_KEEPALIVE_TIME              | Interval between keepalive pings. Use a duration (e.g. `120s`).                                                                                                | 120s         |
| COS_GRPC_KEEPALIVE_TIMEOUT           | How long to wait for ping ack before closing the connection. Use a duration (e.g. `60s`).                                                                      | 60s          |
| COS_GRPC_KEEPALIVE_WITHOUT_CALLS     | Send keepalive pings even when there are no active RPCs (recommended for long-lived connections).                                                              | true         |

## 📡 Telemetry Configuration

Chief of State uses [OpenTelemetry](https://opentelemetry.io/docs/java/) for metrics and tracing. It bundles the [OTLP](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/protocol/otlp.md) gRPC exporter, which pushes metrics and traces to an [OpenTelemetry Collector](https://opentelemetry.io/docs/collector/) that forwards them to your monitoring stack.

Telemetry is enabled when `OTEL_JAVAAGENT_ENABLED` is set to `true`.

> **Note:** By default, telemetry data is collected. Set `OTEL_JAVAAGENT_ENABLED=false` when you don't need it.

### Basic Configuration

| Property                    | Required                                         | Description                                                                                                                                                                |
|-----------------------------|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| OTEL_SERVICE_NAME           | No (when `OTEL_JAVAAGENT_ENABLED` is true)       | Name to differentiate Chief of State deployments                                                                                                                           |
| OTEL_EXPORTER_OTLP_ENDPOINT | No (when `OTEL_JAVAAGENT_ENABLED` is true)       | gRPC endpoint for the OpenTelemetry Collector, e.g. `http://otlp.collector:4317`                                                                                           |
| OTEL_JAVAAGENT_ENABLED      | Yes                                              | Set to `false` to disable telemetry instrumentation                                                                                                                        |
| OTEL_PROPAGATORS            | No (default: `tracecontext,baggage`)             | See [propagator setup](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#propagator)                                   |

### Advanced Configuration

Advanced users can use any [OpenTelemetry environment variables](https://github.com/open-telemetry/opentelemetry-java/blob/main/sdk-extensions/autoconfigure/README.md#exporters) to tweak the agent before starting Chief of State.

## 📖 Read Side Configuration

Chief of State supports any number of read sides. Each read side can use either **gRPC** or **HTTP** to receive events.

You can configure read sides in two ways:

### Via YAML Configuration File(s) — Recommended

Read sides are configured using YAML files. You can put all read-side configs in a **single YAML file** or in **separate files** inside a folder.

**Required settings:**

| Setting       | Required              | Description                                                                                                                                |
|---------------|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------|
| readSideId    | Yes                   | Unique identifier. Alphanumeric plus hyphens and underscores only. No spaces.                                                              |
| protocol      | No (default: `grpc`)  | Protocol: `grpc` or `http`                                                                                                                 |
| host          | Yes                   | Read-side host (used for both gRPC and HTTP)                                                                                               |
| port          | Yes                   | Read-side port (used for both gRPC and HTTP)                                                                                               |
| useTls        | No (default: `false`) | Use TLS. For gRPC: TLS negotiation. For HTTP: uses `https` instead of `http`.                                                              |
| timeout       | No (default: `30000`) | Timeout in milliseconds. For gRPC: call deadline. For HTTP: request timeout.                                                               |
| autoStart     | No (default: `true`)  | `true` = ready to process on start. `false` = paused on start. Use the [CLI](https://github.com/chief-of-state/cos-cli) to resume.         |
| enabled       | No (default: `true`)  | Enable or disable the read side. Unlike `autoStart`, changing this requires a restart. `autoStart` only controls whether it starts paused. |
| failurePolicy | No                    | Failure policy: `STOP`, `SKIP`, `REPLAY_SKIP`, `REPLAY_STOP`                                                                               |

#### Failure Policies

- **`STOP`** — Completely stop the read side when event processing fails
- **`SKIP`** — Skip the failed event, advance the offset, and continue
- **`REPLAY_SKIP`** — Replay the failed event up to five times, then skip
- **`REPLAY_STOP`** — Replay the failed event up to five times, then stop

#### Example: `read-side-config.yml` (multiple read sides in one file)

```yaml
# gRPC read side (timeout = gRPC call deadline)
readSideId: read-side-1
protocol: grpc
host: read-handler
port: 50053
useTls: false
timeout: 30000
autoStart: true
enabled: true
failurePolicy: SKIP
---
# HTTP read side (URL: http(s)://host:port, timeout = request timeout)
readSideId: read-side-2
protocol: http
host: read-handler
port: 8080
useTls: false
timeout: 30000
autoStart: true
enabled: true
failurePolicy: REPLAY_SKIP
---
# HTTP read side with TLS and custom timeout
readSideId: read-side-3
protocol: http
host: read-handler
port: 8081
useTls: true
timeout: 60000
autoStart: true
enabled: false
```

> **Tip:** Use the `---` YAML separator to define multiple read sides in one file.

Set the config path with:

| Environment Variable | Description                                                                                    |
|----------------------|------------------------------------------------------------------------------------------------|
| COS_READ_SIDE_CONFIG | Path to the read-side config file, or a folder containing read-side YAML configs               |

**Examples:**

- Config file at `/etc/read-side-config.yml`:
  ```shell
  COS_READ_SIDE_CONFIG=/etc/read-side-config.yml
  ```

- Config folder at `/etc`:
  ```shell
  COS_READ_SIDE_CONFIG=/etc
  ```

### Via Environment Variables

> **Note:** This approach is not scalable and can be error-prone. Prefer YAML config files.

| Environment Variable                             | Description                      | Default |
|--------------------------------------------------|----------------------------------|---------|
| `COS_READ_SIDE_CONFIG__<SETTING>__<READSIDE_ID>` | Read-side configuration settings | (none)  |

**<SETTING>** values:

- **PROTOCOL** — `grpc` or `http` (default: `grpc`)
- **HOST** — Read-side host (used for both gRPC and HTTP)
- **PORT** — Read-side port (used for both gRPC and HTTP)
- **USE_TLS** — Use TLS. For HTTP, uses `https` instead of `http` (default: `false`)
- **TIMEOUT** — Timeout in milliseconds. For gRPC: call deadline. For HTTP: request timeout (default: `30000`)
- **AUTO_START** — `true` = ready on start; `false` = paused on start. Use the [CLI](https://github.com/chief-of-state/cos-cli) to resume (default: `true`)
- **ENABLED** — `true` = enabled; `false` = disabled. Changing this requires a restart; `autoStart` only controls pause state (default: `true`)
- **FAILURE_POLICY** — See [Failure Policies](#failure-policies) above

**<READSIDE_ID>** — Unique ID for the read side. Replace with your actual ID.

**Example:**

```shell
# gRPC read side (timeout = gRPC call deadline)
COS_READ_SIDE_CONFIG__PROTOCOL__DB_WRITER=grpc
COS_READ_SIDE_CONFIG__HOST__DB_WRITER=db-writer
COS_READ_SIDE_CONFIG__PORT__DB_WRITER=50053
COS_READ_SIDE_CONFIG__USE_TLS__DB_WRITER=false
COS_READ_SIDE_CONFIG__TIMEOUT__DB_WRITER=30000
COS_READ_SIDE_CONFIG__AUTO_START__DB_WRITER=false
COS_READ_SIDE_CONFIG__ENABLED__DB_WRITER=false
COS_READ_SIDE_CONFIG__FAILURE_POLICY__DB_WRITER=REPLAY_SKIP

# HTTP read side (URL: http(s)://host:port)
COS_READ_SIDE_CONFIG__PROTOCOL__HTTP_HANDLER=http
COS_READ_SIDE_CONFIG__HOST__HTTP_HANDLER=handler
COS_READ_SIDE_CONFIG__PORT__HTTP_HANDLER=8080
COS_READ_SIDE_CONFIG__TIMEOUT__HTTP_HANDLER=30000
COS_READ_SIDE_CONFIG__AUTO_START__HTTP_HANDLER=true
COS_READ_SIDE_CONFIG__ENABLED__HTTP_HANDLER=true
COS_READ_SIDE_CONFIG__FAILURE_POLICY__HTTP_HANDLER=SKIP
```
