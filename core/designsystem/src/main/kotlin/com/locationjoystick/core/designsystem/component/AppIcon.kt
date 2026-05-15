package com.locationjoystick.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.locationjoystick.core.designsystem.R

@Composable
fun AppIcon(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
) {
    Image(
        painter = painterResource(R.mipmap.ic_app),
        contentDescription = null,
        modifier =
            modifier
                .size(size)
                .clip(CircleShape),
    )
}
