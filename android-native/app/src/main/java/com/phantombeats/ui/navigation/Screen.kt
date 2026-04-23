package com.phantombeats.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector
import android.net.Uri

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Para Ti", Icons.Default.Home)
    object Explore : Screen("explore", "Explorar", Icons.Default.Search)
    object MySongs : Screen("my_songs", "Mis canciones", Icons.Default.LibraryMusic)
    object Offline : Screen("offline", "Descargas", Icons.Default.Download)
    object Playlists : Screen("playlists", "Playlists", Icons.Default.List)
    
    // Screens for Artist and Album Details
    object ArtistProfile : Screen("artist_profile/{artistName}/{imageUrl}", "Artista", Icons.Default.Search) {
        fun createRoute(name: String, image: String): String {
            val safeImage = if (image.isBlank()) "none" else Uri.encode(image)
            return "artist_profile/${Uri.encode(name)}/$safeImage"
        }
    }
    object AlbumProfile : Screen("album_profile/{albumId}/{albumTitle}/{artistName}/{coverUrl}", "Álbum", Icons.Default.Search) {
        fun createRoute(albumId: String, title: String, artist: String, cover: String): String {
            val safeCover = if (cover.isBlank()) "none" else Uri.encode(cover)
            val safeAlbumId = if (albumId.isBlank()) "none" else Uri.encode(albumId)
            return "album_profile/$safeAlbumId/${Uri.encode(title)}/${Uri.encode(artist)}/$safeCover"
        }
    }

    object AutoPlaylistDetail : Screen("auto_playlist/{playlistId}", "Auto Playlist", Icons.Default.LibraryMusic) {
        fun createRoute(playlistId: String): String {
            val safeId = if (playlistId.isBlank()) "none" else Uri.encode(playlistId)
            return "auto_playlist/$safeId"
        }
    }
    
    // El player completo será en general un Modal / Overlay y no tanto una "Ruta" de Tab
    object FullPlayer : Screen("full_player", "Reproductor", Icons.Default.Home)
}
