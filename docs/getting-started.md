# 🚀 Getting Started

This guide describes the anatomy of a typical Chief of State application and the language-agnostic steps to get started. See the [sample projects](https://github.com/chief-of-state/chief-of-state#-sample-projects) for language-specific examples.

## 📦 Generating the Protos

Chief of State defines its public interfaces as [gRPC](https://grpc.io/) services and objects as [protocol-buffers](https://developers.google.com/protocol-buffers) in `.proto` files. You'll need to generate these for your application language to interact with Chief of State and to implement the required write-handler and read-handler methods (more on those below).

[The protos are on GitHub](https://github.com/chief-of-state/chief-of-state-protos)

A popular way to incorporate them into your project is via Git submodules:

```sh
# Add Chief of State protos to proto/cos
git submodule add git@github.com:chief-of-state/chief-of-state-protos ./protos/cos
```

From there, generate the Chief of State interfaces for your language (see the [official gRPC quick start](https://grpc.io/docs/languages/) for your language).

**Example:** Using [protoc](https://grpc.io/docs/protoc-installation/) to generate Java source code:

```sh
export SRC_DIR=protos/cos
export DST_DIR=build/gen
# Generate a single proto
protoc -I=$SRC_DIR --java_out=$DST_DIR $SRC_DIR/path/to/your.proto
# Generate all .proto files in protos/cos
find $SRC_DIR -name "*.proto" | xargs -I{} protoc -I=$SRC_DIR --java_out=$DST_DIR {}
```

> **Pro tip:** Many languages have wrappers around protoc to make this easier, e.g. [ScalaPB](https://scalapb.github.io/) for Scala (which Chief of State uses internally).

## ✍️ Implement a Write Side

Drawing from CQRS terminology, Chief of State defines a [writeside interface](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/writeside.proto) that you must implement to define how state is created and updated.

### Methods

- **`HandleCommand`** — Accepts a command (or "request") and the prior state, and returns an event. For example, given `UpdateUserEmail` and a `User`, this RPC might return `UserEmailUpdated`. Throw exceptions (as gRPC error statuses) **if** the inbound command is invalid.
- **`HandleEvent`** — Accepts an event and the prior state of an entity, and returns the new state. For example, given `UserEmailUpdated` and a `User`, it returns a new `User` instance with the email updated. Do all validations in `HandleCommand` so this method rarely encounters invalid data. If `HandleCommand` fails, `HandleEvent` is not called for that command.

### Registration

Register your write handler service with Chief of State via environment variables:

```yaml
# Host and port for your write handler (gRPC or HTTP server)
COS_WRITE_SIDE_HOST: localhost
COS_WRITE_SIDE_PORT: 50051
```

> New to Chief of State? See the full [configuration](./configuration.md) guide.

### Sending Requests to Chief of State

Once you've implemented a write handler and registered it, you can start sending requests using the Chief of State [gRPC/HTTP client methods](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/service.proto).

- **`ProcessCommand`** — Accepts a command and an entity ID. Processes the command by forwarding it to your write handler. Commands for a given entity are processed serially; commands for unrelated entities can run in parallel.
- **`GetState`** — Accepts an entity ID and returns the latest state. Use it like a key/value lookup.

## 📖 Implement a Read Side

Chief of State defines a [readside interface](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/readside.proto) and lets you implement many read sides to stream events and state to external destinations. For example, you might implement a read side that writes to your private Elasticsearch cluster for search queries, and another that publishes events to Kafka for other services.

Chief of State guarantees that events are served in the order they were persisted, per entity. Offsets are tracked per read side, so adding a read side later will start from the beginning of your event journal (or the oldest available event if you have limited retention).

A read handler implements a single method, **`HandleReadSide`**, which accepts the event, the resulting state after that event, and Chief of State metadata. Your handler can either acknowledge the message and advance the offset, or fail (in which case Chief of State will retry).

### Registration

Register your read handler(s) with these settings:

```yaml
# Enable the read side processor
COS_READ_SIDE_ENABLED: true
# Read side "elastic" on localhost:50052
COS_READ_SIDE_CONFIG__HOST__ELASTIC: localhost
COS_READ_SIDE_CONFIG__PORT__ELASTIC: 50052
# Read side "kafka" on localhost:50053
COS_READ_SIDE_CONFIG__HOST__KAFKA: localhost
COS_READ_SIDE_CONFIG__PORT__KAFKA: 50053
```

See the [read side configuration](./configuration.md#read-side-configuration) docs for more details.

## 📡 Real-Time Event Streaming (Subscription)

Chief of State can stream entity events in real time over gRPC. When subscription is enabled, clients can call **Subscribe** (events for one entity) or **SubscribeAll** (events for all entities) and receive each event as it is persisted. Use **Unsubscribe** / **UnsubscribeAll** with the returned `subscription_id` to stop a stream.

See the [Subscription](./subscription.md) guide for enabling, API details, and usage.
