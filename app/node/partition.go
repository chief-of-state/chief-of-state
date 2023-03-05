package node

import (
	"context"
	"errors"
	"log"
	"sync"

	"github.com/google/uuid"
	"google.golang.org/protobuf/proto"

	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
)

type Response struct {
	Msg proto.Message
	Err error
}

type PartitionMsg struct {
	ctx      context.Context // the context for this message
	id       string
	msg      *local.EntityMessage
	response chan<- *Response
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
func (p *Partition) Process(ctx context.Context, msg *local.EntityMessage) <-chan *Response {
	responseChan := make(chan *Response, 1)
	partitionMsg := PartitionMsg{
		ctx:      ctx,
		id:       uuid.New().String(),
		msg:      msg,
		response: responseChan,
	}
	p.messages <- partitionMsg
	return responseChan
}

func (p *Partition) receive(partitionMsg PartitionMsg) *Response {
	if partitionMsg.msg.GetEntityId() == "" {
		return &Response{
			Err: errors.New("missing entity ID"),
		}
	}
	// get or create the entity
	entity := p.entities.GetOrCreate(partitionMsg.ctx, partitionMsg.msg.GetEntityId())
	// make entity process the message
	state, err := entity.Process(partitionMsg.ctx, partitionMsg.msg)
	if err != nil {
		return &Response{
			Err: err,
		}
	}
	log.Printf("entity %s returned a state", entity.entityID)
	return &Response{
		Msg: state,
	}
}

func (p *Partition) processAll(ctx context.Context) {
	log.Printf("partition is receiving messages")
	for {
		select {
		case <-ctx.Done():
			return
		case msg := <-p.messages:
			log.Printf("received message '%s'\n", msg.id)
			entityResp := p.receive(msg)
			log.Printf("entity responded %v", entityResp)
			msg.response <- entityResp
			close(msg.response)
		}
	}
}
