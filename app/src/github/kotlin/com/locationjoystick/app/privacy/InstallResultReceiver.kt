package com.locationjoystick.app.privacy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class InstallResultReceiver : BroadcastReceiver() {
    @Inject lateinit var installer: CloneInstaller

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = installer.onResult(intent)
}
