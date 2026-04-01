package youtube

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math/rand"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"phantom-beats-backend/internal/core/domain"
	"phantom-beats-backend/pkg/network"
	"phantom-beats-backend/pkg/utils"

	"github.com/kkdai/youtube/v2"
)

var (
	maxSongsForITunesEnrichment = envInt("YT_ITUNES_TOP_N", 8)
	maxConcurrentITunesLookups = envInt("YT_ITUNES_MAX_CONCURRENCY", 3)
	itunesLookupTimeout        = time.Duration(envInt("YT_ITUNES_TIMEOUT_MS", 1500)) * time.Millisecond
	itunesCoverCacheTTL        = time.Duration(envInt("YT_ITUNES_CACHE_TTL_MIN", 720)) * time.Minute
	maxYouTubeSearchResults    = envInt("YT_SEARCH_MAX_RESULTS", 120)
)

var (
	multiSpaceRe             = regexp.MustCompile(`\s+`)
	parenthesisOrBracketRe   = regexp.MustCompile(`\[[^\]]*\]|\([^\)]*\)`)
	ytNoiseTokenRe           = regexp.MustCompile(`(?i)\b(official|music\s*video|video|lyric\s*video|lyrics?|audio|visualizer|explicit|clean|remaster(?:ed)?|version|edit(?:ed)?|live|clip|mv)\b`)
	yearTokenRe              = regexp.MustCompile(`\b(19|20)\d{2}\b`)
	nonAlnumForCacheKeyRe    = regexp.MustCompile(`[^a-z0-9]+`)
	unofficialNoiseRe        = regexp.MustCompile(`(?i)\b(lyrics?|lyric\s*video|cover|karaoke|slowed|reverb|sped\s*up|nightcore|fan\s*made|edit(?:ed)?|mashup)\b`)
	officialSignalRe         = regexp.MustCompile(`(?i)\b(official|vevo|topic|artist\s*-\s*topic)\b`)
)

type coverCacheEntry struct {
	CoverURL  string
	ExpiresAt time.Time
}

type coverCache struct {
	mu    sync.RWMutex
	items map[string]coverCacheEntry
}

func newCoverCache() *coverCache {
	return &coverCache{items: make(map[string]coverCacheEntry)}
}

func (c *coverCache) Get(key string) (string, bool) {
	c.mu.RLock()
	entry, ok := c.items[key]
	c.mu.RUnlock()
	if !ok {
		return "", false
	}
	if time.Now().After(entry.ExpiresAt) {
		c.mu.Lock()
		delete(c.items, key)
		c.mu.Unlock()
		return "", false
	}
	return entry.CoverURL, true
}

func (c *coverCache) Set(key string, coverURL string, ttl time.Duration) {
	if key == "" || coverURL == "" || ttl <= 0 {
		return
	}

	c.mu.Lock()
	c.items[key] = coverCacheEntry{
		CoverURL:  coverURL,
		ExpiresAt: time.Now().Add(ttl),
	}
	c.mu.Unlock()
}

var ytITunesCoverCache = newCoverCache()

type oEmbedResponse struct {
	Title      string `json:"title"`
	AuthorName string `json:"author_name"`
}

type itunesCoverSearchResponse struct {
	Results []struct {
		TrackName     string `json:"trackName"`
		ArtistName    string `json:"artistName"`
		ArtworkURL100 string `json:"artworkUrl100"`
	} `json:"results"`
}

type itunesCandidate struct {
	Artist string
	Track  string
	Cover  string
}

func envInt(name string, defaultValue int) int {
	raw, ok := os.LookupEnv(name)
	if !ok || strings.TrimSpace(raw) == "" {
		return defaultValue
	}
	parsed, err := strconv.Atoi(strings.TrimSpace(raw))
	if err != nil || parsed <= 0 {
		return defaultValue
	}
	return parsed
}

func normalizeForCacheKey(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	s = strings.ReplaceAll(s, "&", " and ")
	s = nonAlnumForCacheKeyRe.ReplaceAllString(s, " ")
	return strings.TrimSpace(multiSpaceRe.ReplaceAllString(s, " "))
}

