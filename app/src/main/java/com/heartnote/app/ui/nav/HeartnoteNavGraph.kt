package com.heartnote.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartnote.app.ui.library.LibraryScreen
import com.heartnote.app.ui.playback.PlaybackScreen
import com.heartnote.app.ui.playback.PlaybackViewModel
import com.heartnote.app.ui.record.RecordScreen

object Routes {
    const val LIBRARY = "library"
    const val RECORD = "record"
    const val PLAYBACK = "playback/{${PlaybackViewModel.NOTE_ID_ARG}}"
    fun playback(noteId: String) = "playback/$noteId"
}

@Composable
fun HeartnoteNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                onRecord = { nav.navigate(Routes.RECORD) },
                onOpenNote = { id -> nav.navigate(Routes.playback(id)) },
                onHold = { },
                onPrivacy = { },
                onAbout = { },
            )
        }
        composable(Routes.RECORD) {
            RecordScreen(
                onBack = { nav.popBackStack() },
                onNoteSaved = { id ->
                    nav.navigate(Routes.playback(id)) { popUpTo(Routes.LIBRARY) }
                },
            )
        }
        composable(
            Routes.PLAYBACK,
            arguments = listOf(navArgument(PlaybackViewModel.NOTE_ID_ARG) { type = NavType.StringType }),
        ) {
            PlaybackScreen(
                onBack = { nav.popBackStack() },
                onHold = { },
                onDeleted = { nav.popBackStack(Routes.LIBRARY, inclusive = false) },
            )
        }
    }
}
