package com.ttvralph.miruroapp.data

import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val TAG = "MiruroRepository"
private const val MIRURO_URL = "https://www.miruro.tv"
private const val PIPE_URL = "$MIRURO_URL/api/secure/pipe"
private const val MAX_PROVIDER_ATTEMPTS = 6
private const val MAX_QUALITY_CHOICES = 8
private const val MAX_CHOICES_PER_PROVIDER = 2
private const val PIPE_CALL_TIMEOUT_SECONDS = 9L
private val PROVIDER_PRIORITY = listOf("zoro", "animepahe", "gogoanime", "kiwi")

private val STREAM_ARRAY_KEYS = arrayOf("streams", "sources", "playlist")
private val SUBTITLE_ARRAY_KEYS = arrayOf("subtitles", "tracks", "captions")
private val STREAM_URL_KEYS = arrayOf("url", "file", "stream", "link")

data class EpisodeMetadataWithSources(
    val metadata: EpisodeMetadata,
    val candidates: List<EpisodeSourceCandidate>
)

class MiruroRepository {
    private val client = OkHttpClient.Builder()
        .callTimeout(PIPE_CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()
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

    suspend fun episodeCandidates(anilistId: Int): Map<Int, List<EpisodeSourceCandidate>> =
        episodeData(anilistId).mapValues { it.value.candidates }

    suspend fun episodeData(anilistId: Int): Map<Int, EpisodeMetadataWithSources> =
        withContext(Dispatchers.IO) {
            val providers = resultPreservingCancellation { fetchRawEpisodes(anilistId) }
                .onFailure { error ->
                    Log.w(TAG, "episodeData: fetchRawEpisodes failed for anilistId=$anilistId", error)
                }
                .getOrNull()
                ?.path("providers")
                ?: return@withContext emptyMap()
            if (!providers.isObject) return@withContext emptyMap()

            val byEpisode = mutableMapOf<Int, MutableList<EpisodeSourceCandidate>>()
            val metadataByEpisode = mutableMapOf<Int, EpisodeMetadata>()
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
                    list.forEach { episode ->
                        val rawId = text(episode, "id", "url") ?: return@forEach
                        val number = episode.path("number").asInt(0)
                        if (number <= 0) return@forEach
                        val episodeId = normalizeEpisodeId(rawId)
                        byEpisode.getOrPut(number) { mutableListOf() }
                            .add(EpisodeSourceCandidate(provider, episodeId, category))

                        val title = text(episode, "title", "name")
                            ?.takeUnless { it.equals("Episode $number", ignoreCase = true) }
                        val thumbnail = text(
                            episode,
                            "thumbnail",
                            "thumbnailUrl",
                            "image",
                            "img",
                            "poster"
                        )
                        val synopsis = listOf("description", "synopsis", "overview", "summary", "desc")
                            .firstNotNullOfOrNull { key ->
                                episode.get(key)?.asText(null)
                                    ?: episode.path("metadata").get(key)?.asText(null)
                                    ?: episode.path("meta").get(key)?.asText(null)
                            }
                            ?.replace(Regex("""(?i)<br\s*/?>"""), "\n")
                            ?.replace(Regex("<[^>]*>"), "")
                            ?.replace("&amp;", "&")
                            ?.replace("&quot;", 34.toChar().toString())
                            ?.replace("&#39;", "'")
                            ?.trim()
                            ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                        if (title != null || thumbnail != null || synopsis != null) {
                            val current = metadataByEpisode[number]
                            metadataByEpisode[number] = EpisodeMetadata(
                                title = current?.title ?: title,
                                thumbnailUrl = current?.thumbnailUrl ?: thumbnail,
                                synopsis = current?.synopsis ?: synopsis
                            )
                        }
                    }
                }
            }

