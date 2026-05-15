package com.locationjoystick.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import com.locationjoystick.core.designsystem.LjTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NAVIGATE_TO_MAP = "navigate_to_map"
        const val ACTION_MOVE_TO_BACK = "com.locationjoystick.app.ACTION_MOVE_TO_BACK"
    }

    private val _navigateToMapFlow = MutableSharedFlow<Unit>(replay = 1)
    internal val navigateToMapFlow = _navigateToMapFlow.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                0,
            )
        }

        setContent {
            LjTheme {
                LjApp(navigateToMapFlow = navigateToMapFlow)
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    internal fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_MAP, false) == true) {
            _navigateToMapFlow.tryEmit(Unit)
        }
        if (intent?.action == ACTION_MOVE_TO_BACK) {
            moveTaskToBack(true)
        }
    }
}
