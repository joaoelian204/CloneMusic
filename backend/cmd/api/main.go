package main

import (
	"log"
	"os"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/logger"
	"github.com/gofiber/fiber/v2/middleware/recover"

	"phantom-beats-backend/internal/handlers"
	ytclient "github.com/kkdai/youtube/v2"

	"phantom-beats-backend/internal/orchestration"
	"phantom-beats-backend/internal/providers/itunes"
	"phantom-beats-backend/internal/providers/youtube"
	"phantom-beats-backend/pkg/network"
)

func main() {
	// Inicializar la aplicación Fiber
	app := fiber.New(fiber.Config{
		DisableStartupMessage: false,
		AppName:               "Phantom Beats Backend Proxy",
	})

	// Middlewares
	app.Use(recover.New())
	app.Use(logger.New())
	app.Use(cors.New(cors.Config{
		AllowOrigins: "*",
		AllowHeaders: "Origin, Content-Type, Accept",
	}))

	// Inicializar el orquestador principal de Red (Request Manager)
	// Aquí se pueden agregar strings de proxies reales: []string{"http://proxy1..."}
	reqManager := network.NewRequestManager([]string{}, 3)

	// Inicializar proveedores y dependencias inyectando el RequestManager
	itunesProvider := itunes.NewITunesProvider(reqManager.GetHTTPClient())
	ytProvider := youtube.NewYouTubeProvider(reqManager)

	// El Provider Manager maneja la cadena de fallback
	providerManager := orchestration.NewProviderManager(ytProvider, itunesProvider)

	// Handlers
	searchHandler := handlers.NewSearchHandler(providerManager)

	// Endpoints base para diagnostico y compatibilidad con plataformas PaaS
	app.Get("/", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{
			"service": "phantom-beats-backend",
			"status":  "ok",
		})
	})
	app.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})

	// Rutas
	api := app.Group("/api/v1")
	
	// Endpoints principales
	api.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})
	api.Get("/search", searchHandler.Search)

	// Client-Side stream resolver endpoint
	api.Get("/stream-url/:id", func(c *fiber.Ctx) error {
		id := c.Params("id")
		client := ytclient.Client{}
		video, err := client.GetVideo(id)
		if err != nil {
			return c.Status(400).JSON(fiber.Map{"error": err.Error()})
		}
		formats := video.Formats.WithAudioChannels()
		for _, f := range formats {
			if f.ItagNo == 140 || f.ItagNo == 251 {
				url, err := client.GetStreamURL(video, &f)
				if err == nil {
					return c.JSON(fiber.Map{"url": url})
				}
			}
		}
		return c.Status(404).JSON(fiber.Map{"error": "No compatible audio stream found"})
	})

	// Iniciar servidor
	port := os.Getenv("PORT")
	if port == "" {
		port = "3000"
	}

	log.Printf("Iniciando Phantom Beats Proxy en el puerto %s...", port)
	if err := app.Listen(":" + port); err != nil {
		log.Fatalf("Error al arrancar el servidor: %v", err)
	}
}
