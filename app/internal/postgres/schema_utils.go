package postgres

import (
	"context"
	"database/sql"
	"fmt"

	sq "github.com/Masterminds/squirrel"
	"github.com/pkg/errors"
)

// SchemaUtils helps interact with the database
// and perform some postgres DDL task
type SchemaUtils struct {
	DBConn
	sb sq.StatementBuilderType
}

// NewSchemaUtils returns an instance of SchemaUtils
func NewSchemaUtils(db DBConn) *SchemaUtils {
	return &SchemaUtils{
		DBConn: db,
		sb:     sq.StatementBuilder.PlaceholderFormat(sq.Dollar),
	}
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
	// create the statement
	stmt := s.sb.
		Select("schema_name").
		From("information_schema.schemata").
		Where(sq.Eq{"schema_name": schemaName})
	// get the SQL statement
	query, args, err := stmt.ToSql()
	// handle the error
	if err != nil {
		return false, err
	}

	var check string
	if err := s.Select(ctx, &check, query, args...); err != nil {
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
func (s SchemaUtils) TableExists(ctx context.Context, schema, tableName string) (bool, error) {
	// build the statement
	stmt := s.sb.
		Select("COUNT(table_name) as count").
		From("information_schema.tables").
		Where(sq.Like{"table_schema": schema}).
		Where(sq.Eq{"table_name": tableName}).
		Where(sq.Like{"table_type": "BASE TABLE"})

	// grab the SQL
	query, args, err := stmt.ToSql()
	if err != nil {
		return false, err
	}

	// execute the SQL
	var count int
	if err = s.Select(ctx, &count, query, args...); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return false, nil
		}
		return false, err
	}

	return count == 1, nil
}
