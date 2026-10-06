package com.chorereminder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chorereminder.data.IconType
import com.chorereminder.ui.edit.iconForKey

/** Renders a task's icon, whichever of the two picker modes produced it (FR-4). */
@Composable
fun TaskIcon(
    iconType: IconType,
    iconValue: String,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    when (iconType) {
        IconType.EMOJI -> Text(
            text = iconValue,
            fontSize = (size.value * 0.82f).sp,
            modifier = modifier,
        )
        IconType.MATERIAL -> {
            val vector = iconForKey(iconValue)
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = tint,
                modifier = modifier.size(size),
            )
        }
    }
}

/** Icon inside a tinted circle, for list rows and headers. */
@Composable
fun TaskIconBadge(
    iconType: IconType,
    iconValue: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
) {
    Box(
        modifier = modifier
            .size(diameter)
            .background(containerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        TaskIcon(
            iconType = iconType,
            iconValue = iconValue,
            tint = contentColor,
            size = diameter * 0.5f,
        )
    }
}
