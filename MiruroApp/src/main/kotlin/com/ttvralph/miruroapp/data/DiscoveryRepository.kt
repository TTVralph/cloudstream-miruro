package com.ttvralph.miruroapp.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DiscoveryRepository {
    private val mapper = jacksonObjectMapper()
    private val jsonType = "application/json".toMediaType()
    private val client = OkHttpClient.Builder()
        .callTimeout(14, TimeUnit.SECONDS)
        .build()
    private val miruro = MiruroRepository()
    private val cacheLock = Any()
    private val titleCache = linkedMapOf<Int, DiscoveryTitleInfo>()
    private var studioCache: List<StudioOption>? = null

    suspend fun search(
        filters: DiscoverySearchFilters,
        favoriteIds: Set<Int>
    ): List<AnimeItem> = withContext(Dispatchers.IO) {
        val variables = linkedMapOf<String, Any?>(
            "page" to filters.page,
            "perPage" to if (filters.dubbedOnly) 18 else 30,
            "search" to filters.query.trim().takeIf { it.isNotBlank() },
            "sort" to listOf(
                when {
                    filters.query.isBlank() && filters.sort == DiscoverySort.BEST_MATCH -> DiscoverySort.POPULARITY.aniListValue
                    else -> filters.sort.aniListValue
                }
            ),
            "formatIn" to filters.formats.takeIf { it.isNotEmpty() }?.toList(),
            "statusIn" to filters.statuses.takeIf { it.isNotEmpty() }?.toList(),
            "season" to filters.season,
            "startDateGreater" to filters.yearFrom?.let { it * 10_000 + 101 },
            "startDateLesser" to filters.yearTo?.let { it * 10_000 + 1231 },
            "genreIn" to filters.includeGenres.takeIf { it.isNotEmpty() }?.toList(),
            "genreNotIn" to filters.excludeGenres.takeIf { it.isNotEmpty() }?.toList(),
            "averageScoreGreater" to filters.minimumScore?.let { (it - 1).coerceAtLeast(0) },
            "episodesLesser" to filters.maximumEpisodes?.let { it + 1 },
            "durationLesser" to filters.maximumDurationMinutes?.let { it + 1 },
            "source" to filters.source,
            "country" to filters.country
        ).filterValues { it != null }

        val candidates = graphQl(SEARCH_QUERY, variables)
            .path("Page")
            .path("media")
            .mapNotNull { node ->
                val item = node.toAnimeItem() ?: return@mapNotNull null
                SearchCandidate(
                    item = item,
                    studioIds = node.path("studios").path("nodes").map { it.path("id").asInt() }.toSet()
                )
            }
            .filter { candidate -> filters.studioId == null || filters.studioId in candidate.studioIds }
            .filter { candidate -> !filters.myListOnly || candidate.item.id in favoriteIds }
            .distinctBy { it.item.id }

        if (!filters.dubbedOnly) return@withContext candidates.map { it.item }

        val gate = Semaphore(4)
        supervisorScope {
            candidates.map { candidate ->
                async {
                    gate.withPermit {
                        candidate.takeIf { dubbedAvailable(it.item.id) }
                    }
                }
            }.awaitAll().filterNotNull().map { it.item }
        }
    }

    suspend fun studioOptions(): List<StudioOption> = withContext(Dispatchers.IO) {
        synchronized(cacheLock) { studioCache }?.let { return@withContext it }
        val values = graphQl(STUDIO_QUERY, emptyMap())
            .path("Page")
            .path("studios")
            .mapNotNull { node ->
                val id = node.path("id").asInt(0).takeIf { it > 0 } ?: return@mapNotNull null
                val name = node.path("name").asText(null)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                StudioOption(id, name)
            }
            .distinctBy { it.id }
            .take(30)
        synchronized(cacheLock) { studioCache = values }
        values
    }

    suspend fun pick(
        mode: DiscoveryMode,
        excludedIds: Set<Int>
    ): DiscoveryPick = withContext(Dispatchers.IO) {
        require(mode != DiscoveryMode.CONTINUE_SOMETHING) {
            "Continue Something is selected from local watch progress."
        }
        val variables = linkedMapOf<String, Any?>(
            "formatIn" to when (mode) {
                DiscoveryMode.PICK_A_MOVIE -> listOf("MOVIE")
                DiscoveryMode.ONE_EPISODE_WATCH -> listOf("MOVIE", "OVA", "SPECIAL")
                else -> null
            },
            "statusIn" to when (mode) {
                DiscoveryMode.START_SOMETHING_NEW -> listOf("FINISHED", "RELEASING")
                else -> null
            },
            "episodesLesser" to when (mode) {
                DiscoveryMode.ONE_EPISODE_WATCH -> 2
                else -> null
            },
            "sort" to listOf(
                when (mode) {
                    DiscoveryMode.PICK_A_MOVIE -> "SCORE_DESC"
                    DiscoveryMode.ONE_EPISODE_WATCH -> "POPULARITY_DESC"
                    DiscoveryMode.START_SOMETHING_NEW -> "START_DATE_DESC"
                    else -> "TRENDING_DESC"
                }
            )
        ).filterValues { it != null }

        val all = graphQl(PICK_QUERY, variables)
            .path("Page")
            .path("media")
            .mapNotNull { it.toAnimeItem() }
            .distinctBy { it.id }
        val available = all.filterNot { it.id in excludedIds }.ifEmpty { all }
        val item = available.takeIf { it.isNotEmpty() }
            ?.let { it[(System.nanoTime().ushr(1) % it.size.toLong()).toInt()] }
        DiscoveryPick(
            mode = mode,
            anime = item,
            reason = when (mode) {
                DiscoveryMode.SURPRISE_ME -> "A surprise from what is trending now."
                DiscoveryMode.PICK_A_MOVIE -> "A well-rated anime movie."
                DiscoveryMode.ONE_EPISODE_WATCH -> "A movie, OVA, or special designed for a shorter watch."
                DiscoveryMode.START_SOMETHING_NEW -> "A recent title outside your current library and history."
                DiscoveryMode.CONTINUE_SOMETHING -> "Resume an unfinished episode."
            }
        )
    }

    suspend fun titleInfo(animeId: Int): DiscoveryTitleInfo = withContext(Dispatchers.IO) {
        synchronized(cacheLock) { titleCache[animeId] }?.let { return@withContext it }
        val media = graphQl(TITLE_QUERY, mapOf("id" to animeId)).path("Media")
        if (!media.isObject) throw IOException("AniList did not return title information.")
        val anime = media.toAnimeItem() ?: throw IOException("AniList returned incomplete title information.")
        val malId = media.path("idMal").asInt(0).takeIf { it > 0 }

        val relations = media.path("relations").path("edges")
            .mapNotNull { edge ->
                val node = edge.path("node")
                if (node.path("type").asText() != "ANIME") return@mapNotNull null
                val related = node.toAnimeItem() ?: return@mapNotNull null
                DiscoveryRelation(
                    relationType = pretty(edge.path("relationType").asText("RELATED")),
                    anime = related,
                    format = text(node, "format")?.let(::pretty),
                    episodes = node.path("episodes").asInt(0).takeIf { it > 0 },
                    startDate = fuzzyDate(node.path("startDate"))
                )
            }
            .distinctBy { it.anime.id }

        val rootEntry = DiscoveryFranchiseEntry(
            anime = anime,
            format = text(media, "format")?.let(::pretty),
            episodes = media.path("episodes").asInt(0).takeIf { it > 0 },
            startDate = fuzzyDate(media.path("startDate")),
            relationship = "Current title"
        )
        val franchiseEntries = listOf(rootEntry) + relations
            .filter { it.relationType in setOf("Prequel", "Sequel") }
            .map {
                DiscoveryFranchiseEntry(
                    anime = it.anime,
                    format = it.format,
                    episodes = it.episodes,
                    startDate = it.startDate,
                    relationship = it.relationType
                )
            }
        val releaseOrder = franchiseEntries.distinctBy { it.anime.id }
            .sortedWith(compareBy<DiscoveryFranchiseEntry> { it.startDate ?: Int.MAX_VALUE }.thenBy { it.anime.id })
        val storyOrder = buildList {
            addAll(franchiseEntries.filter { it.relationship == "Prequel" }.sortedBy { it.startDate ?: Int.MAX_VALUE })
            add(rootEntry)
            addAll(franchiseEntries.filter { it.relationship == "Sequel" }.sortedBy { it.startDate ?: Int.MAX_VALUE })
        }.distinctBy { it.anime.id }

        val characters = media.path("characters").path("edges")
            .mapNotNull { edge ->
                val node = edge.path("node")
                val id = node.path("id").asInt(0).takeIf { it > 0 } ?: return@mapNotNull null
                val voiceActor = edge.path("voiceActors").firstOrNull()
                DiscoveryPerson(
                    id = id,
                    name = text(node, "name", "full") ?: return@mapNotNull null,
                    nativeName = text(node, "name", "native"),
                    imageUrl = text(node, "image", "large"),
                    role = pretty(edge.path("role").asText("Supporting")),
                    voiceActor = voiceActor?.let { text(it, "name", "full") },
                    voiceActorImageUrl = voiceActor?.let { text(it, "image", "large") }
                )
            }
            .distinctBy { it.id }
            .take(18)

        val staff = media.path("staff").path("edges")
            .mapNotNull { edge ->
                val node = edge.path("node")
                val id = node.path("id").asInt(0).takeIf { it > 0 } ?: return@mapNotNull null
                DiscoveryPerson(
                    id = id,
                    name = text(node, "name", "full") ?: return@mapNotNull null,
                    nativeName = text(node, "name", "native"),
                    imageUrl = text(node, "image", "large"),
                    role = edge.path("role").asText("Staff")
                )
            }
            .distinctBy { it.id to it.role }
            .take(14)

        val themes = malId?.let { runCatching { fetchThemes(it) }.getOrNull() }
            ?: emptyList<String>() to emptyList()
        val info = DiscoveryTitleInfo(
            anime = anime,
            malId = malId,
            description = clean(text(media, "description")),
            format = text(media, "format")?.let(::pretty),
            status = text(media, "status")?.let(::pretty),
            episodes = media.path("episodes").asInt(0).takeIf { it > 0 },
            durationMinutes = media.path("duration").asInt(0).takeIf { it > 0 },
            source = text(media, "source")?.let(::pretty),
            country = text(media, "countryOfOrigin"),
            season = text(media, "season")?.let(::pretty),
            year = media.path("seasonYear").asInt(0).takeIf { it > 0 }
                ?: media.path("startDate").path("year").asInt(0).takeIf { it > 0 },
            genres = media.path("genres").mapNotNull { it.asText(null) },
            synonyms = media.path("synonyms").mapNotNull { it.asText(null) }.filter { it.isNotBlank() },
            studios = media.path("studios").path("nodes").mapNotNull { it.path("name").asText(null) },
            relations = relations,
            releaseOrder = releaseOrder,
            storyOrder = storyOrder,
            characters = characters,
            staff = staff,
            openingThemes = themes.first.take(12),
            endingThemes = themes.second.take(12)
        )
        synchronized(cacheLock) {
            titleCache[animeId] = info
            while (titleCache.size > 30) titleCache.remove(titleCache.keys.first())
        }
        info
    }

    private suspend fun dubbedAvailable(animeId: Int): Boolean = try {
        miruro.episodeData(animeId).values.any { data ->
            data.candidates.any { it.category.equals("dub", ignoreCase = true) }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    private fun fetchThemes(malId: Int): Pair<List<String>, List<String>> {
        val request = Request.Builder()
            .url("https://api.jikan.moe/v4/anime/$malId/themes")
            .header("Accept", "application/json")
            .header("User-Agent", "AniStream-TV/1.0")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList<String>() to emptyList()
            val root = response.body?.string()?.takeIf { it.isNotBlank() }
                ?.let(mapper::readTree)
                ?: return@use emptyList<String>() to emptyList()
            val data = root.path("data")
            data.path("openings").mapNotNull { it.asText(null) }.filter { it.isNotBlank() } to
                data.path("endings").mapNotNull { it.asText(null) }.filter { it.isNotBlank() }
        }
    }

    private fun graphQl(query: String, variables: Map<String, Any?>): JsonNode {
        val body = mapper.writeValueAsString(mapOf("query" to query, "variables" to variables))
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody(jsonType))
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
        val title = text(this, "title", "english")
            ?: text(this, "title", "romaji")
            ?: text(this, "title", "native")
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

    private fun clean(value: String?): String? =
        value?.replace(Regex("<[^>]*>"), "")
            ?.replace("&amp;", "&")
            ?.replace("&quot;", "\"")
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    private fun fuzzyDate(node: JsonNode): Int? {
        val year = node.path("year").asInt(0).takeIf { it > 0 } ?: return null
        val month = node.path("month").asInt(0).coerceIn(0, 12)
        val day = node.path("day").asInt(0).coerceIn(0, 31)
        return year * 10_000 + month * 100 + day
    }

    private fun pretty(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.ROOT) }

    private data class SearchCandidate(
        val item: AnimeItem,
        val studioIds: Set<Int>
    )

    companion object {
        private const val ITEM_FIELDS = "id title{romaji english native} coverImage{large extraLarge} bannerImage format seasonYear startDate{year month day} averageScore"
        private const val SEARCH_QUERY = "query(\$page:Int,\$perPage:Int,\$search:String,\$sort:[MediaSort],\$formatIn:[MediaFormat],\$statusIn:[MediaStatus],\$season:MediaSeason,\$startDateGreater:FuzzyDateInt,\$startDateLesser:FuzzyDateInt,\$genreIn:[String],\$genreNotIn:[String],\$averageScoreGreater:Int,\$episodesLesser:Int,\$durationLesser:Int,\$source:MediaSource,\$country:CountryCode){Page(page:\$page,perPage:\$perPage){media(search:\$search,type:ANIME,isAdult:false,sort:\$sort,format_in:\$formatIn,status_in:\$statusIn,season:\$season,startDate_greater:\$startDateGreater,startDate_lesser:\$startDateLesser,genre_in:\$genreIn,genre_not_in:\$genreNotIn,averageScore_greater:\$averageScoreGreater,episodes_lesser:\$episodesLesser,duration_lesser:\$durationLesser,source:\$source,countryOfOrigin:\$country){$ITEM_FIELDS studios{nodes{id}}}}}"
        private const val STUDIO_QUERY = "query{Page(page:1,perPage:30){studios(sort:FAVOURITES_DESC){id name isAnimationStudio}}}"
        private const val PICK_QUERY = "query(\$formatIn:[MediaFormat],\$statusIn:[MediaStatus],\$episodesLesser:Int,\$sort:[MediaSort]){Page(page:1,perPage:40){media(type:ANIME,isAdult:false,format_in:\$formatIn,status_in:\$statusIn,episodes_lesser:\$episodesLesser,sort:\$sort){$ITEM_FIELDS}}}"
        private const val TITLE_QUERY = "query(\$id:Int){Media(id:\$id,type:ANIME){$ITEM_FIELDS idMal description(asHtml:false) status episodes duration genres source countryOfOrigin season synonyms studios(isMain:true){nodes{id name}} relations{edges{relationType node{type $ITEM_FIELDS episodes}}} characters(sort:[ROLE,RELEVANCE,ID],perPage:18){edges{role node{id name{full native} image{large}} voiceActors(language:JAPANESE,sort:[RELEVANCE,ID]){id name{full native} image{large}}}} staff(sort:[RELEVANCE,ID],perPage:14){edges{role node{id name{full native} image{large}}}}}}"
    }
}
