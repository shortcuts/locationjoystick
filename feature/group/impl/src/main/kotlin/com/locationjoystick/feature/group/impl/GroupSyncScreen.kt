package com.locationjoystick.feature.group.impl

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.locationjoystick.core.common.util.haversineDistance
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.CooldownAdvisoryBadge
import com.locationjoystick.core.designsystem.component.LjAlertDialog
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjCard
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjPrimaryButton
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSelectionMode
import com.locationjoystick.core.designsystem.component.LjSelectionRow
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.designsystem.component.WideContentClamp
import com.locationjoystick.core.designsystem.component.cooldownBadgeText
import com.locationjoystick.core.designsystem.component.rememberCooldownStringLookup
import com.locationjoystick.core.designsystem.component.writePlainText
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.GroupInvite
import com.locationjoystick.core.model.GroupRole
import com.locationjoystick.core.model.GroupState
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.feature.group.impl.R
import kotlinx.coroutines.launch

@Composable
fun GroupSyncRoute(
    onOpenDrawer: () -> Unit,
    viewModel: GroupSyncViewModel = hiltViewModel(),
) {
    val groupState by viewModel.groupState.collectAsStateWithLifecycle()
    val qrBitmap by viewModel.qrBitmap.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val isDiscovering by viewModel.isDiscovering.collectAsStateWithLifecycle()
    val followerCount by viewModel.followerCount.collectAsStateWithLifecycle()
    val hideTeleportFeatures by viewModel.hideTeleportFeatures.collectAsStateWithLifecycle()
    val leaderPosition by viewModel.leaderPosition.collectAsStateWithLifecycle()
    val currentPosition by viewModel.currentPosition.collectAsStateWithLifecycle()
    val cooldownState by viewModel.cooldownState.collectAsStateWithLifecycle()
    val apiEnabled by viewModel.apiEnabled.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val nearbyLeader by viewModel.nearbyLeader.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val spoofToggle = rememberSpoofToggleState()

    var showQrScanner by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage!!)
            viewModel.clearError()
        }
    }

    LaunchedEffect(groupState.role) {
        if (groupState.role != GroupRole.NONE) showQrScanner = false
    }

    // Poll only while the idle view is on screen and started; the QR scanner replaces that view.
    LaunchedEffect(groupState.role == GroupRole.NONE && !showQrScanner) {
        if (groupState.role == GroupRole.NONE && !showQrScanner) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) { viewModel.watchNearbyLeader() }
        }
    }

    if (showQrScanner) {
        GroupQrScannerScreen(
            onQrScanned = { url ->
                viewModel.joinViaScannedUrl(url)
            },
            onNavigateBack = { showQrScanner = false },
        )
    } else {
        GroupSyncScreen(
            groupState = groupState,
            qrBitmap = qrBitmap,
            snackbarHostState = snackbarHostState,
            isDiscovering = isDiscovering,
            followerCount = followerCount,
            isSpoofing = spoofToggle.isSpoofing,
            onToggleSpoofing = spoofToggle.onToggle,
            locationLabel = spoofToggle.locationLabel,
            onOpenDrawer = onOpenDrawer,
            onCreateGroup = viewModel::createGroup,
            onJoinViaQr = { showQrScanner = true },
            onJoinByCode = viewModel::joinByCode,
            nearbyLeader = nearbyLeader,
            onJoinNearby = viewModel::joinNearbyLeader,
            onSetFollowerModeEnabled = viewModel::setFollowerModeEnabled,
            onSetFollowLeaderTeleports = viewModel::setFollowLeaderTeleports,
            onSetSharingEnabled = viewModel::setSharingEnabled,
            onTeleportToLeaderNow = viewModel::teleportToLeaderNow,
            onLeaveGroup = viewModel::leaveGroup,
            onRegenerateQr = viewModel::regenerateQr,
            hideTeleportFeatures = hideTeleportFeatures,
            leaderPosition = leaderPosition,
            currentPosition = currentPosition,
            cooldownState = cooldownState,
            apiEnabled = apiEnabled,
            apiKey = apiKey,
            onSetApiEnabled = viewModel::setApiEnabled,
            onRegenerateApiKey = viewModel::regenerateApiKey,
        )
    }
}

