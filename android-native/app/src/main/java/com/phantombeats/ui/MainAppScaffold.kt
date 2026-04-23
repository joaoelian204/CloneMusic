package com.phantombeats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.phantombeats.ui.components.MiniPlayer
import com.phantombeats.ui.navigation.Screen
import com.phantombeats.ui.viewmodels.LibraryViewModel
import com.phantombeats.ui.viewmodels.PlayerViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun MainAppScaffold(openFullPlayerRequests: StateFlow<Int>) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isFullPlayer = currentRoute == Screen.FullPlayer.route
    val isDownloadsRoute = currentRoute == Screen.Offline.route
    val isPlaylistsRoute = currentRoute == Screen.Playlists.route
    val showGlobalDownloadsButton = !isFullPlayer && !isPlaylistsRoute
    val openFullPlayerTick by openFullPlayerRequests.collectAsState()
    var lastHandledOpenFullPlayerTick by remember { mutableIntStateOf(0) }
    
    // Obtenemos el ViewModel global de Reproducción atado a la Actividad/App
    val playerViewModel: PlayerViewModel = hiltViewModel()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val pendingDownloadsCount by libraryViewModel.pendingDownloadsCount.collectAsState()
    val playlistDownloadState by playerViewModel.playlistDownloadState.collectAsState()
    val globalProgress = playlistDownloadState?.progress ?: 0f
    val showProgressRing = (playlistDownloadState?.isRunning == true || playlistDownloadState?.isPaused == true)
    val badgeCount = when {
        pendingDownloadsCount > 0 -> pendingDownloadsCount
        showProgressRing -> 1
        else -> 0
    }

    LaunchedEffect(openFullPlayerTick) {
        if (openFullPlayerTick <= lastHandledOpenFullPlayerTick) {
            return@LaunchedEffect
        }

        lastHandledOpenFullPlayerTick = openFullPlayerTick

        if (openFullPlayerTick > 0 && currentRoute != Screen.FullPlayer.route) {
            navController.navigate(Screen.FullPlayer.route) {
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!isFullPlayer) {
                MainNavigationBar(navController = navController, currentRoute = currentRoute)
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            MainNavGraph(
                navController = navController,
                playerViewModel = playerViewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (isFullPlayer) 0.dp else 74.dp)
            )

            // Mini Player anclado debajo del contenido de las Tabs, sobre el BottomNavigation
            if (!isFullPlayer) {
                Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                    MiniPlayer(
                        playerViewModel = playerViewModel,
                        onNavigateToFullPlayer = {
                            navController.navigate(Screen.FullPlayer.route)
                        }
                    )
                }
            }

            if (showGlobalDownloadsButton) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 10.dp, end = 14.dp)
                ) {
                    Box {
                        if (showProgressRing) {
                            CircularProgressIndicator(
                                progress = globalProgress.coerceIn(0f, 1f),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(1.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            )
                        }

                        IconButton(
                            onClick = {
                                if (!isDownloadsRoute) {
                                    navController.navigate(Screen.Offline.route) {
                                        launchSingleTop = true
                                    }
                                }
                            },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Descargas",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (badgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = badgeCount.toString(),
                                    color = MaterialTheme.colorScheme.onError,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