func buildCoverCacheKey(artist string, track string) string {
	artistPart := normalizeForCacheKey(artist)
	trackPart := normalizeForCacheKey(track)
	if artistPart == "" && trackPart == "" {
		return ""
	}
	return artistPart + "|" + trackPart
}

// YouTubeProvider implementa ports.AudioProvider
type YouTubeProvider struct {
	client  *youtube.Client
	reqMgr  *network.RequestManager
}

func NewYouTubeProvider(rm *network.RequestManager) *YouTubeProvider {
	return &YouTubeProvider{
		client: &youtube.Client{
			HTTPClient: rm.GetHTTPClient(), 
		},
		reqMgr: rm,
	}
}

func (yp *YouTubeProvider) Name() string {
	return "YouTube"
}

func parseDuration(lenStr string) int {
	parts := strings.Split(lenStr, ":")
	if len(parts) == 2 {
		m, _ := strconv.Atoi(parts[0])
		s, _ := strconv.Atoi(parts[1])
		return m*60 + s
	} else if len(parts) == 3 {
		h, _ := strconv.Atoi(parts[0])
		m, _ := strconv.Atoi(parts[1])
		s, _ := strconv.Atoi(parts[2])
		return h*3600 + m*60 + s
	}
	return 0
}

func buildYouTubeCoverURL(videoID string) string {
	// WebP suele ser más liviano que JPG a misma calidad visual.
	// hqdefault mejora nitidez frente a mqdefault sin subir demasiado el peso.
	if videoID == "" {
		return ""
	}
	return fmt.Sprintf("https://i.ytimg.com/vi_webp/%s/hqdefault.webp", videoID)
}

func buildYouTubeCoverFallbackURL(videoID string) string {
	if videoID == "" {
		return ""
	}
	return fmt.Sprintf("https://i.ytimg.com/vi/%s/hqdefault.jpg", videoID)
}

func (yp *YouTubeProvider) Search(ctx context.Context, query string) ([]domain.Song, error) {
	mode := searchModeFromContext(ctx)
	searchURL := "https://www.youtube.com/results?search_query=" + url.QueryEscape(query)
	html, err := yp.fetchSearchHTML(ctx, searchURL)
	if err != nil {
		return nil, err
	}

	results, err := yp.extractSongsFromHTML(ctx, html, query, mode)
	if err != nil {
		return nil, err
	}

	yp.enrichCoversFromITunes(ctx, query, results)
	ensureCoverFallbacks(results)

	return results, nil
}

func searchModeFromContext(ctx context.Context) string {
	if ctx == nil {
		return domain.SearchModeBalanced
	}
	raw, _ := ctx.Value(domain.SearchModeContextKey).(string)
	return domain.NormalizeSearchMode(raw)
}

func isLikelyArtistIntent(query string) bool {
	q := strings.TrimSpace(query)
	if q == "" {
		return false
	}
	wordCount := len(strings.Fields(q))
	return inRange(wordCount, 1, 4)
}

func scoreSearchResult(query string, song domain.Song, mode string) int {
	queryN := normalizeForCacheKey(query)
	titleN := normalizeForCacheKey(song.Title)
	artistN := normalizeForCacheKey(song.Artist)

	score := 0
	if queryN == "" {
		return score
	}

	if artistN == queryN {
		score += 130
	} else if strings.Contains(artistN, queryN) || strings.Contains(queryN, artistN) {
		score += 90
	}

	if strings.Contains(titleN, queryN) {
		score += 35
	}

	if officialSignalRe.MatchString(song.Title) || officialSignalRe.MatchString(song.Artist) {
		if mode == domain.SearchModeTurbo {
			score += 8
		} else {
		score += 15
		}
	}

	if unofficialNoiseRe.MatchString(song.Title) {
		if mode == domain.SearchModeTurbo {
			score -= 15
		} else {
			score -= 45
		}
	}

	if strings.EqualFold(strings.TrimSpace(song.Artist), "YouTube") {
		score -= 20
	}

	return score
}

