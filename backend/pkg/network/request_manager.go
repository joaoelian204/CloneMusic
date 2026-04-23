package network

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"math"
	"math/rand"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"sync"
	"time"
)

var (
	// ErrMaxRetriesReached se emite cuando la petición ha agotado todos los intentos permitidos.
	ErrMaxRetriesReached = errors.New("se alcanzó el número máximo de reintentos")

	// ErrBlocked se emite cuando se detecta explícitamente un banneo o límite (429/403).
	ErrBlocked = errors.New("detectado bloqueo por el proveedor (403/429)")
)

type proxyEntry struct {
	url       *url.URL
	failUntil time.Time
}

func (p *proxyEntry) MarkBad() {
	// Marcar este proxy como malo por 5 minutos (Cooldown)
	p.failUntil = time.Now().Add(5 * time.Minute)
}

// ProxyPool gestiona una lista de proxies con health check preventivo y rotación Round-Robin.
type ProxyPool struct {
	proxies []*proxyEntry
	mu      sync.Mutex
	current int
}

// NewProxyPool inicializa un pool a partir de uris raw.
func NewProxyPool(proxyURLs []string) *ProxyPool {
	var parsed []*proxyEntry
	for _, p := range proxyURLs {
		if u, err := url.Parse(p); err == nil {
			parsed = append(parsed, &proxyEntry{url: u})
		}
	}
	return &ProxyPool{proxies: parsed}
}

// GetNext obtiene la siguiente URL del proxy de la lista rotativa que esté "sana".
func (p *ProxyPool) GetNext() *proxyEntry {
	p.mu.Lock()
	defer p.mu.Unlock()

	if len(p.proxies) == 0 {
		return nil
	}
	
	// Iterar para encontrar uno sano sin entrar en loop infinito
	for i := 0; i < len(p.proxies); i++ {
		entry := p.proxies[p.current]
		p.current = (p.current + 1) % len(p.proxies)
		
		// Health check: ¿Ya pasó su penalización?
		if time.Now().After(entry.failUntil) {
			return entry
		}
	}
	return nil // Todos los proxies están en cooldown
}

// RequestManager es una capa avanzada sobre http.Client que inyecta resiliencia.
type RequestManager struct {
	client      *http.Client
	proxyPool   *ProxyPool
	maxRetries  int
	baseDelay   time.Duration
	rateLimiter *time.Ticker
}

// NewRequestManager inicializa el mánager con un grupo de proxies, políticas de timeouts agresivos
// e implementa el Transport para usar el ProxyPool dinámicamente en cada petición.
func NewRequestManager(proxies []string, maxRetries int) *RequestManager {
	pool := NewProxyPool(proxies)
	jar, _ := cookiejar.New(nil) // Soporte de cookies para sesiones largas

	transport := &http.Transport{
		Proxy: func(req *http.Request) (*url.URL, error) {
			if p := pool.GetNext(); p != nil {
				return p.url, nil
			}
			// Fallback a los proxies del OS si el pool está vacío o todos quemados
			return http.ProxyFromEnvironment(req)
		},
		ForceAttemptHTTP2:     true,
		MaxIdleConns:          100,
		MaxIdleConnsPerHost:   10,
		IdleConnTimeout:       90 * time.Second,
		TLSHandshakeTimeout:   10 * time.Second,
		ExpectContinueTimeout: 1 * time.Second,
	}

	return &RequestManager{
		client: &http.Client{
			Transport: transport,
			Timeout:   15 * time.Second,
			Jar:       jar,
		},
		proxyPool:   pool,
		maxRetries:  maxRetries,
		baseDelay:   2 * time.Second,
		rateLimiter: time.NewTicker(200 * time.Millisecond), // Limita globalmente a ~5 req/sec
	}
}

// GetHTTPClient devuelve el cliente HTTP subyacente.
func (rm *RequestManager) GetHTTPClient() *http.Client {
	return rm.client
}

// Do ejecuta la petición aplicando reintentos, body caching, Rate Limiting,
// y rotación de headers con Backoff Exponencial + Jitter.
func (rm *RequestManager) Do(req *http.Request, rotateHeaders func(*http.Request)) (*http.Response, error) {
	var resp *http.Response
	var err error

	bodyBytes := extractBodyBytes(req)

	for attempt := 0; rm.maxRetries == -1 || attempt <= rm.maxRetries; attempt++ {
		// --- Ticker Rate Limiter Global ---
		<-rm.rateLimiter.C

		reqClone := buildRequestClone(req, bodyBytes, rotateHeaders)
		resp, err = rm.client.Do(reqClone)

		if err == nil {
			var done bool
			done, err = evaluateResponseForRetry(resp)
			if done {
				return resp, err
			}
		}

		if attempt >= rm.maxRetries && rm.maxRetries != -1 {
			break
		}

		// --- Algoritmo de Exponential Backoff + Jitter ---
		backoff := float64(rm.baseDelay) * math.Pow(2, float64(attempt))
		jitter := rand.Float64() * 1000
		sleepDuration := time.Duration(backoff) + time.Duration(jitter)*time.Millisecond

		select {
		case <-req.Context().Done():
			return nil, req.Context().Err()
		case <-time.After(sleepDuration):
		}
	}

	if err != nil {
		return nil, fmt.Errorf("%w (último factor: %v)", ErrMaxRetriesReached, err)
	}

	return nil, ErrMaxRetriesReached
}

// extractBodyBytes lee y retorna el Body previniendo que se corrompa en retries.
func extractBodyBytes(req *http.Request) []byte {
	if req.Body == nil {
		return nil
	}
	bodyBytes, _ := io.ReadAll(req.Body)
	req.Body.Close()
	return bodyBytes
}

// buildRequestClone clona la Request aislando headers y body listos para su reintento seguros.
func buildRequestClone(req *http.Request, bodyBytes []byte, rotateFunc func(*http.Request)) *http.Request {
	reqClone := req.Clone(req.Context())
	if bodyBytes != nil {
		reqClone.Body = io.NopCloser(bytes.NewReader(bodyBytes))
	}
	if rotateFunc != nil {
		rotateFunc(reqClone)
	}
	return reqClone
}

// evaluateResponseForRetry analiza el status HTTP y dictamina si hay bloqueo, se debe reintentar o retorna ok.
func evaluateResponseForRetry(resp *http.Response) (bool, error) {
	if resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode == http.StatusForbidden {
		_ = resp.Body.Close()
		return false, ErrBlocked
	}
	if resp.StatusCode >= 200 && resp.StatusCode < 400 {
		return true, nil
	}
	if resp.StatusCode >= 500 {
		_ = resp.Body.Close()
		return false, fmt.Errorf("server target error: %d", resp.StatusCode)
	}
	// Otros errores cliente (404, 400) no son motivo de retry, los retornamos
	return true, nil
} 
