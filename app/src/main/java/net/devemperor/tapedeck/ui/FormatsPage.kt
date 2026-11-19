package net.devemperor.tapedeck.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.devemperor.tapedeck.Format
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.MediaKind
import net.devemperor.tapedeck.data.VideoFormat
import net.devemperor.tapedeck.engine.Formats
import net.devemperor.tapedeck.engine.MediaDetails
import net.devemperor.tapedeck.engine.MediaFormat

/**
 * What `yt-dlp -F` lists, as cards: picture with sound, picture only, sound only. For a video one
 * picture is picked (a picture-only one gets the best sound to go with it, which can be changed); for
 * audio one sound. Size and codecs on each card, the sum of the pick at the bottom.
 */
@Composable
fun FormatsPage(
    state: AddSheetState,
    onBack: () -> Unit,
    onPick: (MediaFormat?, MediaFormat?) -> Unit,
    onRetry: () -> Unit,
) {
    val details = state.details
    val kind = state.options.kind
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
            Spacer(Modifier.width(4.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.formats_title), style = MaterialTheme.typography.titleLarge)
                val subtitle = details?.title ?: state.preview?.title
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            details != null && details.formats.isNotEmpty() -> FormatList(details, kind, state.options.videoFormat, state.options.customFormat, onPick)
            state.loadingDetails -> Status {
                CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.formats_loading), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.formats_loading_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> Status {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(if (details != null) R.string.formats_empty else state.detailsError?.let(::errorText) ?: R.string.formats_failed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
        }
    }
}

