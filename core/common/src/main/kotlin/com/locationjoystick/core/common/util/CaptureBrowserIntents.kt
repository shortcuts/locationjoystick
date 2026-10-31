package com.locationjoystick.core.common.util

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.locationjoystick.core.common.R

private const val TAG = "CaptureBrowserIntents"

data class CaptureBrowserChoice(
    val packageName: String,
    val label: String,
)

/**
 * Lists real browser apps even while this app owns Android's browser role. Some Android builds
 * return only the role holder for a generic web query, so also query browser launcher activities.
 */
fun captureBrowserChoices(context: Context): List<CaptureBrowserChoice> {
    val webIntent =
        Intent(Intent.ACTION_VIEW, "https://www.example.com/".toUri()).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
    val browserIntent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_BROWSER)
    return (context.packageManager.queryAllActivities(webIntent) + context.packageManager.queryAllActivities(browserIntent))
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            CaptureBrowserChoice(
                packageName = info.activityInfo.packageName,
                label = info.loadLabel(context.packageManager).toString(),
            )
        }.sortedBy { it.label.lowercase() }
}

fun Context.captureBrowserPackages(): List<String> = captureBrowserChoices(this).map { it.packageName }

// The app manifest <queries> block declares these intents; library lint cannot see it.
@SuppressLint("QueryPermissionsNeeded")
@Suppress("DEPRECATION")
private fun PackageManager.queryAllActivities(intent: Intent): List<ResolveInfo> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
    } else {
        queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }

fun captureAppOpenByDefaultIntent(packageName: String): Intent {
    val intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
        }
    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

fun captureManageDefaultAppsIntent(): Intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Opens Default apps so the user can set Browser app. RoleManager's request dialog is a no-op on many OEMs. */
fun captureSetDefaultBrowserIntent(): Intent = captureManageDefaultAppsIntent()

fun currentBrowserRoleHolder(context: Context): String? {
    val intent =
        Intent(Intent.ACTION_VIEW, "https://example.com".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName
}

fun Context.isCaptureDefaultBrowser(): Boolean = currentBrowserRoleHolder(this) == packageName

fun Context.launchCaptureDefaultBrowser(onRememberPrevious: (String) -> Unit) {
    val holder = currentBrowserRoleHolder(this)
    if (holder != null && holder != packageName) {
        onRememberPrevious(holder)
    }
    startCaptureSetting(captureSetDefaultBrowserIntent())
}

fun Context.launchCaptureMapsLinks() {
    startCaptureSetting(captureAppOpenByDefaultIntent(CaptureBrowserPackages.GOOGLE_MAPS))
}

fun Context.launchCaptureRestoreDefaultApps() {
    startCaptureSetting(captureManageDefaultAppsIntent())
}

private fun Context.startCaptureSetting(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No settings activity for $intent", e)
        Toast.makeText(this, getString(R.string.capture_browser_intents_couldnt_open_setting), Toast.LENGTH_SHORT).show()
    }
}

/** Some OEM and Go builds lack a handler for specific settings pages, so fall back to the main Settings app. */
fun Context.startSettingsActivity(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No settings activity for $intent", e)
        startCaptureSetting(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
