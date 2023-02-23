VERSION 0.6

FROM tochemey/docker-go:1.20.1-0.7.0

protogen:
    # copy the proto files to generate
    COPY --dir proto/ ./
    COPY buf.work.yaml buf.gen.yaml ./
    # RUN buf build
    # generate the pbs
    RUN buf generate \
        --template buf.gen.yaml \
        --path proto/internal/chief_of_state/v1 \
        --path proto/chief-of-state-protos/chief_of_state/v1
        
    # save artifact to
    SAVE ARTIFACT gen gen AS LOCAL gen
