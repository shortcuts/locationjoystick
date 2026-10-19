package com.locationjoystick.core.datastore

import com.locationjoystick.core.model.GroundCalibration
import com.locationjoystick.core.model.StoredGroundCalibration
import org.json.JSONObject

private const val VERSION = 1

internal fun serializeGroundCalibration(value: StoredGroundCalibration): String =
    JSONObject()
        .apply {
            put("v", VERSION)
            put("headingDeg", value.calibration.headingDeg)
            put("lateralPxPerM", value.calibration.lateralPxPerM)
            put("forwardPxPerM", value.calibration.forwardPxPerM)
            put("perspective", value.calibration.perspective)
            put("feetX", value.calibration.feetX)
            put("feetY", value.calibration.feetY)
            put("screenWidth", value.screenWidth)
            put("screenHeight", value.screenHeight)
            put("calibratedAtMs", value.calibratedAtMs)
            put("flowCount", value.flowCount)
            put("medianAbsPx", value.medianAbsPx)
            put("suspect", value.suspect)
        }.toString()

/** Null for an unknown version, a missing field or malformed JSON: a stale map must never throw. */
internal fun deserializeGroundCalibration(raw: String): StoredGroundCalibration? =
    try {
        val o = JSONObject(raw)
        if (o.getInt("v") != VERSION) {
            null
        } else {
            StoredGroundCalibration(
                calibration =
                    GroundCalibration(
                        o.getDouble("headingDeg"),
                        o.getDouble("lateralPxPerM"),
                        o.getDouble("forwardPxPerM"),
                        o.getDouble("perspective"),
                        o.getDouble("feetX"),
                        o.getDouble("feetY"),
                    ),
                screenWidth = o.getInt("screenWidth"),
                screenHeight = o.getInt("screenHeight"),
                calibratedAtMs = o.getLong("calibratedAtMs"),
                flowCount = o.getInt("flowCount"),
                medianAbsPx = o.getDouble("medianAbsPx"),
                suspect = o.optBoolean("suspect", false),
            )
        }
    } catch (e: org.json.JSONException) {
        null
    }
