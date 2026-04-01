package orchestration

import (
	"context"
	"strings"
	"sync"
	"time"

	"phantom-beats-backend/internal/core/domain"
	"phantom-beats-backend/internal/core/ports"
	"phantom-beats-backend/pkg/utils"
)

// ProviderStatus mantiene el estado para Circuit Breaker
type ProviderStatus struct {
	Failures   int
	DisabledTo time.Time
}

// ProviderManager maneja la lógica de ruteo y fallback entre proveedores
type ProviderManager struct {
	providers []ports.AudioProvider
	status    map[string]*ProviderStatus
	mu        sync.RWMutex
	searchMu  sync.RWMutex
	searchCache map[string]cachedSearchResults
}

type cachedSearchResults struct {
	Songs     []domain.Song
	ExpiresAt time.Time
}

const searchCacheTTL = 2 * time.Minute

// NewProviderManager inicializa un nuevo manager.
func NewProviderManager(providers ...ports.AudioProvider) *ProviderManager {
	status := make(map[string]*ProviderStatus)
	for _, p := range providers {
		status[p.Name()] = &ProviderStatus{}
	}
	return &ProviderManager{
		providers:   providers,
		status:      status,
		searchCache: make(map[string]cachedSearchResults),
	}
}

func (pm *ProviderManager) getCachedSearch(query string) ([]domain.Song, bool) {
	key := strings.TrimSpace(strings.ToLower(query))
	if key == "" {
		return nil, false
	}

	pm.searchMu.RLock()
	entry, ok := pm.searchCache[key]
	pm.searchMu.RUnlock()
	if !ok {
		return nil, false
	}

	if time.Now().After(entry.ExpiresAt) {
		pm.searchMu.Lock()
		delete(pm.searchCache, key)
		pm.searchMu.Unlock()
		return nil, false
	}

	cloned := make([]domain.Song, len(entry.Songs))
	copy(cloned, entry.Songs)
	return cloned, true
}

func (pm *ProviderManager) setCachedSearch(query string, songs []domain.Song) {
	key := strings.TrimSpace(strings.ToLower(query))
	if key == "" || len(songs) == 0 {
		return
	}

	cloned := make([]domain.Song, len(songs))
	copy(cloned, songs)

	pm.searchMu.Lock()
	pm.searchCache[key] = cachedSearchResults{
		Songs:     cloned,
		ExpiresAt: time.Now().Add(searchCacheTTL),
	}
	pm.searchMu.Unlock()
}

// canUseProvider verifica si el Circuit Breaker permite usar este proveedor.
func (pm *ProviderManager) canUseProvider(name string) bool {
	pm.mu.RLock()
	defer pm.mu.RUnlock()
	
	s := pm.status[name]
	if time.Now().Before(s.DisabledTo) {
		return false
	}
	return true
}

// recordSuccess hace reset al contador de fail
func (pm *ProviderManager) recordSuccess(name string) {
	pm.mu.Lock()
	defer pm.mu.Unlock()
	pm.status[name].Failures = 0
}

// recordFailure aumenta el fail. Si llega a 3, desactiva temporalmente el proveedor (Circuit Breaker).
func (pm *ProviderManager) recordFailure(name string) {
	pm.mu.Lock()
	defer pm.mu.Unlock()
	
	s := pm.status[name]
	s.Failures++
	if s.Failures >= 3 {
		utils.LogInfo("CIRCUIT BREAKER: Abierto para " + name + " - Desactivado por 30 segundos.")
		s.DisabledTo = time.Now().Add(30 * time.Second)
		s.Failures = 0 // Reset para la próxima vez
	}
}

// Search busca distribuídamente
func (pm *ProviderManager) Search(ctx context.Context, query string) ([]domain.Song, error) {
	return pm.SearchWithMinResults(ctx, query, 1)
}

func (pm *ProviderManager) SearchWithMinResults(ctx context.Context, query string, minResults int) ([]domain.Song, error) {
	if minResults < 1 {
		minResults = 1
	}

	if cached, ok := pm.getCachedSearch(query); ok && len(cached) >= minResults {
		return cached, nil
	}

	for _, provider := range pm.providers {
		name := provider.Name()
		if !pm.canUseProvider(name) {
			utils.LogInfo("Circuit Breaker activo, saltando provider: " + name)
			continue
		}

		timeoutCtx, cancel := context.WithTimeout(ctx, 8*time.Second) // Timeout para incluir enriquecimiento de metadata sin cortes prematuros
		
		utils.LogInfo("Intentando búsqueda con provider: " + name)
		songs, err := provider.Search(timeoutCtx, query)
		cancel()

		if err == nil && len(songs) > 0 {
			pm.recordSuccess(name)
			pm.setCachedSearch(query, songs)
			return songs, nil
		}
		
		utils.LogError("Fallo en provider "+name, err)
		pm.recordFailure(name)
	}

	return nil, domain.ErrSongNotFound
}

// GetStream obtiene el streaming URL aplicando fallback
func (pm *ProviderManager) GetStream(ctx context.Context, songID string) (*domain.StreamResult, error) {
	for _, provider := range pm.providers {
		name := provider.Name()
		if !pm.canUseProvider(name) {
			utils.LogInfo("Circuit Breaker activo, saltando provider para stream: " + name)
			continue
		}

		timeoutCtx, cancel := context.WithTimeout(ctx, 5*time.Second) // Strict Timeout para Streams

		utils.LogInfo("Pidiendo stream a: " + name + " para id: " + songID)
		result, err := provider.GetStream(timeoutCtx, songID)
		cancel()

		if err == nil && result != nil {
			pm.recordSuccess(name)
			return result, nil
		}
		
		utils.LogError("Fallo en provider "+name+" para stream", err)
		pm.recordFailure(name)
	}
	
	return nil, domain.ErrStreamFailed
}
