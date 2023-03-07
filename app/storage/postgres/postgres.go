package postgres

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/chief-of-state/chief-of-state/app/storage"

	sq "github.com/Masterminds/squirrel"
	"github.com/chief-of-state/chief-of-state/app/internal/postgres"
	"github.com/chief-of-state/chief-of-state/gen/chief_of_state/local"
	"github.com/pkg/errors"
	"go.uber.org/atomic"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/reflect/protoreflect"
	"google.golang.org/protobuf/reflect/protoregistry"
	"google.golang.org/protobuf/types/known/anypb"
)

var (
	columns = []string{
		"ordering",
		"persistence_id",
		"sequence_number",
		"is_deleted",
		"event_payload",
		"event_manifest",
		"state_payload",
		"state_manifest",
		"timestamp",
	}
)

const (
	journalTableSchemaSQL = `
CREATE TABLE event_journal
(
    ordering BIGSERIAL NOT NULL ,
    persistence_id  VARCHAR(255)          NOT NULL,
    sequence_number BIGINT                NOT NULL,
    is_deleted      BOOLEAN DEFAULT FALSE NOT NULL,
    event_payload   BYTEA                 NOT NULL,
    event_manifest  VARCHAR(255)          NOT NULL,
    state_payload   BYTEA                 NOT NULL,
    state_manifest  VARCHAR(255)          NOT NULL,
    timestamp       BIGINT             NOT NULL,

    PRIMARY KEY (persistence_id, sequence_number)
);

CREATE INDEX IF NOT EXISTS idx_event_journal_deleted ON event_journal (is_deleted);
`

	tableName = "event_journal"
)

// JournalStore implements the PostgresJournalStore interface
// and helps persist events in a Postgres database
// This should be instantiated once and reused in the application
type JournalStore struct {
	db postgres.DBConn
	sb sq.StatementBuilderType
	// insertBatchSize represents the chunk of data to bulk insert.
	// This helps avoid the postgres 65535 parameter limit.
	// This is necessary because Postgres uses a 32-bit int for binding input parameters and
	// is not able to track anything larger.
	// Note: Change this value when you know the size of data to bulk insert at once. Otherwise, you
	// might encounter the postgres 65535 parameter limit error.
	insertBatchSize int
	// hold the connection state to avoid multiple connection of the same instance
	connected *atomic.Bool

	schemasUtils *postgres.SchemaUtils
	config       *postgres.Config
}

// make sure the PostgresEventStore implements the PostgresJournalStore interface
var _ storage.JournalStore = &JournalStore{}

// NewJournalStore creates a new instance of PostgresEventStore
func NewJournalStore(config *postgres.Config) *JournalStore {
	// create the underlying db connection
	db := postgres.New(config)
	return &JournalStore{
		db:              db,
		sb:              sq.StatementBuilder.PlaceholderFormat(sq.Dollar),
		insertBatchSize: 500,
		connected:       atomic.NewBool(false),
		schemasUtils:    postgres.NewSchemaUtils(db),
		config:          config,
	}
}

// Connect connects to the underlying postgres database
func (s *JournalStore) Connect(ctx context.Context) error {
	// check whether this instance of the journal is connected or not
	if s.connected.Load() {
		return nil
	}

	// connect to the underlying db
	if err := s.db.Connect(ctx); err != nil {
		return err
	}

	// check whether the database schema exist
	exist, err := s.schemasUtils.SchemaExists(ctx, s.config.DBSchema)
	if err != nil {
		return errors.Wrapf(err, "failed to check the existence of the journal store schema=%s", s.config.DBSchema)
	}

	// create the schema if the schema does not exist and create the various journal table
	if !exist {
		// let us create the schema
		if err := s.schemasUtils.CreateSchema(ctx, s.config.DBSchema); err != nil {
			return errors.Wrapf(err, "failed to create the journal store schema=%s", s.config.DBSchema)
		}
		// run the creation of the various journal tables
		_, err := s.db.Exec(ctx, journalTableSchemaSQL)
		// handle the error
		if err != nil {
			return errors.Wrap(err, "failed to create the journal tables")
		}
	}

	// if the schema exist let us make sure that the given table exist or create it
	exist, err = s.schemasUtils.TableExists(ctx, s.config.DBSchema, tableName)
	if err != nil {
		return errors.Wrapf(err, "failed to check the existence of the journal table=%s", tableName)
	}

	// let us create the table if it does not exist
	if !exist {
		// run the creation of the various journal tables
		_, err := s.db.Exec(ctx, journalTableSchemaSQL)
		// handle the error
		if err != nil {
			return errors.Wrap(err, "failed to create the journal tables")
		}
	}

	// set the connection status
	s.connected.Store(true)

	return nil
}

// Disconnect disconnects from the underlying postgres database
func (s *JournalStore) Disconnect(ctx context.Context) error {
	// check whether this instance of the journal is connected or not
	if !s.connected.Load() {
		return nil
	}

	// disconnect the underlying database
	if err := s.db.Disconnect(ctx); err != nil {
		return err
	}
	// set the connection status
	s.connected.Store(false)

	return nil
}

