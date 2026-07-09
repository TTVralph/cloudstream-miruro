package com.ttvralph.miruroapp.data

import android.util.Base64
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.GZIPInputStream

private const val MIRURO_URL = "https://www.miruro.tv"
private const val PIPE_URL = "$MIRURO_URL/api/secure/pipe"
private val PROVIDER_PRIORITY = listOf("zoro", "animepahe", "gogoanime", "kiwi")

// Reimplements MiruroProvider's pipe protocol locally since this module doesn't depend on Cloudstream.
class MiruroRepository {
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()
    private val headers = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Content-Type" to "application/json",
        "Origin" to MIRURO_URL,
        "Referer" to "$MIRURO_URL/",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"
    )

    suspend fun episodeCandidates(anilistId: Int): Map<Int, List<EpisodeSourceCandidate>> = withContext(Dispatchers.IO) {
        val providers = runCatching { fetchRawEpisodes(anilistId) }.getOrNull()?.path("providers")
            ?: return@withContext emptyMap()
        if (!providers.isObject) return@withContext emptyMap()

        val byEpisode = mutableMapOf<Int, MutableList<EpisodeSourceCandidate>>()
        providers.fields().forEach { providerEntry ->
            val provider = providerEntry.key
            val episodesNode = providerEntry.value.path("episodes")
            val categorized = if (episodesNode.isArray) {
                listOf("sub" to episodesNode)
            } else {
                listOfNotNull(
                    episodesNode.path("sub").takeIf { it.isArray }?.let { "sub" to it },
                    episodesNode.path("dub").takeIf { it.isArray }?.let { "dub" to it }
                )
            }
            categorized.forEach { (category, list) ->
                list.forEach { ep ->
                    val rawId = text(ep, "id", "url") ?: return@forEach
                    val number = ep.path("number").asInt(0)
                    if (number <= 0) return@forEach
                    val episodeId = normalizeEpisodeId(rawId)
                    byEpisode.getOrPut(number) { mutableListOf() }.add(EpisodeSourceCandidate(provider, episodeId, category))
                }
            }
        }
        byEpisode.mapValues { (_, list) -> list.sortedBy { providerRank(it.provider) } }
    }

    suspend fun resolveSource(anilistId: Int, candidates: List<EpisodeSourceCandidate>): PlaybackSource? = withContext(Dispatchers.IO) {
        candidates.forEach { candidate ->
            val sources = runCatching { fetchSources(candidate, anilistId) }.getOrNull() ?: return@forEach

            val streamsNode = findFirstArray(sources, "streams", "sources") ?: return@forEach
            val best = streamsNode.maxByOrNull { qualityRank(streamQuality(it)) } ?: return@forEach
            val url = streamUrl(best) ?: return@forEach
            val typeStr = streamType(best)?.lowercase(Locale.ROOT)
            val subtitles = findFirstArray(sources, "subtitles", "tracks")?.mapNotNull { sub ->
                val subUrl = text(sub, "file", "url") ?: return@mapNotNull null
                SubtitleTrack(subUrl, text(sub, "label", "lang", "language") ?: "Subtitle", text(sub, "lang", "language"))
            }.orEmpty()

            return@withContext PlaybackSource(
                url = url,
                label = "${candidate.provider.uppercase(Locale.ROOT)} ${streamQuality(best)}",
                type = playbackType(typeStr, url),
                headers = playbackHeaders(candidate.provider),
                subtitleTracks = subtitles
            )
        }
        null
    }

    private fun fetchSources(candidate: EpisodeSourceCandidate, anilistId: Int): JsonNode {
        if (candidate.episodeId.startsWith("watch/")) {
            val direct = runCatching { pipeGet(candidate.episodeId) }.getOrNull()
            if (direct != null && hasPlayableStreams(direct)) return direct
        }

        val sourceEpisodeId = resolveSourceEpisodeId(candidate, anilistId)
        return pipeGet(
            "sources",
            mapper.createObjectNode().apply {
                put("episodeId", urlSafeBase64(sourceEpisodeId.toByteArray()))
                put("provider", candidate.provider)
                put("category", candidate.category)
                put("anilistId", anilistId)
            }
        )
    }

    private fun resolveSourceEpisodeId(candidate: EpisodeSourceCandidate, anilistId: Int): String {
        if (!candidate.episodeId.startsWith("watch/")) return candidate.episodeId
        val slug = candidate.episodeId.substringAfterLast("/")
        val episodes = fetchRawEpisodes(anilistId)
            .path("providers")
            .path(candidate.provider)
            .path("episodes")
            .path(candidate.category)
        if (!episodes.isArray) return candidate.episodeId
        episodes.forEach { ep ->
            val rawId = normalizeEpisodeId(text(ep, "id") ?: return@forEach)
            val number = ep.path("number").asInt(0)
            if (number > 0 && generatedEpisodeSlug(rawId, number) == slug) return rawId
        }
        return candidate.episodeId
    }

    private fun hasPlayableStreams(node: JsonNode): Boolean =
        findFirstArray(node, "streams", "sources")?.any { streamUrl(it) != null } == true

    private fun fetchRawEpisodes(anilistId: Int): JsonNode =
        pipeGet("episodes", mapper.createObjectNode().apply { put("anilistId", anilistId) })

    private fun pipeGet(path: String, query: JsonNode? = null): JsonNode {
        val payload = mapper.createObjectNode().apply {
            put("path", path)
            put("method", "GET")
            if (query == null) putNull("query") else set<JsonNode>("query", query)
            putNull("body")
            put("version", "0.1.0")
        }
        val encoded = URLEncoder.encode(encodePipeRequest(payload), "UTF-8")
        val request = Request.Builder().url("$PIPE_URL?e=$encoded").apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build()
        val body = client.newCall(request).execute().use { it.body?.string().orEmpty() }.trim()
        return decodePipeResponse(body)
    }

    private fun urlSafeBase64(value: ByteArray): String =
        Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun encodePipeRequest(payload: JsonNode): String = urlSafeBase64(mapper.writeValueAsBytes(payload))

    private fun decodePipeResponse(value: String): JsonNode {
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        val compressed = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        val decoded = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
        return mapper.readTree(decoded)
    }

    private fun decodeUrlSafeBase64Text(value: String): String? = runCatching {
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP))
    }.getOrNull()

    private fun normalizeEpisodeId(value: String): String {
        val decoded = decodeUrlSafeBase64Text(value)
        return if (decoded != null && ":" in decoded) decoded else value
    }

    private fun generatedEpisodeSlug(episodeId: String, number: Int): String {
        val prefix = episodeId.substringBefore(":")
        return "$prefix-$number"
    }

    private fun text(node: JsonNode, vararg names: String): String? =
        names.firstNotNullOfOrNull { name -> node.get(name)?.asText()?.takeIf { it.isNotBlank() && it != "null" } }

    private fun firstArray(node: JsonNode, vararg names: String): JsonNode? =
        names.firstNotNullOfOrNull { name -> node.path(name).takeIf { it.isArray } }

    private fun findFirstArray(node: JsonNode, vararg names: String): JsonNode? {
        if (node.isObject) {
            firstArray(node, *names)?.let { return it }
            node.fields().forEach { entry -> findFirstArray(entry.value, *names)?.let { return it } }
        } else if (node.isArray) {
            node.forEach { child -> findFirstArray(child, *names)?.let { return it } }
        }
        return null
    }

    private fun streamUrl(node: JsonNode): String? =
        text(node, "url", "file", "stream")
            ?: node.path("source").takeIf { it.isObject }?.let { text(it, "url", "file", "stream") }

    private fun streamType(node: JsonNode): String? =
        text(node, "type", "format")
            ?: node.path("source").takeIf { it.isObject }?.let { text(it, "type", "format") }

    private fun streamQuality(node: JsonNode): String =
        text(node, "quality", "label", "resolution")
            ?: node.path("source").takeIf { it.isObject }?.let { text(it, "quality", "label", "resolution") }
            ?: "Auto"

    private fun qualityRank(label: String): Int = Regex("""(\d{3,4})""").find(label)?.value?.toIntOrNull() ?: 0

    private fun playbackType(type: String?, url: String): PlaybackType {
        val normalized = type?.lowercase(Locale.ROOT)
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            normalized in setOf("dash", "mpd") || path.endsWith(".mpd") -> PlaybackType.DASH
            normalized in setOf("mp4", "file") || path.endsWith(".mp4") -> PlaybackType.MP4
            normalized in setOf("hls", "m3u8") || path.endsWith(".m3u8") -> PlaybackType.HLS
            else -> PlaybackType.UNKNOWN
        }
    }

    private fun playbackHeaders(provider: String): Map<String, String> {
        val providerReferer = when (provider.lowercase(Locale.ROOT)) {
            "animepahe" -> "https://kwik.si/"
            "gogoanime" -> "https://gogocdn.net/"
            else -> "$MIRURO_URL/"
        }
        return mapOf(
            "Referer" to providerReferer,
            "Origin" to providerReferer.trimEnd('/'),
            "User-Agent" to headers.getValue("User-Agent")
        )
    }

    private fun providerRank(provider: String): Int {
        val index = PROVIDER_PRIORITY.indexOf(provider.lowercase(Locale.ROOT))
        return if (index == -1) PROVIDER_PRIORITY.size else index
    }
}
