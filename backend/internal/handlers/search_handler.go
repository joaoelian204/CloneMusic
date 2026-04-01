package handlers

import (
	"context"
	"strconv"

	"github.com/gofiber/fiber/v2"

	"phantom-beats-backend/internal/core/domain"
	"phantom-beats-backend/internal/orchestration"
)

type SearchHandler struct {
	manager *orchestration.ProviderManager
}

func NewSearchHandler(manager *orchestration.ProviderManager) *SearchHandler {
	return &SearchHandler{
		manager: manager,
	}
}

// Search maneja la petición de búsqueda de canciones
func (h *SearchHandler) Search(c *fiber.Ctx) error {
	query := c.Query("q")
	if query == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "El parámetro de búsqueda 'q' es requerido",
		})
	}

	limit := 25
	if raw := c.Query("limit"); raw != "" {
		if parsed, err := strconv.Atoi(raw); err == nil {
			if parsed > 0 && parsed <= 50 {
				limit = parsed
			}
		}
	}

	offset := 0
	if raw := c.Query("offset"); raw != "" {
		if parsed, err := strconv.Atoi(raw); err == nil && parsed >= 0 {
			offset = parsed
		}
	}

	mode := domain.NormalizeSearchMode(c.Query("mode", domain.SearchModeBalanced))

	ctx := context.WithValue(context.Background(), domain.SearchModeContextKey, mode)
	songs, err := h.manager.SearchWithMinResults(ctx, query, offset+1)
	if err != nil {
		return c.Status(fiber.StatusNotFound).JSON(fiber.Map{
			"error": "No se encontraron resultados o fallaron todos los proveedores",
		})
	}

	if offset >= len(songs) {
		return c.JSON([]interface{}{})
	}

	end := offset + limit
	if end > len(songs) {
		end = len(songs)
	}
	songs = songs[offset:end]

	return c.JSON(songs)
}
