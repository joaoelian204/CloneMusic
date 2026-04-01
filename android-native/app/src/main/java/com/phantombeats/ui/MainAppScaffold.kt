package com.phantombeats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.phantombeats.ui.components.MiniPlayer
import com.phantombeats.ui.navigation.Screen
import com.phantombeats.ui.viewmodels.PlayerViewModel

@Composable
fun MainAppScaffold() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isFullPlayer = currentRoute == Screen.FullPlayer.route
    
    // Obtenemos el ViewModel global de Reproducción atado a la Actividad/App
    val playerViewModel: PlayerViewModel = hiltViewModel()

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
        }
    }
}
