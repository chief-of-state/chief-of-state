package grpc

import "github.com/super-flat/parti/logging"

// Config represent the grpc option
type Config struct {
	ServiceName      string         // ServiceName is the name given that will show in the traces
	GrpcHost         string         // GrpcHost is the gRPC host
	GrpcPort         int            // GrpcPort is the gRPC port used to received and handle gRPC requests
	TraceEnabled     bool           // TraceEnabled checks whether tracing should be enabled or not
	TraceURL         string         // TraceURL is the OTLP collector url.
	EnableReflection bool           // EnableReflection this is useful or local dev testing
	Logger           logging.Logger // Logger defines the logger
}
