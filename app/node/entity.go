package node

import (
	"context"
	"errors"
	"log"
	"sync"

	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
	"google.golang.org/protobuf/types/known/anypb"
	"google.golang.org/protobuf/types/known/emptypb"

	chief_of_statev1 "github.com/chief-of-state/chief-of-state/gen/chief_of_state/v1"
)

type Entity struct {
	mtx      sync.Mutex
	entityID string
	state    *anypb.Any
	meta     *chief_of_statev1.MetaData
}

func NewEntity(entityID string) *Entity {
	log.Printf("spinning up entity '%s'\n", entityID)

	meta := &chief_of_statev1.MetaData{
		EntityId:       entityID,
		RevisionNumber: 0,
	}

	initialState := new(emptypb.Empty)
	stateAny, _ := anypb.New(initialState)

	return &Entity{
		entityID: entityID,
		state:    stateAny,
		meta:     meta,
		mtx:      sync.Mutex{},
	}
}

// Process a single message for this entity
func (e *Entity) Process(ctx context.Context, msg *local.EntityMessage) (resp *local.StateWrapper, err error) {
	// lock the entity
	// TODO: do this with a mailbox/channel later
	e.mtx.Lock()
	defer e.mtx.Unlock()
	// handle the message
	switch msg.GetMessageType() {
	case local.EntityMessageType_PUT_COMMAND:
		return e.put(ctx, msg.GetPutCommand())
	case local.EntityMessageType_GET_STATE:
		return e.get(ctx)
	default:
		return nil, errors.New("unrecognized command type")
	}
}

func (e *Entity) put(ctx context.Context, msg *anypb.Any) (*local.StateWrapper, error) {
	log.Printf("entity '%s', received put\n", e.entityID)
	out := &local.StateWrapper{
		State: e.state,
		Meta:  e.meta,
	}
	return out, nil
}

func (e *Entity) get(ctx context.Context) (*local.StateWrapper, error) {
	log.Printf("entity '%s', received get\n", e.entityID)
	out := &local.StateWrapper{
		State: e.state,
		Meta:  e.meta,
	}
	return out, nil
}

// Shutdown the entity
func (e *Entity) Shutdown(ctx context.Context) error {
	log.Printf("shutting down entity '%s'", e.entityID)
	return nil
}
