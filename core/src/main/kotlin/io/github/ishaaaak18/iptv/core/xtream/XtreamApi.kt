package io.github.ishaaaak18.iptv.core.xtream

import io.github.ishaaaak18.iptv.core.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

@Serializable
data class XtreamUserInfo(
    val username: String? = null,
    val status: String? = null,
    @SerialName("exp_date") val expDate: JsonElement? = null,
    @SerialName("active_cons") val activeCons: JsonElement? = null,
    @SerialName("max_connections") val maxConnections: JsonElement? = null,
    @SerialName("is_trial") val isTrial: JsonElement? = null,
) {
    /** Expiry as epoch seconds, or null when the account never expires. */
    val expiresAt: Long? get() = expDate.asText()?.toLongOrNull()
    val active: Int get() = activeCons.asText()?.toIntOrNull() ?: 0
    val max: Int? get() = maxConnections.asText()?.toIntOrNull()
}

@Serializable
data class XtreamAccount(@SerialName("user_info") val userInfo: XtreamUserInfo? = null)

@Serializable
data class XtreamCategory(
    @SerialName("category_id") val id: String = "",
    @SerialName("category_name") val name: String = "",
)

@Serializable
data class XtreamLiveStream(
    @SerialName("stream_id") val streamId: JsonElement? = null,
    val name: String? = null,
    @SerialName("stream_icon") val icon: String? = null,
    @SerialName("epg_channel_id") val epgChannelId: String? = null,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("tv_archive_duration") val archiveDays: JsonElement? = null,
)

internal fun JsonElement?.asText(): String? =
    (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

class XtreamException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Minimal Xtream Codes player_api client. */
class XtreamApi(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true },
) {
    suspend fun account(c: XtreamCredentials): XtreamAccount = get(c.apiUrl)

    suspend fun liveCategories(c: XtreamCredentials): List<XtreamCategory> =
        get("${c.apiUrl}&action=get_live_categories")

    suspend fun liveStreams(c: XtreamCredentials): List<XtreamLiveStream> =
        get("${c.apiUrl}&action=get_live_streams")

    /** Loads all live streams as [Channel]s, grouped by their category name. */
    suspend fun liveChannels(c: XtreamCredentials, extension: String = "m3u8"): List<Channel> {
        val categories = liveCategories(c).associate { it.id to it.name }
        return liveStreams(c).mapNotNull { s ->
            val id = s.streamId.asText() ?: return@mapNotNull null
            Channel(
                id = "x:$id",
                name = s.name?.trim().orEmpty().ifBlank { id },
                url = "${c.host}/live/${c.username}/${c.password}/$id.$extension",
                group = categories[s.categoryId].orEmpty(),
                logo = s.icon.orEmpty(),
                tvgId = s.epgChannelId.orEmpty(),
                catchupDays = s.archiveDays.asText()?.toIntOrNull() ?: 0,
            )
        }
    }

    private suspend inline fun <reified T> get(url: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        val body = try {
            client.newCall(request).execute().use { r ->
                if (!r.isSuccessful) throw XtreamException("HTTP ${r.code}")
                r.body?.string().orEmpty()
            }
        } catch (e: XtreamException) {
            throw e
        } catch (e: Exception) {
            throw XtreamException(e.message ?: "Network error", e)
        }
        try {
            json.decodeFromString<T>(body)
        } catch (e: Exception) {
            throw XtreamException("Invalid server response", e)
        }
    }

    companion object {
        const val USER_AGENT = "IPTVPlayer/1.0"
    }
}
