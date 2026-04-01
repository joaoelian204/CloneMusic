package domain

import "errors"

var (
	ErrSongNotFound    = errors.New("la canción no fue encontrada en ningún proveedor")
	ErrProviderTimeout = errors.New("tiempo de espera agotado al conectar al proveedor")
	ErrStreamFailed    = errors.New("fallo en la obtención del flujo de audio")
)
