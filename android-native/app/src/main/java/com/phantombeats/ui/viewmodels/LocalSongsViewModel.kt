package com.phantombeats.ui.viewmodels

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.phantombeats.domain.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LocalSongsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val prefs = context.getSharedPreferences("local_songs_prefs", Context.MODE_PRIVATE)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _folderUri = MutableStateFlow<String?>(null)
    val folderUri: StateFlow<String?> = _folderUri.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        val saved = prefs.getString(KEY_FOLDER_URI, null)
        _folderUri.value = saved
        if (!saved.isNullOrBlank()) {
            loadSongsFromUri(Uri.parse(saved))
        }
    }

    fun pickFolder(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Some devices may already grant persisted access without this call.
        }

        val asString = uri.toString()
        prefs.edit().putString(KEY_FOLDER_URI, asString).apply()
        _folderUri.value = asString
        loadSongsFromUri(uri)
    }

    fun reload() {
        val current = _folderUri.value ?: return
        loadSongsFromUri(Uri.parse(current))
    }

    private fun loadSongsFromUri(uri: Uri) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null

            val scanned = withContext(Dispatchers.IO) {
                scanFolder(uri)
            }

            if (scanned.isEmpty()) {
                _error.value = "No se encontraron audios en la carpeta seleccionada."
            }

            _songs.value = scanned
            _loading.value = false
        }
    }

    private fun scanFolder(rootUri: Uri): List<Song> {
        val root = DocumentFile.fromTreeUri(context, rootUri) ?: return emptyList()
        val result = mutableListOf<Song>()

        fun walk(node: DocumentFile) {
            node.listFiles().forEach { child ->
                when {
                    child.isDirectory -> walk(child)
                    child.isFile && isAudioFile(child.name) -> {
                        result += toSong(child.uri, child.name ?: "Audio local")
                    }
                }
            }
        }

        walk(root)

        return result.sortedBy { it.title.lowercase(Locale.getDefault()) }
    }

    private fun toSong(uri: Uri, fileName: String): Song {
        val retriever = MediaMetadataRetriever()
        var title = fileName.substringBeforeLast(".")
        var artist = "Archivo local"
        var album: String? = null
        var durationSec = 0

        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.let {
                if (it.isNotBlank()) title = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.let {
                if (it.isNotBlank()) artist = it
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.let {
                if (it.isNotBlank()) album = it
            }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            durationSec = (durationMs / 1000L).toInt()
        } catch (_: Exception) {
            // Keep fallback metadata when parser fails.
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }

        val subtitleArtist = if (!album.isNullOrBlank() && !artist.equals(album, ignoreCase = true)) {
            "$artist • $album"
        } else {
            artist
        }

        return Song(
            id = uri.toString(),
            title = title,
            artist = subtitleArtist,
            duration = durationSec,
            coverUrl = "",
            provider = "Local",
            localPath = uri.toString(),
            playCount = 0,
            isFavorite = false
        )
    }

    private fun isAudioFile(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        val lower = name.lowercase(Locale.getDefault())
        return lower.endsWith(".mp3") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".flac")
    }

    companion object {
        private const val KEY_FOLDER_URI = "folder_uri"
    }
}