@Composable
internal fun GroupSyncScreen(
    groupState: GroupState,
    qrBitmap: Bitmap?,
    snackbarHostState: SnackbarHostState,
    isDiscovering: Boolean,
    followerCount: Int,
    isSpoofing: Boolean,
    onToggleSpoofing: () -> Unit,
    locationLabel: String? = null,
    onOpenDrawer: () -> Unit,
    onCreateGroup: () -> Unit,
    onJoinViaQr: () -> Unit,
    onJoinByCode: (String) -> Unit,
    onSetFollowerModeEnabled: (Boolean) -> Unit,
    onSetFollowLeaderTeleports: (Boolean) -> Unit,
    onSetSharingEnabled: (Boolean) -> Unit,
    onTeleportToLeaderNow: () -> Unit,
    onLeaveGroup: () -> Unit,
    onRegenerateQr: () -> Unit,
    nearbyLeader: GroupInvite? = null,
    onJoinNearby: () -> Unit = {},
    hideTeleportFeatures: Boolean = false,
    leaderPosition: LatLng? = null,
    currentPosition: LatLng? = null,
    cooldownState: CooldownState = CooldownState.Ready,
    apiEnabled: Boolean = false,
    apiKey: String? = null,
    onSetApiEnabled: (Boolean) -> Unit = {},
    onRegenerateApiKey: () -> Unit = {},
) {
    LjScaffold(
        title = stringResource(R.string.group_sync_group_sync),
        isSpoofing = isSpoofing,
        onToggleSpoofing = onToggleSpoofing,
        locationLabel = locationLabel,
        onNavigationClick = onOpenDrawer,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { paddingValues ->
        WideContentClamp(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentModifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            when (groupState.role) {
                GroupRole.NONE -> {
                    NoGroupContent(
                        isDiscovering = isDiscovering,
                        onCreateGroup = onCreateGroup,
                        onJoinViaQr = onJoinViaQr,
                        onJoinByCode = onJoinByCode,
                        nearbyLeader = nearbyLeader,
                        onJoinNearby = onJoinNearby,
                    )
                }

                GroupRole.LEADER -> {
                    LeaderContent(
                        groupState = groupState,
                        qrBitmap = qrBitmap,
                        followerCount = followerCount,
                        onSetSharingEnabled = onSetSharingEnabled,
                        onLeaveGroup = onLeaveGroup,
                        onRegenerateQr = onRegenerateQr,
                        apiEnabled = apiEnabled,
                        apiKey = apiKey,
                        onSetApiEnabled = onSetApiEnabled,
                        onRegenerateApiKey = onRegenerateApiKey,
                    )
                }

                GroupRole.FOLLOWER -> {
                    FollowerContent(
                        groupState = groupState,
                        followerCount = followerCount,
                        onSetFollowerModeEnabled = onSetFollowerModeEnabled,
                        onSetFollowLeaderTeleports = onSetFollowLeaderTeleports,
                        onTeleportToLeaderNow = onTeleportToLeaderNow,
                        onLeaveGroup = onLeaveGroup,
                        hideTeleportFeatures = hideTeleportFeatures,
                        leaderPosition = leaderPosition,
                        currentPosition = currentPosition,
                        cooldownState = cooldownState,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NoGroupContent(
    isDiscovering: Boolean,
    onCreateGroup: () -> Unit,
    onJoinViaQr: () -> Unit,
    onJoinByCode: (String) -> Unit,
    nearbyLeader: GroupInvite?,
    onJoinNearby: () -> Unit,
) {
    var showCodeDialog by rememberSaveable { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = LjIcons.Groups,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(R.string.group_sync_group_sync),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = stringResource(R.string.group_sync_sync_your_fake_location_across_multiple),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))

        LjPrimaryButton(
            onClick = onCreateGroup,
            text = stringResource(R.string.group_sync_create_group_i_m_the_leader),
        )

        if (nearbyLeader != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.group_sync_nearby_group_found, nearbyLeader.groupId),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                LjPrimaryButton(
                    onClick = onJoinNearby,
                    text = stringResource(R.string.group_sync_join),
                    modifier = Modifier.weight(1f),
                    enabled = !isDiscovering,
                )
            }
        }

        Text(
            text = stringResource(R.string.group_sync_or_join_an_existing_group),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LjOutlinedButton(
                onClick = onJoinViaQr,
                modifier = Modifier.weight(1f),
                enabled = !isDiscovering,
            ) {
                Icon(LjIcons.QrCodeScanner, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.group_sync_scan_qr))
            }
            LjOutlinedButton(
                onClick = { showCodeDialog = true },
                modifier = Modifier.weight(1f),
                enabled = !isDiscovering,
            ) {
                if (isDiscovering) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.group_sync_enter_code))
                }
            }
        }
    }

    if (showCodeDialog) {
        EnterCodeDialog(
            onDismiss = { showCodeDialog = false },
            onConfirm = { code ->
                showCodeDialog = false
                onJoinByCode(code)
            },
        )
    }
}

