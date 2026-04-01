package main

import (
	"log"

	"github.com/gofiber/fiber/v2"
	"github.com/gofiber/fiber/v2/middleware/cors"
	"github.com/gofiber/fiber/v2/middleware/recover"

	"phantom-beats-backend/internal/handlers"
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
	proxyHandler := handlers.NewProxyHandler(providerManager)
	searchHandler := handlers.NewSearchHandler(providerManager)

	// Rutas
	api := app.Group("/api/v1")
	
	// Endpoints principales
	api.Get("/health", func(c *fiber.Ctx) error {
		return c.JSON(fiber.Map{"status": "ok"})
	})
	api.Get("/search", searchHandler.Search)
	api.Get("/stream/:id", proxyHandler.Stream)

	// Iniciar servidor
	log.Println("Iniciando Phantom Beats Proxy en el puerto 3000...")
	if err := app.Listen(":3000"); err != nil {
		log.Fatalf("Error al arrancar el servidor: %v", err)
	}
}
