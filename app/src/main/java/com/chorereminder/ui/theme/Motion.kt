package com.chorereminder.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role

/**
 * Shared "expressive" motion specs, modelled on iOS's springy press feedback rather
 * than Material's flat ripple-only taps. A tap presses down snappily and releases
 * with a small overshoot, so the UI feels physical instead of just clickable.
 */
val BouncySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMediumLow,
)

/** A livelier variant for small elements (icons, chips) where the bounce can be bigger. */
val JigglySpring = spring<Float>(
    dampingRatio = Spring.DampingRatioHighBouncy,
    stiffness = Spring.StiffnessMedium,
)

/** Scale to animate towards while [interactionSource] reports a press. */
@Composable
fun rememberPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.93f,
    spec: androidx.compose.animation.core.AnimationSpec<Float> = BouncySpring,
): State<Float> {
    val pressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spec,
        label = "press-scale",
    )
}

/**
 * Drop-in replacement for [Modifier.clickable] with a springy press-scale and no
 * Material ripple -- the scale itself is the feedback, same as iOS controls.
 */
@Composable
fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    pressedScale: Float = 0.93f,
    spec: androidx.compose.animation.core.AnimationSpec<Float> = BouncySpring,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberPressScale(interactionSource, pressedScale, spec)
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}
