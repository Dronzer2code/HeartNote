package com.heartnote.app.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartnote.app.ui.hold.HoldScreen
import com.heartnote.app.ui.library.LibraryScreen
import com.heartnote.app.ui.onboarding.AboutSheet
import com.heartnote.app.ui.onboarding.ConsentScreen
import com.heartnote.app.ui.onboarding.ConsentStore
import com.heartnote.app.ui.onboarding.PrivacySheet
import com.heartnote.app.ui.onboarding.TourScreen
import com.heartnote.app.ui.onboarding.WelcomeChoiceScreen
import com.heartnote.app.ui.playback.PlaybackScreen
import com.heartnote.app.ui.playback.PlaybackViewModel
import com.heartnote.app.ui.record.RecordScreen

object Routes {
    const val CONSENT = "consent"
    const val WELCOME = "welcome"
    const val TOUR = "tour?replay={replay}"
    fun tour(replay: Boolean) = "tour?replay=$replay"
    const val LIBRARY = "library"
    const val RECORD = "record"
    const val PLAYBACK = "playback/{${PlaybackViewModel.NOTE_ID_ARG}}"
    const val HOLD = "hold/{${PlaybackViewModel.NOTE_ID_ARG}}"
    fun playback(noteId: String) = "playback/$noteId"
    fun hold(noteId: String) = "hold/$noteId"
}

private const val AXIS_MS = 300
private const val AXIS_SHIFT = 0.12f

/** Shared-axis X (300 ms): slide a short distance and cross-fade. */
private val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(AXIS_MS, easing = FastOutSlowInEasing)) { (it * AXIS_SHIFT).toInt() } + fadeIn(tween(AXIS_MS))
}
private val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(AXIS_MS, easing = FastOutSlowInEasing)) { -(it * AXIS_SHIFT).toInt() } + fadeOut(tween(AXIS_MS))
}
private val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(tween(AXIS_MS, easing = FastOutSlowInEasing)) { -(it * AXIS_SHIFT).toInt() } + fadeIn(tween(AXIS_MS))
}
private val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(tween(AXIS_MS, easing = FastOutSlowInEasing)) { (it * AXIS_SHIFT).toInt() } + fadeOut(tween(AXIS_MS))
}

/** Consent → Library → Record → Playback → Hold. */
@Composable
fun HeartnoteNavGraph() {
    val nav = rememberNavController()
    val context = LocalContext.current
    val start = remember {
        when {
            !ConsentStore.isAccepted(context) -> Routes.CONSENT
            !ConsentStore.isTourCompleted(context) -> Routes.WELCOME
            else -> Routes.LIBRARY
        }
    }
    val toLibrary: () -> Unit = {
        ConsentStore.completeTour(context)
        nav.navigate(Routes.LIBRARY) { popUpTo(nav.graph.id) { inclusive = true } }
    }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val noteArg = listOf(navArgument(PlaybackViewModel.NOTE_ID_ARG) { type = NavType.StringType })

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable(Routes.CONSENT) {
            ConsentScreen(onAccepted = {
                val next = if (ConsentStore.isTourCompleted(context)) Routes.LIBRARY else Routes.WELCOME
                nav.navigate(next) { popUpTo(Routes.CONSENT) { inclusive = true } }
            })
        }
        composable(Routes.WELCOME) {
            WelcomeChoiceScreen(onTour = { nav.navigate(Routes.tour(replay = false)) }, onSkip = toLibrary)
        }
        composable(
            Routes.TOUR,
            arguments = listOf(navArgument("replay") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val replay = entry.arguments?.getBoolean("replay") == true
            TourScreen(onFinish = { if (replay) nav.popBackStack() else toLibrary() })
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onRecord = { nav.navigate(Routes.RECORD) },
                onOpenNote = { id -> nav.navigate(Routes.playback(id)) },
                onHold = { id -> nav.navigate(Routes.hold(id)) },
                onPrivacy = { showPrivacy = true },
                onAbout = { showAbout = true },
            )
        }
        composable(Routes.RECORD) {
            RecordScreen(
                onBack = { nav.popBackStack() },
                onNoteSaved = { id -> nav.navigate(Routes.playback(id)) { popUpTo(Routes.LIBRARY) } },
            )
        }
        composable(Routes.PLAYBACK, arguments = noteArg) {
            PlaybackScreen(
                onBack = { nav.popBackStack() },
                onHold = { id -> nav.navigate(Routes.hold(id)) },
                onDeleted = { nav.popBackStack(Routes.LIBRARY, inclusive = false) },
            )
        }
        composable(Routes.HOLD, arguments = noteArg) {
            HoldScreen(onBack = { nav.popBackStack() })
        }
    }

    if (showPrivacy) PrivacySheet(onDismiss = { showPrivacy = false })
    if (showAbout) {
        AboutSheet(
            onDismiss = { showAbout = false },
            onReplayTour = {
                showAbout = false
                nav.navigate(Routes.tour(replay = true))
            },
        )
    }
}
