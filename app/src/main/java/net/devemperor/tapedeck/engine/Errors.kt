package net.devemperor.tapedeck.engine

enum class ErrorKind {
    NETWORK,
    UNAVAILABLE,
    PRIVATE,
    AGE_RESTRICTED,
    MEMBERS_ONLY,
    NOT_YET_LIVE,
    BOT_CHECK,
    LOGIN_REQUIRED,
    BLOCKED,
    UNSUPPORTED_URL,
    FORMAT,
    STORAGE,
    EXTRACTOR,
    ENGINE,
    UNKNOWN,
}

data class DownloadError(val kind: ErrorKind, val detail: String?) {
    /**
     * Errors that usually mean YouTube changed something and an older yt-dlp no longer copes. These
     * are worth one automatic update and retry.
     */
    val mayBeFixedByUpdate: Boolean
        get() = kind == ErrorKind.EXTRACTOR || kind == ErrorKind.BLOCKED || kind == ErrorKind.FORMAT || kind == ErrorKind.UNKNOWN
}

/** Maps yt-dlp's stderr to something a person in a car can act on. */
object Errors {
    fun classify(stderr: String?): DownloadError {
        val text = stderr.orEmpty()
        val lower = text.lowercase()
        val detail = lastErrorLine(text)
        val kind = when {
            "no space left" in lower || "errno 28" in lower -> ErrorKind.STORAGE
            "unsupported url" in lower -> ErrorKind.UNSUPPORTED_URL
            "private video" in lower -> ErrorKind.PRIVATE
            "confirm your age" in lower || "age-restricted" in lower || "age restricted" in lower -> ErrorKind.AGE_RESTRICTED
            "members-only" in lower || "join this channel" in lower || "members only" in lower -> ErrorKind.MEMBERS_ONLY
            "premieres in" in lower || "live event will begin" in lower || "is upcoming" in lower -> ErrorKind.NOT_YET_LIVE
            "not a bot" in lower || "sign in to confirm" in lower -> ErrorKind.BOT_CHECK
            // Instagram, Facebook, X & Co. for anything not public; after the YouTube cases, whose messages
            // also point to --cookies.
            "login required" in lower || "log in" in lower || "logged-in" in lower || "logged in" in lower ||
                "use --cookies" in lower || "--cookies-from-browser" in lower || "authentication" in lower -> ErrorKind.LOGIN_REQUIRED
            // Before UNAVAILABLE, whose "is not available" would match it too.
            "requested format is not available" in lower || "no video formats found" in lower -> ErrorKind.FORMAT
            "video unavailable" in lower || "is not available" in lower || "has been removed" in lower ||
                "this video is unavailable" in lower || "account associated with this video has been terminated" in lower -> ErrorKind.UNAVAILABLE
            "http error 403" in lower || "403: forbidden" in lower || "http error 429" in lower -> ErrorKind.BLOCKED
            "unable to extract" in lower || "signature" in lower || "nsig" in lower || "n challenge" in lower ||
                "js challenge" in lower || "player response" in lower || "precondition check failed" in lower ||
                "please report this issue" in lower -> ErrorKind.EXTRACTOR
            "unable to download" in lower || "failed to resolve" in lower || "name resolution" in lower ||
                "network is unreachable" in lower || "timed out" in lower || "connection reset" in lower ||
                "connection refused" in lower || "remote end closed" in lower || "errno 101" in lower ||
                "errno 7" in lower || "ssl" in lower && "eof" in lower -> ErrorKind.NETWORK
            else -> ErrorKind.UNKNOWN
        }
        return DownloadError(kind, detail)
    }

    fun lastErrorLine(text: String): String? =
        text.lineSequence().map { it.trim() }.filter { it.startsWith("ERROR:") }.lastOrNull()?.removePrefix("ERROR:")?.trim()
            ?: text.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }?.take(300)
}
