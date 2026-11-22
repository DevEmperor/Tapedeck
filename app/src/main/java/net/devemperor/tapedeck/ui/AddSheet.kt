package net.devemperor.tapedeck.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.SignalCellularAlt
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import net.devemperor.tapedeck.data.Mixtape
import net.devemperor.tapedeck.data.Subscription
import net.devemperor.tapedeck.data.VideoFormat
import net.devemperor.tapedeck.engine.MediaFormat

val VIDEO_HEIGHTS = listOf(360, 480, 720, 1080)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(
    state: AddSheetState,
    onUrl: (String) -> Unit,
    onOptions: (DownloadOptions) -> Unit,
    onWholePlaylist: (Boolean) -> Unit,
    onOpenFormats: () -> Unit,
    onCloseFormats: () -> Unit,
    onPickFormat: (MediaFormat?, MediaFormat?) -> Unit,
    onClearCustom: () -> Unit,
    onRetryDetails: () -> Unit,
    mixtapes: List<Mixtape>,
    onMixtape: (String?, String?) -> Unit,
    offerMobileData: Boolean,
    onMobileData: (Boolean) -> Unit,
    onSubscribe: (Boolean) -> Unit,
    onKeep: (Int) -> Unit,
    onLoadNow: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        // Back on the format list returns to the options instead of closing the sheet.
        BackHandler(enabled = state.showFormats, onBack = onCloseFormats)
        AnimatedContent(
            targetState = state.showFormats,
            transitionSpec = {
                val dir = if (targetState) 1 else -1
                (slideInHorizontally { it * dir / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it * dir / 3 } + fadeOut())
            },
            label = "add-sheet-page",
        ) { formats ->
            if (formats) {
                FormatsPage(state, onBack = onCloseFormats, onPick = onPickFormat, onRetry = onRetryDetails)
            } else {
                OptionsPage(
                    state, onUrl, onOptions, onWholePlaylist, onOpenFormats, onClearCustom, mixtapes, onMixtape,
                    offerMobileData, onMobileData, onSubscribe, onKeep, onLoadNow, onConfirm,
                )
            }
        }
    }
}

