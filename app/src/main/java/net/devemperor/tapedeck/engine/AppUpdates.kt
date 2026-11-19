package net.devemperor.tapedeck.engine

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import net.devemperor.tapedeck.BuildConfig
import net.devemperor.tapedeck.data.Settings
import net.devemperor.tapedeck.update.InstallResultReceiver
import net.devemperor.tapedeck.update.PackageReplacedReceiver
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import kotlin.time.Duration.Companion.hours

/** A Tapedeck release on GitHub, with the APK that fits this phone. */
data class AppRelease(
    val version: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long = 0,
    /** Lower-case hex SHA-256 that GitHub lists for the asset, if it does. */
    val apkSha256: String? = null,
)

sealed interface AppInstallState {
    data object Idle : AppInstallState
    /** Android first wants "Install unknown apps" allowed for Tapedeck. */
    data object NeedsPermission : AppInstallState
    data class Downloading(val bytes: Long, val total: Long) : AppInstallState
    data object Installing : AppInstallState
    data class Failed(val reason: String?) : AppInstallState
}

/**
 * Tapedeck is not on Google Play, so it looks for its own updates: when the app is opened (once per start,
 * then at most every six hours, never in the background) it asks GitHub for the latest release. On request
 * it downloads the APK built for this phone, checks it against GitHub's SHA-256, and hands it to Android's
 * package installer. Android itself only accepts it if it carries the same signature as the installed app.
 */
class AppUpdates(private val http: OkHttpClient, private val settings: Settings) {
    private val _available = MutableStateFlow<AppRelease?>(null)
    private val _install = MutableStateFlow<AppInstallState>(AppInstallState.Idle)

    /** A newer release the user has not put away yet. */
    val available: StateFlow<AppRelease?> = _available.asStateFlow()
    val install: StateFlow<AppInstallState> = _install.asStateFlow()

    @Volatile
    private var checkedThisProcess = false

    /**
     * Once per app start, then at most every six hours. The answer is only kept in memory, so a fresh
     * start asks again rather than showing a stale hint.
     */
    suspend fun checkIfDue() {
        if (!settings.current.checkAppUpdates) return
        val since = System.currentTimeMillis() - settings.engine.value.lastAppCheckAt
        if (checkedThisProcess && since < CHECK_INTERVAL.inWholeMilliseconds) return
        checkedThisProcess = true
        check()
    }

