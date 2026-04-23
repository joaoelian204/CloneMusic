package com.phantombeats

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.phantombeats.ui.theme.PhantomBeatsTheme
import com.phantombeats.ui.MainAppScaffold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_OPEN_FULL_PLAYER = "extra_open_full_player"
    }

    private val openFullPlayerRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeIntent(intent)
        
        setContent {
            PhantomBeatsTheme {
                MainAppScaffold(openFullPlayerRequests = openFullPlayerRequests)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        val shouldOpenFullPlayer = intent?.getBooleanExtra(EXTRA_OPEN_FULL_PLAYER, false) == true
        if (shouldOpenFullPlayer) {
            openFullPlayerRequests.update { it + 1 }
            intent?.removeExtra(EXTRA_OPEN_FULL_PLAYER)
        }
    }
}
