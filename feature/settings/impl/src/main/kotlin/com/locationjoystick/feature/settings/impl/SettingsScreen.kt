package com.locationjoystick.feature.settings.impl

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.locationjoystick.core.common.constants.AppConstants
import com.locationjoystick.core.data.FavoriteRepository
import com.locationjoystick.core.data.HotLocation
import com.locationjoystick.core.data.HotRoute
import com.locationjoystick.core.data.RouteRepository
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.AppIcon
import com.locationjoystick.core.designsystem.component.LjCheckboxRow
import com.locationjoystick.core.designsystem.component.LjScaffold
import com.locationjoystick.core.designsystem.component.LjSegmentedControl
import com.locationjoystick.core.model.MapFabFeature
import com.locationjoystick.core.model.RoamingDefaults
import com.locationjoystick.core.model.SpeedUnit
import com.locationjoystick.core.model.WidgetFeature

private enum class SettingsSection { GPS, MENUS, FAVORITES_ROUTES, MAP }

private sealed class PendingImport {
    data class File(
        val uri: android.net.Uri,
    ) : PendingImport()

    data class QrData(
        val data: com.locationjoystick.core.model.ExportData,
    ) : PendingImport()

    data class GpsJoystick(
        val uri: android.net.Uri,
    ) : PendingImport()

    data class Yamla(
        val uri: android.net.Uri,
    ) : PendingImport()
}

