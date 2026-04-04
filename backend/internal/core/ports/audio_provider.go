package ports

import (
	"context"

	"phantom-beats-backend/internal/core/domain"
)

// AudioProvider define la interfaz que cualquier origen de música debe cumplir (ej. YouTube, SoundCloud)
type AudioProvider interface {
	Name() string
	Search(ctx context.Context, query string) ([]domain.Song, error)
	
}
