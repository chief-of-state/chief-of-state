package postgres

import (
	"context"
	"database/sql"
	"fmt"

	"github.com/pkg/errors"
)

// SchemaUtils helps interact with the database
// and perform some postgres DDL task
type SchemaUtils struct {
	DBConn
}

// CreateSchema helps create a test schema in a Postgres database
func (s SchemaUtils) CreateSchema(ctx context.Context, schemaName string) error {
	stmt := fmt.Sprintf("CREATE SCHEMA %s", schemaName)
	if _, err := s.Exec(ctx, stmt); err != nil {
		return err
	}
	return nil
}

// SchemaExists helps check the existence of a Postgres schema.
func (s SchemaUtils) SchemaExists(ctx context.Context, schemaName string) (bool, error) {
	stmt := fmt.Sprintf("SELECT schema_name FROM information_schema.schemata WHERE schema_name = '%s';", schemaName)
	var check string
	if err := s.Select(ctx, &check, stmt); err != nil {
		return false, err
	}

	// this redundant check is necessary
	if check == schemaName {
		return true, nil
	}

	return false, nil
}

// DropSchema utility function to drop a database schema
func (s SchemaUtils) DropSchema(ctx context.Context, schemaName string) error {
	var dropSQL = fmt.Sprintf("DROP SCHEMA IF EXISTS %s CASCADE;", schemaName)
	_, err := s.Exec(ctx, dropSQL)
	return err
}

// DropTable utility function to drop a database table
func (s SchemaUtils) DropTable(ctx context.Context, tableName string) error {
	var dropSQL = fmt.Sprintf("DROP TABLE IF EXISTS %s CASCADE;", tableName)
	_, err := s.Exec(ctx, dropSQL)
	return err
}

// TableExists utility function to help check the existence of table in Postgres
// tableName is in the format: <schemaName.tableName>. e.g: public.users
func (s SchemaUtils) TableExists(ctx context.Context, tableName string) error {
	var stmt = fmt.Sprintf("SELECT to_regclass('%s');", tableName)
	_, err := s.Exec(ctx, stmt)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil
		}
		return err
	}

	return nil
}
