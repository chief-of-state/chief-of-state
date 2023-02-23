VERSION 0.6

FROM golang:1.20-alpine

golang-base:
    WORKDIR /app

    # install gcc dependencies into alpine for CGO
    RUN apk add gcc musl-dev curl git openssh

    # install docker tools
    # https://docs.docker.com/engine/install/debian/
    RUN apk add --update --no-cache docker

    # install the go generator plugins
    RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
    RUN go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
    RUN export PATH="$PATH:$(go env GOPATH)/bin"

    # install vektra/mockery
    RUN go install github.com/vektra/mockery/v2@v2.13.0-beta.1

    # install buf from source
    RUN GO111MODULE=on GOBIN=/usr/local/bin go install github.com/bufbuild/buf/cmd/buf@v1.4.0

    # install linter
    # binary will be $(go env GOPATH)/bin/golangci-lint
    RUN curl -sSfL https://raw.githubuse
