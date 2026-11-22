package net.devemperor.tapedeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.devemperor.tapedeck.BuildConfig
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.data.AudioFormat
import net.devemperor.tapedeck.data.AudioQuality
import net.devemperor.tapedeck.data.ShareAction
import net.devemperor.tapedeck.data.Entry
import net.devemperor.tapedeck.data.Storage
import net.devemperor.tapedeck.data.StorageLocation
import net.devemperor.tapedeck.data.UpdateChannel
import net.devemperor.tapedeck.data.VideoFormat

private const val SOURCE_URL = "https://github.com/DevEmperor/Tapedeck"

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val version by vm.engineVersion.collectAsStateWithLifecycle()
    val meta by vm.engineMeta.collectAsStateWithLifecycle()
    val updating by vm.updating.collectAsStateWithLifecycle()
    val appUpdate by vm.appUpdate.collectAsStateWithLifecycle()
    val checkingApp by vm.checkingApp.collectAsStateWithLifecycle()
    val used by vm.usedBytes.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val defaults = settings.defaults

    Column(Modifier.fillMaxSize().blockTouchesBelow().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
        }
        // A plain scrolling column, not a lazy list: the few groups are all measured, so the scrollbar
        // knows the real height instead of guessing it from groups of very different sizes.
        val scroll = rememberScrollState()
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll)
                .padding(start = 16.dp, end = 16.dp)
                .padding(bottom = 32.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
                Group(R.string.group_downloads) {
                    ChoiceRow(
                        R.string.setting_share, settings.shareAction,
                        listOf(ShareAction.ASK to R.string.share_ask, ShareAction.DOWNLOAD_NOW to R.string.share_now),
                    ) { value -> vm.updateSettings { it.copy(shareAction = value) } }
                    SwitchRow(R.string.setting_wifi_only, R.string.setting_wifi_only_sub, settings.wifiOnly) { on -> vm.updateSettings { it.copy(wifiOnly = on) } }
                    WheelRow(
                        title = R.string.setting_parallel,
                        sub = null,
                        current = settings.parallelDownloads.coerceIn(1, AppSettings.MAX_PARALLEL_DOWNLOADS),
                        items = (1..AppSettings.MAX_PARALLEL_DOWNLOADS).toList(),
                        label = { it.toString() },
                    ) { value -> vm.updateSettings { it.copy(parallelDownloads = value) } }
                    SwitchRow(R.string.setting_clipboard, R.string.setting_clipboard_sub, settings.watchClipboard) { on ->
                        vm.updateSettings { it.copy(watchClipboard = on) }
                    }
                    SwitchRow(R.string.setting_sponsorblock, R.string.setting_sponsorblock_sub, settings.sponsorBlock, last = true) { on ->
                        vm.updateSettings { it.copy(sponsorBlock = on) }
                    }
                }
                // Subscriptions: how often they look for new videos, and whether Android lets them download in
                // the background (asked again whenever the screen comes back from Android's dialog).
                Group(R.string.group_subscriptions) {
                    WheelRow(
                        title = R.string.setting_subscription_hours,
                        sub = stringResource(R.string.setting_subscription_hours_sub),
                        current = settings.subscriptionHours,
                        items = AppSettings.SUBSCRIPTION_HOURS,
                        label = { pluralStringResource(R.plurals.hours_n, it, it) },
                    ) { value -> vm.updateSettings { it.copy(subscriptionHours = value) } }
                    var allowed by remember { mutableStateOf(vm.backgroundAllowed()) }
                    LifecycleResumeEffect(Unit) {
                        allowed = vm.backgroundAllowed()
                        onPauseOrDispose { }
                    }
                    ActionRow(
                        stringResource(R.string.setting_background),
                        stringResource(if (allowed) R.string.setting_background_on else R.string.setting_background_off),
                        last = true,
                        trailing = {
                            if (!allowed) FilledTonalButton(onClick = { runCatching { context.startActivity(vm.backgroundPermissionIntent()) } }) {
                                Text(stringResource(R.string.subscription_battery_allow))
                            }
                        },
                    ) { if (!allowed) runCatching { context.startActivity(vm.backgroundPermissionIntent()) } }
                }
                // The defaults the download sheet starts with; Audio or Video itself is picked there each time.
                Group(R.string.kind_audio) {
                    ChoiceRow(
                        R.string.option_format, defaults.audioFormat,
                        AudioFormat.entries.map { it to audioFormatLabel(it) },
                        sub = stringResource(audioHint(defaults.audioFormat, AudioQuality.HIGH)),
                    ) { value -> vm.updateSettings { it.copy(defaults = it.defaults.copy(audioFormat = value)) } }
                    ChoiceRow(
                        R.string.option_quality, defaults.audioQuality,
                        listOf(AudioQuality.HIGH to R.string.quality_high, AudioQuality.SAVER to R.string.quality_saver),
                        sub = stringResource(if (defaults.audioQuality == AudioQuality.SAVER) R.string.hint_saver else R.string.hint_high),
                        last = true,
                    ) { value -> vm.updateSettings { it.copy(defaults = it.defaults.copy(audioQuality = value)) } }
                }
                Group(R.string.kind_video) {
                    ChoiceRow(
                        R.string.option_format, defaults.videoFormat,
                        VideoFormat.entries.map { it to videoFormatLabel(it) },
                        sub = stringResource(videoHint(defaults.videoFormat)),
                    ) { value -> vm.updateSettings { it.copy(defaults = it.defaults.copy(videoFormat = value)) } }
                    ChoiceRowText(
                        R.string.option_resolution, "${defaults.videoHeight}p",
                        VIDEO_HEIGHTS.map { it to "${it}p" }, defaults.videoHeight,
                        sub = stringResource(R.string.resolution_hint),
                    ) { value -> vm.updateSettings { it.copy(defaults = it.defaults.copy(videoHeight = value)) } }
                    // Subtitles: languages put into every downloaded video (the sheet can choose others per video).
                    var pickingSubtitles by remember { mutableStateOf(false) }
                    RowBase(onClick = { pickingSubtitles = true }, last = false) {
                        TitleAndSub(stringResource(R.string.subtitles), stringResource(R.string.setting_subtitles_sub), modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        ValueChip(subtitleLabel(settings.subtitleLangs)) { pickingSubtitles = true }
                    }
                    if (pickingSubtitles) {
                        SubtitleLanguagesDialog(
                            selected = settings.subtitleLangs,
                            onConfirm = { langs -> vm.updateSettings { it.copy(subtitleLangs = langs) }; pickingSubtitles = false },
                            onDismiss = { pickingSubtitles = false },
                        )
                    }
                    SwitchRow(
                        R.string.setting_auto_subtitles, R.string.setting_auto_subtitles_sub, settings.autoSubtitles,
                        enabled = settings.subtitleLangs.isNotEmpty(), last = true,
                    ) { on -> vm.updateSettings { it.copy(autoSubtitles = on) } }
                }
                Group(R.string.group_playback) {
                    SwitchRow(R.string.setting_autoplay, R.string.setting_autoplay_sub, settings.autoPlayNext) { on -> vm.updateSettings { it.copy(autoPlayNext = on) } }
                    SwitchRow(R.string.setting_announcements, R.string.setting_announcements_sub, settings.pauseForAnnouncements) { on ->
                        vm.updateSettings { it.copy(pauseForAnnouncements = on) }
                    }
                    WheelRow(
                        title = R.string.setting_rewind,
                        sub = null,
                        current = settings.resumeRewindSec,
                        items = AppSettings.REWIND_OPTIONS_SEC,
                        label = { stringResource(R.string.seconds_short, it) },
                    ) { value -> vm.updateSettings { it.copy(resumeRewindSec = value) } }
                    SwitchRow(R.string.skip_silence, R.string.skip_silence_sub, settings.skipSilence, last = true) { on -> vm.updateSettings { it.copy(skipSilence = on) } }
                }
                Group(R.string.group_storage) {
                    val count = entries.count { it.isDone }
                    InfoRow(
                        stringResource(R.string.storage_used),
                        stringResource(
                            R.string.storage_used_value, Format.bytes(context, used), pluralStringResource(R.plurals.entries, count, count),
                            Format.bytes(context, Storage.freeBytes(Storage.workRoot(context, settings.storage))),
                        ),
                    )
                    StorageRow(vm, settings.storage)
                    SwitchRow(R.string.setting_auto_delete, R.string.setting_auto_delete_sub, settings.autoDeleteEnabled) { on ->
                        vm.updateSettings { it.copy(autoDeleteEnabled = on) }
                    }
                    WheelRow(
                        title = R.string.setting_delete_after,
                        sub = stringResource(if (settings.autoDeleteOnlyFinished) R.string.delete_after_from_finished else R.string.delete_after_from_download),
                        current = settings.autoDeleteAfterDays,
                        items = (AppSettings.AUTO_DELETE_MIN_DAYS..AppSettings.AUTO_DELETE_MAX_DAYS).toList(),
                        label = { pluralStringResource(R.plurals.days_after, it, it) },
                        enabled = settings.autoDeleteEnabled,
                    ) { value -> vm.updateSettings { it.copy(autoDeleteDays = value) } }
                    SwitchRow(
                        R.string.setting_only_finished, R.string.setting_only_finished_sub, settings.autoDeleteOnlyFinished,
                        enabled = settings.autoDeleteEnabled,
                    ) { on -> vm.updateSettings { it.copy(autoDeleteOnlyFinished = on) } }
                    SwitchRow(R.string.setting_protect_mixtapes, R.string.setting_protect_mixtapes_sub, settings.protectMixtapes) { on ->
                        vm.updateSettings { it.copy(protectMixtapes = on) }
                    }
                    ActionRow(stringResource(R.string.delete_finished_now), stringResource(R.string.delete_finished_now_sub), last = true) { vm.deleteFinished() }
                }
                Group(R.string.group_ytdlp) {
                    val channel = stringResource(if (settings.updateChannel == UpdateChannel.NIGHTLY) R.string.channel_nightly else R.string.channel_stable)
                    val checked = if (meta.lastCheckAt > 0) stringResource(R.string.ytdlp_checked, Format.relative(context, meta.lastCheckAt)) else stringResource(R.string.ytdlp_never_checked)
                    ButtonRow(
                        stringResource(R.string.ytdlp_version),
                        listOfNotNull(version ?: "…", checked, meta.lastError?.let { stringResource(R.string.ytdlp_last_error) }).joinToString(" · "),
                        button = stringResource(R.string.ytdlp_update_button),
                        busy = updating,
                    ) { vm.updateYtDlp() }
                    ChoiceRow(
                        R.string.setting_channel, settings.updateChannel,
                        listOf(UpdateChannel.NIGHTLY to R.string.channel_nightly_long, UpdateChannel.STABLE to R.string.channel_stable),
                        currentLabel = channel,
                    ) { value -> vm.updateSettings { it.copy(updateChannel = value) } }
                    SwitchRow(R.string.setting_auto_update, R.string.setting_auto_update_sub, settings.autoUpdate) { on -> vm.updateSettings { it.copy(autoUpdate = on) } }
                    SwitchRow(R.string.setting_fast_js, R.string.setting_fast_js_sub, settings.fastJsEngine, last = true) { on -> vm.updateSettings { it.copy(fastJsEngine = on) } }
                }
                Group(R.string.group_about) {
                    val newer = appUpdate?.version
                    ButtonRow(
                        stringResource(R.string.app_name),
                        if (newer != null) stringResource(R.string.app_version_available, BuildConfig.VERSION_NAME, newer) else BuildConfig.VERSION_NAME,
                        button = stringResource(R.string.app_check_button),
                        busy = checkingApp,
                    ) { vm.checkAppUpdateNow() }
                    SwitchRow(R.string.setting_check_app_updates, R.string.setting_check_app_updates_sub, settings.checkAppUpdates) { on ->
                        vm.updateSettings { it.copy(checkAppUpdates = on) }
                    }
                    ActionRow(stringResource(R.string.source_code), SOURCE_URL.removePrefix("https://"), trailing = {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }) { runCatching { uriHandler.openUri(SOURCE_URL) } }
                    InfoRow(stringResource(R.string.licenses), stringResource(R.string.licenses_body))
                    InfoRow(stringResource(R.string.legal_title), stringResource(R.string.legal_body), last = true)
                }
        }
        VerticalScrollbar(scroll)
        }
    }
}