func rankAndFilterResults(query string, songs []domain.Song, mode string) []domain.Song {
	type scoredSong struct {
		song  domain.Song
		score int
	}

	scored := make([]scoredSong, 0, len(songs))
	for _, s := range songs {
		scored = append(scored, scoredSong{song: s, score: scoreSearchResult(query, s, mode)})
	}

	artistIntent := isLikelyArtistIntent(query)
	if artistIntent {
		if mode == domain.SearchModeTurbo {
			relaxed := make([]scoredSong, 0, len(scored))
			for _, item := range scored {
				if item.score >= 10 {
					relaxed = append(relaxed, item)
				}
			}
			if len(relaxed) > 0 {
				scored = relaxed
			}
		} else {
			strict := make([]scoredSong, 0, len(scored))
			for _, item := range scored {
				if item.score >= 45 {
					strict = append(strict, item)
				}
			}
			if len(strict) > 0 {
				scored = strict
			}
		}
	}

	sort.SliceStable(scored, func(i, j int) bool {
		if scored[i].score == scored[j].score {
			return scored[i].song.Duration > scored[j].song.Duration
		}
		return scored[i].score > scored[j].score
	})

	ordered := make([]domain.Song, 0, len(scored))
	for _, item := range scored {
		ordered = append(ordered, item.song)
	}
	return ordered
}

func inRange(v int, min int, max int) bool {
	return v >= min && v <= max
}

func ensureCoverFallbacks(results []domain.Song) {
	for i := range results {
		if strings.TrimSpace(results[i].CoverURL) == "" {
			results[i].CoverURL = buildYouTubeCoverFallbackURL(results[i].ID)
		}
	}
}

func cleanYouTubeTitle(raw string) (string, string) {
	title := strings.TrimSpace(strings.ReplaceAll(raw, "\\u0026", "&"))
	title = parenthesisOrBracketRe.ReplaceAllString(title, " ")
	title = ytNoiseTokenRe.ReplaceAllString(title, " ")
	title = yearTokenRe.ReplaceAllString(title, " ")
	title = strings.Trim(title, " -_|:;,.\t\n\r")
	title = multiSpaceRe.ReplaceAllString(title, " ")

	if strings.Contains(title, " - ") {
		parts := strings.SplitN(title, " - ", 2)
		artist := strings.TrimSpace(parts[0])
		track := strings.TrimSpace(parts[1])
		if artist != "" && track != "" {
			return artist + " - " + track, artist
		}
	}

	return title, ""
}

func splitArtistTrack(title string, fallbackArtist string) (string, string) {
	if strings.Contains(title, " - ") {
		parts := strings.SplitN(title, " - ", 2)
		return strings.TrimSpace(parts[0]), strings.TrimSpace(parts[1])
	}
	return strings.TrimSpace(fallbackArtist), strings.TrimSpace(title)
}

func iTunesCoverToHighRes(coverURL string) string {
	if strings.TrimSpace(coverURL) == "" {
		return ""
	}
	if strings.Contains(coverURL, "100x100bb") {
		return strings.Replace(coverURL, "100x100bb", "512x512bb", 1)
	}
	return coverURL
}

func scoreCandidate(songArtist, songTrack string, cand itunesCandidate) int {
	songArtistN := normalizeForCacheKey(songArtist)
	songTrackN := normalizeForCacheKey(songTrack)
	candArtistN := normalizeForCacheKey(cand.Artist)
	candTrackN := normalizeForCacheKey(cand.Track)

	score := 0
	if songArtistN != "" && candArtistN != "" {
		if songArtistN == candArtistN {
			score += 4
		} else if strings.Contains(songArtistN, candArtistN) || strings.Contains(candArtistN, songArtistN) {
			score += 2
		}
	}

	if songTrackN != "" && candTrackN != "" {
		if songTrackN == candTrackN {
			score += 6
		} else if strings.Contains(songTrackN, candTrackN) || strings.Contains(candTrackN, songTrackN) {
			score += 3
		}
	}

	return score
}

