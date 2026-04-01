package com.phantombeats.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.phantombeats.ui.navigation.Screen
import com.phantombeats.ui.screens.ExploreScreen
import com.phantombeats.ui.screens.FullPlayerScreen
import com.phantombeats.ui.screens.HomeScreen
import com.phantombeats.ui.screens.LocalSongsScreen
import com.phantombeats.ui.screens.OfflineScreen
import com.phantombeats.ui.screens.PlaylistsScreen
import com.phantombeats.ui.viewmodels.PlayerViewModel

@Composable
fun MainNavGraph(
    navController: NavHostController,
    playerViewModel: PlayerViewModel,
    modifier: Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            HomeScreen(playerViewModel = playerViewModel)
        }
        composable(Screen.Explore.route) {
            ExploreScreen(playerViewModel = playerViewModel)
        }
        composable(Screen.MySongs.route) {
            LocalSongsScreen(playerViewModel = playerViewModel)
        }
        composable(Screen.Offline.route) {
            OfflineScreen(playerViewModel = playerViewModel)
        }
        composable(Screen.Playlists.route) {
            PlaylistsScreen(playerViewModel = playerViewModel)
        }
        composable(Screen.FullPlayer.route) {
            FullPlayerScreen(
                playerViewModel = playerViewModel,
                onDismiss = { navController.popBackStack() }
            )
        }
    }
}
