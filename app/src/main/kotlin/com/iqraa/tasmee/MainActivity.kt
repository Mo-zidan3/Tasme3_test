package com.iqraa.tasmee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.iqraa.core.designsystem.IqraaTheme
import com.iqraa.feature.reader.ReaderScreen
import dagger.hilt.android.AndroidEntryPoint

/** T6e: Home + navigation per §10.1 — reader → range → recite → summary → history → settings. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IqraaTheme {
                Scaffold { padding ->
                    val nav = rememberNavController()
                    NavHost(
                        navController = nav,
                        startDestination = "reader",
                        modifier =
                            androidx.compose.ui.Modifier.padding(padding),
                    ) {
                        composable("reader") {
                            ReaderScreen(
                                surahNames = emptyList(), // wired with core:quran in T6f
                                onSurahSelected = { },
                            )
                        }
                        composable("recite") { Text("recite") }
                        composable("summary") { Text("summary") }
                        composable("history") { Text("history") }
                        composable("settings") { Text("settings") }
                    }
                }
            }
        }
    }
}