func pickBestCoverForSong(songArtist, songTrack string, candidates []itunesCandidate) string {
	bestScore := 0
	bestCover := ""
	normalizedArtist := normalizeForCacheKey(songArtist)
	for _, cand := range candidates {
		score := scoreCandidate(songArtist, songTrack, cand)
		if score > bestScore {
			bestScore = score
			bestCover = cand.Cover
		}
		if normalizedArtist != "" && normalizeForCacheKey(cand.Artist) == normalizedArtist && strings.TrimSpace(cand.Cover) != "" {
			bestCover = cand.Cover
			if bestScore < 3 {
				bestScore = 3
			}
		}
	}

	if bestScore >= 3 {
		return strings.TrimSpace(bestCover)
	}
	if len(candidates) > 0 {
		return strings.TrimSpace(candidates[0].Cover)
	}
	return ""
}

func (yp *YouTubeProvider) fetchITunesCandidates(ctx context.Context, term string) ([]itunesCandidate, error) {
	if strings.TrimSpace(term) == "" {
		return nil, nil
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, itunesLookupTimeout)
	defer cancel()

	endpoint := "https://itunes.apple.com/search?entity=song&limit=8&term=" + url.QueryEscape(term)
	req, err := http.NewRequestWithContext(timeoutCtx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}

	resp, err := yp.reqMgr.GetHTTPClient().Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("itunes search status no exitoso: %d", resp.StatusCode)
	}

	var parsed itunesCoverSearchResponse
	if err := json.NewDecoder(resp.Body).Decode(&parsed); err != nil {
		return nil, err
	}

	candidates := make([]itunesCandidate, 0, len(parsed.Results))
	for _, item := range parsed.Results {
		cover := iTunesCoverToHighRes(item.ArtworkURL100)
		if strings.TrimSpace(item.TrackName) == "" || strings.TrimSpace(cover) == "" {
			continue
		}

		candidates = append(candidates, itunesCandidate{
			Artist: item.ArtistName,
			Track:  item.TrackName,
			Cover:  cover,
		})
	}

	return candidates, nil
}

func (yp *YouTubeProvider) enrichCoversFromITunes(ctx context.Context, query string, songs []domain.Song) {
	for i := range songs {
		songArtist, songTrack := splitArtistTrack(songs[i].Title, songs[i].Artist)
		if coverURL, ok := ytITunesCoverCache.Get(buildCoverCacheKey(songArtist, songTrack)); ok {
			songs[i].CoverURL = coverURL
		}
	}

	limit := maxSongsForITunesEnrichment
	if len(songs) < limit {
		limit = len(songs)
	}
	if limit <= 0 {
		return
	}

	bulkCandidates, _ := yp.fetchITunesCandidates(ctx, query)
	if len(bulkCandidates) > 0 {
		for i := 0; i < limit; i++ {
			songArtist, songTrack := splitArtistTrack(songs[i].Title, songs[i].Artist)
			cacheKey := buildCoverCacheKey(songArtist, songTrack)
			if _, ok := ytITunesCoverCache.Get(cacheKey); ok {
				continue
			}
			cover := pickBestCoverForSong(songArtist, songTrack, bulkCandidates)
			if cover == "" {
				continue
			}
			songs[i].CoverURL = cover
			ytITunesCoverCache.Set(cacheKey, cover, itunesCoverCacheTTL)
		}
	}

	type coverTask struct {
		idx      int
		term     string
		cacheKey string
	}

	tasks := make([]coverTask, 0, limit)
	for i := 0; i < limit; i++ {
		songArtist, songTrack := splitArtistTrack(songs[i].Title, songs[i].Artist)
		cacheKey := buildCoverCacheKey(songArtist, songTrack)
		if _, ok := ytITunesCoverCache.Get(cacheKey); ok {
			continue
		}
		term := strings.TrimSpace(songArtist + " " + songTrack)
		if term == "" {
			continue
		}
		tasks = append(tasks, coverTask{idx: i, term: term, cacheKey: cacheKey})
	}

	if len(tasks) > 0 {
		sem := make(chan struct{}, maxConcurrentITunesLookups)
		var wg sync.WaitGroup
		for _, task := range tasks {
			t := task
			wg.Add(1)
			go func() {
				defer wg.Done()
				sem <- struct{}{}
				defer func() { <-sem }()

				candidates, err := yp.fetchITunesCandidates(ctx, t.term)
				if err != nil || len(candidates) == 0 {
					return
				}

				cover := strings.TrimSpace(candidates[0].Cover)
				if cover == "" {
					return
				}

				songs[t.idx].CoverURL = cover
				ytITunesCoverCache.Set(t.cacheKey, cover, itunesCoverCacheTTL)
			}()
		}
		wg.Wait()
	}

	go yp.warmITunesCoverCache(songs)

	ensureCoverFallbacks(songs)
}

