package com.locationjoystick.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.locationjoystick.app.R
import com.locationjoystick.core.common.util.CaptureBrowserPackages
import com.locationjoystick.core.common.util.CaptureLinkDecision
import com.locationjoystick.core.common.util.captureBrowserPackages
import com.locationjoystick.core.common.util.decideCaptureLink
import com.locationjoystick.core.common.util.formatCapturedPoint
import com.locationjoystick.core.common.util.isGoogleMapsWebLink
import com.locationjoystick.core.common.util.pickForwardBrowserPackage
import com.locationjoystick.core.common.util.resolvePreferredBrowserPackage
import com.locationjoystick.core.data.CaptureCoordinatesRepository
import com.locationjoystick.core.data.GoogleMapsShortLinkResolver
import com.locationjoystick.core.data.TeleportUseCase
import com.locationjoystick.core.model.LatLng
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LinkInterceptorActivity : ComponentActivity() {
    @Inject lateinit var captureRepository: CaptureCoordinatesRepository

    @Inject lateinit var shortLinkResolver: GoogleMapsShortLinkResolver

    @Inject lateinit var teleportUseCase: TeleportUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        lifecycleScope.launch {
            try {
                dispatch(intent)
            } finally {
                finish()
            }
        }
    }

    private suspend fun dispatch(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val coords = parseCoords(uri)
        val captureModeEnabled = captureRepository.captureModeEnabled.first()
        val captureEnabled = captureRepository.captureEnabled.first()
        val jumpEnabled = captureRepository.jumpEnabled.first()
        when (decideCaptureLink(captureModeEnabled, captureEnabled, jumpEnabled, coords)) {
            CaptureLinkDecision.CAPTURE -> {
                val point = LatLng(coords!!.first, coords.second)
                captureRepository.appendPoint(point)
                Toast
                    .makeText(
                        this,
                        getString(R.string.link_interceptor_captured, formatCapturedPoint(point)),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
            CaptureLinkDecision.JUMP -> {
                val point = LatLng(coords!!.first, coords.second)
                teleportUseCase.execute(point)
                Toast
                    .makeText(
                        this,
                        getString(R.string.link_interceptor_jumped_to, formatCapturedPoint(point)),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
            CaptureLinkDecision.CAPTURE_AND_JUMP -> {
                val point = LatLng(coords!!.first, coords.second)
                captureRepository.appendPoint(point)
                teleportUseCase.execute(point)
                Toast
                    .makeText(
                        this,
                        getString(R.string.link_interceptor_captured_and_jumped_to, formatCapturedPoint(point)),
                        Toast.LENGTH_SHORT,
                    ).show()
            }
            CaptureLinkDecision.FORWARD -> forward(uri, preferGoogleMaps = !captureModeEnabled && isGoogleMapsWebLink(uri.toString()))
        }
    }

    private suspend fun parseCoords(uri: Uri): Pair<Double, Double>? {
        parseUrlCoords(uri.toString())?.let { return it }
        val url = uri.toString()
        if (shortLinkResolver.isShortLink(url)) {
            val resolved = shortLinkResolver.resolve(url) ?: return null
            return parseUrlCoords(resolved)
        }
        return null
    }

    private suspend fun forward(
        uri: Uri,
        preferGoogleMaps: Boolean,
    ) {
        if (preferGoogleMaps && launchInPackage(uri, CaptureBrowserPackages.GOOGLE_MAPS)) return
        val preferred =
            resolvePreferredBrowserPackage(
                savedPackage = captureRepository.previousBrowserPackage.first(),
                selfPackage = packageName,
            )
        val uriCandidates =
            packageManager
                .queryIntentActivities(
                    Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE),
                    0,
                ).map { it.activityInfo.packageName }
        val candidates = captureBrowserPackages() + uriCandidates
        val target = pickForwardBrowserPackage(candidates, packageName, preferred) ?: return
        if (!launchInPackage(uri, target)) Log.e(TAG, "No browser to forward $uri")
    }

    private fun launchInPackage(
        uri: Uri,
        targetPackage: String,
    ): Boolean =
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    setPackage(targetPackage)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            true
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "$targetPackage cannot open $uri", e)
            false
        }

    companion object {
        private const val TAG = "LinkInterceptor"
    }
}
