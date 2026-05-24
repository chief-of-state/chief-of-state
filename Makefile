SBT     ?= sbt
IMAGE   ?= ghcr.io/chief-of-state/chief-of-state
VERSION ?= dev

.DEFAULT_GOAL := help

.PHONY: help test coverage compile stage format clean docker-build docker-push docker-run

help:  ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?##' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

test:  ## Run the full test suite (requires Docker for testcontainers)
	$(SBT) test

coverage:  ## Run tests with coverage reporting
	$(SBT) clean coverage test coverageReport coverageAggregate

compile:  ## Compile main and test sources
	$(SBT) "compile; Test/compile"

stage:  ## Produce the runnable stage at target/universal/stage
	$(SBT) stage

format:  ## Format Scala sources with scalafmt
	$(SBT) scalafmtAll

clean:  ## Clean sbt build artefacts
	$(SBT) clean

docker-build: stage  ## Build the runtime Docker image (override with VERSION=...)
	docker build -t $(IMAGE):$(VERSION) .

docker-push:  ## Push the runtime Docker image to the registry
	docker push $(IMAGE):$(VERSION)

docker-run:  ## Run the runtime image locally
	docker run --rm -it $(IMAGE):$(VERSION)
