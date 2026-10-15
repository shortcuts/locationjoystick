package com.locationjoystick.feature.settings.impl

import android.content.Context
import androidx.annotation.StringRes
import com.locationjoystick.core.location.CalibrationFailure
import com.locationjoystick.core.model.StoredGroundCalibration
import java.text.DateFormat
import java.util.Date

/** Exhaustive on purpose: a new [CalibrationFailure] entry must fail to compile until it has copy. */
@StringRes
internal fun calibrationFailureMessageRes(reason: CalibrationFailure): Int =
    when (reason) {
        CalibrationFailure.NOT_READY -> R.string.settings_menus_calibration_fail_not_ready
        CalibrationFailure.SERVICE_UNAVAILABLE -> R.string.settings_menus_calibration_fail_service
        CalibrationFailure.STORAGE -> R.string.settings_menus_calibration_fail_storage
        CalibrationFailure.TIMEOUT -> R.string.settings_menus_calibration_fail_timeout
        CalibrationFailure.SPEED_ABORT -> R.string.settings_menus_calibration_fail_speed
        CalibrationFailure.INTERRUPTED -> R.string.settings_menus_calibration_fail_interrupted
        CalibrationFailure.FRAME_SIZE_CHANGED -> R.string.settings_menus_calibration_fail_frame_size
        CalibrationFailure.FEET_NOT_FOUND -> R.string.settings_menus_calibration_fail_feet
        CalibrationFailure.TOO_FEW_FLOWS -> R.string.settings_menus_calibration_fail_flows
        CalibrationFailure.IMPLAUSIBLE_FIT -> R.string.settings_menus_calibration_fail_implausible
    }

/** "Calibrated <date and time> from <n> measurements". */
internal fun calibrationDoneText(
    context: Context,
    stored: StoredGroundCalibration,
): String =
    context.getString(
        R.string.settings_menus_calibration_done,
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(stored.calibratedAtMs)),
        stored.flowCount,
    )
