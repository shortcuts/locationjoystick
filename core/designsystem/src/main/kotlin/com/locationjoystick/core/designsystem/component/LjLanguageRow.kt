package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjSpacing
import com.locationjoystick.core.designsystem.R
import com.locationjoystick.core.model.AppLanguage

/**
 * Compact top-right dropdown language switcher, used in both the onboarding header and the
 * Settings top bar's [LjTopBar] `actions` slot — replaces the old full-width language row.
 */
@Composable
fun LjLanguageDropdown(
    selected: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val dropdownCd = stringResource(R.string.language_dropdown_cd)
    Box(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .defaultMinSize(minHeight = 44.dp)
                    .clickable { expanded = true }
                    .semantics { contentDescription = dropdownCd }
                    .padding(horizontal = LjSpacing.sm, vertical = LjSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                LjIcons.Translate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                appLanguageShortLabel(selected),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = LjSpacing.xs),
            )
            Icon(
                LjIcons.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(appLanguageShortLabel(language)) },
                    onClick = {
                        expanded = false
                        onSelect(language)
                    },
                )
            }
        }
    }
}

@Composable
private fun appLanguageShortLabel(language: AppLanguage): String =
    when (language) {
        AppLanguage.SYSTEM_DEFAULT -> stringResource(R.string.language_system_default)
        AppLanguage.ENGLISH -> stringResource(R.string.language_short_english)
        AppLanguage.CHINESE_SIMPLIFIED -> stringResource(R.string.language_short_chinese)
        AppLanguage.CHINESE_TRADITIONAL -> stringResource(R.string.language_short_traditional_chinese)
    }
