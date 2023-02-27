package node

import (
	"context"
	"fmt"
	"log"

	"github.com/super-flat/parti/cluster"
	"google.golang.org/protobuf/types/known/anypb"
	"google.golang.org/protobuf/types/known/wrapperspb"
)

func Run() {

}

type MessageHandler struct{}

func (e *MessageHandler) Handle(ctx context.Context, partitionID uint32, msg *anypb.Any) (*anypb.Any, error) {
	// unpack inner msg
	innerMsg, err := msg.UnmarshalNew()
	if err != nil {
		return nil, err
	}

	resp := ""

	switch v := innerMsg.(type) {
	case *wrapperspb.StringValue:
		resp = fmt.Sprintf("replying to '%s'", v.GetValue())
	default:
		resp = fmt.Sprintf("responding, partition=%d, type=%s", partitionID, msg.GetTypeUrl())
	}
	return anypb.New(wrapperspb.String(resp))
}

func (e *MessageHandler) StartPartition(ctx context.Context, partitionID uint32) error {
	log.Printf("starting partition (%d)", partitionID)
	return nil
}

func (e *MessageHandler) ShutdownPartition(ctx context.Context, partitionID uint32) error {
	log.Printf("shutting down partition (%d)", partitionID)
	// time.Sleep(time.Second * 10)
	return nil
}

var _ cluster.Handler = &MessageHandler{}
