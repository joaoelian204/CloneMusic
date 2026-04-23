package com.phantombeats.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.phantombeats.domain.model.Song
import com.phantombeats.data.download.SongDownloadWorker
import com.phantombeats.domain.repository.SongRepository
import com.phantombeats.player.PhantomPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.os.SystemClock
import android.os.PowerManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.File
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

sealed class PlayerUiState {
    object Idle : PlayerUiState()
    data class Buffering(val song: Song? = null) : PlayerUiState()
    data class Playing(val song: Song, val useLocalCache: Boolean = false) : PlayerUiState()
    data class Error(val message: String, val song: Song? = null) : PlayerUiState()
}

data class PlaylistDownloadUiState(
    val playlistId: String,
    val total: Int,
    val completed: Int,
    val isRunning: Boolean,
    val isPaused: Boolean,
    val pauseReason: String? = null,
    val estimatedTotalBytes: Long,
    val estimatedDownloadedBytes: Long,
    val currentSongId: String? = null,
    val pausedSongId: String? = null
) {
    val progress: Float
        get() = if (total <= 0) 0f else (completed.toFloat() / total.toFloat()).coerceIn(0f, 1f)

    val remainingBytes: Long
        get() = (estimatedTotalBytes - estimatedDownloadedBytes).coerceAtLeast(0L)
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    val playerController: PhantomPlayerController,
    private val repository: SongRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private data class CachedStreamUrl(
        val url: String,
        val expiresAtElapsedMs: Long
    )

    private data class PlaylistDownloadSession(
        val playlistId: String,
        val songs: List<Song>,
        var nextIndex: Int,
        var completed: Int,
        var estimatedDownloadedBytes: Long,
        val estimatedTotalBytes: Long,
        val downloadedInSession: MutableSet<String>
    )

    private data class QueuedPlaylistDownload(
        val playlistId: String,
        val songs: List<Song>
    )

    private data class DownloadProgressBytes(
        val downloadedBytes: Long,
        val totalBytes: Long
    )

    data class ActiveSongDownloadProgress(
        val songId: String,
        val downloadedBytes: Long,
        val totalBytes: Long
    )

    private val streamCacheMutex = Mutex()
    private val streamUrlCache = mutableMapOf<String, CachedStreamUrl>()
    private val powerManager by lazy {
        appContext.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    companion object {
        private const val STREAM_URL_CACHE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_RESOLVED_QUEUE_ITEMS = 8
        private const val MAX_PRIMARY_RESOLVE_MS = 12_000L
        private const val ESTIMATED_BYTES_PER_SECOND = 24_000L // ~192 kbps
        private const val TELEMETRY_PREFS = "download_telemetry"
        private const val TELEMETRY_SUCCESS = "success_count"
        private const val TELEMETRY_FAILURE = "failure_count"
        private const val TELEMETRY_CANCEL = "cancel_count"
        private const val TELEMETRY_BYTES = "downloaded_bytes"
    }

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Idle)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    val isPlaying: StateFlow<Boolean> = playerController.isPlaying
    val currentSong: StateFlow<Song?> = playerController.currentSong
    val positionMs: StateFlow<Long> = playerController.positionMs
    val durationMs: StateFlow<Long> = playerController.durationMs
    val bufferedPositionMs: StateFlow<Long> = playerController.bufferedPositionMs

    private val playbackQueue = MutableStateFlow<List<Song>>(emptyList())
    private var currentQueueIndex = -1

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _playlistDownloadState = MutableStateFlow<PlaylistDownloadUiState?>(null)
    val playlistDownloadState: StateFlow<PlaylistDownloadUiState?> = _playlistDownloadState.asStateFlow()

    private val _queuedPlaylistCount = MutableStateFlow(0)
    val queuedPlaylistCount: StateFlow<Int> = _queuedPlaylistCount.asStateFlow()

    private val _queuedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val queuedPlaylistIds: StateFlow<Set<String>> = _queuedPlaylistIds.asStateFlow()

    private val _activeSongDownloadProgress = MutableStateFlow<ActiveSongDownloadProgress?>(null)
    val activeSongDownloadProgress: StateFlow<ActiveSongDownloadProgress?> = _activeSongDownloadProgress.asStateFlow()

    private val _adaptiveBatteryModeEnabled = MutableStateFlow(true)
    val adaptiveBatteryModeEnabled: StateFlow<Boolean> = _adaptiveBatteryModeEnabled.asStateFlow()

    private val _wifiOnlyDownloadsEnabled = MutableStateFlow(true)
    val wifiOnlyDownloadsEnabled: StateFlow<Boolean> = _wifiOnlyDownloadsEnabled.asStateFlow()

    private var playlistDownloadJob: Job? = null
    private var playQueueJob: Job? = null
    private var activePlaylistSession: PlaylistDownloadSession? = null
    private val pendingPlaylistQueue = ArrayDeque<QueuedPlaylistDownload>()
    private val skippedPlaylistSongIds = mutableSetOf<String>()
    private var pausedPlaylistSongId: String? = null
    private val telemetryPrefs by lazy {
        appContext.getSharedPreferences(TELEMETRY_PREFS, Context.MODE_PRIVATE)
    }
    @Volatile
    private var lastPlayRequestId: Long = 0L
    private var pendingPlaylistDownloadSongIds: Set<String> = emptySet()

    init {
        // Precalentamos el controller una vez por proceso para evitar el "primer play" en frío.
        playerController.preWarm()

        viewModelScope.launch {
            playerController.lastErrorMessage.collectLatest { errorMessage ->
                if (!errorMessage.isNullOrBlank()) {
                    _uiState.value = PlayerUiState.Error(
                        message = "Reproducción fallida: $errorMessage",
                        song = currentSong.value
                    )
                }
            }
        }
    }

    fun stop() {
        playerController.stopPlayback()
        _uiState.value = PlayerUiState.Idle
    }

    fun playSong(song: Song) {
        playSongsQueue(listOf(song), 0)
    }

    fun playSongsQueue(songs: List<Song>, startIndex: Int) {
        if (songs.isEmpty()) return
        playbackQueue.value = songs
        currentQueueIndex = startIndex.coerceIn(0, songs.lastIndex)
        val requestId = ++lastPlayRequestId
        playQueueJob?.cancel()

        playQueueJob = viewModelScope.launch {
            val targetSong = songs.getOrNull(currentQueueIndex)
            _uiState.value = PlayerUiState.Buffering(song = targetSong)
            // Interrumpe la canción actual inmediatamente para que el cambio se perciba al toque.
            playerController.stopPlayback()

            val resolvedQueue = withContext(Dispatchers.IO) {
                val selected = songs.getOrNull(currentQueueIndex)
                val firstResolved = selected?.let { song ->
                    val resolvedUri = withTimeoutOrNull(MAX_PRIMARY_RESOLVE_MS) {
                        resolvePlayableUri(song)
                    }

                    resolvedUri?.let { uri ->
                        PhantomPlayerController.ResolvedQueueItem(song = song, streamUri = uri)
                    }
                }

                if (firstResolved == null) {
                    emptyList()
                } else {
                    val upcoming = buildCachedUpcomingQueue(songs, currentQueueIndex)

                    buildList {
                        add(firstResolved)
                        addAll(upcoming)
                    }
                }
            }

            if (requestId != lastPlayRequestId) {
                return@launch
            }

            if (resolvedQueue.isEmpty()) {
                _uiState.value = PlayerUiState.Error(
                    message = "No se pudieron resolver streams para reproducir.",
                    song = targetSong
                )
                return@launch
            }

            val adjustedIndex = 0
            playerController.playResolvedQueue(resolvedQueue, adjustedIndex)

            val song = resolvedQueue.getOrNull(adjustedIndex)?.song
            if (song != null) {
                _uiState.value = PlayerUiState.Playing(song = song, useLocalCache = song.isDownloaded)
            }
        }
    }

    private suspend fun resolvePlayableUri(song: Song): String? {
        val now = SystemClock.elapsedRealtime()
        streamCacheMutex.withLock {
            val cached = streamUrlCache[song.id]
            if (cached != null && cached.expiresAtElapsedMs > now) {
                return cached.url
            }
        }

        val result = repository.getStreamUrl(song)
        val resolved = result.getOrNull() ?: return null

        val expiresAt = now + STREAM_URL_CACHE_TTL_MS
        streamCacheMutex.withLock {
            streamUrlCache[song.id] = CachedStreamUrl(resolved, expiresAt)
            if (streamUrlCache.size > 180) {
                val cutoff = now - STREAM_URL_CACHE_TTL_MS
                streamUrlCache.entries.removeAll { it.value.expiresAtElapsedMs < cutoff }
            }
        }
        return resolved
    }

    private suspend fun buildCachedUpcomingQueue(
        songs: List<Song>,
        startIndex: Int
    ): List<PhantomPlayerController.ResolvedQueueItem> {
        val now = SystemClock.elapsedRealtime()
        val upcomingSongs = songs
            .drop(startIndex + 1)
            .take(MAX_RESOLVED_QUEUE_ITEMS)

        return streamCacheMutex.withLock {
            upcomingSongs.mapNotNull { song ->
                val cached = streamUrlCache[song.id]
                if (cached != null && cached.expiresAtElapsedMs > now) {
                    PhantomPlayerController.ResolvedQueueItem(song = song, streamUri = cached.url)
                } else {
                    null
                }
            }
        }
    }

    fun playNextInQueue() {
        playerController.playNext()
    }

    fun playPreviousInQueue() {
        playerController.playPrevious()
    }

    fun toggleShuffleMode() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        playerController.setShuffleMode(_shuffleEnabled.value)
    }

    fun setShuffleMode(enabled: Boolean) {
        _shuffleEnabled.value = enabled
        playerController.setShuffleMode(enabled)
    }

    fun togglePlayPause() {
        playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        playerController.seekTo(positionMs)
    }

    fun seekToFraction(progress: Float) {
        val duration = durationMs.value
        if (duration <= 0L) return
        val clamped = progress.coerceIn(0f, 1f)
        val target = (duration * clamped).toLong()
        playerController.seekTo(target)
    }

    fun setFavorite(songId: String, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(songId, isFavorite)

            val current = _uiState.value
            if (current is PlayerUiState.Playing && current.song.id == songId) {
                val updated = current.song.copy(isFavorite = isFavorite)
                _uiState.value = current.copy(song = updated)
                playerController.updateCurrentSong(updated)
            }
        }
    }

    fun setFavorite(song: Song, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.setFavorite(song.id, isFavorite)

            val current = _uiState.value
            if (current is PlayerUiState.Playing && current.song.id == song.id) {
                val updated = current.song.copy(isFavorite = isFavorite)
                _uiState.value = current.copy(song = updated)
                playerController.updateCurrentSong(updated)
            }
        }
    }

    fun downloadSingleSong(song: Song) {
        if (song.provider == "Local") return
        if (song.localPath?.startsWith("__PENDING__") == true) return
        if (song.isDownloaded) return

        viewModelScope.launch(Dispatchers.IO) {
            val result = repository.downloadSong(song)
            if (result.isSuccess) {
                val current = _uiState.value
                if (current is PlayerUiState.Playing && current.song.id == song.id) {
                    val updated = current.song.copy(localPath = "__PENDING__")
                    _uiState.value = current.copy(song = updated)
                    playerController.updateCurrentSong(updated)
                }
            }
        }
    }

    fun downloadSongsSequentially(songs: List<Song>) {
        viewModelScope.launch {
            songs
                .distinctBy { it.id }
                .filter { it.provider != "Local" }
                .forEach { song ->
                    repository.downloadSong(song)
                }
        }
    }

    fun togglePlaylistDownload(playlistId: String, songs: List<Song>) {
        val current = _playlistDownloadState.value
        if (current != null && current.playlistId == playlistId) {
            if (current.isRunning) {
                pausePlaylistDownload(playlistId)
            } else if (current.isPaused) {
                resumePlaylistDownload(playlistId)
            } else {
                startPlaylistDownload(playlistId, songs)
            }
            return
        }

        startPlaylistDownload(playlistId, songs)
    }

    fun setAdaptiveBatteryModeEnabled(enabled: Boolean) {
        _adaptiveBatteryModeEnabled.value = enabled
    }

    fun setWifiOnlyDownloadsEnabled(enabled: Boolean) {
        _wifiOnlyDownloadsEnabled.value = enabled
    }

    fun pausePlaylistDownload(playlistId: String) {
        val current = _playlistDownloadState.value ?: return
        if (current.playlistId != playlistId || !current.isRunning) return

        playlistDownloadJob?.cancel()
        val currentSongId = current.currentSongId
        pausedPlaylistSongId = currentSongId
        if (!currentSongId.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.cancelSongDownload(currentSongId)
            }
        }

        _playlistDownloadState.value = current.copy(
            isRunning = false,
            isPaused = true,
            pauseReason = current.pauseReason,
            currentSongId = null,
            pausedSongId = pausedPlaylistSongId
        )
    }

    fun resumePlaylistDownload(playlistId: String) {
        val current = _playlistDownloadState.value ?: return
        val session = activePlaylistSession ?: return
        if (current.playlistId != playlistId || session.playlistId != playlistId || !current.isPaused) return

        pausedPlaylistSongId = null
        _activeSongDownloadProgress.value = null
        _playlistDownloadState.value = current.copy(
            isRunning = true,
            isPaused = false,
            pauseReason = null,
            pausedSongId = null
        )
        runPlaylistDownloadLoop(session)
    }

    fun pauseCurrentDownloadSong(songId: String) {
        val state = _playlistDownloadState.value ?: return
        if (state.currentSongId != songId || !state.isRunning) return
        pausePlaylistDownload(state.playlistId)
    }

    fun resumeCurrentDownloadSong(songId: String) {
        val state = _playlistDownloadState.value ?: return
        if (!state.isPaused) return
        val activeSongId = activePlaylistSession
            ?.songs
            ?.getOrNull(activePlaylistSession?.nextIndex ?: -1)
            ?.id
        if (activeSongId != songId) return
        resumePlaylistDownload(state.playlistId)
    }

    fun cancelCurrentDownloadSong(songId: String) {
        val state = _playlistDownloadState.value ?: return
        val session = activePlaylistSession ?: return
        if (session.playlistId != state.playlistId) return

        skippedPlaylistSongIds += songId
        pendingPlaylistDownloadSongIds = pendingPlaylistDownloadSongIds - songId

        viewModelScope.launch(Dispatchers.IO) {
            repository.cancelSongDownload(songId)
        }

        if (state.currentSongId == songId && state.isRunning) {
            playlistDownloadJob?.cancel()
            _playlistDownloadState.value = state.copy(currentSongId = null)
            _activeSongDownloadProgress.value = null
            runPlaylistDownloadLoop(session)
        }
    }

    fun cancelPlaylistDownload(playlistId: String, clearDownloadedFromPlaylist: Boolean = false) {
        if (_playlistDownloadState.value?.playlistId != playlistId) {
            val removed = removeQueuedPlaylistDownload(playlistId)
            if (removed) {
                incrementTelemetry(TELEMETRY_CANCEL)
            }
            return
        }

        val current = _playlistDownloadState.value ?: return
        if (current.playlistId != playlistId) return

        playlistDownloadJob?.cancel()
        val session = activePlaylistSession
        val pendingIds = pendingPlaylistDownloadSongIds
        if (pendingIds.isNotEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                pendingIds.forEach { songId ->
                    repository.cancelSongDownload(songId)
                }
            }
        }

        val currentSongId = current.currentSongId
        if (!currentSongId.isNullOrBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.cancelSongDownload(currentSongId)
            }
        }

        if (clearDownloadedFromPlaylist && session != null && session.playlistId == playlistId) {
            viewModelScope.launch(Dispatchers.IO) {
                session.songs.forEach { song ->
                    repository.removeDownloadedSong(song.id)
                }
            }
        }

        pendingPlaylistDownloadSongIds = emptySet()
        activePlaylistSession = null
        skippedPlaylistSongIds.clear()
        pausedPlaylistSongId = null
        _activeSongDownloadProgress.value = null
        _playlistDownloadState.value = null
        incrementTelemetry(TELEMETRY_CANCEL)
        startNextQueuedPlaylistIfNeeded()
    }

    private fun startPlaylistDownload(playlistId: String, songs: List<Song>) {
        val current = _playlistDownloadState.value
        if (current != null && current.playlistId != playlistId && (current.isRunning || current.isPaused)) {
            enqueuePlaylistDownload(playlistId, songs)
            return
        }

        playlistDownloadJob?.cancel()

        val normalized = songs.distinctBy { it.id }.filter { it.provider != "Local" }
        if (normalized.isEmpty()) {
            _playlistDownloadState.value = PlaylistDownloadUiState(
                playlistId = playlistId,
                total = 0,
                completed = 0,
                isRunning = false,
                isPaused = false,
                pauseReason = null,
                estimatedTotalBytes = 0L,
                estimatedDownloadedBytes = 0L,
                currentSongId = null,
                pausedSongId = null
            )
            return
        }

        val estimatedTotal = normalized.sumOf(::estimateSongBytes)
        val alreadyDownloaded = normalized.count { isSongReallyDownloaded(it) }
        val estimatedDownloaded = normalized
            .filter { isSongReallyDownloaded(it) }
            .sumOf(::estimateSongBytes)

        val startIndex = normalized.indexOfFirst { !isSongReallyDownloaded(it) }
            .let { if (it < 0) normalized.size else it }
        val pending = normalized.drop(startIndex)
        pendingPlaylistDownloadSongIds = pending.map { it.id }.toSet()

        val session = PlaylistDownloadSession(
            playlistId = playlistId,
            songs = normalized,
            nextIndex = startIndex,
            completed = alreadyDownloaded,
            estimatedDownloadedBytes = estimatedDownloaded,
            estimatedTotalBytes = estimatedTotal,
            downloadedInSession = mutableSetOf()
        )
        activePlaylistSession = session
        skippedPlaylistSongIds.clear()

        _playlistDownloadState.value = PlaylistDownloadUiState(
            playlistId = playlistId,
            total = normalized.size,
            completed = alreadyDownloaded,
            isRunning = pending.isNotEmpty(),
            isPaused = false,
            pauseReason = null,
            estimatedTotalBytes = estimatedTotal,
            estimatedDownloadedBytes = estimatedDownloaded,
            currentSongId = null,
            pausedSongId = null
        )

        if (pending.isEmpty()) {
            _playlistDownloadState.value = _playlistDownloadState.value?.copy(isRunning = false, isPaused = false)
            return
        }

        runPlaylistDownloadLoop(session)
    }

    private fun runPlaylistDownloadLoop(session: PlaylistDownloadSession) {
        playlistDownloadJob?.cancel()

        playlistDownloadJob = viewModelScope.launch {
            while (true) {
                val current = _playlistDownloadState.value ?: break
                if (current.playlistId != session.playlistId || !current.isRunning || current.isPaused) break
                if (session.nextIndex >= session.songs.size) break

                if (_adaptiveBatteryModeEnabled.value && powerManager.isPowerSaveMode) {
                    _playlistDownloadState.value = current.copy(
                        isRunning = false,
                        isPaused = true,
                        pauseReason = "Ahorro de batería activo",
                        currentSongId = null
                    )
                    break
                }

                if (_wifiOnlyDownloadsEnabled.value && !isOnWifiOrUnmeteredNetwork()) {
                    _playlistDownloadState.value = current.copy(
                        isRunning = false,
                        isPaused = true,
                        pauseReason = "Esperando Wi-Fi",
                        currentSongId = null
                    )
                    break
                }

                val song = session.songs[session.nextIndex]
                if (song.id in skippedPlaylistSongIds) {
                    pendingPlaylistDownloadSongIds = pendingPlaylistDownloadSongIds - song.id
                    session.nextIndex += 1
                    continue
                }
                if (isSongReallyDownloaded(song)) {
                    session.nextIndex += 1
                    continue
                }

                _playlistDownloadState.value = current.copy(currentSongId = song.id)
                _activeSongDownloadProgress.value = ActiveSongDownloadProgress(
                    songId = song.id,
                    downloadedBytes = 0L,
                    totalBytes = estimateSongBytes(song)
                )
                val monitorJob = startSongProgressMonitor(
                    session = session,
                    song = song,
                    baseDownloadedBytes = session.estimatedDownloadedBytes
                )

                val bytesBeforeDownload = session.estimatedDownloadedBytes
                val result = repository.downloadSong(song)
                monitorJob.cancel()
                pendingPlaylistDownloadSongIds = pendingPlaylistDownloadSongIds - song.id

                val pausedSameSong = pausedPlaylistSongId == song.id && (_playlistDownloadState.value?.isPaused == true)
                if (pausedSameSong) {
                    _playlistDownloadState.value = _playlistDownloadState.value?.copy(
                        currentSongId = null,
                        pausedSongId = song.id
                    )
                    break
                }

                if (result.isSuccess) {
                    session.completed += 1
                    val currentProgress = _playlistDownloadState.value?.estimatedDownloadedBytes
                        ?: session.estimatedDownloadedBytes
                    session.estimatedDownloadedBytes = currentProgress.coerceAtLeast(bytesBeforeDownload)
                    if (session.estimatedDownloadedBytes <= bytesBeforeDownload) {
                        session.estimatedDownloadedBytes =
                            (bytesBeforeDownload + estimateSongBytes(song)).coerceAtMost(session.estimatedTotalBytes)
                    }
                    session.downloadedInSession += song.id
                    incrementTelemetry(TELEMETRY_SUCCESS)
                    addTelemetryBytes((session.estimatedDownloadedBytes - bytesBeforeDownload).coerceAtLeast(0L))
                } else {
                    incrementTelemetry(TELEMETRY_FAILURE)
                }

                session.nextIndex += 1

                val next = _playlistDownloadState.value ?: break
                if (next.playlistId != session.playlistId) {
                    break
                }
                _playlistDownloadState.value = next.copy(
                    completed = session.completed.coerceAtMost(next.total),
                    estimatedDownloadedBytes = session.estimatedDownloadedBytes.coerceAtMost(next.estimatedTotalBytes),
                    pauseReason = null,
                    currentSongId = null,
                    pausedSongId = null
                )
                _activeSongDownloadProgress.value = null
            }

            val finalState = _playlistDownloadState.value
            if (finalState != null && finalState.playlistId == session.playlistId) {
                val finished = session.nextIndex >= session.songs.size
                _playlistDownloadState.value = finalState.copy(
                    isRunning = false,
                    isPaused = if (finished) false else finalState.isPaused,
                    pauseReason = if (finished) null else finalState.pauseReason,
                    currentSongId = null,
                    pausedSongId = if (finished) null else finalState.pausedSongId,
                    completed = session.completed.coerceAtMost(finalState.total),
                    estimatedDownloadedBytes = session.estimatedDownloadedBytes.coerceAtMost(finalState.estimatedTotalBytes)
                )

                if (finished) {
                    activePlaylistSession = null
                    skippedPlaylistSongIds.clear()
                    pausedPlaylistSongId = null
                    _activeSongDownloadProgress.value = null
                    startNextQueuedPlaylistIfNeeded()
                }
            }
            pendingPlaylistDownloadSongIds = emptySet()
        }
    }

    private fun enqueuePlaylistDownload(playlistId: String, songs: List<Song>) {
        val normalized = songs.distinctBy { it.id }.filter { it.provider != "Local" }
        if (normalized.isEmpty()) return

        val existsInQueue = pendingPlaylistQueue.any { it.playlistId == playlistId }
        val isCurrent = _playlistDownloadState.value?.playlistId == playlistId
        if (existsInQueue || isCurrent) return

        pendingPlaylistQueue.addLast(
            QueuedPlaylistDownload(
                playlistId = playlistId,
                songs = normalized
            )
        )
        _queuedPlaylistCount.value = pendingPlaylistQueue.size
        _queuedPlaylistIds.value = pendingPlaylistQueue.map { it.playlistId }.toSet()
    }

    private fun removeQueuedPlaylistDownload(playlistId: String): Boolean {
        val removed = pendingPlaylistQueue.removeAll { it.playlistId == playlistId }
        if (removed) {
            _queuedPlaylistCount.value = pendingPlaylistQueue.size
            _queuedPlaylistIds.value = pendingPlaylistQueue.map { it.playlistId }.toSet()
        }
        return removed
    }

    private fun startNextQueuedPlaylistIfNeeded() {
        if (_playlistDownloadState.value != null || activePlaylistSession != null) return
        val next = pendingPlaylistQueue.removeFirstOrNull() ?: run {
            _queuedPlaylistCount.value = 0
            _queuedPlaylistIds.value = emptySet()
            return
        }
        _queuedPlaylistCount.value = pendingPlaylistQueue.size
        _queuedPlaylistIds.value = pendingPlaylistQueue.map { it.playlistId }.toSet()
        startPlaylistDownload(next.playlistId, next.songs)
    }

    private fun startSongProgressMonitor(
        session: PlaylistDownloadSession,
        song: Song,
        baseDownloadedBytes: Long
    ): Job {
        return viewModelScope.launch {
            while (isActive) {
                val current = _playlistDownloadState.value ?: break
                if (current.playlistId != session.playlistId || current.currentSongId != song.id) break

                val info = getUniqueWorkInfo("download_${song.id}")
                val progress = extractDownloadProgress(info)
                if (progress != null) {
                    val remainingEstimated = session.songs
                        .drop(session.nextIndex + 1)
                        .sumOf(::estimateSongBytes)
                    val dynamicTotal = if (progress.totalBytes > 0L) {
                        (baseDownloadedBytes + progress.totalBytes + remainingEstimated)
                            .coerceAtLeast(session.estimatedTotalBytes)
                    } else {
                        session.estimatedTotalBytes
                    }

                    val dynamicDownloaded = (baseDownloadedBytes + progress.downloadedBytes)
                        .coerceAtMost(dynamicTotal)

                    _playlistDownloadState.value = current.copy(
                        estimatedTotalBytes = dynamicTotal,
                        estimatedDownloadedBytes = dynamicDownloaded
                    )
                    _activeSongDownloadProgress.value = ActiveSongDownloadProgress(
                        songId = song.id,
                        downloadedBytes = progress.downloadedBytes,
                        totalBytes = if (progress.totalBytes > 0L) progress.totalBytes else estimateSongBytes(song)
                    )
                }

                if (info?.state?.isFinished == true) break
                delay(350)
            }
        }
    }

    private suspend fun getUniqueWorkInfo(workName: String): WorkInfo? {
        return withContext(Dispatchers.IO) {
            runCatching {
                WorkManager.getInstance(appContext)
                    .getWorkInfosForUniqueWork(workName)
                    .get()
                    .firstOrNull()
            }.getOrNull()
        }
    }

    private fun extractDownloadProgress(info: WorkInfo?): DownloadProgressBytes? {
        if (info == null) return null
        val downloaded = info.progress.getLong(SongDownloadWorker.KEY_PROGRESS_BYTES, -1L)
        val total = info.progress.getLong(SongDownloadWorker.KEY_PROGRESS_TOTAL_BYTES, -1L)
        if (downloaded < 0L) return null
        return DownloadProgressBytes(downloadedBytes = downloaded, totalBytes = total)
    }

    private fun incrementTelemetry(key: String) {
        val current = telemetryPrefs.getLong(key, 0L)
        telemetryPrefs.edit().putLong(key, current + 1L).apply()
    }

    private fun addTelemetryBytes(bytes: Long) {
        if (bytes <= 0L) return
        val current = telemetryPrefs.getLong(TELEMETRY_BYTES, 0L)
        telemetryPrefs.edit().putLong(TELEMETRY_BYTES, current + bytes).apply()
    }

    private fun estimateSongBytes(song: Song): Long {
        val safeDuration = song.duration.coerceAtLeast(30)
        return safeDuration * ESTIMATED_BYTES_PER_SECOND
    }

    private fun isSongReallyDownloaded(song: Song): Boolean {
        val path = song.localPath ?: return false
        if (path.startsWith("__PENDING__")) return false

        val normalizedPath = if (path.startsWith("file://")) {
            path.removePrefix("file://")
        } else {
            path
        }

        return runCatching { File(normalizedPath).exists() }.getOrDefault(false)
    }

    private fun isOnWifiOrUnmeteredNetwork(): Boolean {
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false

        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun removeOfflineDownload(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeDownloadedSong(song.id)
        }
    }

    override fun onCleared() {
        playlistDownloadJob?.cancel()
        playQueueJob?.cancel()
        activePlaylistSession = null
        pendingPlaylistDownloadSongIds = emptySet()
        skippedPlaylistSongIds.clear()
        pausedPlaylistSongId = null
        _activeSongDownloadProgress.value = null
        _queuedPlaylistIds.value = emptySet()
        playerController.destroy()
        super.onCleared()
    }
}
