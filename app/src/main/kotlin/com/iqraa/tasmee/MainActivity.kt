package com.iqraa.tasmee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.iqraa.core.designsystem.IqraaTheme
import com.iqraa.tasmee.ui.AppRoot
import dagger.hilt.android.AndroidEntryPoint

/**
 * T6e/V-048: Home + navigation per §10.1 — the wired shell (AppRoot): reader → range →
 * recite → summary → history → settings → about, startDestination COMPUTED (onboarding
 * once). Phase 3's skeleton (emptyList reader + Text() placeholders) is gone (issue #11).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IqraaTheme {
                AppRoot()
            }
        }
    }
}
