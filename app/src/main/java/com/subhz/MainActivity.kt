package com.subhz

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.subhz.player.PlayerManager
import com.subhz.ui.navigation.MainScreen
import com.subhz.ui.theme.SubHzTheme
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {

    private val playerManager: PlayerManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request notification permission for Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContent {
            SubHzTheme {
                // MaterialTheme only provides the scheme, it does not paint anything.
                // Without this Surface a screen that has no Scaffold of its own (LoginScreen)
                // shows the window background through, which stays white in dark mode.
                Surface(color = MaterialTheme.colorScheme.background) {
                    MainScreen()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playerManager.connect()
    }

    // Unbinding here is what lets the service actually stop when the app is swiped out of
    // recents; playback itself lives in the service and carries on regardless.
    override fun onStop() {
        playerManager.disconnect()
        super.onStop()
    }
}
