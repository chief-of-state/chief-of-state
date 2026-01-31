# HTTP API

Chief of State exposes an HTTP/JSON API that mirrors the gRPC service. Use it to process commands and retrieve entity state when `COS_SERVER_PROTOCOL` is `http` or `both`.

**Base URL**: `http://localhost:9001` (default; set `COS_HTTP_ADDRESS` and `COS_HTTP_PORT` as needed)

**Content-Type**: `application/json`

---

## Quick start: How to use the HTTP API

### 1. Get entity state

```bash
curl http://localhost:9001/v1/entities/user-123/state
```

Returns the current state and metadata for the entity.

### 2. Send a command (custom types – recommended)

For your own command types (defined in your protos), use **base64-encoded protobuf**:

1. Serialize your command to protobuf.
2. Pack it in `google.protobuf.Any`.
3. Base64-encode the bytes.
4. Send in the `command` field as a string.

**Example (conceptual):**

```
bytes = serialize(Any.pack(myCommand))
base64Command = base64(bytes)
```

**Request:**

```bash
curl -X POST http://localhost:9001/v1/commands/user-123/process \
  -H "Content-Type: application/json" \
  -d '{"entity_id":"user-123","command":"<your-base64-encoded-any-bytes>"}'
```

No registration or configuration is required; Chief of State forwards the bytes to your write-side handler.

### 3. Optional: Propagate headers

Add headers for tracing, auth, or correlation. Chief of State forwards them to the write-side handler:

```bash
curl -X POST http://localhost:9001/v1/commands/user-123/process \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: abc-123" \
  -H "Authorization: Bearer <token>" \
  -d '{"entity_id":"user-123","command":"<base64>"}'
```

---

## Endpoints

### Process Command

**POST** `/v1/commands/{entityId}/process`

Sends a command to the given entity. Chief of State provisions the entity if needed, then forwards the command to your write-side handler.

|                    |                                                                                 |
|--------------------|---------------------------------------------------------------------------------|
| **Path parameter** | `entityId` (required) – unique identifier for the entity                        |
| **Request body**   | JSON object (see below)                                                         |
| **Success**        | `200 OK` with `ProcessCommandResponse`                                          |
| **Error**          | `400 Bad Request` (invalid JSON), `500 Internal Server Error` (handler failure) |

#### Request body

```json
{
  "entity_id": "user-123",
  "command": "<base64-encoded-protobuf-any-bytes>"
}
```

- **`entity_id`** – optional. If omitted, the path `entityId` is used. If both are present, the path wins.
- **`command`** – optional. **Always base64-encoded.** Pack your command in `google.protobuf.Any`, serialize to bytes (wire format), then base64-encode. No type registration is required.

#### Response

```json
{
  "state": "<base64-encoded-protobuf-any-bytes>",
  "meta": {
    "entity_id": "user-123",
    "revision_number": 2,
    "revision_date": "2025-01-31T10:00:00.000Z"
  }
}
```

- **`state`** – base64-encoded protobuf `Any` (resulting entity state)
- **`meta`** – metadata (entity ID, revision, timestamp)

---

### Get State

**GET** `/v1/entities/{entityId}/state`

Returns the current state and metadata for the entity.

|                    |                                                          |
|--------------------|----------------------------------------------------------|
| **Path parameter** | `entityId` (required) – unique identifier for the entity |
| **Success**        | `200 OK` with `GetStateResponse`                         |
| **Error**          | `500 Internal Server Error` (e.g. entity lookup failure) |

#### Response

```json
{
  "state": "<base64-encoded-protobuf-any-bytes>",
  "meta": {
    "entity_id": "user-123",
    "revision_number": 2,
    "revision_date": "2025-01-31T10:00:00.000Z"
  }
}
```

- **`state`** – base64-encoded protobuf `Any` (current entity state)
- **`meta`** – metadata (entity ID, revision, timestamp)

---

## Error response

On failure:

```json
{
  "error": "human-readable error message"
}
```

---

## Complete examples

### Get state

```bash
curl http://localhost:9001/v1/entities/user-123/state
```

### Process command (base64)

```bash
# Replace <base64> with base64(serialize(Any.pack(myCommand)))
curl -X POST http://localhost:9001/v1/commands/user-123/process \
  -H "Content-Type: application/json" \
  -d '{"entity_id":"user-123","command":"<base64>"}'
```

### Process command with headers

```bash
curl -X POST http://localhost:9001/v1/commands/order-456/process \
  -H "Content-Type: application/json" \
  -H "X-Request-Id: abc-123" \
  -d '{"entity_id":"order-456","command":"<base64>"}'
```

### Minimal body (entity ID from path)

```bash
curl -X POST http://localhost:9001/v1/commands/user-123/process \
  -H "Content-Type: application/json" \
  -d '{"command":"<base64>"}'
```

---

## HTTP read handler (implementing)

When using `protocol: http` in your read-side config, Chief of State pushes events to your HTTP endpoint. Implement this contract so Chief of State can deliver events.

### Endpoint

**POST** `/v1/readside/handle`

Chief of State sends one POST per event. Your handler processes the event and returns whether to commit the offset.

### Request

|                     |                                                                 |
|---------------------|-----------------------------------------------------------------|
| **Method**          | POST                                                            |
| **URL**             | `http(s)://host:port/v1/readside/handle`                        |
| **Content-Type**    | `application/json`                                              |
| **Headers**         | `x-cos-entity-id`, `x-cos-event-tag`                            |
| **Body**            | `HandleReadSideRequest` (JSON)                                  |

#### Request headers

| Header              | Description                           |
|---------------------|---------------------------------------|
| `x-cos-entity-id`   | Entity ID                             |
| `x-cos-event-tag`   | Event tag                             |

#### Request body

```json
{
  "event": "<base64-encoded-protobuf-any-bytes>",
  "state": "<base64-encoded-protobuf-any-bytes>",
  "meta": {
    "entity_id": "user-123",
    "revision_number": 2,
    "revision_date": "2025-01-31T10:00:00.000Z"
  },
  "read_side_id": "my-read-side"
}
```

- **`event`** – base64-encoded protobuf `Any` (the event)
- **`state`** – base64-encoded protobuf `Any` (resulting state after the event)
- **`meta`** – metadata (entity ID, revision, timestamp)
- **`read_side_id`** – read side ID from config

### Response

| Status    | Meaning                                                         |
|-----------|-----------------------------------------------------------------|
| **200 OK**| Request processed. Response body controls offset commitment.    |
| **Other** | Error. Chief of State retries according to `failurePolicy`.     |

#### Response body (200 OK)

```json
{
  "successful": true
}
```

- **`successful: true`** – commit offset and advance
- **`successful: false`** – do not commit; Chief of State retries

### Config

Set your read-side handler URL in the read-side config:

```yaml
readSideId: my-read-side
protocol: http
host: my-handler
port: 8080
useTls: false
autoStart: true
enabled: true
failurePolicy: REPLAY_SKIP
```

Chief of State calls `http://my-handler:8080/v1/readside/handle` (or `https://` when `useTls: true`).

### Event and state format

`event` and `state` are **base64-encoded** protobuf `Any`. Decode the base64 to bytes, parse as protobuf `Any`, then unpack to your event/state types. No type registration is required.


---

## JSON format

- Field names use **snake_case** (`entity_id`, `revision_number`, `revision_date`).
- All command, event, and state fields (`protobuf.Any`) are **base64-encoded** protobuf wire format. No TypeRegistry or type registration.
- `revision_date` is ISO 8601 (e.g. `2025-01-31T10:00:00.000Z`).
