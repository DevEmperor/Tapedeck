package net.devemperor.tapedeck.engine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class PreviewInfo(val title: String, val author: String?, val thumbnailUrl: String?)

/**
 * Title and picture for the "new download" sheet. YouTube's oEmbed endpoint answers in a fraction of
 * a second, while yt-dlp would first have to start Python and solve the player's JavaScript.
 */
class Preview(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetch(url: String): PreviewInfo? = withContext(Dispatchers.IO) {
        if (!Links.isYoutube(url)) return@withContext null
        val endpoint = "https://www.youtube.com/oembed".toHttpUrl().newBuilder()
            .addQueryParameter("format", "json")
            .addQueryParameter("url", url)
            .build()
        try {
            http.newCall(Request.Builder().url(endpoint).build()).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val root = json.parseToJsonElement(response.body.string()).jsonObject
                val title = root.text("title") ?: return@withContext null
                PreviewInfo(
                    title = title,
                    author = root.text("author_name"),
                    thumbnailUrl = Links.thumbnailFor(url) ?: root.text("thumbnail_url"),
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
