package com.ttvralph.miruroapp.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl

class NetflixFeatureRepository {
    private val mapper = jacksonObjectMapper()
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    private val extrasCache = linkedMapOf<Int, TitleExtras>()
    private val malIdCache = mutableMapOf<Int, Int?>()
    private val itemCache = linkedMapOf<Int, AnimeItem>()
    private val skipCache = linkedMapOf<String, List<SkipInterval>>()

    suspend fun titleExtras(animeId: Int): TitleExtras = withContext(Dispatchers.IO) {
        extrasCache[animeId]?.let { return@withContext it }
        val media = graphQl(EXTRAS_QUERY, mapOf("id" to animeId)).path("Media")
        if (!media.isObject) throw IOException("AniList did not return title extras.")
        val baseItem = media.toAnimeItem()
        baseItem?.let { rememberItems(listOf(it)) }
        val related = media.path("relations").path("nodes")
            .mapNotNull { node ->
                node.takeIf { text(it, "type") == "ANIME" }?.toAnimeItem()
            }
            .distinctBy { it.id }
            .take(20)
        val recommendations = media.path("recommendations").path("nodes")
            .mapNotNull { node -> node.path("mediaRecommendation").toAnimeItem() }
            .distinctBy { it.id }
            .take(20)
        rememberItems(related + recommendations)
        val next = media.path("nextAiringEpisode").takeIf { it.isObject }?.let { airing ->
            val anime = baseItem ?: return@let null
            val episode = airing.path("episode").asInt(0)
            val at = airing.path("airingAt").asLong(0L)
            if (episode > 0 && at > 0L) UpcomingEpisode(anime, episode, at) else null
        }
        val extras = TitleExtras(
            animeId = animeId,
            malId = media.path("idMal").asInt(0).takeIf { it > 0 },
            related = related,
            recommendations = recommendations,
            nextAiring = next
        )
        malIdCache[animeId] = extras.malId
        extrasCache[animeId] = extras
        trim(extrasCache, 40)
        extras
    }

    suspend fun items(ids: Set<Int>): List<AnimeItem> = withContext(Dispatchers.IO) {
        val validIds = ids.filter { it > 0 }.distinct().take(50)
        val cached = validIds.mapNotNull(itemCache::get)
        val missing = validIds.filterNot { it in itemCache }
        if (missing.isNotEmpty()) {
            val fetched = graphQl(ITEMS_QUERY, mapOf("ids" to missing))
                .path("Page")
                .path("media")
                .mapNotNull { it.toAnimeItem() }
            rememberItems(fetched)
        }
        validIds.mapNotNull(itemCache::get).ifEmpty { cached }
    }

    suspend fun upcoming(ids: Set<Int>): List<UpcomingEpisode> = withContext(Dispatchers.IO) {
        val validIds = ids.filter { it > 0 }.distinct().take(50)
        if (validIds.isEmpty()) return@withContext emptyList()
        val media = graphQl(UPCOMING_QUERY, mapOf("ids" to validIds))
            .path("Page")
            .path("media")
        val result = media.mapNotNull { node ->
            val anime = node.toAnimeItem() ?: return@mapNotNull null
            val airing = node.path("nextAiringEpisode")
            val episode = airing.path("episode").asInt(0)
            val at = airing.path("airingAt").asLong(0L)
            if (episode > 0 && at > 0L) UpcomingEpisode(anime, episode, at) else null
        }.sortedBy { it.airingAtEpochSeconds }
        rememberItems(result.map { it.anime })
        result
    }

    suspend fun skipTimes(
        anilistId: Int,
        episodeNumber: Int,
        episodeLengthSeconds: Double
    ): List<SkipInterval> = withContext(Dispatchers.IO) {
        if (episodeNumber <= 0 || episodeLengthSeconds <= 0.0) return@withContext emptyList()
        val lengthBucket = (episodeLengthSeconds / 30.0).toInt()
        val cacheKey = "$anilistId:$episodeNumber:$lengthBucket"
        skipCache[cacheKey]?.let { return@withContext it }

        val malId = malIdFor(anilistId) ?: return@withContext emptyList()
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episodeNumber"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("types[]", "op")
            .addQueryParameter("types[]", "mixed-op")
            .addQueryParameter("types[]", "recap")
            .addQueryParameter("types[]", "ed")
            .addQueryParameter("types[]", "mixed-ed")
            .addQueryParameter("episodeLength", "%.2f".format(Locale.US, episodeLengthSeconds))
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "AniStream-TV/1.0")
            .build()

