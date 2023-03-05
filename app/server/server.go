package server

import (
	context "context"
	"errors"

	chief_of_statev1 "github.com/chief-of-state/chief-of-state/gen/chief_of_state/v1"
)

type COSServer struct {
}

// GetState implements chief_of_statev1.ChiefOfStateServiceServer
func (*COSServer) GetState(context.Context, *chief_of_statev1.GetStateRequest) (*chief_of_statev1.GetStateResponse, error) {
	return nil, errors.New("unimplemented")
}

// ProcessCommand implements chief_of_statev1.ChiefOfStateServiceServer
func (*COSServer) ProcessCommand(context.Context, *chief_of_statev1.ProcessCommandRequest) (*chief_of_statev1.ProcessCommandResponse, error) {
	return nil, errors.New("unimplemented")
}

// make sure we implement all methods
var _ chief_of_statev1.ChiefOfStateServiceServer = &COSServer{}
