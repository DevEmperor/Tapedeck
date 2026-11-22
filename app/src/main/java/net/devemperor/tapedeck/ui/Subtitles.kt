package net.devemperor.tapedeck.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.ClosedCaptionDisabled
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.devemperor.tapedeck.R
import net.devemperor.tapedeck.data.AppSettings
import net.devemperor.tapedeck.player.PlayerUiState
import java.util.Locale

/** "Deutsch" for "de", in the phone's own language. */
fun languageName(code: String): String =
    Locale.forLanguageTag(code).getDisplayLanguage(Locale.getDefault()).takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: code

/** "Off", or the languages: "Deutsch, English". */
@Composable
fun subtitleLabel(languages: List<String>): String =
    if (languages.isEmpty()) stringResource(R.string.subtitles_off) else languages.joinToString(", ") { languageName(it) }

/** Ticks the subtitle languages to download; the phone's names, in the order offered. */
@Composable
fun SubtitleLanguagesDialog(selected: List<String>, onConfirm: (List<String>) -> Unit, onDismiss: () -> Unit) {
    val ticked = remember { mutableStateListOf<String>().apply { addAll(selected) } }
    val offered = (selected + AppSettings.SUBTITLE_LANGUAGES).distinct()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subtitles_dialog_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                offered.forEach { code ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (code in ticked) ticked.remove(code) else ticked.add(code) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = code in ticked, onCheckedChange = null)
                        Spacer(Modifier.width(12.dp))
                        Text(languageName(code), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        // Kept in the order offered, so the label reads the same each time.
        confirmButton = { TextButton(onClick = { onConfirm(offered.filter { it in ticked }) }) { Text(stringResource(R.string.ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * CC in the player, for a video that has subtitles: with one language a tap switches them on and off, with
 * several it opens the choice ("Off", or a language). The choice holds for the next videos too.
 */
@Composable
fun SubtitleButton(vm: MainViewModel, state: PlayerUiState, modifier: Modifier = Modifier, tint: Color = Color.White) {
    if (state.textLanguages.isEmpty()) return
    val settings by vm.settings.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    val on = settings.showSubtitles
    Box(modifier) {
        IconButton(
            onClick = {
                if (state.textLanguages.size == 1) vm.updateSettings { it.copy(showSubtitles = !it.showSubtitles) } else menu = true
            },
            modifier = Modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape),
        ) {
            Icon(if (on) Icons.Rounded.ClosedCaption else Icons.Rounded.ClosedCaptionDisabled, stringResource(R.string.subtitles), tint = tint)
        }
        DropdownMenu(menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.subtitles_off)) },
                trailingIcon = { if (!on) Icon(Icons.Rounded.Check, null) },
                onClick = { menu = false; vm.updateSettings { it.copy(showSubtitles = false) } },
            )
            state.textLanguages.forEach { code ->
                DropdownMenuItem(
                    text = { Text(languageName(code)) },
                    trailingIcon = { if (on && state.textLanguage == code) Icon(Icons.Rounded.Check, null) },
                    onClick = { menu = false; vm.updateSettings { it.copy(showSubtitles = true, subtitlePick = code) } },
                )
            }
        }
    }
}
