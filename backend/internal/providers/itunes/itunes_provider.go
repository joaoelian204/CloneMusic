package itunes

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"net/url"
	"strconv"

	"phantom-beats-backend/internal/core/domain"
)

type ITunesProvider struct {
	client *http.Client
}

func NewITunesProvider(client *http.Client) *ITunesProvider {
	return &ITunesProvider{client: client}
}

func (p *ITunesProvider) Name() string {
	return "iTunes"
}

type itunesSearchResponse struct {
	Results []struct {
		TrackID         int64  `json:"trackId"`
		TrackName       string `json:"trackName"`
		ArtistName      string `json:"artistName"`
		TrackTimeMillis int    `json:"trackTimeMillis"`
		ArtworkURL100   string `json:"artworkUrl100"`
		PreviewURL      string `json:"previewUrl"`
	} `json:"results"`
}

func (p *ITunesProvider) Search(ctx context.Context, query string) ([]domain.Song, error) {
	endpoint := "https://itunes.apple.com/search?entity=song&limit=20&term=" + url.QueryEscape(query)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}

	resp, err := p.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, fmt.Errorf("itunes search status no exitoso: %d", resp.StatusCode)
	}

	var parsed itunesSearchResponse
	if err := json.NewDecoder(resp.Body).Decode(&parsed); err != nil {
		return nil, err
	}

	results := make([]domain.Song, 0, len(parsed.Results))
	for _, item := range parsed.Results {
		if item.TrackID == 0 || item.TrackName == "" {
			continue
		}

		results = append(results, domain.Song{
			ID:       strconv.FormatInt(item.TrackID, 10),
			Title:    item.TrackName,
			Artist:   item.ArtistName,
			Duration: item.TrackTimeMillis / 1000,
			CoverURL: item.ArtworkURL100,
			Provider: p.Name(),
		})
	}

	if len(results) == 0 {
		return nil, domain.ErrSongNotFound
	}

	return results, nil
}