/**
 * Where downloads go: the app folder, Download/Tapedeck (file managers; Android 11+) or the SD card (when
 * one is in). After a change it offers to move the downloads that are already there, and shows how far
 * that has got.
 */
@Composable
private fun StorageRow(vm: MainViewModel, current: StorageLocation) {
    val context = LocalContext.current
    val moving by vm.moving.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var offerMove by remember { mutableStateOf<Pair<StorageLocation, List<Entry>>?>(null) }
    val sdCard = remember { Storage.sdCardRoot(context) != null }
    val sub = moving?.let { (done, total) -> stringResource(R.string.storage_moving, done, total) }
        ?: stringResource(storageHint(current))
    RowBase(onClick = { picking = true }, last = false, enabled = moving == null) {
        TitleAndSub(stringResource(R.string.setting_storage), sub, moving == null, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        ValueChip(stringResource(storageLabel(current)), moving == null) { picking = true }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.setting_storage)) },
            text = {
                Column {
                    StorageLocation.entries.forEach { option ->
                        val available = when (option) {
                            StorageLocation.APP -> true
                            StorageLocation.DOWNLOADS -> Storage.downloadsAvailable
                            StorageLocation.SD_CARD -> sdCard
                        }
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .selectable(selected = option == current, enabled = available, role = Role.RadioButton) {
                                    picking = false
                                    if (option != current) {
                                        vm.updateSettings { it.copy(storage = option) }
                                        val there = vm.downloadsElsewhere(option)
                                        if (there.isNotEmpty()) offerMove = option to there
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == current, onClick = null, enabled = available)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(stringResource(storageLabel(option)), style = MaterialTheme.typography.titleSmall, color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                Text(
                                    stringResource(
                                        when {
                                            option == StorageLocation.DOWNLOADS && !available -> R.string.storage_downloads_old
                                            option == StorageLocation.SD_CARD && !available -> R.string.storage_sd_none
                                            else -> storageHint(option)
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    offerMove?.let { (target, there) ->
        AlertDialog(
            onDismissRequest = { offerMove = null },
            title = { Text(pluralStringResource(R.plurals.storage_move_title, there.size, there.size)) },
            text = { Text(stringResource(R.string.storage_move_body, Format.bytes(context, there.sumOf { it.sizeBytes }), stringResource(storageLabel(target)))) },
            confirmButton = { TextButton(onClick = { offerMove = null; vm.relocate(target) }) { Text(stringResource(R.string.storage_move)) } },
            dismissButton = { TextButton(onClick = { offerMove = null }) { Text(stringResource(R.string.storage_move_not)) } },
        )
    }
}

private fun storageLabel(location: StorageLocation) = when (location) {
    StorageLocation.APP -> R.string.storage_app
    StorageLocation.DOWNLOADS -> R.string.storage_downloads
    StorageLocation.SD_CARD -> R.string.storage_sd
}

private fun storageHint(location: StorageLocation) = when (location) {
    StorageLocation.APP -> R.string.storage_app_sub
    StorageLocation.DOWNLOADS -> R.string.storage_downloads_sub
    StorageLocation.SD_CARD -> R.string.storage_sd_sub
}

@Composable
private fun Group(title: Int, content: @Composable () -> Unit) {
    Column {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp, top = 8.dp),
        )
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
            Column { content() }
        }
    }
}

@Composable
private fun RowBase(onClick: (() -> Unit)?, last: Boolean, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth()
                .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) { content() }
        if (!last) HorizontalDivider(Modifier.padding(start = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun TitleAndSub(title: String, sub: String?, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val alpha = if (enabled) 1f else 0.45f
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
        if (!sub.isNullOrEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
        }
    }
}

@Composable
private fun SwitchRow(title: Int, sub: Int?, checked: Boolean, enabled: Boolean = true, last: Boolean = false, onChange: (Boolean) -> Unit) {
    RowBase(onClick = { onChange(!checked) }, last = last, enabled = enabled) {
        TitleAndSub(stringResource(title), sub?.let { stringResource(it) }, enabled, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun InfoRow(title: String, value: String, last: Boolean = false) {
    RowBase(onClick = null, last = last) { TitleAndSub(title, value, modifier = Modifier.weight(1f)) }
}

/** A value with its action right beside it: "yt-dlp 2026.11.18 … [Update]". */
@Composable
private fun ButtonRow(title: String, value: String, button: String, busy: Boolean, last: Boolean = false, onClick: () -> Unit) {
    RowBase(onClick = null, last = last) {
        TitleAndSub(title, value, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        FilledTonalButton(
            onClick = onClick,
            enabled = !busy,
            contentPadding = PaddingValues(horizontal = 14.dp),
            modifier = Modifier.height(36.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(button, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ActionRow(title: String, sub: String?, last: Boolean = false, trailing: @Composable () -> Unit = {}, onClick: () -> Unit) {
    RowBase(onClick = onClick, last = last) {
        TitleAndSub(title, sub, modifier = Modifier.weight(1f))
        trailing()
    }
}

@Composable
private fun <T> ChoiceRow(
    title: Int,
    current: T,
    options: List<Pair<T, Int>>,
    currentLabel: String? = null,
    sub: String? = null,
    last: Boolean = false,
    onPick: (T) -> Unit,
) {
    val labels = options.map { (value, label) -> value to stringResource(label) }
    ChoiceRowText(title, currentLabel ?: labels.firstOrNull { it.first == current }?.second.orEmpty(), labels, current, sub, last, onPick)
}

/** The value sits in a chip on the right, like [WheelRow]; [sub] explains the current choice. */
@Composable
private fun <T> ChoiceRowText(
    title: Int,
    currentLabel: String,
    options: List<Pair<T, String>>,
    current: T,
    sub: String? = null,
    last: Boolean = false,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    RowBase(onClick = { open = true }, last = last) {
        if (sub == null) {
            TitleAndSub(stringResource(title), currentLabel, modifier = Modifier.weight(1f))
        } else {
            TitleAndSub(stringResource(title), sub, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            ValueChip(currentLabel) { open = true }
        }
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(title)) },
            text = {
                Column {
                    options.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(selected = value == current, role = Role.RadioButton, onClick = {
                                onPick(value)
                                open = false
                            }).padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = value == current, onClick = null)
                            Spacer(Modifier.width(14.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}

/** "[7 days ▾]": the current value of a setting, on the right of its row. */
@Composable
private fun ValueChip(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val container = if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
    val content = if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    Row(
        Modifier.clip(MaterialTheme.shapes.small).background(container)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = content)
        Icon(Icons.Rounded.ArrowDropDown, null, tint = content)
    }
}

/** A setting whose value sits in a chip on the right ("[7 days ▾]") and is chosen on a [WheelPicker]. */
@Composable
private fun <T> WheelRow(
    title: Int,
    sub: String?,
    current: T,
    items: List<T>,
    label: @Composable (T) -> String,
    enabled: Boolean = true,
    last: Boolean = false,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    RowBase(onClick = { open = true }, last = last, enabled = enabled) {
        TitleAndSub(stringResource(title), sub, enabled, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        ValueChip(label(current), enabled) { open = true }
    }
    if (open) {
        WheelPickerDialog(
            title = stringResource(title),
            items = items,
            current = current,
            label = label,
            onConfirm = { value ->
                onPick(value)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}
