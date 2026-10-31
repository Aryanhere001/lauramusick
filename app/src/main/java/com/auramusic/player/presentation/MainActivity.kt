package com.auramusic.player.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import com.auramusic.player.presentation.nowplaying.NowPlayingScreen

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Yahan se tumhari app ka asli UI start hoga
            NowPlayingScreen() 
        }
    }
}
