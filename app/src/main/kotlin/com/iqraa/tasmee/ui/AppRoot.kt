package com.iqraa.tasmee.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.iqraa.core.align.WordState
import com.iqraa.core.designsystem.FeedbackEncoding
import com.iqraa.core.session.LifecycleState
import com.iqraa.feature.history.HistoryScreen
import com.iqraa.feature.history.HistoryViewModel
import com.iqraa.feature.reader.RangePickerScreen
import com.iqraa.feature.reader.ReaderScreen
import com.iqraa.feature.reader.ReaderViewModel
import com.iqraa.feature.recite.DegradedBadge
import com.iqraa.feature.recite.ModelLoadErrorCard
import com.iqraa.feature.recite.ReciteFlowViewModel
import com.iqraa.feature.recite.ReciteScreen
import com.iqraa.feature.recite.ReciteUiState
import com.iqraa.feature.recite.SummaryScreen
import com.iqraa.feature.recite.SummaryUiState
import com.iqraa.feature.recite.SummaryViewModel
import com.iqraa.feature.settings.SettingsScreen
import com.iqraa.feature.settings.SettingsViewModel
import com.iqraa.tasmee.MainViewModel
import com.iqraa.tasmee.R
import com.iqraa.tasmee.StartState

/**
 * V-048 (issue #11): the wired app shell — replaces MainActivity's skeleton (emptyList
 * reader, `Text("recite")`… placeholders). All §10.1 routes are real; the bottom bar
 * exposes Reader / History / Settings / About; startDestination is COMPUTED from the
 * onboarding flag OR the RECORD_AUDIO grant (MainViewModel) — never hardcoded.
 */
@Composable
@Suppress("FunctionName") // Compose convention: composables are PascalCase
fun AppRoot(mainViewModel: MainViewModel = hiltViewModel()) {
    val start by mainViewModel.start.collectAsState()
    when (val s = start) {
        StartState.Resolving -> {
            // no flash of the wrong first screen — blank theme surface while resolving
            Box(Modifier.fillMaxSize())
        }

        is StartState.Resolved -> {
            val nav = rememberNavController()
            Scaffold(
                bottomBar = { IqraaNavBar(nav) },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = if (s.startOnboarding) "onboarding" else "reader",
                    modifier = Modifier.padding(padding),
                ) {
                    composable("onboarding") {
                        OnboardingScreen(
                            onGranted = {
                                mainViewModel.markOnboardingDone()
                                nav.navigate("reader") { popUpTo("onboarding") { inclusive = true } }
                            },
                            onDeclined = {
                                mainViewModel.markOnboardingDone()
                                nav.navigate("reader") { popUpTo("onboarding") { inclusive = true } }
                            },
                        )
                    }
                    composable("reader") { ReaderRoute(nav) }
                    composable("range/{surah}") { entry -> RangePickerRoute(entry, nav) }
                    composable(
                        "recite/{surah}/{from}/{to}/{mode}",
                        arguments =
                            listOf(
                                navArgument("surah") { type = NavType.IntType },
                                navArgument("from") { type = NavType.IntType },
                                navArgument("to") { type = NavType.IntType },
                                navArgument("mode") { type = NavType.StringType },
                            ),
                    ) { _ -> ReciteRoute(nav) }
                    composable(
                        "summary/{sessionId}",
                        arguments = listOf(navArgument("sessionId") { type = NavType.LongType }),
                    ) { _ -> SummaryRoute(nav) }
                    composable("history") { HistoryRoute(nav) }
                    composable("settings") { SettingsRoute(nav) }
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

/** §10.1 bottom navigation — Reader / History / Settings / About (hidden mid-flow). */
@Composable
@Suppress("FunctionName") // Compose convention: composables are PascalCase
private fun IqraaNavBar(nav: NavHostController) {
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val topLevel = setOf("reader", "history", "settings", "about")
    if (route in topLevel) {
        NavigationBar {
            NavigationBarItem(
                selected = route == "reader",
                onClick = { nav.navigateTopLevel("reader") },
                icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_reader)) },
            )
            NavigationBarItem(
                selected = route == "history",
                onClick = { nav.navigateTopLevel("history") },
                icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_history)) },
            )
            NavigationBarItem(
                selected = route == "settings",
                onClick = { nav.navigateTopLevel("settings") },
                icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_settings)) },
            )
            NavigationBarItem(
                selected = route == "about",
                onClick = { nav.navigateTopLevel("about") },
                icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                label = { Text(stringResource(R.string.nav_about)) },
            )
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo("reader") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** F1: the reader with its ViewModel — data-driven, error + retry (§11). */
@Composable
@Suppress("FunctionName") // Compose convention: composables are PascalCase
private fun ReaderRoute(
    nav: NavHostController,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    ReaderScreen(
        state = state,
        onSurahSelected = { surah -> nav.navigate("range/${surah.number}") },
        onRetry = viewModel::retry,
    )
}

