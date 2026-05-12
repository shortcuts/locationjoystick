package com.locationjoystick.feature.widget.impl

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.model.Route

private const val TAG = "RoutePickerPopup"

class RoutePickerPopup(
    private val context: Context,
    private val windowManager: WindowManager,
    private val lifecycleOwner: LifecycleOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val routeRepository: RouteRepository,
    private val onRouteSelected: (Route, Boolean) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var popupView: ComposeView? = null

    fun show() {
        if (popupView != null) return
        val view =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

                setContent {
                    val routes by routeRepository.getRoutes().collectAsStateWithLifecycle(initialValue = emptyList())
                    MaterialTheme {
                        Surface(
                            shadowElevation = 8.dp,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            LazyColumn(modifier = Modifier.padding(vertical = 8.dp)) {
                                if (routes.isEmpty()) {
                                    item {
                                        Text(
                                            "No routes saved",
                                            modifier = Modifier.padding(16.dp),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }
                                }
                                items(routes, key = { it.id }) { route ->
                                    Row(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { onRouteSelected(route, false) }
                                                .padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(route.name, style = MaterialTheme.typography.bodyLarge)
                                            Text(
                                                "${route.waypoints.size} waypoints",
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                        }
                                        IconButton(onClick = { onRouteSelected(route, false) }) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = "Play forward")
                                        }
                                        IconButton(onClick = { onRouteSelected(route, true) }) {
                                            Icon(Icons.Default.Replay, contentDescription = "Play backward")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        val displayHeight = context.resources.displayMetrics.heightPixels
        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    (displayHeight * 0.6f).toInt(),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.BOTTOM
                }

        try {
            windowManager.addView(view, params)
            popupView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show route picker popup", e)
        }
    }

    fun dismiss() {
        val view = popupView ?: return
        try {
            if (view.isAttachedToWindow) windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss route picker popup", e)
        } finally {
            popupView = null
        }
    }
}
