package com.storybrain.app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.storybrain.app.ui.StoryBrainApp
import com.storybrain.app.ui.theme.StoryBrainTheme

class MainActivity : ComponentActivity() {
    private val requestNotificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* In-app progress remains available when notifications are denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val permissionPreferences = getSharedPreferences("notification_permission", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !permissionPreferences.getBoolean("requested", false)
        ) {
            permissionPreferences.edit { putBoolean("requested", true) }
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            StoryBrainTheme {
                Surface(Modifier.fillMaxSize()) { StoryBrainApp() }
            }
        }
    }
}
