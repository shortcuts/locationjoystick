package com.locationjoystick.feature.widget.impl

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.locationjoystick.core.location.MockLocationService
import com.locationjoystick.core.overlay.OverlayServiceHelper
import com.locationjoystick.feature.joystick.impl.JoystickOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the [ServiceConnection]s and bound-service references used by [FloatingWidgetService].
 *
 * Binds to two services:
 * - [MockLocationService] (via [OverlayServiceHelper.bindTrackedService] — torn down by
 *   [OverlayServiceHelper.cleanupOverlayBindings])
 * - [JoystickOverlayService] (direct bind/unbind, only while spoofing is active)
 *
 * Joystick bind uses [Context.BIND_AUTO_CREATE]. The host must [unbindJoystick] before mock GPS
 * goes idle; otherwise a parked widget would restart the joystick overlay.
 *
 * The bound services are exposed as nullable properties. The host service calls [bind] from
 * `onCreate` and [unbind] from `onDestroy`.
 */
internal class WidgetServiceBinder(
    private val context: Context,
    private val serviceScope: CoroutineScope,
    private val overlayHelper: OverlayServiceHelper,
    private val joystickVisibleFlow: MutableStateFlow<Boolean>,
    private val joystickLockedFlow: MutableStateFlow<Boolean>,
) {
    companion object {
        private const val TAG = "FloatingWidgetService"
    }

    var mockLocationService: MockLocationService? = null
        private set

    var joystickService: JoystickOverlayService? = null
        private set

    private var joystickBound = false

    private val mockLocationServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                mockLocationService = (binder as MockLocationService.LocalBinder).getService()
                Log.d(TAG, "Bound to MockLocationService")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mockLocationService = null
                Log.d(TAG, "Unbound from MockLocationService")
            }
        }

    private val joystickConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                val svc = (binder as JoystickOverlayService.LocalBinder).getService()
                joystickService = svc
                serviceScope.launch { svc.isVisible.collect { joystickVisibleFlow.value = it } }
                serviceScope.launch { svc.isLocked.collect { joystickLockedFlow.value = it } }
                Log.d(TAG, "Bound to JoystickOverlayService")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                joystickService = null
                joystickBound = false
                joystickVisibleFlow.value = false
                joystickLockedFlow.value = false
                Log.d(TAG, "Unbound from JoystickOverlayService")
            }
        }

    fun bind() {
        overlayHelper.bindTrackedService(context, Intent(context, MockLocationService::class.java), mockLocationServiceConnection)
    }

    fun bindJoystick() {
        if (joystickBound) return
        val joystickIntent = Intent(context, JoystickOverlayService::class.java)
        joystickBound = context.bindService(joystickIntent, joystickConnection, Context.BIND_AUTO_CREATE)
    }

    fun unbindJoystick() {
        if (!joystickBound) return
        try {
            context.unbindService(joystickConnection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Joystick service was not bound when attempting to unbind", e)
        }
        joystickBound = false
        joystickService = null
        joystickVisibleFlow.value = false
        joystickLockedFlow.value = false
    }

    fun unbind() {
        unbindJoystick()
    }
}
