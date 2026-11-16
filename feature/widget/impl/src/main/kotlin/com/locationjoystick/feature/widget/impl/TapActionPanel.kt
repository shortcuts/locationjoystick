package com.locationjoystick.feature.widget.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.onClick
import com.locationjoystick.core.common.util.copyCoordinatesToClipboard
import com.locationjoystick.core.common.util.shareLocationLink
import com.locationjoystick.core.data.CooldownState
import com.locationjoystick.core.data.advisoryPair
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.component.LjButton
import com.locationjoystick.core.designsystem.component.LjOutlinedButton
import com.locationjoystick.core.designsystem.component.LjShareLocationAction
import com.locationjoystick.core.designsystem.component.LjTextButton
import com.locationjoystick.core.designsystem.component.TeleportCooldownBadge
import com.locationjoystick.core.model.LatLng
import com.locationjoystick.core.model.TapToWalkMove
import com.locationjoystick.core.model.effectiveTapToWalkMoves
import com.locationjoystick.feature.widget.impl.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Composable
internal fun MoveButtons(
    moves: Collection<TapToWalkMove>,
    onMove: (TapToWalkMove) -> Unit,
) {
    val ordered = listOf(TapToWalkMove.TELEPORT, TapToWalkMove.WALK, TapToWalkMove.VIA_ROADS).filter { it in moves }
    Column {
        ordered.forEachIndexed { i, move ->
            if (i > 0) Spacer(Modifier.height(LjSpacing.sm))
            val label =
                stringResource(
                    when (move) {
                        TapToWalkMove.TELEPORT -> R.string.map_floating_teleport_here
                        TapToWalkMove.WALK -> R.string.map_floating_walk_here
                        TapToWalkMove.VIA_ROADS -> R.string.map_floating_walk_here_via_roads
                    },
                )
            if (move == TapToWalkMove.TELEPORT) {
                LjButton(onClick = { onMove(move) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            } else {
                LjOutlinedButton(onClick = { onMove(move) }, modifier = Modifier.fillMaxWidth()) { Text(label) }
            }
        }
    }
}

@Composable
internal fun BoxScope.TapActionPanel(
    tap: LatLng,
    isRouteReplay: Boolean,
    isEphemeralReplay: Boolean,
    isWalkActive: Boolean,
    walkStart: LatLng?,
    walkTarget: LatLng?,
    routeWaypoints: List<LatLng>?,
    cooldownForPosition: ((LatLng) -> Flow<CooldownState>)?,
    onTeleport: (LatLng) -> Unit,
    onWalkTo: (LatLng) -> Unit,
    onWalkViaRoads: (LatLng) -> Unit,
    onStopRouteAndTeleport: (LatLng) -> Unit,
    onStopRouteAndWalkTo: (LatLng) -> Unit,
    onFinishRouteAndWalkTo: (LatLng) -> Unit,
    onAddEphemeralWaypoint: (LatLng, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onShareOpened: () -> Unit,
    hideTeleportFeatures: Boolean = false,
) {
    val context = LocalContext.current
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable {}
                .padding(LjSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    if (isRouteReplay && !isEphemeralReplay) {
                        R.string.map_floating_route_in_progress
                    } else {
                        R.string.map_floating_move_to_this_location
                    },
                ),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            LjShareLocationAction(
                onCopyCoordinates = { copyCoordinatesToClipboard(context, tap) },
                onShareLink = {
                    shareLocationLink(context, tap)
                    onShareOpened()
                },
            )
        }
        if (isRouteReplay && !isEphemeralReplay) {
            Spacer(Modifier.height(LjSpacing.md))
            if (!hideTeleportFeatures) {
                LjButton(
                    onClick = {
                        onStopRouteAndTeleport(tap)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.map_floating_stop_route_and_teleport)) }
                Spacer(Modifier.height(LjSpacing.sm))
            }
            LjOutlinedButton(
                onClick = {
                    onStopRouteAndWalkTo(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.map_floating_stop_route_and_walk_here)) }
            Spacer(Modifier.height(LjSpacing.sm))
            LjOutlinedButton(
                onClick = {
                    onFinishRouteAndWalkTo(tap)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.map_floating_finish_route_and_walk_here)) }
        } else {
            Spacer(Modifier.height(LjSpacing.sm))
            TeleportCooldownBadge(tap, cooldownForPosition.asAdvisory())
            Spacer(Modifier.height(LjSpacing.md))
            MoveButtons(effectiveTapToWalkMoves(TapToWalkMove.entries.toSet(), hideTeleportFeatures)) { move ->
                when (move) {
                    TapToWalkMove.TELEPORT -> onTeleport(tap)
                    TapToWalkMove.WALK -> onWalkTo(tap)
                    TapToWalkMove.VIA_ROADS -> onWalkViaRoads(tap)
                }
                onDismiss()
            }
            if (isWalkActive) {
                Spacer(Modifier.height(LjSpacing.sm))
                LjOutlinedButton(
                    onClick = {
                        onAddEphemeralWaypoint(tap, false)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.map_floating_add_next_point)) }
                Spacer(Modifier.height(LjSpacing.sm))
                LjOutlinedButton(
                    onClick = {
                        onAddEphemeralWaypoint(tap, true)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.map_floating_add_next_point_via_roads)) }
            }
        }
        Spacer(Modifier.height(LjSpacing.xs))
        LjTextButton(
            onClick = { onDismiss() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.map_floating_do_nothing)) }
    }
}

internal fun ((LatLng) -> Flow<CooldownState>)?.asAdvisory(): ((LatLng) -> Flow<Pair<Long, Double>?>)? =
    this?.let { source -> { pos -> source(pos).map { it.advisoryPair() } } }
