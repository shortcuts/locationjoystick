package com.locationjoystick.core.location

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between [CompassAccessibilityService] (in feature:widget:impl) and consumers
 * (e.g. FloatingWidgetService). The service binds/unbinds itself; consumers call
 * [captureHeading] which delegates to the live service instance.
 */
@Singleton
class CompassHeadingSource
    @Inject
    constructor() {
        private var service: CompassAccessibilityServiceBridge? = null

        fun bind(svc: CompassAccessibilityServiceBridge) {
            service = svc
        }

        fun unbind() {
            service = null
        }

        val isAvailable: Boolean get() = service != null

        suspend fun captureHeading(
            cx: Float,
            cy: Float,
            radius: Float,
        ): Float? = service?.captureHeading(cx, cy, radius)
    }

/** Minimal interface exposed by CompassAccessibilityService to avoid a circular module dependency. */
interface CompassAccessibilityServiceBridge {
    suspend fun captureHeading(
        cx: Float,
        cy: Float,
        radius: Float,
    ): Float?
}
