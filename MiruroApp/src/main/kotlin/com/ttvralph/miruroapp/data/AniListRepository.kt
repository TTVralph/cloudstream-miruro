package com.ttvralph.miruroapp.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AniListRepository {
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()
    private val jsonType = "application/json".toMediaType()
    private val dateCache = mutableMapOf<Int, Map<Int, String>>()
    private val miruro = MiruroRepository()

    suspend fun homeRows(): List<HomeRow> {
        val definitions = listOf(
            "Trending Now" to mapOf("sort" to listOf("TRENDING_DESC")),
            "Currently Airing" to mapOf("status" to "RELEASING", "sort" to listOf("POPULARITY_DESC")),
            "Popular This Season" to mapOf(
                "season" to currentSeason(),
                "seasonYear" to currentYear(),
                "sort" to listOf("POPULARITY_DESC")
            ),
            "Top Rated Anime" to mapOf("sort" to listOf("SCORE_DESC")),
            "Anime Movies" to mapOf("format" to "MOVIE", "sort" to listOf("POPULARITY_DESC"))
        )
        val rows = mutableListOf<HomeRow>()
        var lastFailure: Throwable? = null
        definitions.forEach { (title, variables) ->
            runCatching {
                HomeRow(title, mediaPage(variables + mapOf("page" to 1, "perPage" to 20)))
            }.onSuccess { row ->
                if (row.items.isNotEmpty()) rows += row
            }.onFailure { error ->
                lastFailure = error
            }
        }
        if (rows.isEmpty() && lastFailure != null) {
            throw IOException("AniList could not load the home catalogue.", lastFailure)
        }
        return rows
    }

    suspend fun search(filters: AnimeSearchFilters): List<AnimeItem> {
        val sort = if (filters.sort == AnimeSort.SEARCH_MATCH && filters.query.isBlank()) {
            AnimeSort.POPULARITY
        } else {
            filters.sort
        }
        return mediaPage(
            mapOf(
                "search" to filters.query.takeIf { it.isNotBlank() },
                "format" to filters.format,
                "seasonYear" to filters.year,
                "genreIn" to filters.genres.takeIf { it.isNotEmpty() },
                "status" to filters.status,
                "page" to filters.page,
                "perPage" to 30,
                "sort" to listOf(sort.aniList)
            )
        )
    }

    suspend fun browse(format: String, page: Int = 1): List<AnimeItem> = mediaPage(
        mapOf(
            "format" to format,
            "page" to page,
            "perPage" to 30,
            "sort" to listOf("POPULARITY_DESC")
        )
    )

    suspend fun browseGenre(
        genres: List<String>,
        format: String? = null,
        page: Int = 1,
        sort: AnimeSort = AnimeSort.POPULARITY,
        status: String? = null,
        year: Int? = null
    ): List<AnimeItem> = mediaPage(
        mapOf(
            "genreIn" to genres.takeIf { it.isNotEmpty() },
            "format" to format,
            "page" to page,
            "perPage" to 30,
            "sort" to listOf(sort.aniList),
            "status" to status,
            "seasonYear" to year
        )
    )

    suspend fun resolveEpisodeSource(episode: AnimeEpisode, provider: String? = null): SourceResolution =
        miruro.resolveSource(
            episode.anilistId,
            episode.sourceCandidates.let { candidates ->
                provider
                    ?.takeIf { it != "Auto" }
                    ?.let { selected -> candidates.filter { it.provider.equals(selected, ignoreCase = true) } }
                    ?.ifEmpty { candidates }
                    ?: candidates
            }
        )

    suspend fun details(id: Int): AnimeDetails = withContext(Dispatchers.IO) {
        val media = anilist(MEDIA_QUERY, mapOf("id" to id)).path("Media")
        val title = preferredTitle(media.path("title")) ?: error("AniList returned details without a title.")
        val root = media.toSeasonEntry() ?: error("AniList returned incomplete media details.")
        val chain = runCatching { findSeasonChain(root) }
            .getOrDefault(listOf(root))
            .ifEmpty { listOf(root) }
        val seasons = chain.mapIndexed { index, entry ->
            val dates = episodeAirDates(entry.id)
            val episodeData = runCatching { miruro.episodeData(entry.id) }.getOrDefault(emptyMap())
            val episodeCount = resolvedEpisodeCount(entry, episodeData)
            val episodes = (1..episodeCount).flatMap { episodeNumber ->
                val metadata = episodeData[episodeNumber]
                    ?.metadata
                    ?.takeIf { isMetadataSafeForSeason(it, entry) }
                    ?: EpisodeMetadata()
                val candidates = episodeData[episodeNumber]?.candidates.orEmpty()
                val episodeTitle = metadata.title ?: "Episode $episodeNumber"
                val grouped = candidates.groupBy { it.category.lowercase(Locale.ROOT) }
                when {
                    grouped.isEmpty() -> listOf(
                        AnimeEpisode(
                            index + 1,
                            episodeNumber,
                            episodeTitle,
                            metadata.thumbnailUrl,
                            entry.duration,
                            dates[episodeNumber],
                            AudioType.SUB,
                            entry.id
                        )
                    )
                    else -> listOfNotNull(
                        grouped["sub"]?.let {
                            AnimeEpisode(
                                index + 1,
                                episodeNumber,
                                episodeTitle,
                                metadata.thumbnailUrl,
                                entry.duration,
                                dates[episodeNumber],
                                AudioType.SUB,
                                entry.id,
                                it
                            )
                        },
                        grouped["dub"]?.let {
                            AnimeEpisode(
                                index + 1,
                                episodeNumber,
                                episodeTitle,
                                metadata.thumbnailUrl,
                                entry.duration,
                                dates[episodeNumber],
                                AudioType.DUB,
                                entry.id,
                                it
                            )
                        }
                    ).ifEmpty {
                        listOf(
                            AnimeEpisode(
                                index + 1,
                                episodeNumber,
                                episodeTitle,
                                metadata.thumbnailUrl,
                                entry.duration,
                                dates[episodeNumber],
                                AudioType.SUB,
                                entry.id,
                                candidates
                            )
                        )
                    }
                }
            }
            AnimeSeason(entry.id, index + 1, entry.title, entry.year, episodes)
        }
        AnimeDetails(
            id = id,
            title = title,
            posterUrl = text(media, "coverImage", "extraLarge") ?: text(media, "coverImage", "large"),
            bannerUrl = text(media, "bannerImage"),
            description = clean(text(media, "description")),
            status = text(media, "status")?.pretty(),
            year = media.path("startDate").path("year").asInt(0).takeIf { it > 0 }
                ?: media.path("seasonYear").asInt(0).takeIf { it > 0 },
            rating = media.path("averageScore").asInt(0).takeIf { it > 0 }?.let { "$it% AniList" },
            genres = media.path("genres").mapNotNull { it.asText(null) },
            seasons = seasons
        )
    }

    private fun resolvedEpisodeCount(
        entry: SeasonEntry,
        episodeData: Map<Int, EpisodeMetadataWithSources>
    ): Int {
        val officialCount = entry.episodes?.takeIf { it in 1..MAX_EPISODES_PER_SEASON }
        if (officialCount != null) return officialCount
        return contiguousEpisodeCount(episodeData.keys).coerceAtMost(MAX_EPISODES_PER_SEASON)
    }

    private fun contiguousEpisodeCount(numbers: Collection<Int>): Int {
        val sorted = numbers
            .asSequence()
            .filter { it in 1..MAX_EPISODES_PER_SEASON }
            .distinct()
            .sorted()
            .toList()
        if (sorted.isEmpty() || sorted.first() > 2) return 0

        var lastAccepted = 0
        for (number in sorted) {
            if (number <= lastAccepted) continue
            val missingBetween = number - lastAccepted - 1
            if (missingBetween > MAX_ALLOWED_EPISODE_GAP) break
            lastAccepted = number
        }
        return lastAccepted
    }

    private fun isMetadataSafeForSeason(metadata: EpisodeMetadata, entry: SeasonEntry): Boolean {
        val title = metadata.title ?: return true
        val seasonHint = Regex("""(?i)\b(?:season|s)\s*(\d+)\b""")
            .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(\d+)(?:st|nd|rd|th)\s+season\b""")
                .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return true
        val entrySeasonHint = Regex("""(?i)\b(?:season|s)\s*(\d+)\b""")
            .find(entry.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(\d+)(?:st|nd|rd|th)\s+season\b""")
                .find(entry.title)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return entrySeasonHint == null || seasonHint == entrySeasonHint
    }

    private suspend fun mediaPage(variables: Map<String, Any?>): List<AnimeItem> = withContext(Dispatchers.IO) {
        val page = anilist(PAGE_QUERY, variables).path("Page")
        val media = page.path("media")
        if (!media.isArray) throw IOException("AniList returned an invalid catalogue response.")
        media.mapNotNull { it.toAnimeItem() }
    }

    private fun JsonNode.toAnimeItem(): AnimeItem? {
        val id = path("id").asInt(0).takeIf { it > 0 } ?: return null
        return AnimeItem(
            id = id,
            title = preferredTitle(path("title")) ?: return null,
            posterUrl = text(this, "coverImage", "extraLarge") ?: text(this, "coverImage", "large"),
            bannerUrl = text(this, "bannerImage"),
            type = animeType(text(this, "format")),
            year = path("seasonYear").asInt(0).takeIf { it > 0 }
                ?: path("startDate").path("year").asInt(0).takeIf { it > 0 },
            score = path("averageScore").asInt(0).takeIf { it > 0 }
        )
    }

    private suspend fun findSeasonChain(root: SeasonEntry, max: Int = MAX_SEASON_CHAIN): List<SeasonEntry> {
        if (root.format !in TV_FORMATS || root.isLongRunning()) return listOf(root)
        val seasons = linkedMapOf(root.id to root)
        val visited = mutableSetOf<Int>()

        suspend fun walk(entry: SeasonEntry) {
            if (!visited.add(entry.id) || seasons.size >= max) return
            if (entry.format !in TV_FORMATS || !sameFranchise(root.title, entry.title)) return
            seasons[entry.id] = entry
            (entry.prequels + entry.sequels).forEach { relatedId ->
                if (seasons.size < max) fetchSeason(relatedId)?.let { walk(it) }
            }
        }

        walk(root)
        return seasons.values.sortedWith(compareBy<SeasonEntry> { it.sortKey }.thenBy { it.id })
    }

    private suspend fun fetchSeason(id: Int): SeasonEntry? =
        anilist(SEASON_QUERY, mapOf("id" to id)).path("Media").toSeasonEntry()

    private fun JsonNode.toSeasonEntry(): SeasonEntry? {
        val id = path("id").asInt(0).takeIf { it > 0 } ?: return null
        val relations = path("relations").path("edges")
        return SeasonEntry(
            id = id,
            title = preferredTitle(path("title")) ?: return null,
            episodes = path("episodes").asInt(0).takeIf { it > 0 },
            duration = path("duration").asInt(0).takeIf { it > 0 },
            format = text(this, "format"),
            year = path("seasonYear").asInt(0).takeIf { it > 0 }
                ?: path("startDate").path("year").asInt(0).takeIf { it > 0 },
            month = path("startDate").path("month").asInt(12),
            day = path("startDate").path("day").asInt(31),
            prequels = relations
                .filter { text(it, "node", "type") == "ANIME" && text(it, "relationType") == "PREQUEL" }
                .map { it.path("node").path("id").asInt() },
            sequels = relations
                .filter { text(it, "node", "type") == "ANIME" && text(it, "relationType") == "SEQUEL" }
                .map { it.path("node").path("id").asInt() }
        )
    }

    private suspend fun episodeAirDates(id: Int): Map<Int, String> = dateCache[id] ?: runCatching {
        anilist(AIRING_QUERY, mapOf("mediaId" to id))
            .path("Page")
            .path("airingSchedules")
            .associate { it.path("episode").asInt() to epochDate(it.path("airingAt").asLong()) }
    }.getOrDefault(emptyMap()).also { dateCache[id] = it }

    private fun anilist(query: String, variables: Map<String, Any?>): JsonNode {
        val requestBody = mapper.writeValueAsString(mapOf("query" to query, "variables" to variables))
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody.toRequestBody(jsonType))
            .header("Accept", "application/json")
            .header("User-Agent", "AniStream-TV/1.0")
            .build()

        return client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val retryAfter = response.header("Retry-After")?.let { " Retry after $it seconds." }.orEmpty()
                throw IOException("AniList request failed with HTTP ${response.code}.$retryAfter")
            }
            if (responseText.isBlank()) throw IOException("AniList returned an empty response.")

            val root = runCatching { mapper.readTree(responseText) }
                .getOrElse { throw IOException("AniList returned malformed JSON.", it) }
            val errors = root.path("errors")
            if (errors.isArray && errors.size() > 0) {
                val message = errors.mapNotNull { it.path("message").asText(null) }
                    .joinToString("; ")
                    .ifBlank { "Unknown GraphQL error" }
                throw IOException("AniList error: $message")
            }
            val data = root.path("data")
            if (!data.isObject) throw IOException("AniList response did not contain data.")
            data
        }
    }

    private fun preferredTitle(node: JsonNode): String? =
        node.path("english").asText(null)
            ?: node.path("romaji").asText(null)
            ?: node.path("native").asText(null)

    private fun text(node: JsonNode, vararg path: String): String? =
        path.fold(node) { current, part -> current.path(part) }
            .asText(null)
            ?.takeIf { it.isNotBlank() && it != "null" }

    private fun clean(value: String?): String? =
        value?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotBlank() }

    private fun animeType(format: String?): AnimeType = when (format) {
        "MOVIE" -> AnimeType.MOVIE
        "OVA" -> AnimeType.OVA
        "SPECIAL" -> AnimeType.SPECIAL
        "TV", "TV_SHORT" -> AnimeType.TV
        else -> AnimeType.UNKNOWN
    }

    private fun String.pretty(): String =
        lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { it.titlecase(Locale.ROOT) }

    private fun epochDate(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(value * 1000))

    private fun currentYear(): Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    private fun currentSeason(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) {
        in 1..3 -> "WINTER"
        in 4..6 -> "SPRING"
        in 7..9 -> "SUMMER"
        else -> "FALL"
    }

    private fun titleTokens(value: String): Set<String> = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .split(Regex("\\s+"))
        .filter {
            it.isNotBlank() && it !in setOf(
                "the", "season", "part", "movie", "ova", "special", "final", "cour", "series"
            ) && it.toIntOrNull() == null
        }
        .toSet()

    private fun sameFranchise(first: String, second: String): Boolean {
        val firstTokens = titleTokens(first)
        val secondTokens = titleTokens(second)
        if (firstTokens.isEmpty() || secondTokens.isEmpty()) return false
        val shorterSize = minOf(firstTokens.size, secondTokens.size)
        val overlap = firstTokens.intersect(secondTokens).size
        val required = if (shorterSize == 1) 1 else 2
        return overlap >= required && overlap.toDouble() / shorterSize >= 0.6
    }

    private data class SeasonEntry(
        val id: Int,
        val title: String,
        val episodes: Int?,
        val duration: Int?,
        val format: String?,
        val year: Int?,
        val month: Int,
        val day: Int,
        val prequels: List<Int>,
        val sequels: List<Int>
    ) {
        val sortKey: Int = (year ?: 9999) * 10000 + month * 100 + day

        fun isLongRunning(): Boolean = (episodes ?: 0) >= 100 || title.lowercase(Locale.ROOT).let { value ->
            listOf("one piece", "detective conan", "case closed", "pokemon", "boruto", "naruto")
                .any(value::contains)
        }
    }

    companion object {
        private const val MAX_EPISODES_PER_SEASON = 1500
        private const val MAX_ALLOWED_EPISODE_GAP = 1
        private const val MAX_SEASON_CHAIN = 8
        private val TV_FORMATS = setOf("TV", "TV_SHORT")

        private const val PAGE_QUERY = "query(\$page:Int,\$perPage:Int,\$search:String,\$sort:[MediaSort],\$status:MediaStatus,\$season:MediaSeason,\$seasonYear:Int,\$format:MediaFormat,\$genreIn:[String]){Page(page:\$page,perPage:\$perPage){media(search:\$search,type:ANIME,sort:\$sort,status:\$status,season:\$season,seasonYear:\$seasonYear,format:\$format,genre_in:\$genreIn,isAdult:false){id title{romaji english native} coverImage{large extraLarge} bannerImage format seasonYear startDate{year} averageScore}}}"
        private const val MEDIA_QUERY = "query(\$id:Int){Media(id:\$id,type:ANIME){id title{romaji english native} description(asHtml:false) coverImage{large extraLarge} bannerImage format episodes duration averageScore genres status seasonYear startDate{year month day} relations{edges{relationType node{id type format title{romaji english native} episodes duration seasonYear startDate{year month day}}}}}}"
        private const val SEASON_QUERY = MEDIA_QUERY
        private const val AIRING_QUERY = "query(\$mediaId:Int){Page(page:1,perPage:200){airingSchedules(mediaId:\$mediaId,sort:EPISODE){episode airingAt}}}"
    }
}
