package com.example.tasmee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

/** Phase 0 scaffold — Home screen with real navigation lands in Phase 3 (§10.1). */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Text("تسميع — scaffold")  // TODO(Phase 3): Home per §10.1
        }
    }
}
