package node

import (
	"context"
	"errors"
	"log"

	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
	"google.golang.org/protobuf/types/known/anypb"

	chief_of_statev1 "github.com/chief-of-state/chief-of-state/gen/chief_of_state/v1"
)

type Entity struct {
	entityID string
	state    *anypb.Any
	meta     *chief_of_statev1.MetaData
}

func NewEntity(entityID string) *Entity {
	log.Printf("spinning up entity '%s'\n", entityID)
	return &Entity{
		entityID: entityID,
	}
}

func (e *Entity) Receive(ctx context.Context, msg *local.SendCommand) (resp *local.StateWrapper, err error) {
	switch typedMsg := msg.GetMessage().(type) {
	case *local.SendCommand_RemoteCommand:
		return e.put(ctx, typedMsg.RemoteCommand)
	case *local.SendCommand_GetStateCommand:
		return e.get(ctx, typedMsg.GetStateCommand)
	default:
		return nil, errors.New("unrecognized command type")
	}
}

func (e *Entity) put(ctx context.Context, msg *local.RemoteCommand) (*local.StateWrapper, error) {
	log.Printf("entity '%s', received put\n", e.entityID)
	return nil, nil
}

func (e *Entity) get(ctx context.Context, msg *local.GetStateCommand) (*local.StateWrapper, error) {
	log.Printf("entity '%s', received get\n", e.entityID)
	return nil, nil
}

func (e *Entity) Shutdown(ctx context.Context) error {
	log.Printf("shutting down entity '%s'", e.entityID)
	return nil
}
