# Chief of State

![GitHub Workflow Status (branch)](https://img.shields.io/github/workflow/status/chief-of-state/chief-of-state/Master/master)
![GitHub release (latest SemVer including pre-releases)](https://img.shields.io/github/v/release/chief-of-state/chief-of-state?include_prereleases)


## Overview

Chief-of-state (COS) is an Open Source clustered persistence tool for building event sourced applications. COS supports CQRS and
event-sourcing through simple, language-agnostic interfaces via gRPC, and it allows developers to describe their schema
with Protobuf. Under the hood, COS leverages [Akka](https://akka.io/)
to scale out and guarantee performant, reliable persistence.

Chief-of-state was built with the following principles:

* Wire format should be the same as persistence
* Scaling should not require re-architecture
* Developers shouldn't face race conditions or database locks
* Rules should be enforced with interfaces
* Event sourcing is valuable, but challenging to implement
* An ideal event-sourcing datastore would offer random access by key, streaming, and atomic writes

## Anatomy of a chief-of-state app

Developers implement two gRPC interfaces: a write handler for building state and, optionally, many read handlers for
reacting to state changes.

![Architecture Diagram](img/anatomy.png?raw=true "Title")

### Chief Of State Service

The main entry point of a chief-of-state based application is the
[Service](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/service.proto). Developers will
interact with chief of state via:

- `ProcessCommand` is used by the application to send commands to process via [Write Handler](#write-handler).
- `GetState` is used by the application to retrieve the current state of a persistent entity

### Write Handler

Developers describe state mutations by implementing two RPC’s in
the [WriteSideHandlerService](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/writeside.proto):

- `HandleCommand` accepts a command and the prior state of an entity and returns an Event. For example, given a command
  to UpdateUserEmail and a User, this RPC might return UserEmailUpdated.
- `HandleEvent` accepts an event and the prior state of an entity and returns a new state. For example, given a
  UserEmailUpdated event and a User, this RPC would return a new User instance with the email updated.

### Read Handler

In response to state mutations, COS is able to send changes to
many [ReadSideHandlerService](https://github.com/chief-of-state/chief-of-state-protos/blob/main/chief_of_state/v1/readside.proto)
implementations, which may take any action. COS guarantees at-least-once delivery of events and resulting state to each
read side in the order they were persisted.

Some potential read side handlers might:

- Write state changes to a special data store like elastic
- Publish changes to kafka topics
- Send notifications to users in response to specific events

## Features

- Journal and Snapshot serialization using google protocol buffer message format
- Preconfigured Akka clustering and domain entity sharding with the split-brain-resolver algorithm
- Automatic caching and entity passivation
- Automatic configuration of postgres storage on boot
- Opentelemetry integration for tracing and prometheus metrics
- Direct integration to Kubernetes to form a cluster
- Read Side Management
  - Skip offset per shard and across the whole CoS cluster
  - Pause and Resume read sides per shard and across the whole CoS cluster
  - Restart read sides per shard and across the whole CoS cluster
  - List read sides' offsets per shard and across the whole CoS cluster

## Documentation

The following docs are available:

- [Getting Started](./docs/getting-started.md)
- [Configuration options](./docs/configuration.md)
- [Docker Deployment](./docs/docker-deployment.md)
- [Kubernetes Deployment](./docs/kubernetes-deployment.md)

## Community

You can join these groups and chat to discuss and ask Chief Of State related questions on:

[![Github: chief-of-state/chief-of-state](https://img.shields.io/badge/github%3A-issues-blue.svg?style=flat-square)](https://github.com/chief-of-state/chief-of-state/issues)

## Contribution

Contributions are welcome!

The project adheres to [Semantic Versioning](https://semver.org) and [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/).
If you see an issue that you'd like to see fixed, the best way to make it happen is to help out by submitting a pull request implementing it. To test your implementation locally follow the steps below:


### Locally build / test

```bash
# install earthly cli
brew install earthly/earthly/earthly (for mac users)

# locally build the image
earthly +build-image

# run tests
earthly -P --no-output +test-local
```

## License

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square)](https://opensource.org/licenses/MIT)

## Sample Projects

[Python](https://github.com/namely/cos-python-sample)
