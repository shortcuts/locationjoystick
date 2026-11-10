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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal sealed interface JoystickBinding {
    data object Unbound : JoystickBinding

    data object Binding : JoystickBinding

    data class Bound(
        val service: JoystickOverlayService,
        val collectors: Job,
    ) : JoystickBinding
}

/** Null when unbound: a late connection after unbind must not revive the service. */
internal fun JoystickBinding.connected(
    service: JoystickOverlayService,
    startCollectors: () -> Job,
): JoystickBinding? =
    when (this) {
        JoystickBinding.Unbound -> null
        JoystickBinding.Binding, is JoystickBinding.Bound -> JoystickBinding.Bound(service, startCollectors())
    }

/** Null when a bind is already pending or live. */
internal fun JoystickBinding.bindRequested(): JoystickBinding? = if (this == JoystickBinding.Unbound) JoystickBinding.Binding else null

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

    @Volatile
    private var mockLocation: MockLocationService? = null

    @Volatile
    private var joystick: JoystickBinding = JoystickBinding.Unbound

    val mockLocationService: MockLocationService? get() = mockLocation

    val joystickService: JoystickOverlayService? get() = (joystick as? JoystickBinding.Bound)?.service

    private val mockLocationServiceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName,
                binder: IBinder,
            ) {
                mockLocation = (binder as MockLocationService.LocalBinder).getService()
                Log.d(TAG, "Bound to MockLocationService")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                mockLocation = null
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
                synchronized(this@WidgetServiceBinder) {
                    val current = joystick
                    val next =
                        current.connected(svc) {
                            serviceScope.launch {
                                launch { svc.isVisible.collect { joystickVisibleFlow.value = it } }
                                launch { svc.isLocked.collect { joystickLockedFlow.value = it } }
                            }
                        }
                    if (next == null) {
                        Log.w(TAG, "ignored joystick connection while $current")
                        return
                    }
                    (current as? JoystickBinding.Bound)?.collectors?.cancel()
                    joystick = next
                }
                Log.d(TAG, "Bound to JoystickOverlayService")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                resetJoystick()
                Log.d(TAG, "Unbound from JoystickOverlayService")
            }
        }

    fun bind() {
        overlayHelper.bindTrackedService(context, Intent(context, MockLocationService::class.java), mockLocationServiceConnection)
    }

    fun bindJoystick() {
        synchronized(this) {
            joystick = joystick.bindRequested() ?: return
        }
        val joystickIntent = Intent(context, JoystickOverlayService::class.java)
        if (!context.bindService(joystickIntent, joystickConnection, Context.BIND_AUTO_CREATE)) resetJoystick()
    }

    fun unbindJoystick() {
        if (joystick == JoystickBinding.Unbound) return
        try {
            context.unbindService(joystickConnection)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Joystick service was not bound when attempting to unbind", e)
        }
        resetJoystick()
    }

    @Synchronized
    private fun resetJoystick() {
        (joystick as? JoystickBinding.Bound)?.collectors?.cancel()
        joystick = JoystickBinding.Unbound
        joystickVisibleFlow.value = false
        joystickLockedFlow.value = false
    }

    fun unbind() {
        unbindJoystick()
    }
}
