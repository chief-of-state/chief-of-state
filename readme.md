# Chief of State

![GitHub Workflow Status (branch)](https://img.shields.io/github/actions/workflow/status/chief-of-state/chief-of-state/master.yml?branch=main)
[![codecov](https://codecov.io/gh/chief-of-state/chief-of-state/graph/badge.svg?token=DBO3JBTHZ0)](https://codecov.io/gh/chief-of-state/chief-of-state)
![GitHub release (latest SemVer including pre-releases)](https://img.shields.io/github/v/release/chief-of-state/chief-of-state?include_prereleases)
[![GitHub](https://img.shields.io/github/license/chief-of-state/chief-of-state)](https://github.com/chief-of-state/chief-of-state/blob/master/LICENSE)

## Table of Contents

- [Overview](#-overview)
- [Features](#-features)
- [Production](#-production)
- [Anatomy](#-anatomy-of-a-chief-of-state-app)
  - [Chief of State Service](#chief-of-state-service)
  - [Write Handler](#write-handler)
  - [Read Handler](#read-handler)
- [Documentation](#-documentation)
- [Community](#-community)
- [Contribution](#-contribution)
- [License](#-license)
- [Sample Projects](#-sample-projects)

## 🎯 Overview

Chief of State (CoS) is an open-source clustered persistence tool for building event-sourced applications. CoS supports CQRS and event-sourcing through simple, language-agnostic interfaces via **gRPC** or **HTTP/JSON**, and it lets you describe your schema with Protobuf. Under the hood, CoS leverages [Apache Pekko](https://pekko.apache.org/) to scale out and deliver performant, reliable persistence.

Chief of State was built with these principles:

- Wire format should be the same as persistence
- Scaling should not require re-architecture
- Developers shouldn't face race conditions or database locks
- Rules should be enforced with interfaces
- Event sourcing is valuable but challenging to implement
- An ideal event-sourcing datastore would offer random access by key, streaming, and atomic writes

Chief of State was created in 2020 at [Namely](https://github.com/namely/chief-of-state) based on the principles above.

### 📌 Note

If your company is still using the Namely version and wants to migrate to this fork, you must be at least on [namely v0.9.2-rc.1](https://github.com/namely/chief-of-state/releases/tag/v0.9.2-rc.1). See the [documentation](#-documentation) for setup instructions.

From version `v2.4.11` onward, Chief of State has been fully migrated from Akka to Apache Pekko. Projects on versions before that cannot be upgraded to the latest releases due to the lack of a migration kit at this time.

## ✨ Features

- **Journal & Snapshot** — Serialization using Google Protocol Buffer message format
- **Clustering** — Preconfigured clustering and domain entity sharding with the split-brain-resolver algorithm
- **Caching** — Automatic caching and entity passivation
- **Storage** — Automatic configuration of PostgreSQL storage on boot
- **Observability** — OpenTelemetry integration for tracing and metrics
- **Compaction** — Journal compaction support
- **Kubernetes** — Direct integration to form a cluster using the Kubernetes API
- **Read Side Management** — Via the [CLI tool](https://github.com/chief-of-state/cos-cli):
  - Skip offset per shard and across the whole CoS cluster
  - Pause, resume, and restart read sides per shard or cluster-wide
  - List read sides' offsets per shard and across the cluster

## 🏭 Production

Chief of State has been used in production by notable companies since 2020.

## 🏗️ Anatomy of a Chief of State App

You implement two handler interfaces (gRPC or HTTP): a **write handler** to process `commands` and `events`, and optionally **many read handlers** that react to state changes by consuming events from the write handler.

![Architecture Diagram](./img/cos-anatomy.png?raw=true "Chief of State Architecture")

### Chief of State Service

The main entry point is the [Service](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/service.proto). You interact with Chief of State via:

- **`ProcessCommand`** — Send commands to be processed by the [Write Handler](#write-handler)
- **`GetState`** — Retrieve the current state of a persistent entity

### Write Handler

You describe state mutations by implementing two RPCs in the [WriteSideHandlerService](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/writeside.proto):

- **`HandleCommand`** — Accepts a command and the prior state, returns an event. For example, given `UpdateUserEmail` and a `User`, it might return `UserEmailUpdated`.
- **`HandleEvent`** — Accepts an event and the prior state, returns the new state. For example, given `UserEmailUpdated` and a `User`, it returns a new `User` instance with the email updated.

### Read Handler

In response to state mutations, CoS sends changes to many [ReadSideHandlerService](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/readside.proto) implementations. CoS guarantees at-least-once delivery of events and resulting state to each read side, in the order they were persisted.

Read side handlers can:

- Write state changes to a data store (e.g., Elasticsearch)
- Publish changes to Kafka topics
- Send notifications to users in response to specific events

## 📚 Documentation

- [Getting Started](./docs/getting-started.md)
- [Configuration Options](./docs/configuration.md)
- [Docker Deployment](./docs/docker-deployment.md)
- [Kubernetes Deployment](./docs/kubernetes-deployment.md)
- [HTTP API](./docs/http.md)

## 💬 Community

Join the discussion and ask Chief of State–related questions:

[![GitHub Discussions](https://img.shields.io/github/discussions/chief-of-state/chief-of-state?style=flat-square)](https://github.com/chief-of-state/chief-of-state/discussions)

## 🤝 Contribution

Contributions are welcome!

The project adheres to [Semantic Versioning](https://semver.org) and [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/). If you see an issue you'd like to fix, the best way is to submit a pull request. To test your changes locally:

```bash
# Install Earthly CLI (macOS)
brew install earthly/earthly/earthly

# Build the Docker image
earthly +build-image

# Run tests
earthly -P --no-output +test-local
```

> **Tip:** See the [Earthly docs](https://docs.earthly.dev/) for installation on Linux and Windows.

## 📄 License

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](https://opensource.org/licenses/MIT)

Chief of State is free and will remain so, with no paid license requirement.

## 🚀 Sample Projects

- [Python](https://github.com/chief-of-state/cos-python-sample)
- [Go](https://github.com/Tochemey/cos-go-sample)
