/*
 * Copyright (c) The go-kit Authors
 */

package postgres

import (
	"context"
	"testing"

	"github.com/stretchr/testify/suite"
)

type schemaUtilsSuite struct {
	suite.Suite
	container *TestContainer
}

// SetupSuite starts the Postgres database engine and set the container
// host and port to use in the tests
func (s *schemaUtilsSuite) SetupSuite() {
	s.container = NewTestContainer("testdb", "test", "test")
}

func (s *schemaUtilsSuite) TearDownSuite() {
	s.container.Cleanup()
}

// In order for 'go test' to run this suite, we need to create
// a normal test function and pass our suite to suite.Run
func TestSchemaUtils(t *testing.T) {
	suite.Run(t, new(schemaUtilsSuite))
}

func (s *schemaUtilsSuite) TestDropTable() {
	s.Run("with no table defined", func() {
		ctx := context.TODO()
		db := s.container.GetTestDB()

		err := db.Connect(ctx)
		s.Assert().NoError(err)

		schemaUtils := NewSchemaUtils(db)

		// drop fake table
		err = schemaUtils.DropTable(ctx, "fake")
		s.Assert().NoError(err)
		s.Assert().Nil(err)

		err = db.Disconnect(ctx)
		s.Assert().NoError(err)
	})
}

func (s *schemaUtilsSuite) TestTableExist() {
	s.Run("with no table defined", func() {
		ctx := context.TODO()
		db := s.container.GetTestDB()

		err := db.Connect(ctx)
		s.Assert().NoError(err)

		schemaUtils := NewSchemaUtils(db)

		// check fake table existence
		ok, err := schemaUtils.TableExists(ctx, "public", "fake")
		s.Assert().NoError(err)
		s.Assert().Nil(err)
		s.Assert().False(ok)
		err = db.Disconnect(ctx)
		s.Assert().NoError(err)
	})
}

func (s *schemaUtilsSuite) TestCreateAndCheckExistence() {
	s.Run("happy path", func() {
		ctx := context.TODO()
		const schemaName = "example"

		db := s.container.GetTestDB()

		err := db.Connect(ctx)
		s.Assert().NoError(err)

		schemaUtils := NewSchemaUtils(db)

		err = schemaUtils.CreateSchema(ctx, schemaName)
		s.Assert().NoError(err)

		ok, err := schemaUtils.SchemaExists(ctx, schemaName)
		s.Assert().NoError(err)
		s.Assert().True(ok)

		err = schemaUtils.DropSchema(ctx, schemaName)
		s.Assert().NoError(err)

		err = db.Disconnect(ctx)
		s.Assert().NoError(err)
	})
	s.Run("schema does not exist", func() {
		ctx := context.TODO()
		const schemaName = "example"

		db := s.container.GetTestDB()

		err := db.Connect(ctx)
		s.Assert().NoError(err)

		schemaUtils := NewSchemaUtils(db)

		ok, err := schemaUtils.SchemaExists(ctx, schemaName)
		s.Assert().NoError(err)
		s.Assert().False(ok)

		err = db.Disconnect(ctx)
		s.Assert().NoError(err)
	})
}

func (s *schemaUtilsSuite) TestCreateTable() {
	s.Run("happy path", func() {
		ctx := context.TODO()
		const stmt = `create table mangoes(id serial, taste varchar(10));`

		db := s.container.GetTestDB()

		err := db.Connect(ctx)
		s.Assert().NoError(err)

		_, err = db.Exec(ctx, stmt)
		s.Assert().NoError(err)

		schemaUtils := NewSchemaUtils(db)

		ok, err := schemaUtils.TableExists(ctx, "public", "mangoes")
		s.Assert().NoError(err)
		s.Assert().Nil(err)
		s.Assert().True(ok)

		err = schemaUtils.DropTable(ctx, "public.mangoes")
		s.Assert().NoError(err)

		err = db.Disconnect(ctx)
		s.Assert().NoError(err)
	})
	s.Run("happy path in a different schema", func() {
		ctx := context.TODO()
		schemaName := "example"
		stmt := `create table example.mangoes(id serial, taste varchar(10));`

		db := s.container.GetTestDB()

		err := db.Connect(ctx)
		s.Assert().NoError(err)

		schemaUtils := NewSchemaUtils(db)

		err = schemaUtils.CreateSchema(ctx, schemaName)
		s.Assert().NoError(err)

		ok, err := schemaUtils.SchemaExists(ctx, schemaName)
		s.Assert().NoError(err)
		s.Assert().True(ok)

		_, err = db.Exec(ctx, stmt)
		s.Assert().NoError(err)

		ok, err = schemaUtils.TableExists(ctx, "example", "mangoes")
		s.Assert().NoError(err)
		s.Assert().Nil(err)
		s.Assert().True(ok)

		err = schemaUtils.DropSchema(ctx, schemaName)
		s.Assert().NoError(err)
	})
}
