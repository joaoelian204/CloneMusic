package domain

// Song representa la entidad principal dentro de la aplicación interactuada.
type Song struct {
	ID       string `json:"id"`
	Title    string `json:"title"`
	Artist   string `json:"artist"`
	Duration int    `json:"duration"`
	CoverURL string `json:"coverUrl"`
	Provider string `json:"provider"`
}

// StreamResult representa el resultado de obtener el flujo de audio
type StreamResult struct {
	URL         string `json:"url"`         // URL directa temporal del stream de audio
	ContentType string `json:"contentType"` // Tipo de contenido (ej. audio/mpeg)
	Provider    string `json:"provider"`    // Cuál proveedor fue exitoso resolviendo
}