    suspend fun check(): AppRelease? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Tapedeck/${BuildConfig.VERSION_NAME}")
            .build()
        val release = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                parse(response.body.string(), Build.SUPPORTED_ABIS.toList())
            }
        } catch (e: Exception) {
            Log.w(TAG, "release check failed", e)
            return@withContext null
        }
        settings.updateEngine { it.copy(lastAppCheckAt = System.currentTimeMillis()) }
        val newer = release?.takeIf { Versions.isNewer(it.version, BuildConfig.VERSION_NAME) }
        _available.value = newer?.takeIf { it.version != settings.engine.value.dismissedAppVersion }
        newer
    }

    fun dismiss() {
        val version = _available.value?.version ?: return
        settings.updateEngine { it.copy(dismissedAppVersion = version) }
        _available.value = null
        _install.value = AppInstallState.Idle
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** The system page with Tapedeck's "Install unknown apps" switch. */
    fun permissionIntent(context: Context): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads, verifies and installs [release]. Android ends Tapedeck once the update is in place. */
    suspend fun downloadAndInstall(context: Context, release: AppRelease) {
        val url = release.apkUrl ?: run {
            _install.value = AppInstallState.Failed(null)
            return
        }
        if (!canInstall(context)) {
            _install.value = AppInstallState.NeedsPermission
            return
        }
        try {
            val apk = download(context, url, release)
            _install.value = AppInstallState.Installing
            install(context, apk)
        } catch (e: CancellationException) {
            _install.value = AppInstallState.Idle
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "update failed", e)
            _install.value = AppInstallState.Failed(e.message)
        }
    }

    // Android blocks activity starts from the background, so a confirmation that arrives while Tapedeck is
    // not on screen waits here until the activity is back.
    @Volatile
    private var pendingConfirm: Intent? = null

    fun holdConfirmation(intent: Intent) {
        pendingConfirm = intent
    }

    fun takePendingConfirmation(): Intent? = pendingConfirm.also { pendingConfirm = null }

    /** Called by [InstallResultReceiver] when the installer gives up or the user declines. */
    fun onInstallFailed(message: String?) {
        _install.value = AppInstallState.Failed(message)
    }

    fun onInstallAborted() {
        _install.value = AppInstallState.Idle
    }

    /**
     * Back to the start. Needed because the installer reports nothing when its dialog is closed by tapping
     * beside it: the session would otherwise wait forever and the banner say "Installing…".
     */
    fun reset(context: Context) {
        _install.value = AppInstallState.Idle
        PackageReplacedReceiver.clearPending(context)
        runCatching {
            val installer = context.packageManager.packageInstaller
            installer.mySessions.forEach { installer.abandonSession(it.sessionId) }
        }
    }

    private suspend fun download(context: Context, url: String, release: AppRelease): File = withContext(Dispatchers.IO) {
        val file = File(updateDir(context), "tapedeck-${release.version}.apk")
        // A second try (e.g. after the installer dialog was closed) reuses the APK already checked.
        if (file.exists() && matches(file, release)) return@withContext file
        val dir = updateDir(context).apply { deleteRecursively(); mkdirs() }
        val request = Request.Builder().url(url).header("User-Agent", "Tapedeck/${BuildConfig.VERSION_NAME}").build()
        val digest = MessageDigest.getInstance("SHA-256")
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val total = response.body.contentLength().takeIf { it > 0 } ?: release.apkSize
            var done = 0L
            var lastReport = 0L
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (done - lastReport > 256 * 1024) {
                            lastReport = done
                            _install.value = AppInstallState.Downloading(done, total)
                        }
                    }
                }
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        if (release.apkSha256 != null && release.apkSha256 != actual) {
            file.delete()
            error("checksum mismatch")
        }
        file
    }

    private fun matches(file: File, release: AppRelease): Boolean {
        release.apkSha256?.let { expected ->
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) } == expected
        }
        return release.apkSize > 0 && file.length() == release.apkSize
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        // A session left open by a dialog that was tapped away.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // From Android 12 on, an app that installed its own last update may update itself without the
            // confirmation dialog; the first time Android still asks.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("tapedeck.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            val callback = PendingIntent.getBroadcast(
                context, sessionId, Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName), flags,
            )
            PackageReplacedReceiver.markPending(context)
            session.commit(callback.intentSender)
        }
    }

    companion object {
        private const val TAG = "TapedeckAppUpdates"
        const val RELEASES_PAGE = "https://github.com/DevEmperor/Tapedeck/releases"
        private const val LATEST_URL = "https://api.github.com/repos/DevEmperor/Tapedeck/releases/latest"
        private val CHECK_INTERVAL = 6.hours
        private val json = Json { ignoreUnknownKeys = true }

        fun updateDir(context: Context) = File(context.cacheDir, "app-update")

        /** GitHub's "latest release" answer, reduced to the version and the APK for [abis] (pure, unit-tested). */
        fun parse(body: String, abis: List<String>): AppRelease? {
            val root = json.parseToJsonElement(body).jsonObject
            val tag = root.text("tag_name") ?: return null
            val page = root.text("html_url") ?: RELEASES_PAGE
            data class Asset(val name: String, val url: String, val size: Long, val sha256: String?)
            val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
                val asset = element as? JsonObject ?: return@mapNotNull null
                Asset(
                    name = asset.text("name") ?: return@mapNotNull null,
                    url = asset.text("browser_download_url") ?: return@mapNotNull null,
                    size = (asset["size"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0,
                    sha256 = asset.text("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase(),
                )
            }
            // The APK built for this phone's processor, else the universal one.
            val apk = abis.firstNotNullOfOrNull { abi -> assets.firstOrNull { it.name.endsWith("-$abi.apk") } }
                ?: assets.firstOrNull { it.name.endsWith("-universal.apk") }
            return AppRelease(version = tag.removePrefix("v"), pageUrl = page, apkUrl = apk?.url, apkSize = apk?.size ?: 0, apkSha256 = apk?.sha256)
        }

        private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}

object Versions {
    /** "0.2.0" against "0.1.0-debug": compares the numbers, ignoring a "v" prefix and any "-suffix". */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = numbers(candidate)
        val b = numbers(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int> =
        version.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
}
