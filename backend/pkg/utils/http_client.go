package utils

import (
	"math/rand"
	"net/http"
	"net/url"
)

// Constantes para encabezados HTTP comunes
const (
	headerSecFetchDest            = "Sec-Fetch-Dest"
	headerSecFetchMode            = "Sec-Fetch-Mode"
	headerSecFetchSite            = "Sec-Fetch-Site"
	headerSecFetchUser            = "Sec-Fetch-User"
	headerUpgradeInsecureRequests = "Upgrade-Insecure-Requests"
	headerSecChUa                 = "Sec-Ch-Ua"
	headerSecChUaPlatform         = "Sec-Ch-Ua-Platform"
	headerSecChUaMobile           = "Sec-Ch-Ua-Mobile"
)

// BrowserProfile define las características de un navegador para evitar fingerprinting
type BrowserProfile struct {
	UserAgent  string
	SecChUa    string
	Platform   string
	IsChromium bool
	IsFirefox  bool
}

var browserProfiles = []BrowserProfile{
	{
		UserAgent:  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36",
		SecChUa:    `"Not_A Brand";v="8", "Chromium";v="123", "Google Chrome";v="123"`,
		Platform:   `"Windows"`,
		IsChromium: true,
	},
	{
		UserAgent:  "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36",
		SecChUa:    `"Not_A Brand";v="8", "Chromium";v="122", "Google Chrome";v="122"`,
		Platform:   `"macOS"`,
		IsChromium: true,
	},
	{
		UserAgent:  "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:124.0) Gecko/20100101 Firefox/124.0",
		Platform:   `"Windows"`,
		IsFirefox:  true,
	},
	{
		UserAgent:  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 Edg/123.0.0.0",
		SecChUa:    `"Not_A Brand";v="8", "Chromium";v="123", "Microsoft Edge";v="123"`,
		Platform:   `"Windows"`,
		IsChromium: true,
	},
	{
		UserAgent: "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15",
		Platform:  `"macOS"`,
	},
}

var languages = []string{
	"es-419,es;q=0.9,en;q=0.8",
	"en-US,en;q=0.9",
	"es-ES,es;q=0.9,en;q=0.8",
}

// GetRandomUserAgent devuelve un User Agent de la lista de forma aleatoria para evitar fingerprinting.
// En Go 1.20+, rand está auto-seadeado globalmente, por lo que rand.Seed() no es necesario ni recomendado.
func GetRandomUserAgent() string {
	return browserProfiles[rand.Intn(len(browserProfiles))].UserAgent
}

// GetRandomProfile devuelve un perfil de navegador para asignar headers de forma coherente.
func GetRandomProfile() BrowserProfile {
	return browserProfiles[rand.Intn(len(browserProfiles))]
}

// SetStealthHeaders inyecta encabezados HTTP avanzados para simular un navegador
// real. Soporta rotación de User-Agent, Referer dinámico y contexto (mode: "page" o "api").
func SetStealthHeaders(req *http.Request, referer string, mode string) {
	profile := GetRandomProfile()
	req.Header.Set("User-Agent", profile.UserAgent)
	req.Header.Set("Accept-Language", languages[rand.Intn(len(languages))])
	req.Header.Set("Accept-Encoding", "gzip, deflate, br")

	setConnectionAndAcceptHeaders(req, mode)
	setRefererHeaders(req, referer, mode)
	setBrowserSpecificHeaders(req, profile)
	setFetchModeHeaders(req, profile, mode)
}

func setConnectionAndAcceptHeaders(req *http.Request, mode string) {
	if req.ProtoMajor == 0 || req.ProtoMajor == 1 {
		req.Header.Set("Connection", "keep-alive")
	}
	if mode == "api" {
		req.Header.Set("Accept", "application/json, text/plain, */*")
	} else {
		req.Header.Set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
		req.Header.Set("Cache-Control", "max-age=0")
	}
}

func setRefererHeaders(req *http.Request, referer string, mode string) {
	if referer == "" {
		return
	}
	req.Header.Set("Referer", referer)
	if mode == "api" {
		u, err := url.Parse(referer)
		if err == nil {
			req.Header.Set("Origin", u.Scheme+"://"+u.Host)
		} else {
			req.Header.Set("Origin", referer)
		}
	}
}

func setBrowserSpecificHeaders(req *http.Request, profile BrowserProfile) {
	if !profile.IsChromium {
		return
	}

	req.Header.Set(headerSecChUaMobile, "?0")
	req.Header.Set(headerSecChUaPlatform, profile.Platform)
	req.Header.Set(headerSecChUa, profile.SecChUa)
}

func setFetchModeHeaders(req *http.Request, profile BrowserProfile, mode string) {
	// Safari no usa Sec-Fetch-*, por lo que implícitamente lo omitimos
	if !profile.IsChromium && !profile.IsFirefox {
		return
	}

	if mode == "page" {
		req.Header.Set(headerSecFetchDest, "document")
		req.Header.Set(headerSecFetchMode, "navigate")
		req.Header.Set(headerSecFetchSite, "cross-site")
		req.Header.Set(headerUpgradeInsecureRequests, "1")
		
		if profile.IsChromium {
			req.Header.Set(headerSecFetchUser, "?1")
		}
	} else if mode == "api" {
		req.Header.Set(headerSecFetchDest, "empty")
		req.Header.Set(headerSecFetchMode, "cors")
		req.Header.Set(headerSecFetchSite, "same-origin")
	}
}
