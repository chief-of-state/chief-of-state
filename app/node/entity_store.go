package node

import (
	"context"
	"log"
	"sync"
	"time"

	"github.com/patrickmn/go-cache"
)

// EntityFactory generates a new entity for the entity store
type EntityFactory func(entityID string) *Entity

// EntityStore manges entities, creating, caching, and passivating them
type EntityStore struct {
	entities *cache.Cache
	mtx      sync.Mutex
	factory  EntityFactory
}

func NewEntityStore(factory EntityFactory) *EntityStore {
	c := cache.New(time.Minute*5, time.Minute*10)
	c.OnEvicted(func(id string, entity interface{}) {
		e := entity.(*Entity)
		e.Shutdown(context.TODO())
	})
	e := &EntityStore{
		entities: c,
		mtx:      sync.Mutex{},
		factory:  factory,
	}
	return e
}

// func (e EntityStore) onEvict(entityID string, entity)

// GetOrCreate retrieves an entity or generates one with the provided factory
func (e *EntityStore) GetOrCreate(ctx context.Context, entityID string) *Entity {
	// lock the store while potentially creating an entity
	e.mtx.Lock()
	defer e.mtx.Unlock()
	// unpack entity from cache if found, else set it
	if val, found := e.entities.Get(entityID); found {
		entity := val.(*Entity)
		return entity
	} else {
		// create new entity
		entity := e.factory(entityID)
		e.entities.Set(entityID, entity, cache.DefaultExpiration)
		return entity
	}
}

// Shutdown all entities in this store
func (e *EntityStore) Shutdown(ctx context.Context) error {
	log.Printf("shutting down EntityStore")
	e.mtx.Lock()
	defer e.mtx.Unlock()
	e.entities.Flush()
	// TODO run "shutdown" on each entity
	return nil
}
