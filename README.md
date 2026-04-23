# Phantom Beats - Contexto tecnico para generar post de LinkedIn con otra IA

Este documento NO es una plantilla de post.
Es una base factual del proyecto para que otra IA construya una publicacion de LinkedIn precisa.

## 1. Objetivo del brief

Entregar a otra IA informacion verificada sobre el estado actual del proyecto para redactar un post que:

- refleje la realidad tecnica de la app nativa;
- destaque decisiones de arquitectura relevantes;
- evite afirmaciones inconsistentes o de marketing sin evidencia.

## 2. Resumen ejecutivo del proyecto

Phantom Beats es una app musical con foco en Android nativo, orientada a resiliencia y experiencia offline-first.

Estado arquitectonico que debe comunicarse:

- El flujo principal de reproduccion en Android se resuelve del lado cliente.
- La resolucion de streams ocurre on-demand en el dispositivo.
- La reproduccion y cache se soportan con Media3 ExoPlayer + almacenamiento local.

## 3. Hechos tecnicos verificados (usar como fuente)

### 3.1 Stack Android principal

- Kotlin + Jetpack Compose.
- Hilt para inyeccion de dependencias.
- Room para persistencia local.
- Media3 ExoPlayer para reproduccion.
- WorkManager para descargas en segundo plano.
- NewPipeExtractor para busqueda/extraccion cliente.

Evidencia:

- android-native/app/build.gradle.kts

### 3.2 Reproduccion client-side y resolucion lazy

- La app usa esquemas `phantom-yt` y `phantom-search`.
- El DataSource resuelve URL real de stream en tiempo de reproduccion.
- El pipeline pasa por ResolvingDataSource + CacheDataSource.

Evidencia:

- android-native/app/src/main/java/com/phantombeats/di/MediaModule.kt

### 3.3 Servicio de reproduccion en segundo plano

- Existe un MediaLibraryService para mantener reproduccion y exponer controles del ecosistema Android (bloqueo/auto/wear).

Evidencia:

- android-native/app/src/main/java/com/phantombeats/player/PhantomMediaService.kt

### 3.4 Busqueda y datos locales en Android

- La busqueda principal usa fuentes remotas directas desde el cliente (InnerTube + iTunes).
- Hay fallback a base local cuando falla red.
- Se guarda historial, favoritos y metrica de reproduccion en Room.

Evidencia:

- android-native/app/src/main/java/com/phantombeats/data/repository/SongRepositoryImpl.kt

### 3.5 Navegacion funcional de la app nativa

- Pantallas clave: Home, Explore, ArtistProfile, AlbumProfile, LocalSongs, Offline, Playlists, FullPlayer.

Evidencia:

- android-native/app/src/main/java/com/phantombeats/ui/MainNavGraph.kt

## 4. Delimitacion de alcance para el post

Si el objetivo del post es la app nativa Android, priorizar este marco:

- Producto: app nativa musical resiliente.
- Arquitectura: serverless thick-client en flujo principal Android.
- Valor: continuidad de UX con red inestable y menor dependencia de backend operativo.

No mezclar como mensaje principal:

- estado de web/PWA con endpoints backend,
- legado Go como arquitectura activa,
- claims de escalabilidad global sin matiz tecnico.

## 5. Riesgos de comunicacion (cosas que otra IA debe evitar)

Evitar frases absolutas como:

- "100% sin backend en todo el proyecto" (hay piezas legacy y servicios web aun referenciados).
- "todo esta resuelto con FTS en produccion" (hay partes en transicion y consultas LIKE activas en flujo actual).
- "escalabilidad infinita" o promesas no medibles.

Usar formulaciones seguras:

- "en el flujo principal de Android..."
- "la arquitectura actual prioriza..."
- "el proyecto evoluciono de... hacia..."

## 6. Angulos recomendados para la IA redactora (sin texto de post)

Pedir a la IA que elija 1 enfoque:

1. Arquitectura y decisiones tecnicas.
2. Producto y experiencia de usuario offline-first.
3. Aprendizajes de migracion (backend-centric -> client-centric).

## 7. Formato de entrada sugerido para otra IA

Usar este bloque como prompt de contexto:

```text
Genera un post de LinkedIn profesional basado SOLO en los hechos siguientes:

- Proyecto: Phantom Beats (app musical Android nativa).
- Stack: Kotlin, Jetpack Compose, Hilt, Room, Media3 ExoPlayer, WorkManager, NewPipeExtractor.
- Reproduccion client-side con resolucion lazy de streams via esquemas phantom-yt/phantom-search.
- Cache y continuidad de experiencia offline-first.
- Servicio de reproduccion en segundo plano con MediaLibraryService.
- Navegacion actual incluye Home, Explore, perfiles de artista/album, offline, playlists y full player.
- El backend Go es legado/deuda historica y no debe presentarse como nucleo operativo actual del flujo Android.

Restricciones de redaccion:
- No inventar metricas.
- No usar claims absolutos.
- Mantener tono tecnico-profesional.
- Priorizar claridad para audiencia de engineering/product.
```

## 8. Archivos de referencia rapida

- android-native/app/build.gradle.kts
- android-native/app/src/main/java/com/phantombeats/di/MediaModule.kt
- android-native/app/src/main/java/com/phantombeats/player/PhantomMediaService.kt
- android-native/app/src/main/java/com/phantombeats/data/repository/SongRepositoryImpl.kt
- android-native/app/src/main/java/com/phantombeats/ui/MainNavGraph.kt
- docs/architecture/10_arquitectura_backend_go.md

## 9. Resultado esperado al usar este README con otra IA

Un post de LinkedIn que comunique correctamente:

- que se construyo,
- por que la arquitectura importa,
- que decisiones diferenciales se tomaron,
- y que valor tecnico/producto entrega hoy la app nativa.


