VERSION 0.6

FROM busybox:1.32

test-and-build:
    # target running tests and building image
    BUILD +test-all
    BUILD +prepare-image

release:
    # uploads the image to registry
    BUILD +test-all
    BUILD +build-image

dependencies:
    # copy relevant files in, save as a base image
    FROM +sbt

    # create user & working dir for sbt
    ARG BUILD_DIR="/build"

    USER root

    RUN mkdir $BUILD_DIR && \
        chmod 777 /$BUILD_DIR

    WORKDIR $BUILD_DIR

    # copy configurations
    COPY .scalafmt.conf build.sbt .
    COPY --dir project .

    # clean & install dependencies
    RUN sbt clean cleanFiles update

code:
    FROM +dependencies
    # copy proto definitions & generate
    COPY --dir proto .
    RUN sbt protocGenerate
    # copy code
    COPY --dir code .

compile:
    # package the jars/executables
    FROM +code
    RUN sbt stage
    RUN chmod -R u=rX,g=rX target/universal/stage
    SAVE ARTIFACT target/universal/stage/ /target

prepare-image:
    # bundle into a slimmer, runnable container
    FROM openjdk:11-jre-slim

    USER root

    # create cos user for the service
    RUN groupadd -r cos && useradd --gid cos -r --shell /bin/false cos

    # copy over files
    WORKDIR /opt/docker
    COPY --chown cos:root +compile/target .

    # set runtime user to cos
    USER cos

    ENTRYPOINT /opt/docker/bin/entrypoint
    CMD []

build-image:
    FROM +prepare-image
    # build the image and push remotely (if all steps are successful)
    ARG VERSION=dev
    SAVE IMAGE --push ghcr.io/chief-of-state/chief-of-state:${VERSION}

test-local:
    FROM +code
    # enable coverage mode and compile tests
    RUN sbt coverage test:compile compile

    # run with docker to enable testcontainers
    WITH DOCKER
        RUN sbt coverage test coverageAggregate
    END

    # push to earthly cache
    SAVE IMAGE --push ghcr.io/chief-of-state/chief-of-state-cache:test

codecov:
    FROM +test-local
    ARG COMMIT_HASH=""
    ARG BRANCH_NAME=""
    ARG BUILD_NUMBER=""
    RUN curl -s https://codecov.io/bash > codecov.sh && chmod +x codecov.sh
    RUN --secret CODECOV_TOKEN=+secrets/CODECOV_TOKEN \
        ./codecov.sh -t "${CODECOV_TOKEN}" -B "${BRANCH_NAME}" -C "${COMMIT_HASH}" -b "${BUILD_NUMBER}"

test-all:
    BUILD +test-local
    BUILD +codecov

sbt:
    # TODO: move this to a central image
    FROM eclipse-temurin:19.0.2_7-jdk-jammy

    USER root

    # create directories
    RUN mkdir /sbt && chmod 777 /sbt
    RUN mkdir /logs && chmod 777 /logs

    # Install sbt
    ARG SBT_VERSION=1.8.0
    ARG SBT_URL="https://github.com/sbt/sbt/releases/download/v$SBT_VERSION/sbt-$SBT_VERSION.tgz"

    # Install sbt, add symlink
    RUN \
      curl -fsL "$SBT_URL" | tar xfz - -C /usr/share && \
      chown -R root:root /usr/share/sbt && \
      chmod -R 755 /usr/share/sbt && \
      chmod +x /usr/share/sbt && \
      ln -s /usr/share/sbt/bin/sbt /usr/local/bin/sbt

    # Switch working directory
    WORKDIR /sbt

    # This triggers a bunch of useful downloads.
    RUN sbt sbtVersion

    # install docker tools
    # https://docs.docker.com/engine/install/debian/
    RUN apt-get remove -y docker docker-engine docker.io containerd runc || true

    RUN apt-get update
    RUN apt-get upgrade

    RUN apt-get install -y \
        apt-transport-https \
        ca-certificates \
        curl \
        gnupg-agent \
        software-properties-common
    
    RUN mkdir -p /etc/apt/keyrings
    RUN curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
    
    RUN chmod a+r /etc/apt/keyrings/docker.gpg

    RUN echo \
          "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
          $(lsb_release -cs) stable" | tee /etc/apt/sources.list.d/docker.list > /dev/null
       
    RUN apt-get update

    RUN apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
