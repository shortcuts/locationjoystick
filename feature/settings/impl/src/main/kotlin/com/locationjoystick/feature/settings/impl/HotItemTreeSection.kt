package com.locationjoystick.feature.settings.impl

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.component.LjCheckboxRow

/**
 * Generic collapsible country → city → item tree for selecting subsets of hot items.
 *
 * Used by both FavoritesSection (hot locations) and RoutesSection (hot routes).
 * All ID computation and grouping happens upstream in [SettingsViewModel]; this composable
 * is purely presentational.
 */
@Composable
internal fun HotItemTreeSection(
    headerLabel: String,
    tree: HotItemTree,
    selectedIds: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
) {
    var expandedCountries by remember { mutableStateOf(emptySet<String>()) }
    var expandedCities by remember { mutableStateOf(emptySet<String>()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(headerLabel, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = { onSelectionChange(emptySet()) }) { Text("Uncheck all") }
        TextButton(onClick = { onSelectionChange(tree.allIds) }) { Text("Check all") }
    }

    tree.byCountry.forEach { (country, citiesMap) ->
        val countryIds =
            citiesMap.values
                .flatten()
                .map { it.id }
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
                    val updated = selectedIds.toMutableSet()
                    if (countryState == ToggleableState.On) updated.removeAll(countryIds) else updated.addAll(countryIds)
                    onSelectionChange(updated)
                },
            )
            Text(country, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                expandedCountries = if (isCountryExpanded) expandedCountries - country else expandedCountries + country
            }) {
                Icon(
                    imageVector = if (isCountryExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (isCountryExpanded) "Collapse" else "Expand",
                )
            }
        }

        if (isCountryExpanded) {
            citiesMap.forEach { (city, items) ->
                val cityIds = items.map { it.id }.toSet()
                val hasMultiple = items.size > 1
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
                            val updated = selectedIds.toMutableSet()
                            if (cityState == ToggleableState.On) updated.removeAll(cityIds) else updated.addAll(cityIds)
                            onSelectionChange(updated)
                        },
                    )
                    Text(city, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (hasMultiple) {
                        IconButton(onClick = {
                            expandedCities = if (isCityExpanded) expandedCities - cityKey else expandedCities + cityKey
                        }) {
                            Icon(
                                imageVector = if (isCityExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = if (isCityExpanded) "Collapse" else "Expand",
                            )
                        }
                    }
                }

                if (hasMultiple && isCityExpanded) {
                    items.forEach { entry ->
                        LjCheckboxRow(
                            checked = entry.id in selectedIds,
                            onCheckedChange = { isChecked ->
                                val updated = selectedIds.toMutableSet()
                                if (isChecked) updated.add(entry.id) else updated.remove(entry.id)
                                onSelectionChange(updated)
                            },
                            title = entry.name,
                            modifier = Modifier.padding(start = 48.dp),
                        )
                    }
                }
            }
        }
    }
}
