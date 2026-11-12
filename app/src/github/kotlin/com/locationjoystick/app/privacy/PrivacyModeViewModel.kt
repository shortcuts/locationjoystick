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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject

private const val TAG = "PrivacyMode"

sealed interface CloneOp {
    data object Idle : CloneOp

    data object NeedsInstallPermission : CloneOp

    data object Building : CloneOp

    data class Installing(
        val updated: Boolean,
    ) : CloneOp

    data class Installed(
        val updated: Boolean,
    ) : CloneOp

    data class Failed(
        val detail: String,
    ) : CloneOp
}

val CloneOp.isBusy: Boolean get() = this is CloneOp.Building || this is CloneOp.Installing

data class PrivacyModeUiState(
    val flags: PrivacyFlags,
    val record: CloneRecord?,
    val op: CloneOp = CloneOp.Idle,
    val showDisclosure: Boolean = false,
)

@HiltViewModel
class PrivacyModeViewModel
    @Inject
    constructor(
        private val builder: CloneBuilder,
        private val installer: CloneInstaller,
        private val store: CloneStore,
        private val flagsStore: PrivacyFlagsStore,
    ) : ViewModel() {
        private val _state = MutableStateFlow(PrivacyModeUiState(flagsStore.read(), store.read()))
        val state: StateFlow<PrivacyModeUiState> = _state.asStateFlow()
        val confirmIntents: Flow<Intent> = installer.status.filterIsInstance<InstallStatus.NeedsUserAction>().map { it.intent }

        init {
            viewModelScope.launch {
                installer.status.collect { status ->
                    when (status) {
                        InstallStatus.Success -> transition { if (it is CloneOp.Installing) CloneOp.Installed(it.updated) else null }
                        is InstallStatus.Failure -> transition { if (it is CloneOp.Installing) CloneOp.Failed(status.detail) else null }
                        is InstallStatus.NeedsUserAction -> Unit
                    }
                }
            }
        }

        fun setEnabled(on: Boolean) {
            val flags = _state.value.flags
            when {
                on && !flags.disclosureAccepted -> _state.update { it.copy(showDisclosure = true) }
                else -> saveFlags(flags.copy(enabled = on))
            }
        }

        fun confirmDisclosure() {
            saveFlags(PrivacyFlags(enabled = true, disclosureAccepted = true))
            _state.update { it.copy(showDisclosure = false) }
        }

        fun dismissDisclosure() = _state.update { it.copy(showDisclosure = false) }

        fun start(name: String) {
            val updated = _state.value.record != null
            val canInstall = installer.canInstall()
            val moved =
                transition { op ->
                    if (op.isBusy) {
                        null
                    } else if (canInstall) {
                        CloneOp.Building
                    } else {
                        CloneOp.NeedsInstallPermission
                    }
                }
            if (!moved || !canInstall) return
            viewModelScope.launch {
                when (val result = builder.build(name)) {
                    is CloneBuildResult.Failed -> transition { if (it is CloneOp.Building) CloneOp.Failed(result.reason) else null }
                    is CloneBuildResult.Built -> {
                        _state.update { it.copy(record = result.record) }
                        if (transition { if (it is CloneOp.Building) CloneOp.Installing(updated) else null }) handOff(result)
                    }
                }
            }
        }

        fun refreshInstallPermission() {
            if (installer.canInstall()) transition { if (it is CloneOp.NeedsInstallPermission) CloneOp.Idle else null }
        }

        fun onRemoved() {
            store.clear()
            _state.update { it.copy(record = null, op = CloneOp.Idle) }
        }

        private suspend fun handOff(built: CloneBuildResult.Built) {
            try {
                installer.install(built.apk)
            } catch (e: IOException) {
                transition { if (it is CloneOp.Installing) CloneOp.Failed(e.message ?: "io error") else null }
            } catch (e: SecurityException) {
                transition { if (it is CloneOp.Installing) CloneOp.Failed(e.message ?: "install refused") else null }
            }
        }

        private fun saveFlags(flags: PrivacyFlags) {
            flagsStore.write(flags)
            _state.update { it.copy(flags = flags) }
        }

        private fun transition(next: (CloneOp) -> CloneOp?): Boolean {
            while (true) {
                val current = _state.value
                val op =
                    next(current.op) ?: run {
                        Log.w(TAG, "ignored transition from ${current.op}")
                        return false
                    }
                if (_state.compareAndSet(current, current.copy(op = op))) return true
            }
        }
    }
