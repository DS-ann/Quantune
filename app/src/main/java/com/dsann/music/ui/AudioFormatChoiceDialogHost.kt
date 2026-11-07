package com.dsann.music.ui

import com.dsann.music.ui.theme.DsmusicCard
import com.dsann.music.ui.theme.DsmusicSurface
import com.dsann.music.ui.theme.DsmusicListItem
import com.dsann.music.ui.theme.DsmusicButton
import com.dsann.music.ui.theme.DsmusicOutlinedButton
import com.dsann.music.ui.theme.DsmusicTextButton
import com.dsann.music.ui.theme.DsmusicIconButton
import com.dsann.music.ui.theme.DsmusicFilterChip
import com.dsann.music.ui.theme.DsmusicSwitchControl
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.dsann.music.ui.theme.AlertDialog
import com.dsann.music.ui.theme.DsmusicTheme
import com.dsann.music.ui.theme.Text
import com.dsann.music.ui.theme.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dsann.music.data.innertube.AudioFormatPurpose
import com.dsann.music.data.innertube.AudioFormatSelectionCoordinator

/** Activity-level host; works even when a download starts from Library or Explore. */
@Composable
fun AudioFormatChoiceDialogHost() {
    val request by AudioFormatSelectionCoordinator.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current

    request?.let { pending ->
        AlertDialog(
            onDismissRequest = { AudioFormatSelectionCoordinator.cancel(pending) },
            title = {
                Text(
                    if (pending.purpose == AudioFormatPurpose.DOWNLOAD) {
                        "Choose available download format"
                    } else {
                        "Choose available playback format"
                    },
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "The requested format (${pending.requestedFormat.uppercase()}) isn't available for ${pending.songTitle}. " +
                            "Choose one of the formats this track actually offers. Your Settings preference won't be changed.",
                        style = DsmusicTheme.typography.bodyMedium
                    )
                    pending.options.forEach { option ->
                        DsmusicTextButton(
                            onClick = { AudioFormatSelectionCoordinator.select(pending, option) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    option.formatLabel,
                                    color = DsmusicTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    buildString {
                                        append(
                                            if (option.bitrateKbps > 0) {
                                                "${option.bitrateKbps} kbps"
                                            } else {
                                                "Bitrate unknown"
                                            }
                                        )
                                        option.contentLength?.takeIf { it > 0L }?.let {
                                            append(" · ")
                                            append(android.text.format.Formatter.formatFileSize(context, it))
                                        }
                                    },
                                    color = DsmusicTheme.colorScheme.onSurfaceVariant,
                                    style = DsmusicTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                DsmusicTextButton(onClick = { AudioFormatSelectionCoordinator.cancel(pending) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