@Composable
private fun Status(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun ColumnScope.FormatList(
    details: MediaDetails,
    kind: MediaKind,
    container: VideoFormat,
    current: String?,
    onPick: (MediaFormat?, MediaFormat?) -> Unit,
) {
    val videos = details.videos
    val muxed = videos.filter { it.isMuxed }
    val pictureOnly = videos.filter { it.isVideoOnly }
    val audios = details.audios
    // Sites without a separate sound (some only offer finished videos): the sound is taken out of one.
    val audioChoices = if (kind == MediaKind.AUDIO && audios.isEmpty()) muxed else audios
    val preferAac = container == VideoFormat.MP4

    val currentIds = current?.split('+').orEmpty()
    var video by remember(details, kind) {
        mutableStateOf(if (kind == MediaKind.VIDEO) videos.firstOrNull { it.id == currentIds.firstOrNull() } else null)
    }
    var audio by remember(details, kind) {
        mutableStateOf(
            audioChoices.firstOrNull { it.id == currentIds.lastOrNull() && (kind == MediaKind.AUDIO || currentIds.size == 2) },
        )
    }

    // Languages only matter when there is more than one (dubbed YouTube videos).
    val showLanguage = audios.mapNotNull { it.language }.distinct().size > 1

    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if (kind == MediaKind.VIDEO) {
            if (muxed.isNotEmpty()) {
                Section(Icons.Rounded.VideoLibrary, R.string.formats_muxed)
                Cards(muxed, details, video?.let(::listOf).orEmpty(), showLanguage) { picked ->
                    video = picked
                    audio = null
                }
            }
            if (pictureOnly.isNotEmpty()) {
                Section(Icons.Rounded.Movie, R.string.formats_video_only)
                Cards(pictureOnly, details, video?.let(::listOf).orEmpty(), showLanguage) { picked ->
                    video = picked
                    if (audio == null) audio = details.bestAudioFor(picked, preferAac)
                }
            }
        }
        if (audioChoices.isNotEmpty()) {
            Section(Icons.Rounded.GraphicEq, R.string.formats_audio)
            if (kind == MediaKind.AUDIO && audios.isEmpty()) {
                Text(
                    stringResource(R.string.formats_audio_from_video),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (kind == MediaKind.VIDEO && video?.isMuxed == true) {
                Text(
                    stringResource(R.string.formats_sound_included),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            Cards(audioChoices, details, audio?.let(::listOf).orEmpty(), showLanguage) { picked ->
                when {
                    // Picture-only without sound is a valid pick too: tapping the sound again takes it away.
                    kind == MediaKind.VIDEO && audio == picked && video?.isVideoOnly == true -> audio = null
                    else -> {
                        audio = picked
                        // A picture with its own sound takes no second one: yt-dlp would drop it.
                        if (video?.isMuxed == true) video = null
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    val context = LocalContext.current
    val ready = if (kind == MediaKind.VIDEO) video != null else audio != null
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            val label = if (kind == MediaKind.VIDEO) Formats.label(video, audio) { context.getString(R.string.format_kbit, it) } else Formats.label(null, audio) { context.getString(R.string.format_kbit, it) }
            Text(
                label ?: stringResource(if (kind == MediaKind.VIDEO) R.string.formats_pick_video else R.string.formats_pick_audio),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val parts = listOfNotNull(if (kind == MediaKind.VIDEO) video else null, audio)
            val sizes = parts.map { it.size(details.durationSec) }
            val sub = when {
                parts.isEmpty() -> null
                sizes.any { it == null } -> stringResource(R.string.formats_size_unknown)
                else -> {
                    val sum = sizes.sumOf { it ?: 0 }
                    val estimate = parts.any { it.isSizeEstimate() }
                    (if (estimate) "≈ " else "") + Format.bytes(context, sum)
                }
            }
            val noSound = kind == MediaKind.VIDEO && video?.isVideoOnly == true && audio == null
            val line = listOfNotNull(sub, if (noSound) stringResource(R.string.formats_no_sound) else null).joinToString(" · ")
            if (line.isNotEmpty()) {
                Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = { onPick(if (kind == MediaKind.VIDEO) video else null, audio) }, enabled = ready, shape = RoundedCornerShape(14.dp)) {
            Text(stringResource(R.string.formats_use))
        }
    }
}

@Composable
private fun Section(icon: ImageVector, title: Int) {
    Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

/** Two cards per row: enough room for "1080p60 HDR" and "VP9 · WebM · 5.1". */
@Composable
private fun Cards(
    formats: List<MediaFormat>,
    details: MediaDetails,
    selected: List<MediaFormat>,
    showLanguage: Boolean,
    onClick: (MediaFormat) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        formats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { format ->
                    FormatCard(format, details, format in selected, showLanguage, Modifier.weight(1f)) { onClick(format) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun FormatCard(
    format: MediaFormat,
    details: MediaDetails,
    selected: Boolean,
    showLanguage: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val container = if (selected) colors.primaryContainer else colors.surfaceContainerHigh
    val content = if (selected) colors.onPrimaryContainer else colors.onSurface
    val muted = if (selected) colors.onPrimaryContainer.copy(alpha = 0.75f) else colors.onSurfaceVariant
    Surface(
        color = container,
        shape = MaterialTheme.shapes.medium,
        border = if (selected) BorderStroke(1.5.dp, colors.primary) else null,
        modifier = modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
    ) {
        Box {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
                val kbit = format.bitrate?.let { context.getString(R.string.format_kbit, it) }
                val title = if (format.isAudioOnly) kbit ?: format.note ?: format.id else format.resolutionLabel ?: format.note ?: format.id
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(6.dp))
                    // yt-dlp's format id, small: for those who know `-F`.
                    Text(
                        format.id,
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = muted,
                        maxLines = 1,
                    )
                }
                val codecs = listOfNotNull(format.videoCodecName, format.audioCodecName).joinToString(" + ").ifEmpty { null }
                val tags = buildList {
                    codecs?.let(::add)
                    add(format.ext.uppercase().let { if (it == "WEBM") "WebM" else it })
                    if ((format.channels ?: 0) > 2) add("${format.channels!! - 1}.1")
                    if (format.id.endsWith("-drc")) add("DRC")
                    if (format.protocol?.startsWith("m3u8") == true) add("HLS")
                    if (showLanguage) format.language?.let(::add)
                }
                Text(tags.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val size = format.size(details.durationSec)
                val sizeText = size?.let { (if (format.isSizeEstimate()) "≈ " else "") + Format.bytes(context, it) }
                val second = listOfNotNull(sizeText, if (!format.isAudioOnly) kbit else null).joinToString(" · ")
                Text(second.ifEmpty { "–" }, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1)
            }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle, null, tint = colors.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(18.dp),
                )
            }
        }
    }
}
