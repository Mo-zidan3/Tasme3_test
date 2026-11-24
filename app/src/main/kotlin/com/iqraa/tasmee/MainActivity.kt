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
import com.iqraa.tasmee.ui.AboutScreen
import com.iqraa.tasmee.ui.OnboardingScreen
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
                        startDestination = "onboarding", // §10.1: F7 first (rationale + permission)
                        modifier =
                            androidx.compose.ui.Modifier
                                .padding(padding),
                    ) {
                        composable("onboarding") {
                            OnboardingScreen(
                                onGranted = { nav.navigate("reader") { popUpTo("onboarding") { inclusive = true } } },
                                onDeclined = { nav.navigate("reader") { popUpTo("onboarding") { inclusive = true } } },
                            )
                        }
                        composable("reader") {
                            ReaderScreen(
                                surahNames = emptyList(), // wired with core:quran in B3
                                onSurahSelected = { },
                            )
                        }
                        composable("recite") { Text("recite") }
                        composable("summary") { Text("summary") }
                        composable("history") { Text("history") }
                        composable("settings") { Text("settings") }
                        composable("about") {
                            AboutScreen(
                                noticeFiles =
                                    listOf(
                                        "CC-BY-3.0-Tanzil.txt",
                                        "OFL-1.1-AmiriQuran.txt",
                                        "MIT-models.txt",
                                        "Apache-2.0.txt",
                                        "CC-BY-4.0-tlog.txt",
                                        "README.txt",
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}
