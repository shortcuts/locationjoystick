package com.locationjoystick.feature.routes.impl

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.data.SettingsRepository
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjListItemCard
import com.locationjoystick.core.designsystem.component.LjOverflowMenu
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.speedProfileLabel
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.RouteType
import com.locationjoystick.core.model.SpeedProfile
import com.locationjoystick.feature.routes.impl.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteDetailViewModel
    @Inject
    constructor(
        private val routeRepository: RouteRepository,
        private val settingsRepository: SettingsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        companion object {
            private const val TAG = "RouteDetailViewModel"
        }

        private val routeId: String = checkNotNull(savedStateHandle["routeId"])

        val route: StateFlow<Route?> =
            routeRepository.getRouteWithWaypoints(routeId).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )

        val speedProfiles: StateFlow<List<SpeedProfile>> =
            settingsRepository.getSpeedProfiles().stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

        private val _nameError = MutableStateFlow(false)
        val nameError: StateFlow<Boolean> = _nameError.asStateFlow()

        fun removeWaypoint(waypointId: String) {
            viewModelScope.launch {
                routeRepository.removeWaypoint(waypointId)
            }
        }

        fun deleteRoute() {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    routeRepository.deleteRoute(routeId)
                } catch (e: Exception) {
                    Log.e(TAG, "delete failed", e)
                }
            }
        }

        suspend fun renameRoute(name: String) {
            if (name.isBlank()) {
                _nameError.value = true
                return
            }
            _nameError.value = false
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    routeRepository.renameRoute(routeId, name)
                } catch (e: Exception) {
                    Log.e(TAG, "rename failed", e)
                    _nameError.value = true
                }
            }
        }

        fun setSpeedProfile(speedProfileId: String?) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    routeRepository.setRouteSpeedProfile(routeId, speedProfileId)
                } catch (e: Exception) {
                    Log.e(TAG, "set speed profile failed", e)
                }
            }
        }

        fun setWaypointWaitSeconds(
            waypointId: String,
            waitSeconds: Int,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    routeRepository.setWaypointWaitSeconds(waypointId, waitSeconds)
                } catch (e: Exception) {
                    Log.e(TAG, "set waypoint wait seconds failed", e)
                }
            }
        }

        fun setAllWaypointsWaitSeconds(waitSeconds: Int) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    routeRepository.setAllWaypointsWaitSeconds(routeId, waitSeconds)
                } catch (e: Exception) {
                    Log.e(TAG, "set all waypoints wait seconds failed", e)
                }
            }
        }

        fun setRandomizeTeleportOrder(randomize: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    routeRepository.setRandomizeTeleportOrder(routeId, randomize)
                } catch (e: Exception) {
                    Log.e(TAG, "set randomize teleport order failed", e)
                }
            }
        }
    }