func (yp *YouTubeProvider) warmITunesCoverCache(songs []domain.Song) {
	if len(songs) == 0 {
		return
	}

	backgroundLimit := 12
	if len(songs) < backgroundLimit {
		backgroundLimit = len(songs)
	}
	if backgroundLimit <= 0 {
		return
	}

	ctx, cancel := context.WithTimeout(context.Background(), 4*time.Second)
	defer cancel()

	sem := make(chan struct{}, maxConcurrentITunesLookups)
	var wg sync.WaitGroup

	for i := 0; i < backgroundLimit; i++ {
		songArtist, songTrack := splitArtistTrack(songs[i].Title, songs[i].Artist)
		cacheKey := buildCoverCacheKey(songArtist, songTrack)
		if cacheKey == "" {
			continue
		}
		if _, ok := ytITunesCoverCache.Get(cacheKey); ok {
			continue
		}

		term := strings.TrimSpace(songArtist + " " + songTrack)
		if term == "" {
			continue
		}

		wg.Add(1)
		go func(searchTerm string, key string) {
			defer wg.Done()
			sem <- struct{}{}
			defer func() { <-sem }()

			candidates, err := yp.fetchITunesCandidates(ctx, searchTerm)
			if err != nil || len(candidates) == 0 {
				return
			}

			cover := strings.TrimSpace(candidates[0].Cover)
			if cover == "" {
				return
			}

			ytITunesCoverCache.Set(key, cover, itunesCoverCacheTTL)
		}(term, cacheKey)
	}

	wg.Wait()
}

func (yp *YouTubeProvider) fetchSearchHTML(ctx context.Context, searchURL string) (string, error) {
	req, err := http.NewRequestWithContext(ctx, "GET", searchURL, nil)
	if err != nil {
		return "", err
	}
	
	// Simular timing humano/jitter antes de ejecutar el request de red
	time.Sleep(time.Duration(rand.Intn(300)+100) * time.Millisecond)

	// Usar el RequestManager centralizado con Rotación de Headers por reintento
	resp, err := yp.reqMgr.Do(req, func(r *http.Request) {
		utils.SetStealthHeaders(r, "https://www.youtube.com/", "page")
	})
	if err != nil {
		return yp.fetchSearchHTMLDirect(ctx, searchURL)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}
	html := string(body)
	if !strings.Contains(html, "watch?v=") {
		return yp.fetchSearchHTMLDirect(ctx, searchURL)
	}

	return html, nil
}

func (yp *YouTubeProvider) fetchSearchHTMLDirect(ctx context.Context, searchURL string) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, nil)
	if err != nil {
		return "", err
	}

	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")

	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return "", err
	}

	return string(body), nil
}