@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onOpenDrawer: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val roamingDefaults by viewModel.roamingDefaults.collectAsStateWithLifecycle()
    val isRooted by viewModel.isRooted.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingImport by remember { mutableStateOf<PendingImport?>(null) }
    var showQrShare by remember { mutableStateOf(false) }
    var qrChunkResult by remember { mutableStateOf<QrChunker.ChunkResult?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.userFeedback.collect { feedback ->
            if (feedback.isError) {
                val result =
                    snackbarHostState.showSnackbar(
                        message = feedback.message,
                        actionLabel = "Report",
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    val intent =
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(com.locationjoystick.core.common.constants.AppConstants.AppInfo.GITHUB_ISSUES_URL),
                        )
                    context.startActivity(intent)
                }
            } else {
                snackbarHostState.showSnackbar(
                    message = feedback.message,
                    duration = androidx.compose.material3.SnackbarDuration.Short,
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.qrImportReady.collect { exportData ->
            showQrScanner = false
            pendingImport = PendingImport.QrData(exportData)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.qrChunksReady.collect { result ->
            qrChunkResult = result
            showQrShare = true
        }
    }

    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument(AppConstants.ExportConstants.MIME_TYPE),
        ) { uri ->
            if (uri != null) viewModel.writeExportToUri(uri)
        }

    val importLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingImport = PendingImport.File(uri)
        }

    val importGpsJoystickLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingImport = PendingImport.GpsJoystick(uri)
        }

    val importYamlaLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) pendingImport = PendingImport.Yamla(uri)
        }

    val pending = pendingImport
    if (pending != null) {
        ImportConfirmDialog(
            onReplace = {
                when (pending) {
                    is PendingImport.File -> viewModel.importSettings(pending.uri, replace = true)
                    is PendingImport.QrData -> viewModel.importSettings(pending.data, replace = true)
                    is PendingImport.GpsJoystick -> viewModel.importFromGpsJoystick(pending.uri, replace = true)
                    is PendingImport.Yamla -> viewModel.importFromYamla(pending.uri, replace = true)
                }
                pendingImport = null
            },
            onAdd = {
                when (pending) {
                    is PendingImport.File -> viewModel.importSettings(pending.uri, replace = false)
                    is PendingImport.QrData -> viewModel.importSettings(pending.data, replace = false)
                    is PendingImport.GpsJoystick -> viewModel.importFromGpsJoystick(pending.uri, replace = false)
                    is PendingImport.Yamla -> viewModel.importFromYamla(pending.uri, replace = false)
                }
                pendingImport = null
            },
            onDismiss = { pendingImport = null },
        )
    }

    val qrScanProgress by viewModel.qrScanProgress.collectAsStateWithLifecycle()

    if (showQrScanner) {
        QrScannerScreen(
            onChunkScanned = viewModel::onChunkScanned,
            onPermissionDenied = { showQrScanner = false },
            onNavigateBack = { showQrScanner = false },
            scanProgress = qrScanProgress,
        )
        return
    }

    val result = qrChunkResult
    if (showQrShare && result != null) {
        QrShareDialog(
            chunks = result.chunks,
            skippedRoutes = result.skippedRoutes,
            onDismiss = {
                showQrShare = false
                qrChunkResult = null
            },
        )
    }

    SettingsScreen(
        uiState = uiState,
        roamingDefaults = roamingDefaults,
        isRooted = isRooted,
        hotLocations = viewModel.availableHotLocations,
        hotRoutes = viewModel.availableHotRoutes,
        onOpenDrawer = onOpenDrawer,
        onAction = { action ->
            when (action) {
                is SettingsAction.SetWalkSpeed -> {
                    viewModel.setWalkSpeed(action.displaySpeed)
                }

                is SettingsAction.SetRunSpeed -> {
                    viewModel.setRunSpeed(action.displaySpeed)
                }

                is SettingsAction.SetBikeSpeed -> {
                    viewModel.setBikeSpeed(action.displaySpeed)
                }

                is SettingsAction.SetSpeedUnit -> {
                    viewModel.setSpeedUnit(action.unit)
                }

                is SettingsAction.SetWidgetFeatures -> {
                    viewModel.setWidgetFeatures(action.features)
                }

                is SettingsAction.SetMapFabFeatures -> {
                    viewModel.setMapFabFeatures(action.features)
                }

                is SettingsAction.SetRememberLastLocation -> {
                    viewModel.setRememberLastLocation(action.enabled)
                }

                is SettingsAction.SetMapFollowsLocation -> {
                    viewModel.setMapFollowsLocation(action.enabled)
                }

                is SettingsAction.SetJitterIdleRadius -> {
                    viewModel.setJitterIdleRadius(action.meters)
                }

                is SettingsAction.SetJitterMovingRadius -> {
                    viewModel.setJitterMovingRadius(action.meters)
                }

                is SettingsAction.SetJitterIntervalSeconds -> {
                    viewModel.setJitterIntervalSeconds(action.seconds)
                }

                is SettingsAction.SetJitterIdleIntervalSeconds -> {
                    viewModel.setJitterIdleIntervalSeconds(action.seconds)
                }

                is SettingsAction.SetRealismBearingHoldIdle -> {
                    viewModel.setRealismBearingHoldIdle(action.enabled)
                }

                is SettingsAction.SetRealismAltitudeEnabled -> {
                    viewModel.setRealismAltitudeEnabled(action.enabled)
                }

                is SettingsAction.SetRealismWarmupEnabled -> {
                    viewModel.setRealismWarmupEnabled(action.enabled)
                }

                is SettingsAction.SetRealismSatelliteExtrasEnabled -> {
                    viewModel.setRealismSatelliteExtrasEnabled(action.enabled)
                }

                is SettingsAction.SetRealismSuspendedMockingEnabled -> {
                    viewModel.setRealismSuspendedMockingEnabled(action.enabled)
                }

                is SettingsAction.SetRealismPedometerMockingEnabled -> {
                    viewModel.setRealismPedometerMockingEnabled(action.enabled)
                }

                is SettingsAction.SetJitterSpeedIdleVariationPct -> {
                    viewModel.setJitterSpeedIdleVariationPct(action.pct)
                }

                is SettingsAction.SetJitterSpeedMovingVariationPct -> {
                    viewModel.setJitterSpeedMovingVariationPct(action.pct)
                }

                is SettingsAction.SetElevationTiltJitterDegrees -> {
                    viewModel.setElevationTiltJitterDegrees(action.degrees)
                }

                is SettingsAction.SetElevationNoiseAmplitudeMs2 -> {
                    viewModel.setElevationNoiseAmplitudeMs2(action.amplitude)
                }

                is SettingsAction.SetHotLocationsEnabled -> {
                    viewModel.setHotLocationsEnabled(action.enabled)
                }

                is SettingsAction.SetSelectedHotLocationIds -> {
                    viewModel.setSelectedHotLocationIds(action.ids)
                }

                is SettingsAction.SetHotRoutesEnabled -> {
                    viewModel.setHotRoutesEnabled(action.enabled)
                }

                is SettingsAction.SetSelectedHotRouteIds -> {
                    viewModel.setSelectedHotRouteIds(action.ids)
                }

                is SettingsAction.RequestElevationAccess -> {
                    viewModel.requestElevationAccess()
                }

                is SettingsAction.UpdateRoamingDefaults -> {
                    viewModel.updateRoamingDefaults(action.defaults)
                }

                SettingsAction.Export -> {
                    exportLauncher.launch(
                        "${AppConstants.ExportConstants.FILENAME_PREFIX}-${System.currentTimeMillis()}.json",
                    )
                }

                SettingsAction.Import -> {
                    importLauncher.launch(arrayOf(AppConstants.ExportConstants.MIME_TYPE))
                }

                SettingsAction.ImportGpsJoystick -> {
                    importGpsJoystickLauncher.launch(arrayOf("*/*"))
                }

                SettingsAction.ImportYamla -> {
                    importYamlaLauncher.launch(arrayOf("application/json"))
                }

                SettingsAction.QrShare -> {
                    viewModel.prepareQrChunks()
                }

                SettingsAction.QrScan -> {
                    showQrScanner = true
                }

                SettingsAction.SaveChanges -> {
                    viewModel.saveChanges()
                }

                SettingsAction.DiscardChanges -> {
                    viewModel.discardChanges()
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
    )
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SettingsScreen(
        uiState = SettingsUiState(),
        onOpenDrawer = {},
        onAction = {},
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    roamingDefaults: RoamingDefaults = RoamingDefaults(),
    isRooted: Boolean = false,
    hotLocations: List<HotLocation> = emptyList(),
    hotRoutes: List<HotRoute> = emptyList(),
    onOpenDrawer: () -> Unit = {},
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
) {
    var currentSection by remember { mutableStateOf<SettingsSection?>(null) }

    BackHandler(enabled = currentSection != null) {
        currentSection = null
    }

    when (currentSection) {
        null -> {
            SettingsHubScreen(
                uiState = uiState,
                onOpenDrawer = onOpenDrawer,
                onNavigate = { currentSection = it },
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.GPS -> {
            SettingsGpsSubScreen(
                uiState = uiState,
                isRooted = isRooted,
                onNavigateBack = { currentSection = null },
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.MENUS -> {
            SettingsMenusSubScreen(
                uiState = uiState,
                isRooted = isRooted,
                onNavigateBack = { currentSection = null },
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.FAVORITES_ROUTES -> {
            SettingsFavoritesRoutesSubScreen(
                uiState = uiState,
                roamingDefaults = roamingDefaults,
                hotLocations = hotLocations,
                hotRoutes = hotRoutes,
                onNavigateBack = { currentSection = null },
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }

        SettingsSection.MAP -> {
            SettingsMapSubScreen(
                uiState = uiState,
                onNavigateBack = { currentSection = null },
                onAction = onAction,
                bottomBar = bottomBar,
                snackbarHost = snackbarHost,
            )
        }
    }
}

@Composable
private fun SettingsHubScreen(
    uiState: SettingsUiState,
    onOpenDrawer: () -> Unit,
    onNavigate: (SettingsSection) -> Unit,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Settings",
        onNavigationClick = onOpenDrawer,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = {
            var showDownloadMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showDownloadMenu = true }) {
                    Icon(LjIcons.FileDownload, contentDescription = "Export")
                }
                DropdownMenu(expanded = showDownloadMenu, onDismissRequest = { showDownloadMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Export via QR code") },
                        onClick = {
                            showDownloadMenu = false
                            onAction(SettingsAction.QrShare)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export settings") },
                        onClick = {
                            showDownloadMenu = false
                            onAction(SettingsAction.Export)
                        },
                    )
                }
            }
            var showUploadMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showUploadMenu = true }) {
                    Icon(LjIcons.FileUpload, contentDescription = "Import")
                }
                DropdownMenu(expanded = showUploadMenu, onDismissRequest = { showUploadMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Import from QR code") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.QrScan)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from file") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.Import)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from GPS Joystick") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.ImportGpsJoystick)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Import from YAMLA") },
                        onClick = {
                            showUploadMenu = false
                            onAction(SettingsAction.ImportYamla)
                        },
                    )
                }
            }
            if (uiState.isDirty) {
                TextButton(
                    onClick = { onAction(SettingsAction.DiscardChanges) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                ) { Text("Discard") }
                TextButton(
                    onClick = { onAction(SettingsAction.SaveChanges) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                ) { Text("Save") }
            }
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(remember { ScrollState(0) })
                    .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(40.dp))
            AppIcon()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "locationjoystick",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))
            SettingsDestinationCard(
                icon = LjIcons.LocationOn,
                title = "GPS Settings",
                description = "Speed profiles, GPS signal realism, and location jitter.",
                onClick = { onNavigate(SettingsSection.GPS) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsDestinationCard(
                icon = LjIcons.Joystick,
                title = "Menus",
                description = "Quick-access buttons in the floating widget panel.",
                onClick = { onNavigate(SettingsSection.MENUS) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsDestinationCard(
                icon = LjIcons.Favorite,
                title = "Favorites & Routes",
                description = "Hot locations and default roaming settings.",
                onClick = { onNavigate(SettingsSection.FAVORITES_ROUTES) },
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingsDestinationCard(
                icon = LjIcons.LocationOn,
                title = "Map",
                description = "Choose which buttons appear on the map screen.",
                onClick = { onNavigate(SettingsSection.MAP) },
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsDestinationCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SubScreenActions(
    isDirty: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    if (isDirty) {
        TextButton(
            onClick = { onAction(SettingsAction.DiscardChanges) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) { Text("Discard") }
        TextButton(
            onClick = { onAction(SettingsAction.SaveChanges) },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        ) { Text("Save") }
    }
}

@Composable
private fun SettingsGpsSubScreen(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onNavigateBack: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "GPS Settings",
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = { SubScreenActions(uiState.isDirty, onAction) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    val isMph = uiState.speedUnit == SpeedUnit.MPH
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(16.dp),
                    ) {
                        SpeedProfilesSection(uiState, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsJitterSection(uiState, isMph, onAction)
                        if (WidgetFeature.ELEVATION_CONTROLS in uiState.enabledWidgetFeatures) {
                            Spacer(modifier = Modifier.height(24.dp))
                            ElevationJitterSection(uiState, elevationControlsEnabled = true, onAction)
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        GpsRealismSection(uiState, isRooted, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text("Location Memory", style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Controls how the app handles location state across restarts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LjCheckboxRow(
                            checked = uiState.rememberLastLocation,
                            onCheckedChange = { onAction(SettingsAction.SetRememberLastLocation(it)) },
                            title = "Remember last location",
                            description = "Restores the last spoofed position when the app restarts.",
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsMenusSubScreen(
    uiState: SettingsUiState,
    isRooted: Boolean,
    onNavigateBack: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Menus",
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = { SubScreenActions(uiState.isDirty, onAction) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(16.dp),
                    ) {
                        FloatingWidgetSection(uiState, isRooted, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsFavoritesRoutesSubScreen(
    uiState: SettingsUiState,
    roamingDefaults: RoamingDefaults,
    hotLocations: List<HotLocation>,
    hotRoutes: List<HotRoute>,
    onNavigateBack: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Favorites & Routes",
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = { SubScreenActions(uiState.isDirty, onAction) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    val isMph = uiState.speedUnit == SpeedUnit.MPH
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(16.dp),
                    ) {
                        FavoritesSection(uiState, hotLocations, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        RoutesSection(uiState, hotRoutes, onAction)
                        Spacer(modifier = Modifier.height(24.dp))
                        RoamingSection(roamingDefaults, isMph, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutesSection(
    uiState: SettingsUiState,
    hotRoutes: List<HotRoute>,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Routes", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Options for the routes list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hotRoutesEnabled,
        onCheckedChange = { onAction(SettingsAction.SetHotRoutesEnabled(it)) },
        title = "Show hot routes",
        description = "Adds a curated set of pre-built routes to your routes list. Select which ones to include below.",
    )
    if (uiState.hotRoutesEnabled && hotRoutes.isNotEmpty()) {
        val allIds = remember(hotRoutes) { hotRoutes.map { RouteRepository.idForRoute(it.name, it.city) }.toSet() }
        val selectedIds = uiState.selectedHotRouteIds
        val groupedByCountry =
            remember(hotRoutes) {
                hotRoutes.groupBy { it.country }.mapValues { (_, routes) -> routes.groupBy { it.city } }
            }
        var expandedCountries by remember { mutableStateOf(emptySet<String>()) }
        var expandedCities by remember { mutableStateOf(emptySet<String>()) }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Routes",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onAction(SettingsAction.SetSelectedHotRouteIds(emptySet())) }) {
                Text("Uncheck all")
            }
            TextButton(onClick = { onAction(SettingsAction.SetSelectedHotRouteIds(allIds)) }) {
                Text("Check all")
            }
        }

        groupedByCountry.forEach { (country, citiesMap) ->
            val countryIds =
                citiesMap.values
                    .flatten()
                    .map { RouteRepository.idForRoute(it.name, it.city) }
                    .toSet()
            val selectedInCountry = countryIds.count { it in selectedIds }
            val countryState =
                when {
                    selectedInCountry == countryIds.size -> ToggleableState.On
                    selectedInCountry == 0 -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }
            val isCountryExpanded = country in expandedCountries

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(
                    state = countryState,
                    onClick = {
                        val newIds = selectedIds.toMutableSet()
                        if (countryState == ToggleableState.On) newIds.removeAll(countryIds) else newIds.addAll(countryIds)
                        onAction(SettingsAction.SetSelectedHotRouteIds(newIds))
                    },
                )
                Text(country, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    expandedCountries = if (isCountryExpanded) expandedCountries - country else expandedCountries + country
                }) {
                    Icon(
                        imageVector = if (isCountryExpanded) LjIcons.ElevationUp else LjIcons.ElevationDown,
                        contentDescription = if (isCountryExpanded) "Collapse" else "Expand",
                    )
                }
            }

            if (isCountryExpanded) {
                citiesMap.forEach { (city, routes) ->
                    val cityIds = routes.map { RouteRepository.idForRoute(it.name, it.city) }.toSet()
                    val hasMultiple = routes.size > 1
                    val cityKey = "$country/$city"
                    val isCityExpanded = cityKey in expandedCities
                    val selectedInCity = cityIds.count { it in selectedIds }
                    val cityState =
                        when {
                            selectedInCity == cityIds.size -> ToggleableState.On
                            selectedInCity == 0 -> ToggleableState.Off
                            else -> ToggleableState.Indeterminate
                        }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(
                            state = cityState,
                            onClick = {
                                val newIds = selectedIds.toMutableSet()
                                if (cityState == ToggleableState.On) newIds.removeAll(cityIds) else newIds.addAll(cityIds)
                                onAction(SettingsAction.SetSelectedHotRouteIds(newIds))
                            },
                        )
                        Text(city, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (hasMultiple) {
                            IconButton(onClick = {
                                expandedCities = if (isCityExpanded) expandedCities - cityKey else expandedCities + cityKey
                            }) {
                                Icon(
                                    imageVector = if (isCityExpanded) LjIcons.ElevationUp else LjIcons.ElevationDown,
                                    contentDescription = if (isCityExpanded) "Collapse" else "Expand",
                                )
                            }
                        }
                    }

                    if (hasMultiple && isCityExpanded) {
                        routes.forEach { route ->
                            val routeId = RouteRepository.idForRoute(route.name, route.city)
                            LjCheckboxRow(
                                checked = routeId in selectedIds,
                                onCheckedChange = { isChecked ->
                                    val newIds = selectedIds.toMutableSet()
                                    if (isChecked) newIds.add(routeId) else newIds.remove(routeId)
                                    onAction(SettingsAction.SetSelectedHotRouteIds(newIds))
                                },
                                title = route.name,
                                modifier = Modifier.padding(start = 48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedProfileInput(
    label: String,
    displaySpeed: Double,
    onSpeedChange: (Double) -> Unit,
    unit: String,
) {
    var localValue by remember { mutableStateOf(String.format("%.1f", displaySpeed)) }
    var lastSentValue by remember { mutableStateOf(displaySpeed) }

    LaunchedEffect(displaySpeed) {
        if (displaySpeed != lastSentValue) {
            localValue = String.format("%.1f", displaySpeed)
            lastSentValue = displaySpeed
        }
    }

    val parsedValue = localValue.toDoubleOrNull()
    val isValid = parsedValue != null && parsedValue > 0.0

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.2f))

        Row(
            modifier = Modifier.weight(0.8f).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = localValue,
                onValueChange = { newValue ->
                    localValue = newValue
                    val parsed = newValue.toDoubleOrNull()
                    if (parsed != null && parsed > 0.0) {
                        onSpeedChange(parsed)
                        lastSentValue = parsed
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = localValue.isNotBlank() && !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(unit, modifier = Modifier.width(40.dp))
        }
    }
}

@Composable
private fun AntiCheatWarning() {
    Text(
        text = "Speed exceeds 8 m/s — may trigger anti-cheat in some games",
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

private fun convertMsToDisplay(
    ms: Double,
    unit: SpeedUnit,
): Double =
    when (unit) {
        SpeedUnit.KMH -> ms * 3.6
        SpeedUnit.MPH -> ms * 2.237
    }

@Composable
private fun SpeedProfilesSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Speed Profiles", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Movement speed used by the joystick, route replay, and roaming. Select a unit, then set each preset.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Unit", modifier = Modifier.weight(0.3f))
        LjSegmentedControl(
            options = listOf(SpeedUnit.KMH to "km/h", SpeedUnit.MPH to "mph"),
            selected = uiState.speedUnit,
            onSelect = { onAction(SettingsAction.SetSpeedUnit(it)) },
            modifier = Modifier.weight(0.7f),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))

    SpeedProfileInput(
        label = "Walk",
        displaySpeed = convertMsToDisplay(uiState.walkSpeed, uiState.speedUnit),
        onSpeedChange = { onAction(SettingsAction.SetWalkSpeed(it)) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.walkSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) AntiCheatWarning()

    Spacer(modifier = Modifier.height(8.dp))

    SpeedProfileInput(
        label = "Run",
        displaySpeed = convertMsToDisplay(uiState.runSpeed, uiState.speedUnit),
        onSpeedChange = { onAction(SettingsAction.SetRunSpeed(it)) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.runSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) AntiCheatWarning()

    Spacer(modifier = Modifier.height(8.dp))

    SpeedProfileInput(
        label = "Bike",
        displaySpeed = convertMsToDisplay(uiState.bikeSpeed, uiState.speedUnit),
        onSpeedChange = { onAction(SettingsAction.SetBikeSpeed(it)) },
        unit = if (uiState.speedUnit == SpeedUnit.KMH) "km/h" else "mph",
    )
    if (uiState.bikeSpeed > AppConstants.ProfileConstants.ANTI_CHEAT_WARNING_THRESHOLD_MS) AntiCheatWarning()
}

@Composable
private fun FavoritesSection(
    uiState: SettingsUiState,
    hotLocations: List<HotLocation>,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Favorites", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Options for the favorites list.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))
    LjCheckboxRow(
        checked = uiState.hotLocationsEnabled,
        onCheckedChange = { onAction(SettingsAction.SetHotLocationsEnabled(it)) },
        title = "Show hot locations",
        description = "Adds a curated list of popular locations to your favorites. Select which ones to include below.",
    )
    if (uiState.hotLocationsEnabled && hotLocations.isNotEmpty()) {
        val allIds = remember(hotLocations) { hotLocations.map { FavoriteRepository.idForLocation(it.name, it.city) }.toSet() }
        val selectedIds = uiState.selectedHotLocationIds
        val groupedByCountry =
            remember(hotLocations) {
                hotLocations.groupBy { it.country }.mapValues { (_, locs) -> locs.groupBy { it.city } }
            }
        var expandedCountries by remember { mutableStateOf(emptySet<String>()) }
        var expandedCities by remember { mutableStateOf(emptySet<String>()) }

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Locations",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onAction(SettingsAction.SetSelectedHotLocationIds(emptySet())) }) {
                Text("Uncheck all")
            }
            TextButton(onClick = { onAction(SettingsAction.SetSelectedHotLocationIds(allIds)) }) {
                Text("Check all")
            }
        }

        groupedByCountry.forEach { (country, citiesMap) ->
            val countryIds =
                citiesMap.values
                    .flatten()
                    .map { FavoriteRepository.idForLocation(it.name, it.city) }
                    .toSet()
            val selectedInCountry = countryIds.count { it in selectedIds }
            val countryState =
                when {
                    selectedInCountry == countryIds.size -> ToggleableState.On
                    selectedInCountry == 0 -> ToggleableState.Off
                    else -> ToggleableState.Indeterminate
                }
            val isCountryExpanded = country in expandedCountries

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TriStateCheckbox(
                    state = countryState,
                    onClick = {
                        val newIds = selectedIds.toMutableSet()
                        if (countryState == ToggleableState.On) newIds.removeAll(countryIds) else newIds.addAll(countryIds)
                        onAction(SettingsAction.SetSelectedHotLocationIds(newIds))
                    },
                )
                Text(country, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    expandedCountries = if (isCountryExpanded) expandedCountries - country else expandedCountries + country
                }) {
                    Icon(
                        imageVector = if (isCountryExpanded) LjIcons.ElevationUp else LjIcons.ElevationDown,
                        contentDescription = if (isCountryExpanded) "Collapse" else "Expand",
                    )
                }
            }

            if (isCountryExpanded) {
                citiesMap.forEach { (city, locations) ->
                    val cityIds = locations.map { FavoriteRepository.idForLocation(it.name, it.city) }.toSet()
                    val hasMultiple = locations.size > 1
                    val cityKey = "$country/$city"
                    val isCityExpanded = cityKey in expandedCities
                    val selectedInCity = cityIds.count { it in selectedIds }
                    val cityState =
                        when {
                            selectedInCity == cityIds.size -> ToggleableState.On
                            selectedInCity == 0 -> ToggleableState.Off
                            else -> ToggleableState.Indeterminate
                        }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TriStateCheckbox(
                            state = cityState,
                            onClick = {
                                val newIds = selectedIds.toMutableSet()
                                if (cityState == ToggleableState.On) newIds.removeAll(cityIds) else newIds.addAll(cityIds)
                                onAction(SettingsAction.SetSelectedHotLocationIds(newIds))
                            },
                        )
                        Text(city, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (hasMultiple) {
                            IconButton(onClick = {
                                expandedCities = if (isCityExpanded) expandedCities - cityKey else expandedCities + cityKey
                            }) {
                                Icon(
                                    imageVector = if (isCityExpanded) LjIcons.ElevationUp else LjIcons.ElevationDown,
                                    contentDescription = if (isCityExpanded) "Collapse" else "Expand",
                                )
                            }
                        }
                    }

                    if (hasMultiple && isCityExpanded) {
                        locations.forEach { location ->
                            val locId = FavoriteRepository.idForLocation(location.name, location.city)
                            LjCheckboxRow(
                                checked = locId in selectedIds,
                                onCheckedChange = { isChecked ->
                                    val newIds = selectedIds.toMutableSet()
                                    if (isChecked) newIds.add(locId) else newIds.remove(locId)
                                    onAction(SettingsAction.SetSelectedHotLocationIds(newIds))
                                },
                                title = location.name,
                                modifier = Modifier.padding(start = 48.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetFeatureRow(
    feature: WidgetFeature,
    label: String,
    enabledFeatures: Set<WidgetFeature>,
    onAction: (SettingsAction) -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null,
    subtitleColor: androidx.compose.ui.graphics.Color? = null,
    icon: ImageVector? = null,
    onEnabled: (() -> Unit)? = null,
) {
    LjCheckboxRow(
        checked = feature in enabledFeatures,
        onCheckedChange = { isChecked ->
            if (enabled) {
                val updated = enabledFeatures.toMutableSet()
                if (isChecked) {
                    updated.add(feature)
                    onEnabled?.invoke()
                } else {
                    updated.remove(feature)
                }
                onAction(SettingsAction.SetWidgetFeatures(updated))
            }
        },
        title = label,
        description = subtitle,
        enabled = enabled,
        descriptionColor = subtitleColor,
        icon = icon,
    )
}

@Composable
private fun FloatingWidgetSection(
    uiState: SettingsUiState,
    isRooted: Boolean = false,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Floating Widget", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Choose which quick-access buttons appear in the floating widget overlay.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    WidgetFeatureRow(
        feature = WidgetFeature.MAP_FLOATING,
        label = "Map shortcut",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.LocationOn,
        subtitle = "Opens a compact map view without switching to the main app.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.JOYSTICK_TOGGLE,
        label = "Show/hide joystick",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Visibility,
        subtitle = "Toggles the floating joystick overlay on or off.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.JOYSTICK_LOCK,
        label = "Lock joystick",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Lock,
        subtitle = "Keeps the joystick moving in the last held direction after you release.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.ROUTES_FLOATING,
        label = "Routes picker",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Route,
        subtitle = "Lists saved routes and starts replay without opening the app.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.FAVORITES_FLOATING,
        label = "Favorites picker",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Favorite,
        subtitle = "Lists favorite locations with one-tap teleport and walk shortcuts.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.SPEED_CYCLE,
        label = "Speed cycle",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Speed,
        subtitle = "Cycles through Walk, Run, and Bike speed profiles with a single tap.",
    )
    WidgetFeatureRow(
        feature = WidgetFeature.ELEVATION_CONTROLS,
        label = "Elevation controls",
        enabledFeatures = uiState.enabledWidgetFeatures,
        onAction = onAction,
        icon = LjIcons.Layers,
        subtitle = "Shows a floating overlay to tilt the simulated sensor angle · requires root",
        subtitleColor = MaterialTheme.colorScheme.error,
        onEnabled = { onAction(SettingsAction.RequestElevationAccess) },
    )
}

@Composable
private fun RoamingSection(
    roamingDefaults: RoamingDefaults,
    isMph: Boolean,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Roaming", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Default settings used when starting a roaming session from the map.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    var radiusText by remember(isMph) {
        mutableStateOf(
            if (isMph) {
                String.format("%.2f", roamingDefaults.radiusMeters / 1609.344)
            } else {
                roamingDefaults.radiusMeters.toInt().toString()
            },
        )
    }
    OutlinedTextField(
        value = radiusText,
        onValueChange = { text ->
            radiusText = text
            text.toDoubleOrNull()?.let { v ->
                val meters = if (isMph) v * 1609.344 else v
                onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(radiusMeters = meters.coerceIn(1_000.0, 100_000.0))))
            }
        },
        label = { Text(if (isMph) "Radius (mi)" else "Radius (m)") },
        keyboardOptions = KeyboardOptions(keyboardType = if (isMph) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(4.dp))

    var distanceText by remember(isMph) {
        mutableStateOf(
            if (isMph) {
                String.format("%.2f", roamingDefaults.distanceMeters / 1609.344)
            } else {
                roamingDefaults.distanceMeters.toInt().toString()
            },
        )
    }
    OutlinedTextField(
        value = distanceText,
        onValueChange = { text ->
            distanceText = text
            text.toDoubleOrNull()?.let { v ->
                val meters = if (isMph) v * 1609.344 else v
                onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(distanceMeters = meters.coerceIn(50.0, 50_000.0))))
            }
        },
        label = { Text(if (isMph) "Route distance (mi)" else "Route distance (m)") },
        keyboardOptions = KeyboardOptions(keyboardType = if (isMph) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text("Speed profile", style = MaterialTheme.typography.labelLarge)
    Spacer(modifier = Modifier.height(4.dp))
    LjSegmentedControl(
        options = listOf("walk" to "Walk", "run" to "Run", "bike" to "Bike"),
        selected = roamingDefaults.speedProfileId,
        onSelect = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(speedProfileId = it))) },
        modifier = Modifier.fillMaxWidth(),
    )

    LjCheckboxRow(
        checked = roamingDefaults.followRoads,
        onCheckedChange = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(followRoads = it))) },
        title = "Follow roads",
        description = "Uses OSRM road routing to stay on walkable paths. Falls back to straight-line if unavailable.",
    )
    LjCheckboxRow(
        checked = roamingDefaults.returnToInitialLocation,
        onCheckedChange = { onAction(SettingsAction.UpdateRoamingDefaults(roamingDefaults.copy(returnToInitialLocation = it))) },
        title = "Return to start",
        description = "Walks back to the starting position after the roaming session completes.",
    )
}

@Composable
private fun SettingsMapSubScreen(
    uiState: SettingsUiState,
    onNavigateBack: () -> Unit,
    onAction: (SettingsAction) -> Unit,
    bottomBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
) {
    LjScaffold(
        title = "Map",
        onNavigationClick = onNavigateBack,
        navigationIcon = LjIcons.ArrowBack,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        actions = { SubScreenActions(uiState.isDirty, onAction) },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                else -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(remember { ScrollState(0) })
                                .padding(16.dp),
                    ) {
                        MapButtonsSection(uiState, onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun MapButtonsSection(
    uiState: SettingsUiState,
    onAction: (SettingsAction) -> Unit,
) {
    Text("Map Buttons", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        "Choose which buttons appear in the map screen. Start/stop simulation is always shown.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(8.dp))

    val enabled = uiState.enabledMapFabFeatures
    MapFabFeatureRow(
        feature = MapFabFeature.FAVORITES,
        label = "Favorites",
        subtitle = "Opens the favorites picker to teleport or walk to a saved location.",
        icon = LjIcons.Favorite,
        enabledFeatures = enabled,
        onAction = onAction,
    )
    MapFabFeatureRow(
        feature = MapFabFeature.ROUTES,
        label = "Routes",
        subtitle = "Opens the routes picker to start or manage route replay.",
        icon = LjIcons.Route,
        enabledFeatures = enabled,
        onAction = onAction,
    )
    MapFabFeatureRow(
        feature = MapFabFeature.ROAMING,
        label = "Roaming",
        subtitle = "Opens the roaming sheet to configure and start random walking.",
        icon = LjIcons.Explore,
        enabledFeatures = enabled,
        onAction = onAction,
    )
    MapFabFeatureRow(
        feature = MapFabFeature.SEARCH,
        label = "Search",
        subtitle = "Opens the location search bar to find and jump to a place.",
        icon = LjIcons.Search,
        enabledFeatures = enabled,
        onAction = onAction,
    )
}

@Composable
private fun MapFabFeatureRow(
    feature: MapFabFeature,
    label: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabledFeatures: Set<MapFabFeature>,
    onAction: (SettingsAction) -> Unit,
) {
    LjCheckboxRow(
        checked = feature in enabledFeatures,
        onCheckedChange = { isChecked ->
            val updated = enabledFeatures.toMutableSet()
            if (isChecked) updated.add(feature) else updated.remove(feature)
            onAction(SettingsAction.SetMapFabFeatures(updated))
        },
        title = label,
        description = subtitle,
        icon = icon,
    )
}

@Composable
private fun ImportConfirmDialog(
    onReplace: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import data") },
        text = { Text("How would you like to handle existing data?") },
        confirmButton = {
            Row {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onAdd) { Text("Add") }
                TextButton(onClick = onReplace) { Text("Replace") }
            }
        },
        dismissButton = {},
    )
}
