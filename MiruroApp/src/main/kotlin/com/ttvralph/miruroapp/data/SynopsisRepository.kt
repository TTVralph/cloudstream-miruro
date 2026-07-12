package com.ttvralph.miruroapp.data

import android.util.Base64
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A stable key for synopsis metadata that is independent of Sub/Dub versions. */
data class EpisodeSynopsisKey(
    val anilistId: Int,
    val episodeNumber: Int
)

data class SynopsisBundle(
    val seasonSynopses: Map<Int, String> = emptyMap(),
    val episodeSynopses: Map<EpisodeSynopsisKey, String> = emptyMap()
)

/**
 * Loads optional synopsis metadata without changing playback or catalogue models.
 *
 * AniList provides a synopsis for each media/season entry. Episode summaries are
 * taken only from Miruro provider metadata when a provider actually supplies one;
 * the UI deliberately shows "Synopsis unavailable" instead of inventing text.
 */
object SynopsisRepository {
    private const val MIRURO_URL = "https://www.miruro.tv"
    private const val PIPE_URL = "$MIRURO_URL/api/secure/pipe"

    private val mapper = jacksonObjectMapper()
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .callTimeout(14, TimeUnit.SECONDS)
        .build()

    private val seasonCache = ConcurrentHashMap<Int, String>()
    private val episodeCache = ConcurrentHashMap<Int, Map<Int, String>>()

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

    suspend fun load(details: AnimeDetails): SynopsisBundle = supervisorScope {
        val seasons = details.seasons.distinctBy { it.id }
        val seasonJobs = seasons.map { season ->
            async { season.id to seasonSynopsis(season.id) }
        }
        val episodeJobs = seasons.map { season ->
            async { season.id to episodeSynopses(season.id) }
        }

        val seasonValues = seasonJobs.awaitAll()
            .mapNotNull { (id, synopsis) -> synopsis?.let { id to it } }
            .toMap()
        val episodeValues = episodeJobs.awaitAll().flatMap { (anilistId, values) ->
            values.map { (episodeNumber, synopsis) ->
                EpisodeSynopsisKey(anilistId, episodeNumber) to synopsis
            }
        }.toMap()

        SynopsisBundle(seasonValues, episodeValues)
    }

    suspend fun seasonSynopsis(anilistId: Int): String? {
        seasonCache[anilistId]?.let { return it }
        val value = runCatching { fetchSeasonSynopsis(anilistId) }.getOrNull()
        if (!value.isNullOrBlank()) seasonCache[anilistId] = value
        return value
    }

    suspend fun episodeSynopsis(anilistId: Int, episodeNumber: Int): String? =
        episodeSynopses(anilistId)[episodeNumber]

    private suspend fun episodeSynopses(anilistId: Int): Map<Int, String> {
        episodeCache[anilistId]?.let { return it }
        val value = runCatching { fetchEpisodeSynopses(anilistId) }.getOrDefault(emptyMap())
        episodeCache[anilistId] = value
        return value
    }

    private suspend fun fetchSeasonSynopsis(anilistId: Int): String? = withContext(Dispatchers.IO) {
        val requestBody = mapper.writeValueAsString(
            mapOf(
                "query" to "query(\$id:Int){Media(id:\$id,type:ANIME){description(asHtml:false)}}",
                "variables" to mapOf("id" to anilistId)
            )
        )
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody.toRequestBody(jsonType))
            .header("Accept", "application/json")
            .header("User-Agent", "AniStream-TV/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("AniList synopsis request failed: HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val node = mapper.readTree(body).path("data").path("Media").path("description")
            cleanSynopsis(node.asText(null))
        }
    }

    private suspend fun fetchEpisodeSynopses(anilistId: Int): Map<Int, String> = withContext(Dispatchers.IO) {
        val providers = pipeGet(
            "episodes",
            mapper.createObjectNode().apply { put("anilistId", anilistId) }
        ).path("providers")
        if (!providers.isObject) return@withContext emptyMap()

        val candidates = mutableMapOf<Int, MutableList<String>>()
        providers.fields().forEach { providerEntry ->
            val episodes = providerEntry.value.path("episodes")
            val lists = if (episodes.isArray) {
                listOf(episodes)
            } else {
                listOfNotNull(
                    episodes.path("sub").takeIf { it.isArray },
                    episodes.path("dub").takeIf { it.isArray }
                )
            }
            lists.forEach { list ->
                list.forEach { episode ->
                    val number = episode.path("number").asInt(0)
                    if (number <= 0) return@forEach
                    findSynopsis(episode)?.let { synopsis ->
                        candidates.getOrPut(number) { mutableListOf() }.add(synopsis)
                    }
                }
            }
        }
        candidates.mapValues { (_, values) ->
            values.distinct().maxByOrNull { it.length }.orEmpty()
        }.filterValues { it.isNotBlank() }
    }

    private fun pipeGet(path: String, query: JsonNode): JsonNode {
        val payload = mapper.createObjectNode().apply {
            put("path", path)
            put("method", "GET")
            set<JsonNode>("query", query)
            putNull("body")
            put("version", "0.1.0")
        }
        val encoded = URLEncoder.encode(urlSafeBase64(mapper.writeValueAsBytes(payload)), "UTF-8")
        val request = Request.Builder().url("$PIPE_URL?e=$encoded").apply {
            headers.forEach { (key, value) -> addHeader(key, value) }
        }.build()
        val body = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Miruro synopsis request failed: HTTP ${response.code}")
            response.body?.string().orEmpty().trim()
        }
        return decodePipeResponse(body)
    }

    private fun findSynopsis(node: JsonNode): String? {
        val direct = listOf("description", "synopsis", "overview", "summary", "desc")
            .firstNotNullOfOrNull { key -> node.get(key)?.asText(null) }
        cleanSynopsis(direct)?.let { return it }

        val metadata = listOf("metadata", "meta", "details")
            .firstNotNullOfOrNull { key -> node.get(key)?.takeIf { it.isObject } }
        return metadata?.let(::findSynopsis)
    }

    private fun cleanSynopsis(value: String?): String? = value
        ?.replace(Regex("(?i)<br\\s*/?>"), "\n")
        ?.replace(Regex("<[^>]*>"), "")
        ?.replace("&amp;", "&")
        ?.replace("&quot;", "\"")
        ?.replace("&#39;", "'")
        ?.replace("&lt;", "<")
        ?.replace("&gt;", ">")
        ?.replace(Regex("[ \\t]+"), " ")
        ?.replace(Regex("\\n{3,}"), "\n\n")
        ?.trim()
        ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

    private fun urlSafeBase64(value: ByteArray): String =
        Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun decodePipeResponse(value: String): JsonNode {
        return runCatching {
            val padded = value + "=".repeat((4 - value.length % 4) % 4)
            val compressed = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
            GZIPInputStream(ByteArrayInputStream(compressed))
                .bufferedReader()
                .use { mapper.readTree(it.readText()) }
        }.getOrElse {
            mapper.readTree(value)
        }
    }
}