@Composable
private fun OptionsPage(
    state: AddSheetState,
    onUrl: (String) -> Unit,
    onOptions: (DownloadOptions) -> Unit,
    onWholePlaylist: (Boolean) -> Unit,
    onOpenFormats: () -> Unit,
    onClearCustom: () -> Unit,
    mixtapes: List<Mixtape>,
    onMixtape: (String?, String?) -> Unit,
    offerMobileData: Boolean,
    onMobileData: (Boolean) -> Unit,
    onSubscribe: (Boolean) -> Unit,
    onKeep: (Int) -> Unit,
    onLoadNow: (Int) -> Unit,
    onConfirm: () -> Unit,
) {
    run {
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

            // Sites without oEmbed are read by yt-dlp right away, so a login wall shows before the download.
            val previewError = state.detailsError
            if (previewError != null && state.preview == null && !state.loadingPreview) {
                Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp).padding(top = 1.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(errorText(previewError)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }

            if (state.alreadyInLibrary) {
                Row(Modifier.padding(top = 10.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LibraryMusic, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.already_in_library), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            }

            if (state.isChannel) {
                ChannelRow(state)
            }

            if (state.playlistId != null) {
                PlaylistRow(state, onWholePlaylist)
                AnimatedVisibility(state.wholePlaylist, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    SubscribeRow(state.subscribe, onSubscribe)
                }
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
                val custom = options.customFormat != null
                // Picking from the list works for one video; a whole playlist goes by the presets.
                val customChip: @Composable () -> Unit = {
                    if (state.canPickFormat) {
                        Chip(stringResource(R.string.format_custom), custom, leading = Icons.Rounded.Tune, onClick = onOpenFormats)
                    }
                }
                if (options.kind == MediaKind.AUDIO) {
                    Column {
                        OptionLabel(R.string.option_format)
                        ChipRow {
                            AudioFormat.entries.forEach { format ->
                                // A picked stream is saved as it is, so the format does not apply to it.
                                Chip(stringResource(audioFormatLabel(format)), !custom && options.audioFormat == format, enabled = !custom) {
                                    onOptions(options.copy(audioFormat = format))
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OptionLabel(R.string.option_quality)
                        ChipRow {
                            Chip(stringResource(R.string.quality_high), !custom && options.audioQuality == AudioQuality.HIGH) {
                                onOptions(options.asDefaults().copy(audioQuality = AudioQuality.HIGH))
                            }
                            Chip(stringResource(R.string.quality_saver), !custom && options.audioQuality == AudioQuality.SAVER) {
                                onOptions(options.asDefaults().copy(audioQuality = AudioQuality.SAVER))
                            }
                            customChip()
                        }
                        if (custom) {
                            CustomFormatCard(options, R.string.custom_audio_hint, onOpenFormats, onClearCustom)
                        } else {
                            Hint(audioHint(options.audioFormat, options.audioQuality))
                        }
                    }
                } else {
                    Column {
                        OptionLabel(R.string.option_format)
                        ChipRow {
                            VideoFormat.entries.forEach { format ->
                                Chip(stringResource(videoFormatLabel(format)), options.videoFormat == format) { onOptions(options.copy(videoFormat = format)) }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OptionLabel(R.string.option_resolution)
                        ChipRow {
                            VIDEO_HEIGHTS.forEach { height ->
                                Chip("${height}p", !custom && options.videoHeight == height) { onOptions(options.asDefaults().copy(videoHeight = height)) }
                            }
                            customChip()
                        }
                        if (custom) {
                            CustomFormatCard(options, R.string.custom_video_hint, onOpenFormats, onClearCustom)
                        } else {
                            Hint(videoHint(options.videoFormat))
                        }
                    }
                }
            }

            // A subscription brings its own mixtape (named after the channel or playlist); instead it asks how
            // many downloads to keep and, for a channel, how many of the newest to load right away.
            val subscribing = state.isChannel || (state.wholePlaylist && state.subscribe)
            if (subscribing) {
                SubscriptionChoice(state, onKeep, onLoadNow)
            } else {
                MixtapeChoice(state, mixtapes, onMixtape)
            }

            AnimatedVisibility(offerMobileData, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                MobileDataRow(state.mobileData, onMobileData)
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onConfirm,
                enabled = state.canStart,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(if (subscribing) Icons.Rounded.RssFeed else Icons.Rounded.Download, null)
                Spacer(Modifier.width(10.dp))
                val count = state.playlist?.items?.size ?: 0
                Text(
                    when {
                        subscribing -> stringResource(R.string.subscribe)
                        state.wholePlaylist && count > 0 -> pluralStringResource(R.plurals.download_n, count, count)
                        else -> stringResource(R.string.download)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

/** On mobile data with "Wi-Fi only": a switch to load this download now instead of waiting for Wi-Fi. */
@Composable
private fun MobileDataRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clip(MaterialTheme.shapes.medium).toggleable(on, onValueChange = onChange),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SignalCellularAlt, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.mobile_data_title), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.mobile_data_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = on, onCheckedChange = null)
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

/** A channel link: subscribing to its videos, with its name and how many were read. */
@Composable
private fun ChannelRow(state: AddSheetState) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.RssFeed, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.subscribe_channel), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    when {
                        state.loadingChannel -> stringResource(R.string.subscribe_channel_loading)
                        state.channelFailed -> stringResource(R.string.subscribe_channel_failed)
                        else -> stringResource(R.string.subscribe_channel_hint)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.channelFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                )
            }
            if (state.loadingChannel) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/** Under a whole playlist: "Keep it up to date" — its new videos come by themselves later (a subscription). */
@Composable
private fun SubscribeRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clip(MaterialTheme.shapes.medium).toggleable(on, onValueChange = onChange),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.RssFeed, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.playlist_subscribe), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.playlist_subscribe_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = on, onCheckedChange = null)
        }
    }
}

/** How many downloads a subscription keeps (older ones make room), and for a channel how many load now. */
@Composable
private fun SubscriptionChoice(state: AddSheetState, onKeep: (Int) -> Unit, onLoadNow: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val keepLabel: @Composable (Int) -> String = { n -> if (n <= 0) stringResource(R.string.subscription_keep_all) else pluralStringResource(R.plurals.videos, n, n) }
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.subscription_keep), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.subscription_keep_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
        }
        Spacer(Modifier.width(12.dp))
        Surface(onClick = { picking = true }, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(keepLabel(state.keep), style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.ArrowDropDown, null)
            }
        }
    }
    if (state.isChannel) {
        Spacer(Modifier.height(10.dp))
        OptionLabel(R.string.subscription_now)
        ChipRow {
            Chip(stringResource(R.string.subscription_now_none), state.loadNow == 0) { onLoadNow(0) }
            Chip(stringResource(R.string.subscription_now_newest), state.loadNow == 1) { onLoadNow(1) }
            val most = state.keep.takeIf { it > 1 } ?: (state.channel?.items?.size ?: 0).takeIf { state.keep <= 0 && it > 1 }
            if (most != null) Chip(pluralStringResource(R.plurals.subscription_now_up_to, most, most), state.loadNow == most) { onLoadNow(most) }
        }
    }
    if (picking) {
        WheelPickerDialog(
            title = stringResource(R.string.subscription_keep),
            items = (1..Subscription.MAX_KEEP).toList() + 0,
            current = state.keep,
            label = keepLabel,
            onConfirm = { onKeep(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

/** "On mixtape: [None ▾]" — straight onto a mixtape; a whole playlist can become a new one of its own name. */
@Composable
private fun MixtapeChoice(state: AddSheetState, mixtapes: List<Mixtape>, onMixtape: (String?, String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf(false) }
    val chosen = state.mixtapeId?.let { id -> mixtapes.firstOrNull { it.id == id } }
    val label = when {
        state.newMixtapeName != null -> stringResource(R.string.mixtape_new_named, state.newMixtapeName)
        chosen != null -> chosen.name
        else -> stringResource(R.string.mixtape_none)
    }
    val active = state.newMixtapeName != null || chosen != null
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.mixtape_on), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Box {
            Surface(
                onClick = { open = true },
                shape = CircleShape,
                color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (chosen != null) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(mixtapeColor(chosen.color)))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
                    Icon(Icons.Rounded.ArrowDropDown, null)
                }
            }
            DropdownMenu(open, onDismissRequest = { open = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.mixtape_none)) }, onClick = { open = false; onMixtape(null, null) })
                mixtapes.forEach { tape ->
                    DropdownMenuItem(
                        text = { Text(tape.name) },
                        leadingIcon = { Box(Modifier.size(10.dp).clip(CircleShape).background(mixtapeColor(tape.color))) },
                        onClick = { open = false; onMixtape(tape.id, null) },
                    )
                }
                val playlistTitle = state.playlist?.title
                if (state.wholePlaylist && playlistTitle != null) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.mixtape_new_named, playlistTitle)) },
                        leadingIcon = { Icon(Icons.Rounded.Add, null) },
                        onClick = { open = false; onMixtape(null, playlistTitle) },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.mixtape_new_dots)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, null) },
                    onClick = { open = false; naming = true },
                )
            }
        }
    }
    if (naming) {
        NameDialog(R.string.mixtape_new, state.playlist?.title.orEmpty(), R.string.ok, onDismiss = { naming = false }) { name ->
            naming = false
            onMixtape(null, name.trim())
        }
    }
}

