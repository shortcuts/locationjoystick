package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import com.locationjoystick.core.designsystem.LjIcons
import com.locationjoystick.core.designsystem.LjTheme
import com.locationjoystick.core.designsystem.R

/** Single-line list filter field. Query is caller-owned so closing a panel drops it. */
@Composable
fun ListSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    textColor: Color = Color.Unspecified,
    autoFocus: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(autoFocus) {
        if (autoFocus) focusRequester.requestFocus()
    }
    val colors =
        if (textColor == Color.Unspecified) {
            OutlinedTextFieldDefaults.colors()
        } else {
            OutlinedTextFieldDefaults.colors(
                focusedTextColor = textColor,
                unfocusedTextColor = textColor,
                focusedLabelColor = textColor,
                unfocusedLabelColor = textColor,
                cursorColor = textColor,
                focusedLeadingIconColor = textColor,
                unfocusedLeadingIconColor = textColor,
            )
        }
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text(label, color = textColor) },
        leadingIcon = { Icon(LjIcons.Search, contentDescription = null) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        colors = colors,
        modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

@Preview(showBackground = true)
@Composable
private fun ListSearchFieldPreview() {
    LjTheme {
        ListSearchField(
            query = "",
            onQueryChange = {},
            label = stringResource(R.string.list_search_field_search_favorites),
        )
    }
}
