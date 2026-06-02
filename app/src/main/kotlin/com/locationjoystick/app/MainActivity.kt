package com.locationjoystick.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.locationjoystick.core.designsystem.LjTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_NAVIGATE_TO_MAP = "navigate_to_map"
        const val EXTRA_NAVIGATE_TO_ROUTE_CREATOR = "navigate_to_route_creator"
        const val ACTION_MOVE_TO_BACK = "com.locationjoystick.app.ACTION_MOVE_TO_BACK"
    }

    private val navigateToMapMutableFlow = MutableSharedFlow<Unit>(replay = 1)
    internal val navigateToMapFlow = navigateToMapMutableFlow.asSharedFlow()
    private val navigateToRouteCreatorMutableFlow = MutableSharedFlow<Unit>(replay = 1)
    internal val navigateToRouteCreatorFlow = navigateToRouteCreatorMutableFlow.asSharedFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                0,
            )
        }

        setContent {
            LjTheme {
                LjApp(
                    navigateToMapFlow = navigateToMapFlow,
                    navigateToRouteCreatorFlow = navigateToRouteCreatorFlow,
                )
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
            navigateToMapMutableFlow.tryEmit(Unit)
        }
        if (intent?.getBooleanExtra(EXTRA_NAVIGATE_TO_ROUTE_CREATOR, false) == true) {
            navigateToRouteCreatorMutableFlow.tryEmit(Unit)
        }
        if (intent?.action == ACTION_MOVE_TO_BACK) {
            moveTaskToBack(true)
        }
    }
}