@Preview(showBackground = true)
@Composable
private fun RouteDetailScreenPreview() {
    RouteDetailScreen(
        routeId = "preview",
        onNavigateBack = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    routeId: String,
    onNavigateBack: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: RouteDetailViewModel = hiltViewModel(),
) {
    val route by viewModel.route.collectAsStateWithLifecycle()
    val nameError by viewModel.nameError.collectAsStateWithLifecycle()
    val speedProfiles by viewModel.speedProfiles.collectAsStateWithLifecycle()
    val spoofToggle = rememberSpoofToggleState()
    val coroutineScope = rememberCoroutineScope()

    var editedName by remember { mutableStateOf("") }
    var isNameInitialized by remember { mutableStateOf(false) }
    var editingWaypointId by remember { mutableStateOf<String?>(null) }
    var isSettingAllWait by remember { mutableStateOf(false) }

    LaunchedEffect(route) {
        if (route != null && !isNameInitialized) {
            editedName = route!!.name
            isNameInitialized = true
        }
    }

    BackHandler {
        if (route?.waypoints?.isEmpty() == true) {
            coroutineScope.launch {
                viewModel.deleteRoute()
                onNavigateBack()
            }
        } else {
            onNavigateBack()
        }
    }

    LjScaffold(
        title = stringResource(R.string.route_detail_route_details),
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        actions = {
            if (route != null && editedName != route!!.name) {
                LjOverflowMenu { dismiss ->
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.route_detail_discard)) },
                        onClick = {
                            dismiss()
                            editedName = route!!.name
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.route_detail_save)) },
                        onClick = {
                            dismiss()
                            coroutineScope.launch {
                                if (route?.waypoints?.isEmpty() == true) {
                                    viewModel.deleteRoute()
                                } else if (editedName.isNotBlank()) {
                                    viewModel.renameRoute(editedName)
                                }
                                onNavigateBack()
                            }
                        },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (route == null) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                val isTeleportRoute = route!!.routeType == RouteType.TELEPORT
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    // Editable name field
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = { editedName = it },
                                label = { Text(stringResource(R.string.route_detail_route_name)) },
                                isError = nameError,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            if (nameError) {
                                Text(
                                    stringResource(R.string.route_detail_name_cannot_be_empty),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                )
                            }
                        }
                    }

                    // Speed profile selection — hidden for teleport routes, which never read speedProfileId
                    if (!isTeleportRoute) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    stringResource(R.string.route_detail_speed_profile),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))

                                val options = listOf<SpeedProfile?>(null) + speedProfiles
                                var expanded by remember { mutableStateOf(false) }
                                val noneLabel = stringResource(R.string.route_detail_speed_profile_none)
                                val selectedLabel =
                                    speedProfiles.find { it.id == route!!.speedProfileId }?.let { speedProfileLabel(it.id) }
                                        ?: noneLabel

                                ExposedDropdownMenuBox(
                                    expanded = expanded,
                                    onExpandedChange = { expanded = it },
                                ) {
                                    OutlinedTextField(
                                        value = selectedLabel,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                                    )
                                    ExposedDropdownMenu(
                                        expanded = expanded,
                                        onDismissRequest = { expanded = false },
                                    ) {
                                        options.forEach { profile ->
                                            DropdownMenuItem(
                                                text = { Text(profile?.let { speedProfileLabel(it.id) } ?: noneLabel) },
                                                onClick = {
                                                    viewModel.setSpeedProfile(profile?.id)
                                                    expanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Randomize order + mass wait-time edit — teleport routes only
                    if (isTeleportRoute) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.route_detail_randomize_order),
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                Switch(
                                    checked = route!!.randomizeTeleportOrder,
                                    onCheckedChange = { viewModel.setRandomizeTeleportOrder(it) },
                                )
                            }
                            Text(
                                stringResource(R.string.route_detail_randomize_order_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = { isSettingAllWait = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(LjIcons.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.route_detail_set_all_wait_cd))
                            }
                        }
                    }

                    item {
                        Text(
                            stringResource(R.string.route_detail_waypoints),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Numbered waypoints
                    items(route!!.waypoints, key = { it.id }) { waypoint ->
                        LjListItemCard(
                            trailing = {
                                if (isTeleportRoute) {
                                    IconButton(onClick = { editingWaypointId = waypoint.id }) {
                                        Icon(
                                            LjIcons.Edit,
                                            contentDescription = stringResource(R.string.route_detail_edit_wait_cd),
                                        )
                                    }
                                }
                                IconButton(onClick = { viewModel.removeWaypoint(waypoint.id) }) {
                                    Icon(
                                        LjIcons.Delete,
                                        contentDescription = stringResource(R.string.route_detail_remove_waypoint_cd),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        ) {
                            Text(
                                stringResource(R.string.route_detail_waypoint_number, waypoint.orderIndex + 1),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                "${String.format("%.4f", waypoint.position.latitude)}, " +
                                    "${String.format("%.4f", waypoint.position.longitude)}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (isTeleportRoute) {
                                Text(
                                    stringResource(R.string.route_detail_wait_seconds, waypoint.waitSeconds),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (isSettingAllWait) {
        TeleportWaitDialog(
            titleRes = R.string.route_detail_set_all_wait_title,
            onDismiss = { isSettingAllWait = false },
            onConfirm = { seconds ->
                viewModel.setAllWaypointsWaitSeconds(seconds)
                isSettingAllWait = false
            },
        )
    }

    editingWaypointId?.let { waypointId ->
        val waypoint = route?.waypoints?.find { it.id == waypointId }
        if (waypoint != null) {
            TeleportWaitDialog(
                initialSeconds = waypoint.waitSeconds,
                onDismiss = { editingWaypointId = null },
                onConfirm = { seconds ->
                    viewModel.setWaypointWaitSeconds(waypointId, seconds)
                    editingWaypointId = null
                },
            )
        }
    }
}
