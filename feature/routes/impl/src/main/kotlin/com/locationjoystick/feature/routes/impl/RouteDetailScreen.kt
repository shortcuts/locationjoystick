package com.locationjoystick.feature.routes.impl

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.locationjoystick.core.location.rememberSpoofToggleState
import com.locationjoystick.core.model.Route
import com.locationjoystick.core.model.SpeedProfile
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
        title = "Route Details",
        isSpoofing = spoofToggle.isSpoofing,
        onToggleSpoofing = spoofToggle.onToggle,
        locationLabel = spoofToggle.locationLabel,
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        actions = {
            if (route != null && editedName != route!!.name) {
                LjOverflowMenu { dismiss ->
                    DropdownMenuItem(
                        text = { Text("Discard") },
                        onClick = {
                            dismiss()
                            editedName = route!!.name
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Save") },
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
                                label = { Text("Route name") },
                                isError = nameError,
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                            if (nameError) {
                                Text(
                                    "Name cannot be empty",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                                )
                            }
                        }
                    }

                    // Speed profile selection
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Speed profile",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                            // Segmented row is wrapped in a scrollable Row *without* fillMaxWidth —
                            // combining fillMaxWidth with horizontalScroll forces the row's
                            // constrained width onto its children instead of letting them size
                            // naturally, which squeezed/clipped the buttons instead of scrolling.
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            ) {
                                val options = listOf<SpeedProfile?>(null) + speedProfiles
                                options.forEachIndexed { index, profile ->
                                    SegmentedButton(
                                        selected = route!!.speedProfileId == profile?.id,
                                        onClick = { viewModel.setSpeedProfile(profile?.id) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                                    ) {
                                        Text(profile?.name ?: "None")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Text(
                            "Waypoints",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Numbered waypoints
                    items(route!!.waypoints, key = { it.id }) { waypoint ->
                        LjListItemCard(
                            trailing = {
                                IconButton(onClick = { viewModel.removeWaypoint(waypoint.id) }) {
                                    Icon(
                                        LjIcons.Delete,
                                        contentDescription = "Remove waypoint",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                        ) {
                            Text(
                                "Waypoint ${waypoint.orderIndex + 1}",
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
                        }
                    }
                }
            }
        }
    }
}
