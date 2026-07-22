package com.storybrain.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.storybrain.app.ui.StoryBrainApp
import com.storybrain.app.ui.theme.StoryBrainTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StoryBrainTheme {
                Surface(Modifier.fillMaxSize()) { StoryBrainApp() }
            }
        }
    }
}

