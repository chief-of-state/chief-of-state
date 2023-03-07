package storage

import (
	"context"

	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
)

// JournalStore represents the persistence store.
// This helps implement any persistence storage whether it is an RDBMS or No-SQL database
type JournalStore interface {
	// Connect connects to the journal store
	Connect(ctx context.Context) error
	// Disconnect disconnect the journal store
	Disconnect(ctx context.Context) error
	// Ping verifies a connection to the storage is still alive, establishing a connection if necessary.
	Ping(ctx context.Context) error
	// PersistJournals persist journals in batches for a given persistenceID
	PersistJournals(ctx context.Context, journals []*local.Journal) error
	// DeleteJournals deletes journals from the store upt to a given sequence number (inclusive)
	DeleteJournals(ctx context.Context, persistenceID string, toSequenceNumber uint64) error
	// ReplayJournals fetches journals for a given persistence ID from a given sequence number(inclusive) to a given sequence number(inclusive)
	ReplayJournals(ctx context.Context, persistenceID string, fromSequenceNumber, toSequenceNumber uint64) ([]*local.Journal, error)
	// GetLatestJournal fetches the latest journal
	GetLatestJournal(ctx context.Context, persistenceID string) (*local.Journal, error)
}
