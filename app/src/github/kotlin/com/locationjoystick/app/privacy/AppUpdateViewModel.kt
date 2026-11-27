package com.locationjoystick.app.privacy

import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private const val TAG = "AppUpdate"

sealed interface UpdateOp {
    data object Idle : UpdateOp

    data object NeedsInstallPermission : UpdateOp

    data object Preparing : UpdateOp

    data object UpToDate : UpdateOp

    data object Installing : UpdateOp

    data class Failed(
        val detail: String,
    ) : UpdateOp
}

val UpdateOp.isBusy: Boolean get() = this == UpdateOp.Preparing || this == UpdateOp.Installing

@HiltViewModel
class AppUpdateViewModel
    @Inject
    constructor(
        private val updater: AppUpdater,
        private val installer: CloneInstaller,
    ) : ViewModel() {
        private val _op = MutableStateFlow<UpdateOp>(UpdateOp.Idle)
        val op: StateFlow<UpdateOp> = _op.asStateFlow()
        val confirmIntents: Flow<Intent> = installer.status.filterIsInstance<InstallStatus.NeedsUserAction>().map { it.intent }

        init {
            viewModelScope.launch {
                installer.status.collect { status ->
                    when (status) {
                        // Android replaces the running app, so success rarely reaches this screen.
                        InstallStatus.Success -> transition { if (it == UpdateOp.Installing) UpdateOp.Idle else null }
                        is InstallStatus.Failure -> transition { if (it == UpdateOp.Installing) UpdateOp.Failed(status.detail) else null }
                        is InstallStatus.NeedsUserAction -> Unit
                    }
                }
            }
        }

        fun update() {
            val canInstall = installer.canInstall()
            val moved =
                transition {
                    if (it.isBusy) {
                        null
                    } else if (canInstall) {
                        UpdateOp.Preparing
                    } else {
                        UpdateOp.NeedsInstallPermission
                    }
                }
            if (!moved || !canInstall) return
            viewModelScope.launch {
                when (val result = updater.prepare()) {
                    UpdatePrepareResult.UpToDate -> transition { if (it == UpdateOp.Preparing) UpdateOp.UpToDate else null }
                    is UpdatePrepareResult.Failed -> transition { if (it == UpdateOp.Preparing) UpdateOp.Failed(result.reason) else null }
                    is UpdatePrepareResult.Ready ->
                        if (transition {
                                if (it ==
                                    UpdateOp.Preparing
                                ) {
                                    UpdateOp.Installing
                                } else {
                                    null
                                }
                            }
                        ) {
                            handOff(result)
                        }
                }
            }
        }

        fun refreshInstallPermission() {
            if (installer.canInstall()) transition { if (it == UpdateOp.NeedsInstallPermission) UpdateOp.Idle else null }
        }

        private suspend fun handOff(ready: UpdatePrepareResult.Ready) {
            try {
                installer.install(ready.apk)
            } catch (e: IOException) {
                transition { if (it == UpdateOp.Installing) UpdateOp.Failed(e.message ?: "io error") else null }
            } catch (e: SecurityException) {
                transition { if (it == UpdateOp.Installing) UpdateOp.Failed(e.message ?: "install refused") else null }
            }
        }

        private fun transition(next: (UpdateOp) -> UpdateOp?): Boolean {
            while (true) {
                val current = _op.value
                val op =
                    next(current) ?: run {
                        Log.w(TAG, "ignored transition from $current")
                        return false
                    }
                if (_op.compareAndSet(current, op)) return true
            }
        }
    }
