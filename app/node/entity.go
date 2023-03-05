package node

import (
	"context"
	"errors"
	"log"
	"sync"

	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
	"github.com/golang/protobuf/proto"
	"google.golang.org/protobuf/types/known/anypb"
	"google.golang.org/protobuf/types/known/emptypb"
	"google.golang.org/protobuf/types/known/timestamppb"

	chief_of_statev1 "github.com/chief-of-state/chief-of-state/gen/chief_of_state/v1"
)

type Entity struct {
	mtx         sync.Mutex
	entityID    string
	state       *anypb.Any
	meta        *chief_of_statev1.MetaData
	writeClient chief_of_statev1.WriteSideHandlerServiceClient
}

func NewEntity(entityID string, writeClient chief_of_statev1.WriteSideHandlerServiceClient) *Entity {
	log.Printf("spinning up entity '%s'\n", entityID)

	meta := &chief_of_statev1.MetaData{
		EntityId:       entityID,
		RevisionNumber: 0,
	}

	initialState := new(emptypb.Empty)

	stateAny, _ := anypb.New(initialState)

	return &Entity{
		entityID:    entityID,
		state:       stateAny,
		meta:        meta,
		mtx:         sync.Mutex{},
		writeClient: writeClient,
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

	cmdResp, err := e.writeClient.HandleCommand(ctx, &chief_of_statev1.HandleCommandRequest{
		Command:        msg,
		PriorState:     e.state,
		PriorEventMeta: e.meta,
	})
	if err != nil {
		return nil, err
	}

	// if empty event, no-op with current state
	if cmdResp.GetEvent() == nil || cmdResp.GetEvent().GetTypeUrl() == "" {
		return &local.StateWrapper{State: e.state, Meta: e.meta}, nil
	}

	// compute the new meta data for this event
	newMeta := proto.Clone(e.meta).(*chief_of_statev1.MetaData)
	newMeta.RevisionNumber += 1
	newMeta.RevisionDate = timestamppb.Now()

	evtResp, err := e.writeClient.HandleEvent(ctx, &chief_of_statev1.HandleEventRequest{
		Event:      cmdResp.GetEvent(),
		PriorState: e.state,
		EventMeta:  newMeta,
	})
	if err != nil {
		return nil, err
	}
	// enforce event must be set
	if evtResp.GetResultingState() == nil {
		return nil, errors.New("missing state from event handler!")
	}

	// persist event and state to journal
	// TODO

	// set the state locally
	e.state = evtResp.GetResultingState()
	e.meta = newMeta

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
