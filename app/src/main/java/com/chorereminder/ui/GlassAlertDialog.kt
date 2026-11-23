package com.chorereminder.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.chorereminder.ui.theme.liquidSurface
import com.kyant.backdrop.Backdrop

val LocalSceneBackdrop = staticCompositionLocalOf<Backdrop> { error("Scene backdrop missing") }

/** Full-screen edge-to-edge native dialog keeps source and sampling window origins aligned. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    dismissButton: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val backdrop = LocalSceneBackdrop.current
    Dialog(onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val dialogWindow = (LocalView.current.parent as DialogWindowProvider).window
        SideEffect {
            // Window translations/scaling happen after Compose samples the backdrop.
            // Keep screen-space origins fixed and fade only the panel instead.
            dialogWindow.setWindowAnimations(0)
        }
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val opacity by animateFloatAsState(if (entered) 1f else 0f, tween(160), label = "dialog-opacity")
        BoxWithConstraints(
            Modifier.fillMaxSize().imePadding().safeDrawingPadding()
                .pointerInput(onDismissRequest) { detectTapGestures(onTap = { onDismissRequest() }) }
                .padding(24.dp), contentAlignment = Alignment.Center,
        ) {
            Column(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight)
                .graphicsLayer { alpha = opacity }
                .liquidSurface(backdrop, { RoundedCornerShape(28.dp) }, containerColor.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { } }
                .semantics { paneTitle = "Confirmation" }
                .verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.headlineSmall, title)
                androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyMedium, text)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { dismissButton(); confirmButton() }
            }
        }
    }
}
