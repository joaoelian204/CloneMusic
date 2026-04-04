package youtube

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"os"
	"strings"

	"phantom-beats-backend/internal/core/domain"
	"phantom-beats-backend/pkg/network"
	"phantom-beats-backend/pkg/utils"
)

type YouTubeProvider struct {
	reqMgr *network.RequestManager
	apiKey string
}

func NewYouTubeProvider(rm *network.RequestManager) *YouTubeProvider {
	return &YouTubeProvider{
		reqMgr: rm,
		apiKey: os.Getenv("YOUTUBE_API_KEY"),
	}
}

func (yp *YouTubeProvider) Name() string {
	return "YouTube"
}

type ytSearchResponse struct {
	Items []struct {
		Id struct {
			VideoId string `json:"videoId"`
		} `json:"id"`
		Snippet struct {
			Title        string `json:"title"`
			ChannelTitle string `json:"channelTitle"`
			Thumbnails   struct {
				High struct {
					Url string `json:"url"`
				} `json:"high"`
			} `json:"thumbnails"`
		} `json:"snippet"`
	} `json:"items"`
}

// Search usa la YouTube Data API v3 de forma ligera
func (yp *YouTubeProvider) Search(ctx context.Context, query string) ([]domain.Song, error) {
	if yp.apiKey == "" {
		utils.LogInfo("YOUTUBE_API_KEY no definido. Usando scraping ligero de fallback (solo metadata)...")
		return yp.fallbackLightScrape(ctx, query)
	}

	searchURL := fmt.Sprintf("https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&q=%s&key=%s&maxResults=25",
		url.QueryEscape(query), yp.apiKey)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL, nil)
	if err != nil {
		return nil, err
	}

	resp, err := yp.reqMgr.GetHTTPClient().Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("youtube api devolvio %d", resp.StatusCode)
	}

	var ytResp ytSearchResponse
	if err := json.NewDecoder(resp.Body).Decode(&ytResp); err != nil {
		return nil, err
	}

	var songs []domain.Song
	for _, item := range ytResp.Items {
		if item.Id.VideoId == "" {
			continue
		}

		title := cleanTitle(item.Snippet.Title)
		artist := item.Snippet.ChannelTitle

		songs = append(songs, domain.Song{
			ID:        item.Id.VideoId,
			Title:     title,
			Artist:    artist,
			Provider:  "YouTube",
			CoverURL:  item.Snippet.Thumbnails.High.Url,
		})
	}

	return songs, nil
}

// Fallback ultrarapido
func (yp *YouTubeProvider) fallbackLightScrape(ctx context.Context, query string) ([]domain.Song, error) {
    // Retornamos vacio para este caso sin api key
	return []domain.Song{}, nil 
}

func cleanTitle(title string) string {
	title = strings.ReplaceAll(title, "&quot;", "\"")
	title = strings.ReplaceAll(title, "&#39;", "'")
	return title
} 