func (yp *YouTubeProvider) extractSongsFromHTML(ctx context.Context, html string, query string, mode string) ([]domain.Song, error) {

	// Soporta HTML con saltos de línea y variantes de espaciado del bloque ytInitialData.
	re := regexp.MustCompile(`(?s)var\s+ytInitialData\s*=\s*(\{.*?\});</script>`)
	matches := re.FindStringSubmatch(html)
	jsonStr := html
	if len(matches) >= 2 {
		jsonStr = matches[1]
	}
	// Extrae id/título y toma duración solo cuando existe.
	videoRe := regexp.MustCompile(`(?s)"videoRenderer":\{"videoId":"([^"]+)".*?"title":\{"runs":\[\{"text":"([^"]+)"\}\].*?(?:"lengthText":\{.*?"simpleText":"([^"]+)")?`)
	
	vMatches := videoRe.FindAllStringSubmatch(jsonStr, maxYouTubeSearchResults)
	if len(vMatches) == 0 && jsonStr != html {
		// Fallback: algunas respuestas de YouTube cambian el bloque ytInitialData,
		// pero siguen incluyendo videoRenderer en el HTML completo.
		vMatches = videoRe.FindAllStringSubmatch(html, maxYouTubeSearchResults)
	}
	var results []domain.Song

	for _, m := range vMatches {
		id := m[1]
		rawTitle := m[2]
		durStr := ""
		if len(m) > 3 {
			durStr = m[3]
		}

		title, artist := cleanYouTubeTitle(rawTitle)
		if strings.TrimSpace(artist) == "" {
			artist = "YouTube"
		}

		results = append(results, domain.Song{
			ID:       id,
			Title:    title,
			Artist:   artist,
			Duration: parseDuration(durStr),
			CoverURL: buildYouTubeCoverURL(id),
			Provider: yp.Name(),
		})
	}

	results = rankAndFilterResults(query, results, mode)

	if len(results) == 0 {
		return yp.searchWithWatchIDs(ctx, html, query, mode)
	}

	return results, nil
}

func (yp *YouTubeProvider) searchWithWatchIDs(ctx context.Context, html string, query string, mode string) ([]domain.Song, error) {
	watchRe := regexp.MustCompile(`watch\?v=([A-Za-z0-9_-]{11})`)
	idMatches := watchRe.FindAllStringSubmatch(html, maxYouTubeSearchResults*3)

	seen := make(map[string]struct{})
	results := make([]domain.Song, 0, maxYouTubeSearchResults)

	for _, m := range idMatches {
		if len(m) < 2 {
			continue
		}

		id := m[1]
		if _, ok := seen[id]; ok {
			continue
		}
		seen[id] = struct{}{}

		title := "Track de YouTube"
		artist := "YouTube"

		if meta, err := yp.fetchOEmbedMeta(ctx, id); err == nil {
			if meta.Title != "" {
				title = meta.Title
			}
			if meta.AuthorName != "" {
				artist = meta.AuthorName
			}
		}

		title, parsedArtist := cleanYouTubeTitle(title)
		if strings.TrimSpace(parsedArtist) != "" {
			artist = parsedArtist
		}

		results = append(results, domain.Song{
			ID:       id,
			Title:    title,
			Artist:   artist,
			Duration: 0,
			CoverURL: buildYouTubeCoverURL(id),
			Provider: yp.Name(),
		})

		if len(results) >= maxYouTubeSearchResults {
			break
		}
	}

	if len(results) == 0 {
		return nil, domain.ErrSongNotFound
	}

	results = rankAndFilterResults(query, results, mode)
	if len(results) == 0 {
		return nil, domain.ErrSongNotFound
	}

	ensureCoverFallbacks(results)

	return results, nil
}

func (yp *YouTubeProvider) fetchOEmbedMeta(ctx context.Context, videoID string) (*oEmbedResponse, error) {
	url := "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=" + videoID + "&format=json"
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, err
	}

	resp, err := yp.reqMgr.Do(req, func(r *http.Request) {
		utils.SetStealthHeaders(r, "https://www.youtube.com/", "page")
	})
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	var parsed oEmbedResponse
	if err := json.NewDecoder(resp.Body).Decode(&parsed); err != nil {
		return nil, err
	}

	return &parsed, nil
}

func (yp *YouTubeProvider) GetStream(ctx context.Context, songID string) (*domain.StreamResult, error) {
	video, err := yp.client.GetVideoContext(ctx, songID)
	if err != nil {
		return nil, err
	}

	formats := video.Formats.WithAudioChannels()
	if len(formats) == 0 {
		return nil, fmt.Errorf("no se encontraron formatos de audio")
	}

	// Ordenamos la lista, usualmente kkdai pone las de mejor calidad/tamaño
	formats.Sort()
	
	// Tomamos el primer formato que tiene audio.
	streamURL, err := yp.client.GetStreamURLContext(ctx, video, &formats[0])
	if err != nil {
		return nil, err
	}

	return &domain.StreamResult{
		URL:         streamURL,
		ContentType: formats[0].MimeType,
		Provider:    yp.Name(),
	}, nil
}