// Ping verifies a connection to the database is still alive, establishing a connection if necessary.
func (s *JournalStore) Ping(ctx context.Context) error {
	// check whether we are connected or not
	if !s.connected.Load() {
		return s.Connect(ctx)
	}

	return nil
}

// PersistenceIDs returns the distinct list of all the persistence ids in the journal store
func (s *JournalStore) PersistenceIDs(ctx context.Context) (persistenceIDs []string, err error) {
	// check whether this instance of the journal is connected or not
	if !s.connected.Load() {
		return nil, errors.New("journal store is not connected")
	}

	// create the database delete statement
	statement := s.sb.
		Select("persistence_id").
		Distinct().
		From(tableName)

	// get the sql statement and the arguments
	query, args, err := statement.ToSql()
	// handle the error
	if err != nil {
		return nil, errors.Wrap(err, "failed to build the sql statement")
	}

	// create the ds to hold the database record
	type row struct {
		PersistenceID string
	}

	// execute the query against the database
	var rows []*row
	err = s.db.SelectAll(ctx, &rows, query, args...)
	if err != nil {
		return nil, errors.Wrap(err, "failed to fetch the events from the database")
	}

	// grab the fetched records
	persistenceIDs = make([]string, len(rows))
	for index, row := range rows {
		persistenceIDs[index] = row.PersistenceID
	}

	return
}

// PersistJournals writes a bunch of journals into the underlying postgres database
func (s *JournalStore) PersistJournals(ctx context.Context, journals []*local.Journal) error {
	// check whether this instance of the journal is connected or not
	if !s.connected.Load() {
		return errors.New("journal store is not connected")
	}

	// check whether the journals list is empty
	if len(journals) == 0 {
		// do nothing
		return nil
	}

	// let us begin a database transaction to make sure we atomically write those events into the database
	tx, err := s.db.BeginTx(ctx, &sql.TxOptions{Isolation: sql.LevelReadCommitted})
	// return the error in case we are unable to get a database transaction
	if err != nil {
		return errors.Wrap(err, "failed to obtain a database transaction")
	}

	insertionColumns := []string{
		"persistence_id",
		"sequence_number",
		"is_deleted",
		"event_payload",
		"event_manifest",
		"state_payload",
		"state_manifest",
		"timestamp",
	}

	// start creating the sql statement for insertion
	statement := s.sb.Insert(tableName).Columns(insertionColumns...)
	for index, event := range journals {
		var (
			eventManifest string
			eventBytes    []byte
			stateManifest string
			stateBytes    []byte
		)

		// serialize the event and resulting state
		eventBytes, _ = proto.Marshal(event.GetEvent())
		stateBytes, _ = proto.Marshal(event.GetResultingState())

		// grab the manifest
		eventManifest = string(event.GetEvent().ProtoReflect().Descriptor().FullName())
		stateManifest = string(event.GetResultingState().ProtoReflect().Descriptor().FullName())

		// build the insertion values
		statement = statement.Values(

			event.GetPersistenceId(),
			event.GetSequenceNumber(),
			event.GetIsDeleted(),
			eventBytes,
			eventManifest,
			stateBytes,
			stateManifest,
			event.GetTimestamp(),
		)

		if (index+1)%s.insertBatchSize == 0 || index == len(journals)-1 {
			// get the SQL statement to run
			query, args, err := statement.ToSql()
			// handle the error while generating the SQL
			if err != nil {
				return errors.Wrap(err, "unable to build sql insert statement")
			}
			// insert into the table
			_, execErr := tx.ExecContext(ctx, query, args...)
			if execErr != nil {
				// attempt to roll back the transaction and log the error in case there is an error
				if err = tx.Rollback(); err != nil {
					return errors.Wrap(err, "unable to rollback db transaction")
				}
				// return the main error
				return errors.Wrap(execErr, "failed to record events")
			}

			// reset the statement for the next bulk
			statement = s.sb.Insert(tableName).Columns(columns...)
		}
	}

	// commit the transaction
	if commitErr := tx.Commit(); commitErr != nil {
		// return the commit error in case there is one
		return errors.Wrap(commitErr, "failed to record events")
	}
	// every looks good
	return nil
}

// DeleteJournals deletes journals from the postgres up to a given sequence number (inclusive)
func (s *JournalStore) DeleteJournals(ctx context.Context, persistenceID string, toSequenceNumber uint64) error {
	// check whether this instance of the journal is connected or not
	if !s.connected.Load() {
		return errors.New("journal store is not connected")
	}

	// create the database delete statement
	statement := s.sb.
		Delete(tableName).
		Where(sq.Eq{"persistence_id": persistenceID}).
		Where(sq.LtOrEq{"sequence_number": toSequenceNumber})

	// get the sql statement and the arguments
	query, args, err := statement.ToSql()
	if err != nil {
		return errors.Wrap(err, "failed to build the delete events sql statement")
	}

	// execute the sql statement
	if _, err := s.db.Exec(ctx, query, args...); err != nil {
		return errors.Wrap(err, "failed to delete events from the database")
	}

	return nil
}

