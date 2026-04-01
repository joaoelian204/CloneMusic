package com.phantombeats

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.phantombeats.ui.theme.PhantomBeatsTheme
import com.phantombeats.ui.MainAppScaffold

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            PhantomBeatsTheme {
                MainAppScaffold()
            }
        }
    }
}
