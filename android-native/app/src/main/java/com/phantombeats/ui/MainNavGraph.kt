package com.phantombeats.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import android.net.Uri
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.phantombeats.ui.navigation.Screen
import com.phantombeats.ui.screens.ExploreScreen
import com.phantombeats.ui.screens.FullPlayerScreen
import com.phantombeats.ui.screens.HomeScreen
import com.phantombeats.ui.screens.LocalSongsScreen
import com.phantombeats.ui.screens.OfflineScreen
import com.phantombeats.ui.screens.PlaylistsScreen
import com.phantombeats.ui.screens.ArtistProfileScreen
import com.phantombeats.ui.screens.AlbumProfileScreen
import com.phantombeats.ui.screens.HomeAutoPlaylistDetailScreen
import com.phantombeats.ui.viewmodels.PlayerViewModel

@Composable
fun MainNavGraph(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier
) {
    fun decodeArg(value: String?): String = Uri.decode(value ?: "")

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                playerViewModel = playerViewModel,
                onOpenAutoPlaylist = { playlistId ->
                    navController.navigate(Screen.AutoPlaylistDetail.createRoute(playlistId))
                }
            )
        }
        composable(Screen.Explore.route) {
            ExploreScreen(
                playerViewModel = playerViewModel,
                onNavigateToArtist = { artist ->
                    navController.navigate(Screen.ArtistProfile.createRoute(artist.name, artist.imageUrl))
                },
                onNavigateToAlbum = { album ->
                    navController.navigate(Screen.AlbumProfile.createRoute(album.id, album.title, album.artistName, album.coverUrl))
                }
            )
        }
        composable(
            route = Screen.ArtistProfile.route,
            arguments = listOf(
                navArgument("artistName") { type = NavType.StringType },
                navArgument("imageUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val name = decodeArg(backStackEntry.arguments?.getString("artistName"))
            val image = decodeArg(backStackEntry.arguments?.getString("imageUrl"))
            ArtistProfileScreen(
                artistName = name,
                imageUrl = if (image == "none") "" else image,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = Screen.AlbumProfile.route,
            arguments = listOf(
                navArgument("albumId") { type = NavType.StringType },
                navArgument("albumTitle") { type = NavType.StringType },
                navArgument("artistName") { type = NavType.StringType },
                navArgument("coverUrl") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val albumId = decodeArg(backStackEntry.arguments?.getString("albumId"))
            val title = decodeArg(backStackEntry.arguments?.getString("albumTitle"))
            val artist = decodeArg(backStackEntry.arguments?.getString("artistName"))
            val cover = decodeArg(backStackEntry.arguments?.getString("coverUrl"))
            AlbumProfileScreen(
                albumId = if (albumId == "none") "" else albumId,
                albumTitle = title,
                artistName = artist,
                coverUrl = if (cover == "none") "" else cover,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.MySongs.route) {
            LocalSongsScreen(playerViewModel = playerViewModel)
        }
        composable(Screen.Offline.route) {
            OfflineScreen(playerViewModel = playerViewModel)
        }
        composable(Screen.Playlists.route) {
            PlaylistsScreen(
                playerViewModel = playerViewModel,
                onOpenDownloads = {
                    navController.navigate(Screen.Offline.route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(
            route = Screen.AutoPlaylistDetail.route,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = decodeArg(backStackEntry.arguments?.getString("playlistId"))
            HomeAutoPlaylistDetailScreen(
                playlistId = if (playlistId == "none") "" else playlistId,
                playerViewModel = playerViewModel,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.FullPlayer.route) {
            FullPlayerScreen(
                playerViewModel = playerViewModel,
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}
