# Runtime image for chief-of-state (matches Earthfile prepare-image).
# Build the stage first: sbt stage
# Then: docker build -t ghcr.io/chief-of-state/chief-of-state:tag .
FROM eclipse-temurin:21-jre

USER root

RUN groupadd -r cos && useradd --gid cos -r --shell /bin/false cos

WORKDIR /opt/docker
COPY --chown=cos:root target/universal/stage/ .

USER cos

ENTRYPOINT ["/opt/docker/bin/entrypoint"]
CMD []
