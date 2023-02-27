package node

import (
	"context"
	"log"
	"sync"

	"github.com/google/uuid"
	"google.golang.org/grpc/codes"
	grpcstatus "google.golang.org/grpc/status"

	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
)

type PartitionMsg struct {
	id       string
	msg      *local.SendCommand
	response chan<- *local.CommandReply
}

// Partition manages messages and entities for a single partition
type Partition struct {
	messages chan PartitionMsg
	entities *EntityStore
	mtx      sync.Mutex
	cleanups []context.CancelFunc
}

// NewPartition returns a new partition
func NewPartition(ctx context.Context) *Partition {
	// create entity store
	entityStore := NewEntityStore(NewEntity)
	// create partition
	p := &Partition{
		messages: make(chan PartitionMsg, 100),
		entities: entityStore,
		mtx:      sync.Mutex{},
		cleanups: []context.CancelFunc{},
	}
	p.start(ctx)
	return p
}

func (p *Partition) start(ctx context.Context) error {
	// start the background process with ability to cancel
	processCtx, processCancel := context.WithCancel(ctx)
	go p.processAll(processCtx)
	p.cleanups = append(p.cleanups, processCancel)

	// make entities shut down
	p.cleanups = append(p.cleanups, func() {
		p.entities.Shutdown(ctx)
	})

	return nil
}

// Stop shuts down this partition and all actors
func (p *Partition) Stop(ctx context.Context) error {
	log.Printf("shutting down partition")
	// clean up all processes
	for _, cleanup := range p.cleanups {
		cleanup()
	}
	return nil
}

// Process enqueues a message to be processed
func (p *Partition) Process(ctx context.Context, msg *local.SendCommand) <-chan *local.CommandReply {
	responseChan := make(chan *local.CommandReply)
	partitionMsg := PartitionMsg{
		id:       uuid.New().String(),
		msg:      msg,
		response: responseChan,
	}
	p.messages <- partitionMsg
	return responseChan
}

func (p *Partition) receive(ctx context.Context, msg *local.SendCommand) *local.CommandReply {
	var entityID string = ""
	// switch on message type
	switch typedMsg := msg.GetMessage().(type) {
	case *local.SendCommand_RemoteCommand:
		entityID = typedMsg.RemoteCommand.GetEntityId()
	case *local.SendCommand_GetStateCommand:
		entityID = typedMsg.GetStateCommand.GetEntityId()
	default:
		return &local.CommandReply{
			Reply: &local.CommandReply_Error{
				Error: grpcstatus.New(codes.Internal, "unrecognized command type").Proto(),
			},
		}
	}

	entity := p.entities.GetOrCreate(ctx, entityID)

	state, err := entity.Receive(ctx, msg)
	if err != nil {
		return &local.CommandReply{
			Reply: &local.CommandReply_Error{
				Error: grpcstatus.New(codes.Internal, err.Error()).Proto(),
			},
		}
	}
	return &local.CommandReply{Reply: &local.CommandReply_State{State: state}}
}

func (p *Partition) processAll(ctx context.Context) {
	for {
		select {
		case <-ctx.Done():
			return
		case msg := <-p.messages:
			log.Printf("received message '%s'\n", msg.id)
			msg.response <- p.receive(ctx, msg.msg)
		}
	}
}
