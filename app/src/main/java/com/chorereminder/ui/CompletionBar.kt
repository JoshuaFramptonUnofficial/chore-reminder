package com.chorereminder.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chorereminder.AppContainer
import com.chorereminder.R
import com.chorereminder.ui.theme.liquidPillSurface
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Home presentation only; the navigation-wide lifetime keeps running while this is covered. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompletionBar(container: AppContainer, backdrop: Backdrop, modifier: Modifier = Modifier) {
    val receipts by container.undoQueue.receipts.collectAsStateWithLifecycle()
    // Resume/navigation can compose before the lifetime owner prunes stale data.
    // Never flash an expired receipt during that frame.
    val receipt = receipts.firstOrNull()?.takeIf { it.expiresAt > android.os.SystemClock.elapsedRealtime() }
    AnimatedVisibility(visible = receipt != null, modifier = modifier,
        enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }) {
        FlowRow(
            modifier = Modifier.widthIn(max = 320.dp)
                .liquidPillSurface(backdrop, MaterialTheme.colorScheme.primary)
                .heightIn(min = 56.dp).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
            verticalArrangement = Arrangement.Center,
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Task completed", style = MaterialTheme.typography.bodySmall)
            TextButton(contentPadding = PaddingValues(horizontal = 4.dp), onClick = {
                receipt?.let { container.applicationScope.launch { container.undoCompletion(it.completionId) } }
            }) {
                Icon(painterResource(R.drawable.ic_undo), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("Undo")
            }
        }
    }
}

/** One lifetime owner across Home and navigation-host presentations. */
@Composable
fun CompletionLifetime(container: AppContainer) {
    val receipts by container.undoQueue.receipts.collectAsStateWithLifecycle()
    val receipt = receipts.firstOrNull()
    val accessibility = androidx.compose.ui.platform.LocalAccessibilityManager.current
    LifecycleResumeEffect(container) {
        container.undoQueue.refresh(android.os.SystemClock.elapsedRealtime())
        onPauseOrDispose { }
    }
    LaunchedEffect(receipt?.completionId) {
        receipt?.let {
            val duration = accessibility?.calculateRecommendedTimeoutMillis(8000, containsText = true, containsIcons = true, containsControls = true) ?: 8000
            container.undoQueue.extendFirst(it.completionId, duration, android.os.SystemClock.elapsedRealtime())
        }
    }
    LaunchedEffect(receipt?.completionId, receipt?.expiresAt) {
        if (receipt != null) {
            delay((receipt.expiresAt - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
            container.undoQueue.refresh(android.os.SystemClock.elapsedRealtime())
        }
    }
}
