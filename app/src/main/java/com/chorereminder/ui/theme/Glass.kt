package com.chorereminder.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur

/**
 * Frosted-glass surfaces (FR-19).
 *
 * `Modifier.blur()` cannot do this: it only blurs a composable's own subtree, and
 * backdrop blur is the entire point of glassmorphism. Haze captures the content
 * behind the panel and blurs *that*, which is why it's a requirement here.
 *
 * Glass is deliberately confined to app bars, sheets and the confirmation card --
 * never a full-screen layer over a scrolling list, which would re-capture the
 * layer every frame.
 */

/** Mark the content that should show *through* the glass. */
fun Modifier.glassSource(state: HazeState): Modifier = hazeSource(state)

/**
 * Apply frosted glass to a panel floating above [state]'s source content.
 *
 * @param tint overlaid on the blur to keep text legible against busy backdrops.
 */
fun Modifier.glassPanel(
    state: HazeState,
    tint: Color,
    blurRadius: Dp = 20.dp,
    alpha: Float = 0.82f,
): Modifier = this.hazeBlur(
    input = HazeInput.Sources(state),
    style = HazeBlurStyle {
        blurRadius(blurRadius)
        backgroundColor(tint)
        alpha(alpha)
        noiseFactor(0.04f)
    },
    // Haze's default Balanced performance mode -- left implicit.
)

/** Standard translucent tint for glass surfaces in the current scheme. */
val glassTint: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)

/** Slightly more opaque tint for cards that carry body text. */
val glassCardTint: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f)

/**
 * Translucent card surface for items *inside* a Haze source layer.
 *
 * These deliberately do not use [glassPanel]: a list row is part of the content
 * the app bar samples, so blurring it against that same state would be circular.
 * A layered translucent fill gives the frosted look at zero capture cost, which
 * also matches the plan's "don't put a blur layer over a scrolling list" note.
 */
@Composable
fun Modifier.glassCard(shape: Shape): Modifier {
    val scheme = MaterialTheme.colorScheme
    return this
        .background(
            brush = Brush.verticalGradient(
                listOf(
                    scheme.surfaceContainerHighest.copy(alpha = 0.78f),
                    scheme.surfaceContainer.copy(alpha = 0.58f),
                ),
            ),
            shape = shape,
        )
        .border(1.dp, scheme.onSurface.copy(alpha = 0.08f), shape)
}

/**
 * A capsule button rendered with the Liquid Glass refraction effect (backdrop
 * library), replacing the flat Material FAB for the primary "new chore" action.
 *
 * [backdrop] must come from [com.kyant.backdrop.backdrops.rememberLayerBackdrop]
 * and the same instance must be attached via `.layerBackdrop(backdrop)` to
 * whatever content should show *through* the pill -- the scrolling chore list.
 */
@Composable
fun LiquidPillButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
        label = "liquid-pill-scale",
    )

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(tint.copy(alpha = 0.22f))
                },
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .height(56.dp)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