@Composable
private fun EnterCodeDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }

    LjAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.group_sync_enter_group_code)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.group_sync_ask_the_leader_for_their_6),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(6) },
                    label = { Text(stringResource(R.string.group_sync_code)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (code.length == 6) onConfirm(code) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            LjTextButton(
                onClick = { onConfirm(code) },
                enabled = code.length == 6,
            ) {
                Text(stringResource(R.string.group_sync_join))
            }
        },
        dismissButton = {
            LjTextButton(onClick = onDismiss) { Text(stringResource(R.string.group_sync_cancel)) }
        },
    )
}

@Composable
private fun LeaderContent(
    groupState: GroupState,
    qrBitmap: Bitmap?,
    followerCount: Int,
    onSetSharingEnabled: (Boolean) -> Unit,
    onLeaveGroup: () -> Unit,
    onRegenerateQr: () -> Unit,
    apiEnabled: Boolean,
    apiKey: String?,
    onSetApiEnabled: (Boolean) -> Unit,
    onRegenerateApiKey: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.group_sync_role_leader),
            style = MaterialTheme.typography.titleMedium,
        )

        val code = groupState.groupId ?: stringResource(R.string.group_sync_unknown)
        LjCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.group_sync_group_code),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = code,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = stringResource(R.string.group_sync_followers_can_scan_the_qr_or),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.group_sync_followers_connected, followerCount, followerCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (qrBitmap != null) {
            LjCard(containerColor = MaterialTheme.colorScheme.surface) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(12.dp),
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.group_sync_group_invite_qr_code_cd),
                        modifier = Modifier.size(200.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.semantics(mergeDescendants = true) {},
                        ) {
                            Icon(LjIcons.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = stringResource(R.string.group_sync_scan_to_join),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = onRegenerateQr) {
                            Icon(LjIcons.Refresh, contentDescription = stringResource(R.string.group_sync_regenerate_qr_cd))
                        }
                    }
                }
            }
        }

        SwitchRow(
            label = stringResource(R.string.group_sync_sharing),
            description = stringResource(R.string.group_sync_sends_your_location_to_followers),
            checked = groupState.sharingEnabled,
            onCheckedChange = onSetSharingEnabled,
        )

        ApiAccessSection(
            host = groupState.leaderHost,
            port = groupState.leaderPort,
            apiEnabled = apiEnabled,
            apiKey = apiKey,
            onSetApiEnabled = onSetApiEnabled,
            onRegenerateApiKey = onRegenerateApiKey,
        )

        Spacer(modifier = Modifier.height(8.dp))

        LjButton(
            onClick = onLeaveGroup,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
        ) {
            Text(stringResource(R.string.group_sync_leave_group))
        }
    }
}

