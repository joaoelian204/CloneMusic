package com.phantombeats.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Para Ti", Icons.Default.Home)
    object Explore : Screen("explore", "Explorar", Icons.Default.Search)
    object MySongs : Screen("my_songs", "Mis canciones", Icons.Default.LibraryMusic)
    object Offline : Screen("offline", "Offline", Icons.Default.Download)
    object Playlists : Screen("playlists", "Playlists", Icons.Default.List)
    
    // Screens for Artist and Album Details
    object ArtistProfile : Screen("artist_profile/{artistName}/{imageUrl}", "Artista", Icons.Default.Search) {
        fun createRoute(name: String, image: String): String {
            val safeImage = if (image.isBlank()) "none" else java.net.URLEncoder.encode(image, "UTF-8")
            return "artist_profile/${java.net.URLEncoder.encode(name, "UTF-8")}/$safeImage"
        }
    }
    object AlbumProfile : Screen("album_profile/{albumTitle}/{artistName}/{coverUrl}", "Álbum", Icons.Default.Search) {
        fun createRoute(title: String, artist: String, cover: String): String {
            val safeCover = if (cover.isBlank()) "none" else java.net.URLEncoder.encode(cover, "UTF-8")
            return "album_profile/${java.net.URLEncoder.encode(title, "UTF-8")}/${java.net.URLEncoder.encode(artist, "UTF-8")}/$safeCover"
        }
    }
    
    // El player completo será en general un Modal / Overlay y no tanto una "Ruta" de Tab
    object FullPlayer : Screen("full_player", "Reproductor", Icons.Default.Home)
}
