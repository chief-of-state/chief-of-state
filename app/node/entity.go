package node

import (
	"context"
	"errors"
	"log"
	"sync"

	"github.com/chief-of-state/chief-of-state/app/storage"
	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
	cospb "github.com/chief-of-state/chief-of-state/gen/chief_of_state/v1"
	"github.com/golang/protobuf/proto"
	"google.golang.org/protobuf/types/known/anypb"
	"google.golang.org/protobuf/types/known/emptypb"
	"google.golang.org/protobuf/types/known/timestamppb"
)

// Entity represents each persistence entity
type Entity struct {
	mtx          sync.Mutex
	entityID     string
	state        *anypb.Any
	meta         *cospb.MetaData
	writeClient  cospb.WriteSideHandlerServiceClient
	journalStore storage.JournalStore
}

// NewEntity creates an instance of Entity
func NewEntity(entityID string, writeClient cospb.WriteSideHandlerServiceClient, journalStore storage.JournalStore) *Entity {
	log.Printf("spinning up entity '%s'\n", entityID)

	meta := &cospb.MetaData{
		EntityId:       entityID,
		RevisionNumber: 0,
	}

	initialState := new(emptypb.Empty)

	stateAny, _ := anypb.New(initialState)

	return &Entity{
		entityID:     entityID,
		state:        stateAny,
		meta:         meta,
		mtx:          sync.Mutex{},
		writeClient:  writeClient,
		journalStore: journalStore,
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

	cmdResp, err := e.writeClient.HandleCommand(ctx, &cospb.HandleCommandRequest{
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

	// compute the new metadata for this event
	newMeta := proto.Clone(e.meta).(*cospb.MetaData)
	newMeta.RevisionNumber++
	newMeta.RevisionDate = timestamppb.Now()

	evtResp, err := e.writeClient.HandleEvent(ctx, &cospb.HandleEventRequest{
		Event:      cmdResp.GetEvent(),
		PriorState: e.state,
		EventMeta:  newMeta,
	})
	if err != nil {
		return nil, err
	}
	// enforce event must be set
	if evtResp.GetResultingState() == nil {
		return nil, errors.New("missing state from event handler")
	}

	// persist event and state to journal only when the journal store is defined
	// TODO we can cache the event on the entity and with some worker push them to the journal store at some given interval. For now it is ok to persist it directly
	if e.journalStore != nil {
		// create an instance of the journal
		journal := &local.Journal{
			PersistenceId:  e.entityID,
			SequenceNumber: uint64(newMeta.GetRevisionNumber()),
			IsDeleted:      false,
			Event:          cmdResp.GetEvent(),
			ResultingState: evtResp.GetResultingState(),
			Timestamp:      newMeta.GetRevisionDate().AsTime().Unix(),
		}
		// persist the journal to the journal store
		if err := e.journalStore.PersistJournals(ctx, []*local.Journal{journal}); err != nil {
			// TODO add logging and rich error message
			return nil, err
		}
	}

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
