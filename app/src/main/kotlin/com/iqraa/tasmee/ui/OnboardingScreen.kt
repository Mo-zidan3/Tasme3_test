package com.iqraa.tasmee.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * F7 Onboarding + permission flow (§10.1, §11): mic rationale + privacy statement + the
 * riwaya statement (Hafs only, mandatory display) — then the RECORD_AUDIO request with
 * denied / denied-permanently handling and a deep link to system settings (§3 F7).
 */
@Suppress("FunctionName") // Compose convention: composables are PascalCase
@Composable
fun OnboardingScreen(
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var permanentlyDenied by remember { mutableStateOf(false) }
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) {
                onGranted()
            } else {
                // §11/F7: after a second denial the system no longer shows the dialog →
                // treat as permanent and offer the deep link
                permanentlyDenied = true
            }
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Card {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.permission_rationale_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(stringResource(R.string.mic_rationale))
                Text(
                    stringResource(R.string.riwaya_statement),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.limitations_text),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        if (permanentlyDenied) {
            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.permission_denied_permanent))
                    Button(
                        onClick = {
                            // §3 F7: deep link to the app's system settings page
                            val intent =
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                )
                            context.startActivity(intent)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.open_settings))
                    }
                }
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.onboarding_allow_mic))
                }
                OutlinedButton(onClick = onDeclined, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.onboarding_later))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