            byEpisode.mapValues { (number, list) ->
                EpisodeMetadataWithSources(
                    metadata = metadataByEpisode[number] ?: EpisodeMetadata(),
                    candidates = list.sortedBy { providerRank(it.provider) }
                )
            }
        }

    suspend fun resolveSource(
        anilistId: Int,
        candidates: List<EpisodeSourceCandidate>,
        preferredProvider: String? = null
    ): SourceResolution = withContext(Dispatchers.IO) {
        if (candidates.isEmpty()) {
            return@withContext SourceResolution.NotFound(
                "This episode has no known sources from Miruro."
            )
        }

        val orderedCandidates = candidates
            .distinctBy {
                Triple(
                    it.provider.lowercase(Locale.ROOT),
                    it.episodeId,
                    it.category.lowercase(Locale.ROOT)
                )
            }
            .sortedWith(
                compareBy<EpisodeSourceCandidate> { candidate ->
                    if (
                        preferredProvider != null &&
                        candidate.provider.equals(preferredProvider, ignoreCase = true)
                    ) 0 else 1
                }.thenBy { providerRank(it.provider) }
            )
            .take(MAX_PROVIDER_ATTEMPTS)

        val fetched = supervisorScope {
            orderedCandidates.map { candidate ->
                async {
                    candidate to resultPreservingCancellation {
                        fetchSources(candidate, anilistId)
                    }
                }
            }.awaitAll()
        }

        var lastReason = "No playable stream found for this episode."
        val playableSources = mutableListOf<PlaybackSource>()

        fetched.forEach { (candidate, result) ->
            val sources = result.onFailure { error ->
                lastReason = "${candidate.provider}: ${error.message ?: error::class.simpleName}"
                Log.w(
                    TAG,
                    "resolveSource: fetchSources failed for provider=${candidate.provider} episodeId=${candidate.episodeId}",
                    error
                )
            }.getOrNull() ?: return@forEach

            val streamsNode = findFirstArray(sources, *STREAM_ARRAY_KEYS)
            if (streamsNode == null) {
                lastReason = "${candidate.provider}: response had no stream list."
                Log.w(
                    TAG,
                    "resolveSource: no stream array for provider=${candidate.provider} keys=${topLevelKeys(sources)}"
                )
                return@forEach
            }

            val providerHeaders = playbackHeaders(candidate.provider)
            val subtitles = findFirstArray(sources, *SUBTITLE_ARRAY_KEYS)?.mapNotNull { subtitle ->
                val subtitleUrl = text(subtitle, *STREAM_URL_KEYS)
                    ?.let(::normalizeStreamUrl)
                    ?.takeIf(::isValidStreamUrl)
                    ?: return@mapNotNull null
                SubtitleTrack(
                    url = subtitleUrl,
                    label = "${candidate.provider.uppercase(Locale.ROOT)} • ${text(subtitle, "label", "lang", "language") ?: "Subtitle"}",
                    language = text(subtitle, "lang", "language"),
                    id = "${candidate.provider.lowercase(Locale.ROOT)}:${stableSourcePathFingerprint(subtitleUrl)}",
                    mimeType = subtitleMimeType(subtitle, subtitleUrl),
                    providerId = candidate.provider,
                    headers = providerHeaders
                )
            }.orEmpty()

            val providerSources = streamsNode
                .sortedByDescending { streamRank(it) }
                .mapNotNull { stream ->
                    val rawUrl = streamUrl(stream)?.takeIf(::isValidStreamUrl)
                        ?: return@mapNotNull null
                    val type = playbackType(streamType(stream)?.lowercase(Locale.ROOT), rawUrl)
                    val quality = streamQuality(stream).ifBlank { "Auto" }
                    val streamName = text(stream, "server", "name", "id") ?: quality
                    PlaybackSource(
                        url = rawUrl,
                        label = "${candidate.provider.uppercase(Locale.ROOT)} $quality",
                        type = type,
                        providerId = candidate.provider,
                        sourceId = listOf(
                            candidate.provider.lowercase(Locale.ROOT),
                            candidate.episodeId,
                            streamName.lowercase(Locale.ROOT),
                            quality.lowercase(Locale.ROOT),
                            type.name,
                            stableSourcePathFingerprint(rawUrl)
                        ).joinToString(":"),
                        headers = providerHeaders,
                        subtitleTracks = subtitles
                    )
                }
                .distinctBy { source -> source.url to source.type }
                .take(MAX_CHOICES_PER_PROVIDER)

            if (providerSources.isEmpty()) {
                lastReason = "${candidate.provider}: no valid stream URL in response."
            } else {
                Log.d(
                    TAG,
                    "resolveSource: provider=${candidate.provider} choices=${providerSources.size}"
                )
                playableSources += providerSources
            }
        }

        val uniqueSources = playableSources
            .distinctBy { source -> source.url to source.type }
            .take(MAX_QUALITY_CHOICES)

        // Subtitle URLs and their required headers belong to the provider that
        // returned them. Mixing all providers' tracks into every source made
        // fallback sources request captions with unrelated video headers.
        val first = uniqueSources.firstOrNull()
            ?: return@withContext SourceResolution.NotFound(lastReason)

        SourceResolution.Found(
            first.copy(fallbackSources = uniqueSources.drop(1))
        )
    }

    private fun fetchSources(candidate: EpisodeSourceCandidate, anilistId: Int): JsonNode {
        if (candidate.episodeId.startsWith("watch/")) {
            val direct = resultPreservingCancellation {
                pipeGet(candidate.episodeId)
            }.getOrNull()
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

    private fun resolveSourceEpisodeId(
        candidate: EpisodeSourceCandidate,
        anilistId: Int
    ): String {
        if (!candidate.episodeId.startsWith("watch/")) return candidate.episodeId
        val slug = candidate.episodeId.substringAfterLast("/")
        val episodes = fetchRawEpisodes(anilistId)
            .path("providers")
            .path(candidate.provider)
            .path("episodes")
            .path(candidate.category)
        if (!episodes.isArray) return candidate.episodeId
        episodes.forEach { episode ->
            val rawId = normalizeEpisodeId(text(episode, "id") ?: return@forEach)
            val number = episode.path("number").asInt(0)
            if (number > 0 && generatedEpisodeSlug(rawId, number) == slug) return rawId
        }
        return candidate.episodeId
    }

    private fun hasPlayableStreams(node: JsonNode): Boolean =
        findFirstArray(node, *STREAM_ARRAY_KEYS)
            ?.any { streamUrl(it)?.let(::isValidStreamUrl) == true } == true

    private fun fetchRawEpisodes(anilistId: Int): JsonNode =
        pipeGet(
            "episodes",
            mapper.createObjectNode().apply { put("anilistId", anilistId) }
        )

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
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Miruro pipe request for \"$path\" failed: HTTP ${response.code}")
            }
            response.body?.string().orEmpty()
        }.trim()
        return decodePipeResponse(body)
    }

    private fun urlSafeBase64(value: ByteArray): String =
        Base64.encodeToString(
            value,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

    private fun encodePipeRequest(payload: JsonNode): String =
        urlSafeBase64(mapper.writeValueAsBytes(payload))

    private fun decodePipeResponse(value: String): JsonNode {
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        val compressed = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        val decoded = GZIPInputStream(ByteArrayInputStream(compressed))
            .bufferedReader()
            .use { it.readText() }
        return mapper.readTree(decoded)
    }

    private fun decodeUrlSafeBase64Text(value: String): String? = runCatching {
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP))
    }.getOrNull()

    private inline fun <T> resultPreservingCancellation(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
        }

    private fun normalizeEpisodeId(value: String): String {
        val decoded = decodeUrlSafeBase64Text(value)
        return if (decoded != null && ":" in decoded) decoded else value
    }

    private fun stableSourcePathFingerprint(url: String): String {
        val stablePart = url.substringBefore('?').substringBefore('#').lowercase(Locale.ROOT)
        return MessageDigest.getInstance("SHA-256")
            .digest(stablePart.toByteArray())
            .take(8)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun subtitleMimeType(node: JsonNode, url: String): String {
        val declared = text(node, "mimeType", "mime", "format", "type")
            ?.lowercase(Locale.ROOT)
            .orEmpty()
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            "subrip" in declared || "srt" in declared || path.endsWith(".srt") ->
                "application/x-subrip"
            "ssa" in declared || "ass" in declared || path.endsWith(".ssa") || path.endsWith(".ass") ->
                "text/x-ssa"
            else -> "text/vtt"
        }
    }

    private fun generatedEpisodeSlug(episodeId: String, number: Int): String {
        val prefix = episodeId.substringBefore(":")
        return "$prefix-$number"
    }

    private fun text(node: JsonNode, vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            node.get(name)?.asText()?.takeIf { it.isNotBlank() && it != "null" }
        }

    private fun topLevelKeys(node: JsonNode): List<String> =
        if (node.isObject) node.fieldNames().asSequence().toList() else emptyList()

    private fun firstArray(node: JsonNode, vararg names: String): JsonNode? =
        names.firstNotNullOfOrNull { name -> node.path(name).takeIf { it.isArray } }

    private fun findFirstArray(node: JsonNode, vararg names: String): JsonNode? {
        if (node.isObject) {
            firstArray(node, *names)?.let { return it }
            node.fields().forEach { entry ->
                findFirstArray(entry.value, *names)?.let { return it }
            }
        } else if (node.isArray) {
            node.forEach { child ->
                findFirstArray(child, *names)?.let { return it }
            }
        }
        return null
    }

    private fun streamUrl(node: JsonNode): String? =
        (text(node, *STREAM_URL_KEYS)
            ?: node.path("source").takeIf { it.isObject }?.let { text(it, *STREAM_URL_KEYS) })
            ?.let(::normalizeStreamUrl)

    private fun normalizeStreamUrl(url: String): String =
        if (url.startsWith("//")) "https:$url" else url

    private fun isValidStreamUrl(url: String): Boolean {
        if (
            url.isBlank() ||
            url.equals("null", ignoreCase = true) ||
            url.equals("undefined", ignoreCase = true)
        ) return false
        return url.startsWith("http://", ignoreCase = true) ||
            url.startsWith("https://", ignoreCase = true)
    }

    private fun streamType(node: JsonNode): String? =
        text(node, "type", "format")
            ?: node.path("source").takeIf { it.isObject }
                ?.let { text(it, "type", "format") }

    private fun streamQuality(node: JsonNode): String =
        text(node, "quality", "label", "resolution")
            ?: node.path("source").takeIf { it.isObject }
                ?.let { text(it, "quality", "label", "resolution") }
            ?: "Auto"

    private fun qualityRank(label: String): Int =
        Regex("""(\d{3,4})""").find(label)?.value?.toIntOrNull() ?: 0

    private fun playbackType(type: String?, url: String): PlaybackType {
        val normalized = type?.lowercase(Locale.ROOT)
        val path = url.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            normalized in setOf("dash", "mpd") || path.endsWith(".mpd") -> PlaybackType.DASH
            normalized in setOf("hls", "m3u8") || path.endsWith(".m3u8") -> PlaybackType.HLS
            normalized in setOf("mp4", "file") || path.endsWith(".mp4") -> PlaybackType.MP4
            else -> PlaybackType.UNKNOWN
        }
    }

    private fun streamRank(node: JsonNode): Int {
        val url = streamUrl(node) ?: return -1
        val type = playbackType(streamType(node)?.lowercase(Locale.ROOT), url)
        val formatScore = when (type) {
            PlaybackType.HLS, PlaybackType.DASH -> 1_000
            PlaybackType.MP4 -> 500
            PlaybackType.UNKNOWN -> 0
        }
        return formatScore + qualityRank(streamQuality(node))
    }

    private fun playbackHeaders(provider: String): Map<String, String> {
        val userAgent = headers.getValue("User-Agent")
        val providerReferer = when (provider.lowercase(Locale.ROOT)) {
            "animepahe" -> "https://kwik.si/"
            "gogoanime" -> "https://gogocdn.net/"
            else -> null
        }
        return if (providerReferer != null) {
            mapOf(
                "Referer" to providerReferer,
                "Origin" to providerReferer.trimEnd('/'),
                "User-Agent" to userAgent
            )
        } else {
            mapOf(
                "Referer" to "$MIRURO_URL/",
                "Origin" to MIRURO_URL,
                "User-Agent" to userAgent
            )
        }
    }

    private fun providerRank(provider: String): Int {
        val index = PROVIDER_PRIORITY.indexOf(provider.lowercase(Locale.ROOT))
        return if (index == -1) PROVIDER_PRIORITY.size else index
    }
}
