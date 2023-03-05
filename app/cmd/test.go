package cmd

import (
	"fmt"
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

			outputs := make([]<-chan *node.Response, 0, 3)

			for i := 0; i < 3; i++ {
				msg := &local.EntityMessage{
					EntityId: fmt.Sprintf("entity-%d", i+1),
				}

				respChan := partition.Process(ctx, msg)
				log.Printf("sent message\n")
				outputs = append(outputs, respChan)
			}

			for ix, respChan := range outputs {
				log.Printf("handling output %d", ix)
				resp := <-respChan
				if resp.Err != nil {
					fmt.Printf("err %v", resp.Err)
				} else if resp.Msg != nil {
					fmt.Printf("resp!")
				} else {
					log.Printf("no message")
				}
			}

			partition.Stop(ctx)
		},
	}

	rootCmd.AddCommand(testCMD)
}
