package net.devemperor.tapedeck.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.AudioFormat
import net.devemperor.tapedeck.data.AudioQuality
import net.devemperor.tapedeck.data.DownloadOptions
import net.devemperor.tapedeck.data.MediaKind

private val VIDEO_HEIGHTS = listOf(360, 480, 720, 1080)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    state: AddSheetState,
    onUrl: (String) -> Unit,
    onOptions: (DownloadOptions) -> Unit,
    onWholePlaylist: (Boolean) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding()
                .padding(horizontal = 20.dp).padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.add_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            @Suppress("DEPRECATION")
            val clipboard = LocalClipboardManager.current
            OutlinedTextField(
                value = state.url,
                onValueChange = onUrl,
                label = { Text(stringResource(R.string.add_url_label)) },
                placeholder = { Text("https://youtu.be/…") },
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    IconButton(onClick = { clipboard.getText()?.text?.let(onUrl) }) {
                        Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste))
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            PreviewCard(state)

            if (state.alreadyInLibrary) {
                Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.already_in_library), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (state.playlistId != null) {
                PlaylistRow(state, onWholePlaylist)
            }

            Spacer(Modifier.height(18.dp))
            val options = state.options
            val segmentColors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                activeBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = options.kind == MediaKind.AUDIO,
                    onClick = { onOptions(options.copy(kind = MediaKind.AUDIO)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    colors = segmentColors,
                    icon = { Icon(Icons.Rounded.Headphones, null, Modifier.size(18.dp)) },
                ) { Text(stringResource(R.string.kind_audio)) }
                SegmentedButton(
                    selected = options.kind == MediaKind.VIDEO,
                    onClick = { onOptions(options.copy(kind = MediaKind.VIDEO)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    colors = segmentColors,
                    icon = { Icon(Icons.Rounded.Movie, null, Modifier.size(18.dp)) },
                ) { Text(stringResource(R.string.kind_video)) }
            }

            Spacer(Modifier.height(14.dp))
            Box(Modifier.animateContentSize()) {
                if (options.kind == MediaKind.AUDIO) {
                    Column {
                        OptionLabel(R.string.option_format)
                        ChipRow {
                            AudioFormat.entries.forEach { format ->
                                Chip(stringResource(audioFormatLabel(format)), options.audioFormat == format) { onOptions(options.copy(audioFormat = format)) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OptionLabel(R.string.option_quality)
                        ChipRow {
                            Chip(stringResource(R.string.quality_high), options.audioQuality == AudioQuality.HIGH) { onOptions(options.copy(audioQuality = AudioQuality.HIGH)) }
                            Chip(stringResource(R.string.quality_saver), options.audioQuality == AudioQuality.SAVER) { onOptions(options.copy(audioQuality = AudioQuality.SAVER)) }
                        }
                        Text(
                            stringResource(formatHint(options.audioFormat, options.audioQuality)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                } else {
                    Column {
                        OptionLabel(R.string.option_resolution)
                        ChipRow {
                            VIDEO_HEIGHTS.forEach { height ->
                                Chip("${height}p", options.videoHeight == height) { onOptions(options.copy(videoHeight = height)) }
                            }
                        }
                        Text(
                            stringResource(R.string.video_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onConfirm,
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(10.dp))
                val count = state.playlist?.items?.size ?: 0
                Text(
                    if (state.wholePlaylist && count > 0) pluralStringResource(R.plurals.download_n, count, count) else stringResource(R.string.download),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun PreviewCard(state: AddSheetState) {
    AnimatedVisibility(state.preview != null || state.loadingPreview) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
        ) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.width(120.dp).aspectRatio(16f / 9f).clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    val preview = state.preview
                    if (preview?.thumbnailUrl != null) {
                        AsyncImage(preview.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else if (state.loadingPreview) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.preview?.title ?: stringResource(R.string.preview_loading),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.preview?.author?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(state: AddSheetState, onWholePlaylist: (Boolean) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.playlist_whole), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                val sub = when {
                    state.loadingPlaylist -> stringResource(R.string.playlist_loading)
                    state.playlistFailed -> stringResource(R.string.playlist_failed)
                    state.playlist != null -> listOfNotNull(
                        state.playlist.title,
                        pluralStringResource(R.plurals.videos, state.playlist.items.size, state.playlist.items.size),
                    ).joinToString(" · ")
                    else -> stringResource(R.string.playlist_hint)
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (state.loadingPlaylist) {
                CircularProgressIndicator(Modifier.size(20.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            }
            Switch(checked = state.wholePlaylist, onCheckedChange = onWholePlaylist)
        }
    }
}

@Composable
private fun OptionLabel(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

private fun formatHint(format: AudioFormat, quality: AudioQuality): Int = when {
    quality == AudioQuality.SAVER -> R.string.hint_saver
    format == AudioFormat.M4A -> R.string.hint_m4a
    format == AudioFormat.MP3 -> R.string.hint_mp3
    else -> R.string.hint_original
}
