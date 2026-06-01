package com.locationjoystick.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants.ElevationConstants.GRAVITY
import com.locationjoystick.core.common.constants.AppConstants.ElevationConstants.NOISE_AMPLITUDE_MS2
import com.locationjoystick.core.common.constants.AppConstants.ElevationConstants.TILT_JITTER_DEGREES
import com.locationjoystick.core.model.ElevationMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Singleton
class SensorInjector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val tag = "SensorInjector"
    private val sensorManager = context.getSystemService(SensorManager::class.java)

    private val injectMethod: Method? by lazy {
        try {
            SensorManager::class.java
                .getDeclaredMethod(
                    "injectSensorData",
                    Sensor::class.java,
                    FloatArray::class.java,
                    Int::class.javaPrimitiveType,
                    Long::class.javaPrimitiveType,
                ).also { it.isAccessible = true }
        } catch (e: NoSuchMethodException) {
            Log.w(tag, "injectSensorData not available on this device: ${e.message}")
            null
        }
    }

    fun inject(mode: ElevationMode, tiltDegrees: Float, random: Random) {
        val method = injectMethod ?: return
        val noisyTilt = tiltDegrees + (random.nextFloat() * 2f - 1f) * TILT_JITTER_DEGREES
        val accelValues = computeGravityVector(mode, noisyTilt, random)
        val rotValues = computeRotationVector(mode, noisyTilt)
        val timestamp = System.nanoTime()

        injectSensor(method, Sensor.TYPE_ACCELEROMETER, accelValues, timestamp)
        injectSensor(method, Sensor.TYPE_ROTATION_VECTOR, rotValues, timestamp)
    }

    private fun computeGravityVector(mode: ElevationMode, tiltDeg: Float, random: Random): FloatArray {
        val tiltRad = Math.toRadians(tiltDeg.toDouble()).toFloat()
        val yBase: Float
        val zBase: Float
        when (mode) {
            ElevationMode.Neutral -> {
                yBase = 0f
                zBase = GRAVITY
            }
            ElevationMode.TiltUp -> {
                yBase = -sin(tiltRad) * GRAVITY
                zBase = cos(tiltRad) * GRAVITY
            }
            ElevationMode.TiltDown -> {
                yBase = sin(tiltRad) * GRAVITY
                zBase = cos(tiltRad) * GRAVITY
            }
        }
        return floatArrayOf(
            noise(random),
            yBase + noise(random),
            zBase + noise(random),
        )
    }

    private fun computeRotationVector(mode: ElevationMode, tiltDeg: Float): FloatArray {
        val halfTilt = Math.toRadians(tiltDeg / 2.0)
        return when (mode) {
            ElevationMode.Neutral -> floatArrayOf(0f, 0f, 0f, 1f)
            ElevationMode.TiltUp -> floatArrayOf(
                -sin(halfTilt).toFloat(), 0f, 0f, cos(halfTilt).toFloat()
            )
            ElevationMode.TiltDown -> floatArrayOf(
                sin(halfTilt).toFloat(), 0f, 0f, cos(halfTilt).toFloat()
            )
        }
    }

    private fun noise(r: Random) = (r.nextFloat() * 2f - 1f) * NOISE_AMPLITUDE_MS2

    private fun injectSensor(method: Method, sensorType: Int, values: FloatArray, timestamp: Long) {
        val sensor = sensorManager.getDefaultSensor(sensorType) ?: return
        try {
            method.invoke(sensorManager, sensor, values, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, timestamp)
        } catch (e: Exception) {
            Log.w(tag, "Failed to inject sensor type $sensorType: ${e.message}")
        }
    }
}
