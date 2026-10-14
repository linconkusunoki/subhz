package com.substream.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.substream.data.preferences.PlaybackPreferences
import com.substream.data.preferences.ServerPreferences
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Settings tab: shows the connected server, verifies the stored credentials, sets the streaming
 * quality, and allows changing the server or logging out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onChangeServer: () -> Unit = {},
    serverPreferences: ServerPreferences = koinInject(),
    playbackPreferences: PlaybackPreferences = koinInject(),
    viewModel: PingViewModel = koinViewModel(),
) {
    val config by serverPreferences.config.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val maxBitRate by playbackPreferences.maxBitRate.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Server", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = config?.url ?: "Not configured",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "User: ${config?.username ?: "Not configured"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Streaming quality", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "The server transcodes on the fly, so a lower ceiling means fewer " +
                            "megabytes over the air and a lower ceiling in quality. Original plays " +
                            "the file as it is stored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        STREAM_QUALITIES.forEachIndexed { index, (label, kbps) ->
                            SegmentedButton(
                                selected = maxBitRate == kbps,
                                onClick = { playbackPreferences.setMaxBitRate(kbps) },
                                shape = SegmentedButtonDefaults.itemShape(index, STREAM_QUALITIES.size),
                            ) {
                                Text(label)
                            }
                        }
                    }
                }
            }

            when (val current = state) {
                is PingState.Loading -> CircularProgressIndicator()

                is PingState.Success -> Text(
                    text = "Connected • API ${current.data.version}",
                    color = MaterialTheme.colorScheme.primary
                )

                is PingState.Error -> Text(
                    text = current.message,
                    color = MaterialTheme.colorScheme.error
                )

                PingState.Idle -> Unit
            }

            OutlinedButton(
                onClick = viewModel::testConnection,
                enabled = state !is PingState.Loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test connection")
            }

            OutlinedButton(
                onClick = onChangeServer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Change server")
            }

            Button(
                onClick = serverPreferences::logout,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Log out")
            }
        }
    }
}

/** Streaming ceilings offered in Settings, in kbps. 0 is the original file. */
private val STREAM_QUALITIES = listOf(
    "Original" to 0,
    "320" to 320,
    "192" to 192,
    "128" to 128,
)
