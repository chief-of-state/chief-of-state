# 📡 Subscription (Real-Time Event Streaming)

Chief of State can stream entity events in real time to clients via gRPC. When the subscription feature is enabled, clients can subscribe to events for a **single entity** or for **all entities**, and receive each event as it is persisted—without reading from the journal.

See the [configuration](./configuration.md) guide for deployment and environment variables.

## 📋 Overview

- **Subscribe** — Stream events for one entity by `entity_id`. The stream stays open until the client disconnects. If the entity does not exist yet, events are delivered once the entity is created.
- **SubscribeAll** — Stream events for all entities. The stream stays open until the client disconnects.
- **Unsubscribe** / **UnsubscribeAll** — Stop a subscription using the `subscription_id` returned (or provided) when subscribing.

Events are published as soon as they are persisted. Subscription uses Pekko Distributed Pub/Sub (Topic) so clients receive events in real time with no extra database reads.

> **Note:** Subscription is **gRPC-only**. The [HTTP API](./http.md) does not expose streaming; use the Chief of State gRPC client for subscription.

## ⚙️ Enabling Subscription

Subscription is **disabled** by default. Enable it with:

| Environment Variable     | Description                                              | Default |
|--------------------------|----------------------------------------------------------|---------|
| COS_SUBSCRIPTION_ENABLED | Set to `true` to enable Subscribe / SubscribeAll streams | false   |

**Example (Docker / env):**

```shell
COS_SUBSCRIPTION_ENABLED=true
```

**Example (application.conf):**

```hocon
chiefofstate {
  subscription {
    enabled = true
  }
}
```

When subscription is disabled, `Subscribe` and `SubscribeAll` return gRPC status **UNIMPLEMENTED** with the description: *"Subscription is not enabled; configure subscription to use streaming"*.

## 📡 gRPC API

The subscription RPCs are defined in the [Chief of State service proto](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/service.proto). Generate the client stubs for your language from that repo.

### Subscribe (single entity)

**RPC:** `Subscribe(SubscribeRequest) returns (stream SubscribeResponse)`

| Field           | Type   | Required | Description                                                               |
|-----------------|--------|----------|---------------------------------------------------------------------------|
| entity_id       | string | Yes      | Entity unique id to subscribe to. Must be non-empty.                      |
| subscription_id | string | No       | Client-provided id for this subscription. If empty, server generates one. |

- **Stream:** Each message is a `SubscribeResponse` with `event`, `resulting_state`, `meta`, and `subscription_id`.
- **Errors:** `INVALID_ARGUMENT` if `entity_id` is empty; `UNIMPLEMENTED` if subscription is not enabled.

### SubscribeAll (all entities)

**RPC:** `SubscribeAll(SubscribeAllRequest) returns (stream SubscribeAllResponse)`

| Field           | Type   | Required | Description                                                               |
|-----------------|--------|----------|---------------------------------------------------------------------------|
| subscription_id | string | No       | Client-provided id for this subscription. If empty, server generates one. |

- **Stream:** Each message is a `SubscribeAllResponse` with `event`, `resulting_state`, `meta`, and `subscription_id`.
- **Errors:** `UNIMPLEMENTED` if subscription is not enabled.

### Unsubscribe (single-entity subscription)

**RPC:** `Unsubscribe(UnsubscribeRequest) returns (UnsubscribeResponse)`

| Field           | Type   | Required | Description                                           |
|-----------------|--------|----------|-------------------------------------------------------|
| subscription_id | string | Yes      | Id returned from `Subscribe` (or provided by client). |

- **Errors:** `INVALID_ARGUMENT` if `subscription_id` is empty; `UNIMPLEMENTED` if subscription is not enabled.

### UnsubscribeAll (all-entities subscription)

**RPC:** `UnsubscribeAll(UnsubscribeAllRequest) returns (UnsubscribeAllResponse)`

| Field           | Type   | Required | Description                                              |
|-----------------|--------|----------|----------------------------------------------------------|
| subscription_id | string | Yes      | Id returned from `SubscribeAll` (or provided by client). |

- **Errors:** `INVALID_ARGUMENT` if `subscription_id` is empty; `UNIMPLEMENTED` if subscription is not enabled.

## 📦 Stream Response Format

Each streamed message (`SubscribeResponse` or `SubscribeAllResponse`) contains:

| Field           | Type     | Description                                                          |
|-----------------|----------|----------------------------------------------------------------------|
| subscription_id | string   | Id for this subscription; use with `Unsubscribe` / `UnsubscribeAll`. |
| event           | Any      | The event that was emitted (protobuf `google.protobuf.Any`).         |
| resulting_state | Any      | The entity state after the event was applied.                        |
| meta            | MetaData | Entity id, revision number, revision date, headers, etc.             |

`MetaData` (from `chief_of_state/v1/common.proto`) includes `entity_id`, `revision_number`, `revision_date`, and optional `headers` / `data`.

## 📝 Usage Notes

1. **Subscription id** — You can pass `subscription_id` in the request to correlate streams with your own id. If you omit it, Chief of State returns a generated id in the first (and every) stream response. Use that id when calling `Unsubscribe` or `UnsubscribeAll`.
2. **Stream lifetime** — The stream remains open until the client closes it or disconnects. There is no server-side TTL; call `Unsubscribe` / `UnsubscribeAll` when you no longer need the stream.
3. **Ordering** — Events for a given entity are delivered in the order they were persisted. Across entities, ordering is not guaranteed.
4. **Clustering** — Subscription works across a Chief of State cluster: events are published via Pekko Distributed Pub/Sub, so subscribers receive events regardless of which node persisted them.
5. **Read sides** — Subscription is independent of [read sides](./configuration.md#-read-side-configuration). Read sides process events for projections (e.g. Elasticsearch, Kafka); subscription is for real-time streaming to gRPC clients.

## 📖 Related

- [Configuration](./configuration.md) — All Chief of State options, including `COS_SUBSCRIPTION_ENABLED`.
- [Getting Started](./getting-started.md) — Write side, read side, and sending commands.
- [Chief of State protos](https://github.com/chief-of-state/chief-of-state-protos) — Service and message definitions for `Subscribe`, `SubscribeAll`, `Unsubscribe`, and `UnsubscribeAll`.