// ReplayJournals fetches events for a given persistence ID from a given sequence number(inclusive) to a given sequence number(inclusive)
func (s *JournalStore) ReplayJournals(ctx context.Context, persistenceID string, fromSequenceNumber, toSequenceNumber uint64, max uint64) ([]*local.Journal, error) {
	// check whether this instance of the journal is connected or not
	if !s.connected.Load() {
		return nil, errors.New("journal store is not connected")
	}

	// create the database select statement
	statement := s.sb.
		Select(columns...).
		From(tableName).
		Where(sq.Eq{"persistence_id": persistenceID}).
		Where(sq.GtOrEq{"sequence_number": fromSequenceNumber}).
		Where(sq.LtOrEq{"sequence_number": toSequenceNumber}).
		OrderBy("sequence_number ASC").
		Limit(max)

	// get the sql statement and the arguments
	query, args, err := statement.ToSql()
	if err != nil {
		return nil, errors.Wrap(err, "failed to build the select sql statement")
	}

	// create the ds to hold the database record
	type row struct {
		Ordering       uint64
		PersistenceID  string
		SequenceNumber uint64
		IsDeleted      bool
		EventPayload   []byte
		EventManifest  string
		StatePayload   []byte
		StateManifest  string
		Timestamp      int64
	}

	// execute the query against the database
	var rows []*row
	err = s.db.SelectAll(ctx, &rows, query, args...)
	if err != nil {
		return nil, errors.Wrap(err, "failed to fetch the events from the database")
	}

	events := make([]*local.Journal, 0, max)
	for _, row := range rows {
		// unmarshal the event and the state
		evt, err := s.toProto(row.EventManifest, row.EventPayload)
		if err != nil {
			return nil, errors.Wrap(err, "failed to unmarshal the journal event")
		}
		state, err := s.toProto(row.StateManifest, row.StatePayload)
		if err != nil {
			return nil, errors.Wrap(err, "failed to unmarshal the journal state")
		}
		// create the event and add it to the list of events
		events = append(events, &local.Journal{
			PersistenceId:  row.PersistenceID,
			SequenceNumber: row.SequenceNumber,
			IsDeleted:      row.IsDeleted,
			Event:          evt,
			ResultingState: state,
			Timestamp:      row.Timestamp,
		})
	}

	return events, nil
}

// GetLatestJournal fetches the latest journal
func (s *JournalStore) GetLatestJournal(ctx context.Context, persistenceID string) (*local.Journal, error) {
	// check whether this instance of the journal is connected or not
	if !s.connected.Load() {
		return nil, errors.New("journal store is not connected")
	}

	// create the database select statement
	statement := s.sb.
		Select(columns...).
		From(tableName).
		Where(sq.Eq{"persistence_id": persistenceID}).
		OrderBy("sequence_number DESC").
		Limit(1)

	// get the sql statement and the arguments
	query, args, err := statement.ToSql()
	if err != nil {
		return nil, errors.Wrap(err, "failed to build the select sql statement")
	}

	// create the ds to hold the database record
	type row struct {
		Ordering       uint64
		PersistenceID  string
		SequenceNumber uint64
		IsDeleted      bool
		EventPayload   []byte
		EventManifest  string
		StatePayload   []byte
		StateManifest  string
		Timestamp      int64
	}

	// execute the query against the database
	data := new(row)
	err = s.db.Select(ctx, data, query, args...)
	if err != nil {
		return nil, errors.Wrap(err, "failed to fetch the latest event from the database")
	}

	// check whether we do have data
	if data.PersistenceID == "" {
		return nil, nil
	}

	// unmarshal the event and the state
	evt, err := s.toProto(data.EventManifest, data.EventPayload)
	if err != nil {
		return nil, errors.Wrap(err, "failed to unmarshal the journal event")
	}
	state, err := s.toProto(data.StateManifest, data.StatePayload)
	if err != nil {
		return nil, errors.Wrap(err, "failed to unmarshal the journal state")
	}

	return &local.Journal{
		PersistenceId:  data.PersistenceID,
		SequenceNumber: data.SequenceNumber,
		IsDeleted:      data.IsDeleted,
		Event:          evt,
		ResultingState: state,
		Timestamp:      data.Timestamp,
	}, nil
}

// toProto converts a byte array given its manifest into a valid proto message
func (s *JournalStore) toProto(manifest string, bytea []byte) (*anypb.Any, error) {
	mt, err := protoregistry.GlobalTypes.FindMessageByName(protoreflect.FullName(manifest))
	if err != nil {
		return nil, err
	}

	pm := mt.New().Interface()
	err = proto.Unmarshal(bytea, pm)
	if err != nil {
		return nil, err
	}

	if cast, ok := pm.(*anypb.Any); ok {
		return cast, nil
	}
	return nil, fmt.Errorf("failed to unpack message=%s", manifest)
}
