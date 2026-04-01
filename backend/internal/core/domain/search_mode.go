package domain

type ContextKey string

const SearchModeContextKey ContextKey = "searchMode"

const (
	SearchModeBalanced = "balanced"
	SearchModeTurbo    = "turbo"
)

func NormalizeSearchMode(raw string) string {
	switch raw {
	case SearchModeTurbo:
		return SearchModeTurbo
	default:
		return SearchModeBalanced
	}
}
