package node

import (
	"context"
	"errors"
	"fmt"
	"log"
	"sync"

	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
	chief_of_statev1 "github.com/chief-of-state/chief-of-state/gen/chief_of_state/v1"
	"github.com/super-flat/parti/cluster"
	"google.golang.org/protobuf/types/known/anypb"
)

func Run() {

}

type MessageHandler struct {
	mtx         sync.Mutex
	partitions  map[uint32]*Partition
	writeClient chief_of_statev1.WriteSideHandlerServiceClient
}

func NewMessageHandler(writeClient chief_of_statev1.WriteSideHandlerServiceClient) *MessageHandler {
	return &MessageHandler{
		mtx:         sync.Mutex{},
		partitions:  make(map[uint32]*Partition),
		writeClient: writeClient,
	}
}

// Handle a message for a given partition
func (e *MessageHandler) Handle(ctx context.Context, partitionID uint32, msg *anypb.Any) (*anypb.Any, error) {
	// unpack inner msg
	innerMsg, err := msg.UnmarshalNew()
	if err != nil {
		return nil, err
	}
	// cast the message
	entityMsg, ok := innerMsg.(*local.EntityMessage)
	if !ok {
		return nil, fmt.Errorf("unrecognized proto, typeUrl='%s'", msg.GetTypeUrl())
	}
	// get partition
	partition, found := e.partitions[partitionID]
	if !found {
		return nil, errors.New("partition not on this node")
	}
	// enqueue message
	respChan := partition.Process(ctx, entityMsg)
	// wait for response
	resp := <-respChan
	if resp.Err != nil {
		return nil, err
	}
	// return as any
	return anypb.New(resp.Msg)
}

// StartPartition boots a given partition on this node
func (e *MessageHandler) StartPartition(ctx context.Context, partitionID uint32) error {
	e.mtx.Lock()
	defer e.mtx.Unlock()
	log.Printf("starting partition (%d)", partitionID)
	if _, found := e.partitions[partitionID]; found {
		return nil
	}
	e.partitions[partitionID] = NewPartition(ctx, e.writeClient)
	return nil
}

// ShutdownPartition shuts down the partition on this node
func (e *MessageHandler) ShutdownPartition(ctx context.Context, partitionID uint32) error {
	log.Printf("shutting down partition (%d)", partitionID)
	if partition, found := e.partitions[partitionID]; found {
		return partition.entities.Shutdown(ctx)
	}
	log.Printf("partition %d not found", partitionID)
	return nil
}

// make sure we implement the parti message handler
var _ cluster.Handler = &MessageHandler{}
