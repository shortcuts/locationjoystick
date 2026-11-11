package com.locationjoystick.app.privacy

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.locationjoystick.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InstallStatus {
    data object Success : InstallStatus

    data class NeedsUserAction(
        val intent: Intent,
    ) : InstallStatus

    data class Failure(
        val detail: String,
    ) : InstallStatus
}

/** Hands the clone APK to the system installer. The UI starts the confirm screen: a receiver start from the background is blocked on API 34. */
@Singleton
class CloneInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        @IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) {
        private val results = MutableSharedFlow<InstallStatus>(extraBufferCapacity = 4)
        val status: SharedFlow<InstallStatus> = results.asSharedFlow()

        fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

        suspend fun install(apk: File) =
            withContext(dispatcher) {
                val installer = context.packageManager.packageInstaller
                val sessionId = installer.createSession(PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL))
                installer.openSession(sessionId).use { session ->
                    session.openWrite("clone", 0, apk.length()).use { out ->
                        apk.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    // Mutable: the installer adds the status extras to this intent.
                    val pending =
                        PendingIntent.getBroadcast(
                            context,
                            sessionId,
                            Intent(context, InstallResultReceiver::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                        )
                    session.commit(pending.intentSender)
                }
            }

        fun onResult(intent: Intent) {
            val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val status =
                when (code) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION ->
                        IntentCompat
                            .getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                            ?.let(InstallStatus::NeedsUserAction) ?: InstallStatus.Failure("no confirm screen")
                    PackageInstaller.STATUS_SUCCESS -> InstallStatus.Success
                    else -> InstallStatus.Failure(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "status $code")
                }
            results.tryEmit(status)
            if (status !is InstallStatus.NeedsUserAction) File(context.cacheDir, "privacy-clone/clone.apk").delete()
        }
    }