@Composable
private fun FollowerContent(
    groupState: GroupState,
    followerCount: Int,
    onSetFollowerModeEnabled: (Boolean) -> Unit,
    onSetFollowLeaderTeleports: (Boolean) -> Unit,
    onTeleportToLeaderNow: () -> Unit,
    onLeaveGroup: () -> Unit,
    hideTeleportFeatures: Boolean = false,
    leaderPosition: LatLng? = null,
    currentPosition: LatLng? = null,
    cooldownState: CooldownState = CooldownState.Ready,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.group_sync_role_follower),
            style = MaterialTheme.typography.titleMedium,
        )

        Text(
            text =
                stringResource(
                    R.string.group_sync_connected_to_group,
                    groupState.groupId ?: stringResource(R.string.group_sync_unknown),
                ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (followerCount > 0) {
            Text(
                text = pluralStringResource(R.plurals.group_sync_followers_in_group, followerCount, followerCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SwitchRow(
            label = stringResource(R.string.group_sync_follow_leader),
            description = stringResource(R.string.group_sync_walks_toward_the_leaders_location),
            checked = groupState.followerModeEnabled,
            onCheckedChange = onSetFollowerModeEnabled,
        )

        if (!hideTeleportFeatures) {
            SwitchRow(
                label = stringResource(R.string.group_sync_follow_leader_teleports),
                description = stringResource(R.string.group_sync_follow_leader_teleports_description),
                checked = groupState.followLeaderTeleports,
                onCheckedChange = onSetFollowLeaderTeleports,
            )
        }

        Text(
            text = stringResource(R.string.group_sync_follower_mode_resumes_when_you_open),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (groupState.followerModeEnabled && !hideTeleportFeatures) {
            leaderPosition?.let { leaderPos ->
                CooldownAdvisoryBadge(
                    cooldownBadgeText(
                        (cooldownState as? CooldownState.Cooling)?.let { it.remainingSeconds to it.distanceMeters },
                        currentPosition?.let { haversineDistance(it, leaderPos) },
                        rememberCooldownStringLookup(),
                    ),
                )
            }
            LjOutlinedButton(
                onClick = onTeleportToLeaderNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.group_sync_teleport_to_leader_now))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LjButton(
            onClick = onLeaveGroup,
            modifier = Modifier.fillMaxWidth(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
        ) {
            Text(stringResource(R.string.group_sync_leave_group_2))
        }
    }
}

@Composable
private fun SwitchRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    LjCard(modifier = Modifier.fillMaxWidth()) {
        LjSelectionRow(
            mode = LjSelectionMode.Toggle,
            title = label,
            description = description,
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ApiAccessSection(
    host: String?,
    port: Int?,
    apiEnabled: Boolean,
    apiKey: String?,
    onSetApiEnabled: (Boolean) -> Unit,
    onRegenerateApiKey: () -> Unit,
) {
    SwitchRow(
        label = stringResource(R.string.group_sync_control_api),
        description = stringResource(R.string.group_sync_control_api_description),
        checked = apiEnabled,
        onCheckedChange = onSetApiEnabled,
        enabled = host != null && port != null,
    )
    if (apiEnabled) {
        LjCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                ApiCredentialRow(stringResource(R.string.group_sync_api_host), host.orEmpty())
                ApiCredentialRow(stringResource(R.string.group_sync_api_port), port?.toString().orEmpty())
                ApiCredentialRow(stringResource(R.string.group_sync_api_key), apiKey.orEmpty())
                LjTextButton(onClick = onRegenerateApiKey) {
                    Text(stringResource(R.string.group_sync_api_regenerate_key))
                }
                Text(
                    text = stringResource(R.string.group_sync_api_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ApiCredentialRow(
    label: String,
    value: String,
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
        }
        IconButton(onClick = { scope.launch { clipboard.writePlainText(value) } }) {
            Icon(LjIcons.ContentCopy, contentDescription = stringResource(R.string.group_sync_api_copy_cd, label))
        }
    }
}
