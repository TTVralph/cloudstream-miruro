package com.ttvralph.miruroapp

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ttvralph.miruroapp.data.AnimeItem
import com.ttvralph.miruroapp.data.AnimeType
import com.ttvralph.miruroapp.data.HomeRow
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Keeps presentation-only Home work in process memory. Nothing here is written to disk.
 */
internal object HomePresentationRows {
    private var source: List<HomeRow>? = null
    private var collapsed: List<HomeRow> = emptyList()

    fun collapsed(rows: List<HomeRow>): List<HomeRow> {
        if (source === rows) return collapsed
        val next = rows.collapseHomeFranchises()
        source = rows
        collapsed = next
        return next
    }
}

/**
 * Resolves only the title/artwork needed by Continue Watching.
 * This deliberately avoids AniListRepository.details(), which also resolves seasons,
 * episode dates, Miruro episode metadata, and source candidates.
 */
internal object ContinueWatchingMetadataStore {
    private val mapper = jacksonObjectMapper()
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private val mutex = Mutex()

    private val _items = MutableStateFlow<Map<Int, AnimeItem>>(emptyMap())
    val items: StateFlow<Map<Int, AnimeItem>> = _items.asStateFlow()

    suspend fun resolve(ids: Set<Int>) {
        if (ids.isEmpty()) return
        mutex.withLock {
            val missing = ids.asSequence()
                .filterNot { it in _items.value }
                .take(MAX_BATCH_SIZE)
                .toList()
            if (missing.isEmpty()) return@withLock

            val fetched = runCatching { fetch(missing) }.getOrDefault(emptyList())
            if (fetched.isNotEmpty()) {
                _items.value = _items.value + fetched.associateBy { it.id }
            }
        }
    }

    private suspend fun fetch(ids: List<Int>): List<AnimeItem> = withContext(Dispatchers.IO) {
        val body = mapper.writeValueAsString(
            mapOf(
                "query" to ITEM_QUERY,
                "variables" to mapOf("ids" to ids)
            )
        )
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody(jsonType))
            .header("Accept", "application/json")
            .header("User-Agent", "Yume-TV/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful || responseText.isBlank()) {
                throw IOException("AniList metadata request failed with HTTP ${response.code}.")
            }
            val root = mapper.readTree(responseText)
            val errors = root.path("errors")
            if (errors.isArray && errors.size() > 0) {
                throw IOException("AniList returned a metadata error.")
            }
            root.path("data")
                .path("Page")
                .path("media")
                .mapNotNull { it.toAnimeItem() }
        }
    }

    private fun JsonNode.toAnimeItem(): AnimeItem? {
        val id = path("id").asInt(0).takeIf { it > 0 } ?: return null
        val titleNode = path("title")
        val title = titleNode.path("english").asText(null)
            ?: titleNode.path("romaji").asText(null)
            ?: titleNode.path("native").asText(null)
            ?: return null
        val format = path("format").asText(null)
        return AnimeItem(
            id = id,
            title = title,
            posterUrl = path("coverImage").path("extraLarge").asText(null)
                ?: path("coverImage").path("large").asText(null),
            bannerUrl = path("bannerImage").asText(null),
            type = when (format) {
                "TV", "TV_SHORT" -> AnimeType.TV
                "MOVIE" -> AnimeType.MOVIE
                "OVA" -> AnimeType.OVA
                "SPECIAL" -> AnimeType.SPECIAL
                else -> AnimeType.UNKNOWN
            },
            year = path("seasonYear").asInt(0).takeIf { it > 0 }
                ?: path("startDate").path("year").asInt(0).takeIf { it > 0 },
            score = path("averageScore").asInt(0).takeIf { it > 0 }
        )
    }

    private const val MAX_BATCH_SIZE = 12
    private const val ITEM_QUERY =
        "query(\$ids:[Int]){Page(page:1,perPage:20){media(id_in:\$ids,type:ANIME,isAdult:false){id title{romaji english native} coverImage{large extraLarge} bannerImage format seasonYear startDate{year} averageScore}}}"
}