        val intervals = client.newCall(request).execute().use { response ->
            if (response.code == 404) return@use emptyList()
            if (!response.isSuccessful) throw IOException("AniSkip failed with HTTP ${response.code}.")
            val root = response.body?.string()?.takeIf { it.isNotBlank() }
                ?.let(mapper::readTree)
                ?: return@use emptyList()
            root.path("results").mapNotNull { result ->
                val startSeconds = result.path("interval").path("startTime").asDouble(-1.0)
                val endSeconds = result.path("interval").path("endTime").asDouble(-1.0)
                val kind = when (result.path("skipType").asText()) {
                    "op", "mixed-op" -> SkipKind.INTRO
                    "recap" -> SkipKind.RECAP
                    "ed", "mixed-ed" -> SkipKind.ENDING
                    else -> null
                } ?: return@mapNotNull null
                if (startSeconds < 0.0 || endSeconds <= startSeconds) return@mapNotNull null
                SkipInterval(kind, (startSeconds * 1_000).toLong(), (endSeconds * 1_000).toLong())
            }.distinctBy { Triple(it.kind, it.startMs, it.endMs) }
                .sortedBy { it.startMs }
        }
        skipCache[cacheKey] = intervals
        trim(skipCache, 100)
        intervals
    }

    private fun malIdFor(anilistId: Int): Int? {
        if (anilistId in malIdCache) return malIdCache[anilistId]
        val media = graphQl(MAL_ID_QUERY, mapOf("id" to anilistId)).path("Media")
        val id = media.path("idMal").asInt(0).takeIf { it > 0 }
        malIdCache[anilistId] = id
        return id
    }

    private fun graphQl(query: String, variables: Map<String, Any?>): JsonNode {
        val payload = mapper.writeValueAsString(mapOf("query" to query, "variables" to variables))
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(payload.toRequestBody(jsonType))
            .header("Accept", "application/json")
            .header("User-Agent", "AniStream-TV/1.0")
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("AniList failed with HTTP ${response.code}.")
            if (text.isBlank()) throw IOException("AniList returned an empty response.")
            val root = mapper.readTree(text)
            val errors = root.path("errors")
            if (errors.isArray && errors.size() > 0) {
                throw IOException(errors.firstOrNull()?.path("message")?.asText() ?: "AniList request failed.")
            }
            root.path("data")
        }
    }

    private fun JsonNode.toAnimeItem(): AnimeItem? {
        if (!isObject) return null
        val id = path("id").asInt(0).takeIf { it > 0 } ?: return null
        val titleNode = path("title")
        val title = titleNode.path("english").asText(null)
            ?: titleNode.path("romaji").asText(null)
            ?: titleNode.path("native").asText(null)
            ?: return null
        val format = text(this, "format")
        return AnimeItem(
            id = id,
            title = title,
            posterUrl = text(this, "coverImage", "extraLarge") ?: text(this, "coverImage", "large"),
            bannerUrl = text(this, "bannerImage"),
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

    private fun text(node: JsonNode, vararg path: String): String? =
        path.fold(node) { current, part -> current.path(part) }
            .asText(null)
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun rememberItems(items: List<AnimeItem>) {
        items.forEach { itemCache[it.id] = it }
        trim(itemCache, 300)
    }

    private fun <K, V> trim(map: LinkedHashMap<K, V>, limit: Int) {
        while (map.size > limit) map.remove(map.keys.first())
    }

    companion object {
        private const val ITEM_FIELDS = "id title{romaji english native} coverImage{large extraLarge} bannerImage format seasonYear startDate{year} averageScore"
        private const val ITEMS_QUERY = "query(\$ids:[Int]){Page(page:1,perPage:50){media(id_in:\$ids,type:ANIME,isAdult:false){$ITEM_FIELDS}}}"
        private const val UPCOMING_QUERY = "query(\$ids:[Int]){Page(page:1,perPage:50){media(id_in:\$ids,type:ANIME,isAdult:false){$ITEM_FIELDS nextAiringEpisode{episode airingAt}}}}"
        private const val EXTRAS_QUERY = "query(\$id:Int){Media(id:\$id,type:ANIME){$ITEM_FIELDS idMal nextAiringEpisode{episode airingAt} relations{nodes{type $ITEM_FIELDS}} recommendations(sort:RATING_DESC,perPage:20){nodes{rating mediaRecommendation{$ITEM_FIELDS}}}}}"
        private const val MAL_ID_QUERY = "query(\$id:Int){Media(id:\$id,type:ANIME){idMal}}"
    }
}
