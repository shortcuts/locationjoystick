package com.locationjoystick.core.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.util.Log

/**
 * Mixin helper for [OverlayService] subclasses that need to:
 * 1. Register/unregister a [BroadcastReceiver] for [OverlayService.ACTION_OVERLAY_HIDE] /
 *    [OverlayService.ACTION_OVERLAY_SHOW].
 * 2. Bind/unbind one or more Android services with proper try/catch cleanup in onDestroy.
 *
 * Usage: call [registerOverlayVisibilityReceiver] in `onCreate` and
 * [cleanupOverlayBindings] in `onDestroy`, passing the same [OverlayService] instance as
 * [context].
 */
class OverlayServiceHelper(
    private val tag: String,
) {
    private var visibilityReceiver: BroadcastReceiver? = null
    private val boundConnections = mutableListOf<Pair<Context, ServiceConnection>>()

    /**
     * Registers a [BroadcastReceiver] that delegates overlay hide/show broadcasts to [overlayService].
     * Must be called from [OverlayService.onCreate].
     */
    fun registerOverlayVisibilityReceiver(
        context: Context,
        overlayService: OverlayService,
    ) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    ctx: Context,
                    intent: Intent,
                ) {
                    when (intent.action) {
                        OverlayService.ACTION_OVERLAY_HIDE -> overlayService.hideOverlay()
                        OverlayService.ACTION_OVERLAY_SHOW -> overlayService.showOverlay()
                    }
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(OverlayService.ACTION_OVERLAY_HIDE)
                addAction(OverlayService.ACTION_OVERLAY_SHOW)
            }
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        visibilityReceiver = receiver
    }

    /**
     * Binds a service and tracks the connection for cleanup in [cleanupOverlayBindings].
     * @return the result of [Context.bindService]
     */
    fun bindTrackedService(
        context: Context,
        intent: Intent,
        connection: ServiceConnection,
    ): Boolean {
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (bound) {
            boundConnections.add(context to connection)
        }
        return bound
    }

    /**
     * Unregisters the overlay visibility receiver and unbinds all tracked service connections.
     * Must be called from [OverlayService.onDestroy].
     */
    fun cleanupOverlayBindings(context: Context) {
        val receiver = visibilityReceiver
        if (receiver != null) {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: IllegalArgumentException) {
                Log.e(tag, "Overlay visibility receiver not registered", e)
            }
            visibilityReceiver = null
        }
        val iterator = boundConnections.iterator()
        while (iterator.hasNext()) {
            val (boundContext, connection) = iterator.next()
            try {
                boundContext.unbindService(connection)
            } catch (e: IllegalArgumentException) {
                Log.e(tag, "Service was not bound when attempting to unbind", e)
            }
            iterator.remove()
        }
    }
}
