package cmd

import (
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(runCMD)
}

var runCMD = &cobra.Command{
	Use:   "run",
	Short: "run the graphQL server",
	Run: func(cmd *cobra.Command, args []string) {

	},
}
