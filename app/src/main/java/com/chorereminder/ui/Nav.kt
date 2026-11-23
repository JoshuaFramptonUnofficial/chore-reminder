package com.chorereminder.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.chorereminder.data.UiPreferences
import com.chorereminder.ui.theme.LocalLiquidGlassBlur
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.chorereminder.AppContainer
import com.chorereminder.ui.complete.CompleteScreen
import com.chorereminder.ui.complete.CompleteViewModel
import com.chorereminder.ui.edit.EditTaskScreen
import com.chorereminder.ui.edit.EditTaskViewModel
import com.chorereminder.ui.home.HomeScreen
import com.chorereminder.ui.home.HomeViewModel
import com.chorereminder.ui.settings.SettingsScreen
import com.chorereminder.ui.settings.SettingsViewModel
import kotlinx.coroutines.flow.Flow

object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val EDIT = "edit"
    const val TASK_ID = "taskId"
    fun edit(taskId: Long) = "$EDIT/$taskId"
    const val EDIT_PATTERN = "$EDIT/{$TASK_ID}"
    const val COMPLETE = "complete"
    fun complete(taskId: Long) = "$COMPLETE/$taskId"
    const val COMPLETE_PATTERN = "$COMPLETE/{$TASK_ID}"
}

@Composable
fun ChoreNavHost(
    container: AppContainer,
    /**
     * Task ids arriving from notification taps. Emitted on both `onCreate` and
     * `onNewIntent` (the activity is `singleTop`), so a tap while the app is
     * already open still navigates.
     */
    notificationTaskIds: Flow<Long>,
    /** Task ids from the notification's Delay... action specifically -- opens straight to the picker. */
    notificationDelayTaskIds: Flow<Long> = kotlinx.coroutines.flow.emptyFlow(),
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(notificationTaskIds) {
        notificationTaskIds.collect { taskId ->
            navController.navigate(Routes.complete(taskId)) {
                launchSingleTop = true
            }
        }
    }

    // A replay(1) SharedFlow would re-fire on every later visit to any complete
    // screen if collected there directly, so it's consumed exactly once here and
    // handed down as a plain value matched against the destination's own taskId.
    var pendingDelayTaskId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(notificationDelayTaskIds) {
        notificationDelayTaskIds.collect { taskId -> pendingDelayTaskId = taskId }
    }

    // Shared screen transitions: a short horizontal slide paired with a fade, so
    // pushing and popping read as motion in opposite directions.
    val spec = tween<Float>(durationMillis = 280, easing = FastOutSlowInEasing)
    val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
        durationMillis = 280,
        easing = FastOutSlowInEasing,
    )

    val glassBlur by container.uiPreferences.liquidGlassBlur.collectAsStateWithLifecycle(UiPreferences.DEFAULT_GLASS_BLUR)
    CompletionLifetime(container)
    val entry by navController.currentBackStackEntryAsState()
    val completionBackdrop = com.kyant.backdrop.backdrops.rememberLayerBackdrop()
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().layerBackdrop(completionBackdrop)) {
    androidx.compose.runtime.CompositionLocalProvider(LocalSceneBackdrop provides completionBackdrop, LocalLiquidGlassBlur provides glassBlur.dp) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = slideSpec,
            ) + fadeIn(animationSpec = spec)
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = slideSpec,
            ) + fadeOut(animationSpec = spec)
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = slideSpec,
            ) + fadeIn(animationSpec = spec)
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = slideSpec,
            ) + fadeOut(animationSpec = spec)
        },
    ) {

        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
            HomeScreen(
                viewModel = vm,
                onAddTask = { navController.navigate(Routes.edit(0L)) },
                onOpenTask = { navController.navigate(Routes.edit(it)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                showCompletion = entry?.destination?.route == Routes.HOME,
            )
        }

        composable(
            route = Routes.EDIT_PATTERN,
            arguments = listOf(navArgument(Routes.TASK_ID) { type = NavType.LongType }),
        ) { entry ->
            val taskId = entry.arguments?.getLong(Routes.TASK_ID) ?: 0L
            val vm: EditTaskViewModel =
                viewModel(factory = EditTaskViewModel.factory(container, taskId))
            EditTaskScreen(viewModel = vm, onDone = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.COMPLETE_PATTERN,
            arguments = listOf(navArgument(Routes.TASK_ID) { type = NavType.LongType }),
        ) { entry ->
            val taskId = entry.arguments?.getLong(Routes.TASK_ID) ?: 0L
            val vm: CompleteViewModel =
                viewModel(factory = CompleteViewModel.factory(container, taskId))
            val leave: () -> Unit = {
                // Came straight from a notification with nothing beneath us: land
                // on Home rather than closing the app.
                if (!navController.popBackStack()) {
                    navController.navigate(Routes.HOME) { launchSingleTop = true }
                }
            }
            CompleteScreen(
                viewModel = vm,
                onFinished = leave,
                onDismiss = leave,
                openDelayImmediately = pendingDelayTaskId == taskId,
                onDelayFlagConsumed = { pendingDelayTaskId = null },
            )
        }
    }
    }
    }
    }
}