@Composable
private fun Hint(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** The stream picked from the list, with "Change" and × back to the presets. */
@Composable
private fun CustomFormatCard(options: DownloadOptions, hint: Int, onChange: () -> Unit, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(MaterialTheme.shapes.medium).clickable(onClick = onChange),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    options.customLabel ?: options.customFormat.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    stringResource(R.string.custom_format_ids, options.customFormat.orEmpty()) + " · " + stringResource(hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
            }
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, stringResource(R.string.custom_clear), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
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
private fun Chip(label: String, selected: Boolean, enabled: Boolean = true, leading: ImageVector? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = leading?.let { icon -> { Icon(icon, null, Modifier.size(18.dp)) } },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

fun audioHint(format: AudioFormat, quality: AudioQuality): Int = when {
    quality == AudioQuality.SAVER -> R.string.hint_saver
    format == AudioFormat.M4A -> R.string.hint_m4a
    format == AudioFormat.MP3 -> R.string.hint_mp3
    else -> R.string.hint_original
}

fun videoFormatLabel(format: VideoFormat): Int = when (format) {
    VideoFormat.MP4 -> R.string.format_mp4
    VideoFormat.WEBM -> R.string.format_webm
    VideoFormat.MKV -> R.string.format_mkv
}

fun videoHint(format: VideoFormat): Int = when (format) {
    VideoFormat.MP4 -> R.string.hint_mp4
    VideoFormat.WEBM -> R.string.hint_webm
    VideoFormat.MKV -> R.string.hint_mkv
}
