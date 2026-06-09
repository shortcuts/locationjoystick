package com.locationjoystick.core.location

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.util.Log
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.common.constants.AppConstants.ElevationConstants.GRAVITY
import com.locationjoystick.core.model.ElevationMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.lang.reflect.Method
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Singleton
class SensorInjector
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
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

        private var fractionalSteps = 0f
        private var cumulativeSteps = 0L

        // Called on each 1 Hz tick. speedMs == distance traveled (m) at 1 Hz.
        fun injectPedometerTick(speedMs: Float) {
            val method = injectMethod ?: return
            if (speedMs <= 0f || speedMs > AppConstants.PedometerConstants.MAX_WALKING_SPEED_MPS) return
            val baseStride = AppConstants.PedometerConstants.STRIDE_BASE_METERS + speedMs * AppConstants.PedometerConstants.STRIDE_SPEED_FACTOR
            val timestamp = System.nanoTime()
            var distanceM = speedMs
            while (distanceM > 0f) {
                val stride = baseStride * (1f + (Random.Default.nextFloat() * 2f - 1f) * AppConstants.PedometerConstants.STRIDE_JITTER_PCT)
                val toNextStep = stride - fractionalSteps
                if (distanceM >= toNextStep) {
                    distanceM -= toNextStep
                    fractionalSteps = 0f
                    injectSensor(method, Sensor.TYPE_STEP_COUNTER, floatArrayOf((++cumulativeSteps).toFloat()), timestamp)
                    injectSensor(method, Sensor.TYPE_STEP_DETECTOR, floatArrayOf(1.0f), timestamp)
                } else {
                    fractionalSteps += distanceM
                    break
                }
            }
        }

        // Reset sub-stride carry on spoofing restart; cumulativeSteps intentionally preserved (monotonic).
        fun resetPedometerState() {
            fractionalSteps = 0f
        }

        fun inject(
            mode: ElevationMode,
            tiltDegrees: Float,
            tiltJitterDegrees: Float,
            noiseAmplitudeMs2: Float,
            random: Random,
        ) {
            val method = injectMethod ?: return
            val noisyTilt = tiltDegrees + (random.nextFloat() * 2f - 1f) * tiltJitterDegrees
            val timestamp = System.nanoTime()
            injectSensor(method, Sensor.TYPE_ACCELEROMETER, elevationGravityVector(mode, noisyTilt, noiseAmplitudeMs2, random), timestamp)
            injectSensor(method, Sensor.TYPE_ROTATION_VECTOR, elevationRotationVector(mode, noisyTilt), timestamp)
        }

        private fun injectSensor(
            method: Method,
            sensorType: Int,
            values: FloatArray,
            timestamp: Long,
        ) {
            val sensor = sensorManager.getDefaultSensor(sensorType) ?: return
            try {
                method.invoke(sensorManager, sensor, values, SensorManager.SENSOR_STATUS_ACCURACY_HIGH, timestamp)
            } catch (e: Exception) {
                Log.w(tag, "Failed to inject sensor type $sensorType: ${e.message}")
            }
        }
    }

internal fun elevationGravityVector(
    mode: ElevationMode,
    tiltDeg: Float,
    noiseAmplitude: Float,
    random: Random,
): FloatArray {
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
        elevationNoise(noiseAmplitude, random),
        yBase + elevationNoise(noiseAmplitude, random),
        zBase + elevationNoise(noiseAmplitude, random),
    )
}

internal fun elevationRotationVector(
    mode: ElevationMode,
    tiltDeg: Float,
): FloatArray {
    val halfTilt = Math.toRadians(tiltDeg / 2.0)
    return when (mode) {
        ElevationMode.Neutral -> floatArrayOf(0f, 0f, 0f, 1f)
        ElevationMode.TiltUp -> floatArrayOf(-sin(halfTilt).toFloat(), 0f, 0f, cos(halfTilt).toFloat())
        ElevationMode.TiltDown -> floatArrayOf(sin(halfTilt).toFloat(), 0f, 0f, cos(halfTilt).toFloat())
    }
}

internal fun elevationNoise(
    amplitude: Float,
    r: Random,
) = (r.nextFloat() * 2f - 1f) * amplitude