/** §10.1 range picker for the tapped surah. */
@Composable
@Suppress("FunctionName") // Compose convention: composables are PascalCase
private fun RangePickerRoute(
    entry: NavBackStackEntry,
    nav: NavHostController,
) {
    // the reader passed the surah; reload its row from the source the reader loaded
    val readerViewModel: ReaderViewModel = hiltViewModel(entry)
    val state by readerViewModel.state.collectAsState()
    val surahNumber = entry.arguments?.getInt("surah") ?: 1
    val surah =
        (state as? com.iqraa.feature.reader.ReaderState.Loaded)
            ?.surahs
            ?.firstOrNull { it.number == surahNumber }
    if (surah == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
    } else {
        RangePickerScreen(
            surah = surah,
            onStart = { from, to, tasmee ->
                val mode = if (tasmee) "tasmee" else "follow"
                nav.navigate("recite/$surahNumber/$from/$to/$mode")
            },
            onBack = { nav.popBackStack() },
        )
    }
}

/** F2/F3: the live recitation — the REAL pipeline behind §10.4 states + §11 errors. */
@Composable
@Suppress("FunctionName") // Compose convention: composables are PascalCase
private fun ReciteRoute(
    nav: NavHostController,
    viewModel: ReciteFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    when (val s = state) {
        ReciteUiState.Preparing -> {
            Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        }

        is ReciteUiState.ModelError -> {
            ModelLoadErrorCard(
                onRetry = viewModel::retryModel,
                onTryOtherModel = viewModel::tryOtherModel,
                modifier = Modifier.padding(all = 16.dp),
            )
        }

        is ReciteUiState.Session -> {
            // §10.4: capture begins when the session goes live (the user pressed Start on
            // the range picker); onResume re-starts after a pause
            LaunchedEffect(Unit) { viewModel.start() }
            val paused = s.lifecycle as? LifecycleState.Paused
            Column(Modifier.fillMaxSize()) {
                DegradedBadge(level = com.iqraa.core.session.DegradedLevel.L0, tinyModel = s.tinyModel)
                if (s.micError) {
                    Text(
                        stringResource(R.string.mic_unavailable),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                ReciteScreen(
                    words = s.words,
                    states = s.states.map(::encodingOf),
                    isTasmeeMode = s.tasmeeMode,
                    pausedBanner = paused?.let { stringResource(R.string.session_paused) },
                    onResume = viewModel::start,
                    onHint = viewModel::hint,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = viewModel::endSession,
                    modifier = Modifier.padding(all = 16.dp),
                ) {
                    Text(stringResource(R.string.end_session))
                }
            }
        }

        is ReciteUiState.Ended -> {
            LaunchedEffect(s) {
                nav.navigate("summary/${s.sessionId}") {
                    popUpTo("range/{surah}") { inclusive = true }
                }
            }
        }
    }
}

/** §10.2 encoding per word state — WRONG renders CLOSE until the §13.4 phone gate is met. */
private fun encodingOf(state: WordState): FeedbackEncoding? =
    when (state) {
        WordState.CORRECT -> FeedbackEncoding.CORRECT
        WordState.CLOSE -> FeedbackEncoding.CLOSE
        WordState.WRONG -> FeedbackEncoding.CLOSE
        WordState.MISSED -> FeedbackEncoding.MISSED
        WordState.HINTED -> FeedbackEncoding.HINTED
        WordState.PENDING, WordState.EXTRA -> null
    }

/** §9.6 summary: loads the persisted session row + flags from Room (the history write). */
@Composable
@Suppress("FunctionName") // Compose convention: composables are PascalCase
private fun SummaryRoute(
    nav: NavHostController,
    viewModel: SummaryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    when (val s = state) {
        SummaryUiState.Loading -> {
            Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        }

        is SummaryUiState.Loaded -> {
            Column(Modifier.fillMaxSize()) {
                SummaryScreen(
                    correct = s.data.correct,
                    y = s.data.y,
                    close = s.data.close,
                    wrong = s.data.wrong,
                    missed = s.data.missed,
                    hinted = s.data.hinted,
                    extra = s.data.extra,
                    flaggedWords = s.flagged,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { nav.navigate("reader") { popUpTo("reader") { inclusive = true } } },
                    modifier = Modifier.padding(all = 16.dp),
                ) {
                    Text(stringResource(R.string.summary_done))
                }
            }
        }
    }
}

/** F5 history — the Room flow, tappable rows open that session's summary. */
@Composable
@Suppress("FunctionName") // Compose convention: composables are PascalCase
private fun HistoryRoute(
    nav: NavHostController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    HistoryScreen(
        entries = entries,
        onEntryTapped = { id -> nav.navigate("summary/$id") },
        onDeleteAll = viewModel::deleteAll,
    )
}

/** F6 settings — preset + model persisted to DataStore. */
@Composable
@Suppress("FunctionName") // Compose convention: composables are PascalCase
private fun SettingsRoute(
    nav: NavHostController,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    SettingsScreen(
        state = state,
        onPresetSelected = viewModel::setPreset,
        onModelSelected = viewModel::setModelOverride,
    )
}
