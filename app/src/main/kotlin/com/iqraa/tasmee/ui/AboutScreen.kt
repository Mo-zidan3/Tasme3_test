package com.iqraa.tasmee.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.iqraa.tasmee.R

/**
 * F8 About + licenses: renders the SHIPPED notice files from assets (§17 — the app carries
 * its own license texts), the Tanzil attribution with the tanzil.net link, the disclaimer,
 * and the letters-only Limitations text (V-024). NO INTERNET permission — the link opens
 * via the browser intent (the system handles it; the app never networks).
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun AboutScreen(
    noticeFiles: List<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var notices by remember { mutableStateOf(emptyMap<String, String>()) }

    LaunchedEffect(noticeFiles) {
        val loaded =
            noticeFiles.associateWith { name ->
                runCatching {
                    context.assets
                        .open("notices/$name")
                        .bufferedReader()
                        .use { it.readText() }
                }.getOrDefault("")
            }
        notices = loaded
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.riwaya_statement))
        Text(stringResource(R.string.limitations_text), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.about_licenses_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.about_tanzil_attribution))
        TextButton(
            onClick = {
                // tanzil.net — opened by the browser; the app itself NEVER networks (§4)
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(TANZIL_URL)),
                )
            },
        ) {
            Text(TANZIL_URL)
        }
        Text(stringResource(R.string.about_font_attribution))
        notices.forEach { (name, text) ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private const val TANZIL_URL = "https://tanzil.net"
