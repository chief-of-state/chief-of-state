package cmd

import (
	"log"

	"github.com/chief-of-state/chief-of-state/app/node"
	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
	"github.com/spf13/cobra"
)

func init() {

	var testCMD = &cobra.Command{
		Use: "test",
		Run: func(cmd *cobra.Command, args []string) {
			ctx := cmd.Context()
			partition := node.NewPartition(ctx)

			outputs := make([]<-chan *local.CommandReply, 0, 3)

			for i := 0; i < 3; i++ {
				msg := &local.SendCommand{
					Message: &local.SendCommand_GetStateCommand{
						GetStateCommand: &local.GetStateCommand{
							EntityId: "some-entity-id",
						},
					},
				}

				respChan := partition.Process(ctx, msg)
				log.Printf("sent message\n")
				outputs = append(outputs, respChan)
			}

			for _, respChan := range outputs {
				resp := <-respChan
				switch respType := resp.GetReply().(type) {
				case *local.CommandReply_Error:
					err := respType.Error
					log.Printf("err -> code='%d', msg='%v'\n", err.GetCode(), err.GetMessage())
				case *local.CommandReply_State:
					out := respType.State
					log.Printf("OK, state -> %s\n", out.GetState().GetTypeUrl())
				}
			}

			partition.Stop(ctx)
		},
	}

	rootCmd.AddCommand(testCMD)
}
