package com.heartnote.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.heartnote.app.ui.playback.PlaybackScreen
import com.heartnote.app.ui.playback.PlaybackViewModel
import com.heartnote.app.ui.record.RecordScreen

object Routes {
    const val RECORD = "record"
    const val PLAYBACK = "playback/{${PlaybackViewModel.NOTE_ID_ARG}}"
    fun playback(noteId: String) = "playback/$noteId"
}

@Composable
fun HeartnoteNavGraph() {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.RECORD) {
        composable(Routes.RECORD) {
            RecordScreen(onNoteSaved = { id -> nav.navigate(Routes.playback(id)) })
        }
        composable(
            Routes.PLAYBACK,
            arguments = listOf(navArgument(PlaybackViewModel.NOTE_ID_ARG) { type = NavType.StringType }),
        ) {
            PlaybackScreen()
        }
    }
}
