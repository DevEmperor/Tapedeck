package net.devemperor.tapedeck.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.devemperor.tapedeck.R

enum class Screen { LIBRARY, PLAYER, SETTINGS }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun TapedeckRoot(vm: MainViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.LIBRARY) }
    // A double tap on the mini player opens a video straight in full screen.
    var playerFullscreen by rememberSaveable { mutableStateOf(false) }
    // An open mixtape lies between the library and the player: closing the player returns to it.
    var openMixtape by rememberSaveable { mutableStateOf<String?>(null) }
    // Where the player was opened: a card in a list (its picture travels into the player and back) or,
    // with null, the mini player.
    var playerOrigin by rememberSaveable { mutableStateOf<String?>(null) }
    fun openPlayer(origin: String?, fullscreen: Boolean = false) {
        playerOrigin = origin
        playerFullscreen = fullscreen
        screen = Screen.PLAYER
    }
    val sheet by vm.sheet.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is UiEvent.Message -> launch { snackbar.showSnackbar(event.render(context)) }
                is UiEvent.Deleted -> launch {
                    val count = event.ids.size
                    val result = snackbar.showSnackbar(
                        message = context.resources.getQuantityString(R.plurals.deleted, count, count),
                        actionLabel = context.getString(R.string.undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) vm.undoDelete(event.ids) else vm.commitDelete(event.ids)
                }
            }
        }
    }

    BackHandler(enabled = screen != Screen.LIBRARY || openMixtape != null) {
        if (screen != Screen.LIBRARY) screen = Screen.LIBRARY else openMixtape = null
    }

    // Covers and cassettes travel between screens (see SharedElements.kt).
    SharedTransitionLayout(Modifier.fillMaxSize()) { CompositionLocalProvider(LocalSharedScope provides this) {
    Box(Modifier.fillMaxSize()) {
        LibraryScreen(
            vm,
            playerOpen = screen == Screen.PLAYER,
            openMixtape = openMixtape,
            playerOrigin = playerOrigin,
            onOpenPlayer = { openPlayer(origin = null) },
            onOpenPlayerFrom = { openPlayer(origin = it) },
            onOpenFullscreen = { openPlayer(origin = null, fullscreen = true) },
            onOpenSettings = { screen = Screen.SETTINGS },
            onOpenMixtape = { openMixtape = it },
        )
        // Kept for the slide-out, after openMixtape is already null.
        var shownMixtape by remember { mutableStateOf(openMixtape) }
        if (openMixtape != null) shownMixtape = openMixtape
        // Screens move as long as the elements travelling with them (TRAVEL_MS), or the travel is cut short.
        AnimatedVisibility(
            openMixtape != null,
            enter = slideInHorizontally(travelSpec()) { it } + fadeIn(travelSpec()),
            exit = slideOutHorizontally(travelSpec()) { it } + fadeOut(travelSpec()),
        ) {
            shownMixtape?.let { id ->
                MixtapeScreen(
                    vm,
                    id,
                    visibility = this,
                    playerOpen = screen == Screen.PLAYER,
                    playerOrigin = playerOrigin,
                    onBack = { openMixtape = null },
                    onOpenPlayer = { openPlayer(origin = null) },
                    onOpenPlayerFrom = { openPlayer(origin = it) },
                    onOpenFullscreen = { openPlayer(origin = null, fullscreen = true) },
                )
            }
        }
        AnimatedVisibility(
            screen == Screen.PLAYER,
            enter = slideInVertically(travelSpec()) { it } + fadeIn(travelSpec()),
            exit = slideOutVertically(travelSpec()) { it } + fadeOut(travelSpec()),
        ) {
            PlayerScreen(vm, startFullscreen = playerFullscreen, visibility = this, onClose = {
                // Something else plays by now than the card it was opened from: the picture goes back to
                // the mini player instead.
                if (playerOrigin != null && playerOrigin != vm.player.state.value.entryId) playerOrigin = null
                screen = Screen.LIBRARY
            })
        }
        AnimatedVisibility(
            screen == Screen.SETTINGS,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
        ) { SettingsScreen(vm, onBack = { screen = Screen.LIBRARY }) }
        // Above the add button and, while something is loaded, above the mini player too.
        val playerState by vm.player.state.collectAsStateWithLifecycle()
        val lift = when {
            screen != Screen.LIBRARY -> 16.dp
            playerState.entryId != null -> 176.dp
            else -> 88.dp
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = lift))
    }
    } }

    sheet?.let { state ->
        AddSheet(
            state = state,
            onUrl = vm::onUrlChanged,
            onOptions = vm::setOptions,
            onWholePlaylist = vm::setWholePlaylist,
            onOpenFormats = vm::openFormats,
            onCloseFormats = vm::closeFormats,
            onPickFormat = vm::pickFormat,
            onClearCustom = vm::clearCustomFormat,
            onRetryDetails = vm::retryDetails,
            mixtapes = vm.mixtapes.collectAsStateWithLifecycle().value,
            onMixtape = vm::setSheetMixtape,
            onConfirm = {
                askForNotifications()
                vm.confirmSheet()
            },
            onDismiss = vm::closeSheet,
        )
    }
}
