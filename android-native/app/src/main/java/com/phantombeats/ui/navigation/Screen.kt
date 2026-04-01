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
    
    // El player completo será en general un Modal / Overlay y no tanto una "Ruta" de Tab
    object FullPlayer : Screen("full_player", "Reproductor", Icons.Default.Home)
}
