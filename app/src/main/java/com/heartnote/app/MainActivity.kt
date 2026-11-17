package com.heartnote.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.heartnote.app.ui.nav.HeartnoteNavGraph
import com.heartnote.app.ui.theme.HeartnoteTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HeartnoteTheme {
                HeartnoteNavGraph()
            }
        }
    }
}
