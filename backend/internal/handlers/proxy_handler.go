package handlers

import (
	"context"

	"github.com/gofiber/fiber/v2"

	"phantom-beats-backend/internal/orchestration"
)

type ProxyHandler struct {
	manager *orchestration.ProviderManager
}

func NewProxyHandler(manager *orchestration.ProviderManager) *ProxyHandler {
	return &ProxyHandler{
		manager: manager,
	}
}

// Stream resuelve de dónde conseguir la canción y la sirve (por ahora devuelve URL directa o hace un stream)
func (h *ProxyHandler) Stream(c *fiber.Ctx) error {
	id := c.Params("id")
	if id == "" {
		return c.Status(fiber.StatusBadRequest).JSON(fiber.Map{
			"error": "ID de la canción requerido",
		})
	}

	ctx := context.Background()
	streamResult, err := h.manager.GetStream(ctx, id)
	if err != nil {
		return c.Status(fiber.StatusServiceUnavailable).JSON(fiber.Map{
			"error": "El stream no pudo ser inicializado",
		})
	}

	// Devolver contrato JSON para cliente móvil (Retrofit DTO).
	return c.JSON(streamResult)
}
