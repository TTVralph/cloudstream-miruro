package com.ttvralph.miruroapp.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AniListRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()
    private val mapper = jacksonObjectMapper()
    private val jsonType = "application/json".toMediaType()
    private val miruro = MiruroRepository()
    private val dateCache = ConcurrentHashMap<Int, Map<Int, String>>()
    private val detailsShellCache = ConcurrentHashMap<Int, AnimeDetails>()
    private val seasonCache = ConcurrentHashMap<Int, AnimeSeason>()

    @Volatile
    private var cachedHome: Pair<Long, List<HomeRow>>? = null

    suspend fun homeRows(): List<HomeRow> {
        cachedHome?.takeIf { System.currentTimeMillis() - it.first < HOME_CACHE_MS }
            ?.second
            ?.let { return it }

        val data = withContext(Dispatchers.IO) {
            retryRequest { anilist(HOME_QUERY, mapOf("season" to currentSeason(), "seasonYear" to currentYear())) }
        }
        val definitions = listOf(
            "Trending Now" to "trending",
            "Currently Airing" to "airing",
            "Popular This Season" to "seasonal",
            "Top Rated Anime" to "topRated",
            "Anime Movies" to "movies"
        )
        val rows = definitions.mapNotNull { (title, key) ->
            val items = mediaItems(data.path(key).path("media"))
            items.takeIf { it.isNotEmpty() }?.let { HomeRow(title, it) }
        }
        if (rows.isEmpty()) throw IOException("AniList could not load the home catalogue.")
        cachedHome = System.currentTimeMillis() to rows
        return rows
    }

    suspend fun search(filters: AnimeSearchFilters): List<AnimeItem> {
        val query = filters.query.trim()
        val variables = linkedMapOf<String, Any?>(
            "page" to filters.page,
            "perPage" to 30
        ).apply {
            query.takeIf { it.isNotBlank() }?.let { put("search", it) }
            filters.format?.let { put("format", it) }
            filters.year?.let { put("seasonYear", it) }
            filters.genres.takeIf { it.isNotEmpty() }?.let { put("genreIn", it) }
            filters.status?.let { put("status", it) }
            put(
                "sort",
                listOf(
                    if (query.isBlank() && filters.sort == AnimeSort.SEARCH_MATCH) {
                        AnimeSort.POPULARITY.aniList
                    } else {
                        filters.sort.aniList
                    }
                )
            )
        }

        val first = mediaPage(variables)
        if (first.isNotEmpty() || query.isBlank()) return first

        delay(180L)
        return mediaPage(
            variables.toMutableMap().apply {
                put("search", query.lowercase(Locale.ROOT))
                remove("sort")
            }
        )
    }

    suspend fun browse(format: String, page: Int = 1): List<AnimeItem> =
        mediaPage(
            linkedMapOf(
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
    ): List<AnimeItem> {
        val variables = linkedMapOf<String, Any?>(
            "page" to page,
            "perPage" to 30,
            "sort" to listOf(
                if (sort == AnimeSort.SEARCH_MATCH) AnimeSort.POPULARITY.aniList else sort.aniList
            )
        ).apply {
            genres.takeIf { it.isNotEmpty() }?.let { put("genreIn", it) }
            format?.let { put("format", it) }
            status?.let { put("status", it) }
            year?.let { put("seasonYear", it) }
        }

        val first = mediaPage(variables)
        if (first.isNotEmpty()) return first
        if (genres.isEmpty() && format == null && status == null && year == null) {
            delay(180L)
            return mediaPage(
                linkedMapOf(
                    "page" to page,
                    "perPage" to 30,
                    "sort" to listOf("TRENDING_DESC")
                )
            )
        }
        return first
    }

    suspend fun resolveEpisodeSource(episode: AnimeEpisode, provider: String? = null): SourceResolution =
        miruro.resolveSource(
            episode.anilistId,
            episode.sourceCandidates,
            provider?.takeUnless { it.equals("Auto", ignoreCase = true) }
        )

    /**
     * Returns title metadata and lightweight season placeholders after one AniList request.
     * Episode/provider calls are deliberately deferred until a season is opened.
     */
    suspend fun detailsShell(id: Int): AnimeDetails {
        detailsShellCache[id]?.let { return it }
        val media = withContext(Dispatchers.IO) {
            retryRequest { anilist(MEDIA_QUERY, mapOf("id" to id)) }.path("Media")
        }
        val title = preferredTitle(media.path("title"))
            ?: error("AniList returned details without a title.")
        val root = media.toSeasonEntry()
            ?: error("AniList returned incomplete media details.")

        val seasons = lightweightSeasonChain(root, media).mapIndexed { index, entry ->
            AnimeSeason(
                id = entry.id,
                seasonNumber = index + 1,
                title = entry.title,
                year = entry.year,
                episodes = emptyList(),
                synopsis = entry.description,
                episodeCount = entry.episodes,
                runtimeMinutes = entry.duration,
                episodesLoaded = false
            )
        }

        val result = AnimeDetails(
            id = id,
            title = title,
            posterUrl = text(media, "coverImage", "extraLarge")
                ?: text(media, "coverImage", "large"),
            bannerUrl = text(media, "bannerImage"),
            description = clean(text(media, "description")),
            status = text(media, "status")?.pretty(),
            year = media.path("startDate").path("year").asInt(0).takeIf { it > 0 }
                ?: media.path("seasonYear").asInt(0).takeIf { it > 0 },
            rating = media.path("averageScore").asInt(0).takeIf { it > 0 }
                ?.let { "$it% AniList" },
            genres = media.path("genres").mapNotNull { it.asText(null) },
            seasons = seasons.ifEmpty {
                listOf(
                    AnimeSeason(
                        id = root.id,
                        seasonNumber = 1,
                        title = root.title,
                        year = root.year,
                        episodes = emptyList(),
                        synopsis = root.description,
                        episodeCount = root.episodes,
                        runtimeMinutes = root.duration,
                        episodesLoaded = false
                    )
                )
            }
        )
        detailsShellCache[id] = result
        return result
    }

    /** Keeps older call sites working when they explicitly need every season populated. */
    suspend fun details(id: Int): AnimeDetails {
        val shell = detailsShell(id)
        val limiter = Semaphore(MAX_CONCURRENT_SEASON_LOADS)
        val loaded = coroutineScope {
            shell.seasons.map { season ->
                async { limiter.withPermit { loadSeasonEpisodes(season) } }
            }.awaitAll()
        }
        return shell.copy(seasons = loaded)
    }

    suspend fun loadSeasonEpisodes(season: AnimeSeason): AnimeSeason {
        if (season.episodesLoaded) return season
        seasonCache[season.id]?.let { cached ->
            return cached.copy(
                seasonNumber = season.seasonNumber,
                title = season.title,
                year = season.year,
                synopsis = season.synopsis ?: cached.synopsis
            )
        }

        val episodeData = withContext(Dispatchers.IO) {
            retryRequest(attempts = 2) { miruro.episodeData(season.id) }
        }
        val episodeCount = resolvedEpisodeCount(season.episodeCount, episodeData)
        val episodes = (1..episodeCount).flatMap { episodeNumber ->
            val metadata = episodeData[episodeNumber]
                ?.metadata
                ?.takeIf { isMetadataSafeForSeason(it, season.title) }
                ?: EpisodeMetadata()
            val candidates = episodeData[episodeNumber]?.candidates.orEmpty()
            val episodeTitle = metadata.title ?: "Episode $episodeNumber"
            val grouped = candidates.groupBy { it.category.lowercase(Locale.ROOT) }

            when {
                grouped.isEmpty() -> listOf(
                    AnimeEpisode(
                        seasonNumber = season.seasonNumber,
                        episodeNumber = episodeNumber,
                        title = episodeTitle,
                        thumbnailUrl = metadata.thumbnailUrl,
                        runtimeMinutes = season.runtimeMinutes,
                        releaseDate = null,
                        audioType = AudioType.SUB,
                        anilistId = season.id,
                        synopsis = metadata.synopsis
                    )
                )
                else -> listOfNotNull(
                    grouped["sub"]?.let {
                        AnimeEpisode(
                            seasonNumber = season.seasonNumber,
                            episodeNumber = episodeNumber,
                            title = episodeTitle,
                            thumbnailUrl = metadata.thumbnailUrl,
                            runtimeMinutes = season.runtimeMinutes,
                            releaseDate = null,
                            audioType = AudioType.SUB,
                            anilistId = season.id,
                            sourceCandidates = it,
                            synopsis = metadata.synopsis
                        )
                    },
                    grouped["dub"]?.let {
                        AnimeEpisode(
                            seasonNumber = season.seasonNumber,
                            episodeNumber = episodeNumber,
                            title = episodeTitle,
                            thumbnailUrl = metadata.thumbnailUrl,
                            runtimeMinutes = season.runtimeMinutes,
                            releaseDate = null,
                            audioType = AudioType.DUB,
                            anilistId = season.id,
                            sourceCandidates = it,
                            synopsis = metadata.synopsis
                        )
                    }
                ).ifEmpty {
                    listOf(
                        AnimeEpisode(
                            seasonNumber = season.seasonNumber,
                            episodeNumber = episodeNumber,
                            title = episodeTitle,
                            thumbnailUrl = metadata.thumbnailUrl,
                            runtimeMinutes = season.runtimeMinutes,
                            releaseDate = null,
                            audioType = AudioType.SUB,
                            anilistId = season.id,
                            sourceCandidates = candidates,
                            synopsis = metadata.synopsis
                        )
                    )
                }
            }
        }

        return season.copy(
            episodes = episodes,
            episodeCount = episodeCount,
            episodesLoaded = true
        ).also { seasonCache[season.id] = it }
    }

    suspend fun loadSeasonAirDates(seasonId: Int): Map<Int, String> {
        dateCache[seasonId]?.let { return it }
        val values = runCatching {
            withContext(Dispatchers.IO) {
                retryRequest(attempts = 2) {
                    anilist(AIRING_QUERY, mapOf("mediaId" to seasonId))
                }.path("Page")
                    .path("airingSchedules")
                    .associate {
                        it.path("episode").asInt() to epochDate(it.path("airingAt").asLong())
                    }
            }
        }.getOrDefault(emptyMap())
        dateCache[seasonId] = values
        return values
    }

    private fun lightweightSeasonChain(root: SeasonEntry, media: JsonNode): List<SeasonEntry> {
        if (root.format !in TV_FORMATS || root.isLongRunning()) return listOf(root)
        val related = media.path("relations").path("edges")
            .mapNotNull { edge ->
                val relation = text(edge, "relationType")
                val node = edge.path("node")
                if (relation !in setOf("PREQUEL", "SEQUEL")) return@mapNotNull null
                if (text(node, "type") != "ANIME") return@mapNotNull null
                node.toSeasonEntry()
            }
            .filter { it.format in TV_FORMATS && sameFranchise(root.title, it.title) }

        return (related + root)
            .distinctBy { it.id }
            .sortedWith(compareBy<SeasonEntry> { it.sortKey }.thenBy { it.id })
            .take(MAX_SEASON_CHAIN)
    }

    private fun resolvedEpisodeCount(
        officialCount: Int?,
        episodeData: Map<Int, EpisodeMetadataWithSources>
    ): Int {
        val official = officialCount?.takeIf { it in 1..MAX_EPISODES_PER_SEASON }
        if (official != null) return official
        return contiguousEpisodeCount(episodeData.keys).coerceAtMost(MAX_EPISODES_PER_SEASON)
    }

    private fun contiguousEpisodeCount(numbers: Collection<Int>): Int {
        val sorted = numbers.asSequence()
            .filter { it in 1..MAX_EPISODES_PER_SEASON }
            .distinct()
            .sorted()
            .toList()
        if (sorted.isEmpty() || sorted.first() > 2) return 0
        var lastAccepted = 0
        for (number in sorted) {
            if (number <= lastAccepted) continue
            if (number - lastAccepted - 1 > MAX_ALLOWED_EPISODE_GAP) break
            lastAccepted = number
        }
        return lastAccepted
    }

    private fun isMetadataSafeForSeason(metadata: EpisodeMetadata, seasonTitle: String): Boolean {
        val title = metadata.title ?: return true
        val seasonHint = Regex("""(?i)\b(?:season|s)\s*(\d+)\b""")
            .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(\d+)(?:st|nd|rd|th)\s+season\b""")
                .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return true
        val expected = Regex("""(?i)\b(?:season|s)\s*(\d+)\b""")
            .find(seasonTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("""(?i)\b(\d+)(?:st|nd|rd|th)\s+season\b""")
                .find(seasonTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return expected == null || seasonHint == expected
    }

    private suspend fun mediaPage(variables: Map<String, Any?>): List<AnimeItem> =
        withContext(Dispatchers.IO) {
            val page = retryRequest {
                anilist(PAGE_QUERY, variables.filterValues { it != null })
            }.path("Page")
            mediaItems(page.path("media"))
        }

    private fun mediaItems(media: JsonNode): List<AnimeItem> {
        if (!media.isArray) return emptyList()
        return media.mapNotNull { it.toAnimeItem() }
    }

    private fun JsonNode.toAnimeItem(): AnimeItem? {
        val id = path("id").asInt(0).takeIf { it > 0 } ?: return null
        return AnimeItem(
            id = id,
            title = preferredTitle(path("title")) ?: return null,
            posterUrl = text(this, "coverImage", "extraLarge")
                ?: text(this, "coverImage", "large"),
            bannerUrl = text(this, "bannerImage"),
            type = animeType(text(this, "format")),
            year = path("seasonYear").asInt(0).takeIf { it > 0 }
                ?: path("startDate").path("year").asInt(0).takeIf { it > 0 },
            score = path("averageScore").asInt(0).takeIf { it > 0 }
        )
    }

    private fun JsonNode.toSeasonEntry(): SeasonEntry? {
        val id = path("id").asInt(0).takeIf { it > 0 } ?: return null
        return SeasonEntry(
            id = id,
            title = preferredTitle(path("title")) ?: return null,
            description = clean(text(this, "description")),
            episodes = path("episodes").asInt(0).takeIf { it > 0 },
            duration = path("duration").asInt(0).takeIf { it > 0 },
            format = text(this, "format"),
            year = path("seasonYear").asInt(0).takeIf { it > 0 }
                ?: path("startDate").path("year").asInt(0).takeIf { it > 0 },
            month = path("startDate").path("month").asInt(12),
            day = path("startDate").path("day").asInt(31)
        )
    }

    private suspend fun <T> retryRequest(
        attempts: Int = 3,
        block: suspend () -> T
    ): T {
        var lastError: Throwable? = null
        repeat(attempts) { attempt ->
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                if (attempt < attempts - 1) delay(350L * (attempt + 1))
            }
        }
        throw lastError ?: IOException("Request failed.")
    }

    private fun anilist(query: String, variables: Map<String, Any?>): JsonNode {
        val requestBody = mapper.writeValueAsString(
            mapOf("query" to query, "variables" to variables.filterValues { it != null })
        )
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(requestBody.toRequestBody(jsonType))
            .header("Accept", "application/json")
            .header("User-Agent", "AniStream-TV/1.0")
            .build()

        return client.newCall(request).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val retryAfter = response.header("Retry-After")
                    ?.let { " Retry after $it seconds." }
                    .orEmpty()
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

    private fun clean(value: String?): String? = value
        ?.replace(Regex("(?i)<br\\s*/?>"), "\n")
        ?.replace(Regex("<[^>]*>"), "")
        ?.replace("&amp;", "&")
        ?.replace("&quot;", "\"")
        ?.replace("&#39;", "'")
        ?.trim()
        ?.takeIf { it.isNotBlank() }

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

    private fun currentYear(): Int =
        java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)

    private fun currentSeason(): String =
        when (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) {
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
        val description: String?,
        val episodes: Int?,
        val duration: Int?,
        val format: String?,
        val year: Int?,
        val month: Int,
        val day: Int
    ) {
        val sortKey: Int = (year ?: 9999) * 10000 + month * 100 + day

        fun isLongRunning(): Boolean = (episodes ?: 0) >= 100 ||
            title.lowercase(Locale.ROOT).let { value ->
                listOf("one piece", "detective conan", "case closed", "pokemon", "boruto", "naruto")
                    .any(value::contains)
            }
    }

    companion object {
        private const val HOME_CACHE_MS = 5 * 60 * 1000L
        private const val MAX_EPISODES_PER_SEASON = 1500
        private const val MAX_ALLOWED_EPISODE_GAP = 1
        private const val MAX_SEASON_CHAIN = 8
        private const val MAX_CONCURRENT_SEASON_LOADS = 3
        private val TV_FORMATS = setOf("TV", "TV_SHORT")

        private const val MEDIA_CARD_FIELDS = "id title{romaji english native} coverImage{large extraLarge} bannerImage format seasonYear startDate{year} averageScore"

        private const val HOME_QUERY = "query(\$season:MediaSeason,\$seasonYear:Int){" +
            "trending:Page(page:1,perPage:20){media(type:ANIME,sort:[TRENDING_DESC],isAdult:false){$MEDIA_CARD_FIELDS}}" +
            "airing:Page(page:1,perPage:20){media(type:ANIME,status:RELEASING,sort:[POPULARITY_DESC],isAdult:false){$MEDIA_CARD_FIELDS}}" +
            "seasonal:Page(page:1,perPage:20){media(type:ANIME,season:\$season,seasonYear:\$seasonYear,sort:[POPULARITY_DESC],isAdult:false){$MEDIA_CARD_FIELDS}}" +
            "topRated:Page(page:1,perPage:20){media(type:ANIME,sort:[SCORE_DESC],isAdult:false){$MEDIA_CARD_FIELDS}}" +
            "movies:Page(page:1,perPage:20){media(type:ANIME,format:MOVIE,sort:[POPULARITY_DESC],isAdult:false){$MEDIA_CARD_FIELDS}}" +
            "}"

        private const val PAGE_QUERY = "query(\$page:Int,\$perPage:Int,\$search:String,\$sort:[MediaSort],\$status:MediaStatus,\$season:MediaSeason,\$seasonYear:Int,\$format:MediaFormat,\$genreIn:[String]){Page(page:\$page,perPage:\$perPage){media(search:\$search,type:ANIME,sort:\$sort,status:\$status,season:\$season,seasonYear:\$seasonYear,format:\$format,genre_in:\$genreIn,isAdult:false){$MEDIA_CARD_FIELDS}}}"

        private const val MEDIA_QUERY = "query(\$id:Int){Media(id:\$id,type:ANIME){id title{romaji english native} description(asHtml:false) coverImage{large extraLarge} bannerImage format episodes duration averageScore genres status seasonYear startDate{year month day} relations{edges{relationType node{id type format title{romaji english native} description(asHtml:false) episodes duration seasonYear startDate{year month day}}}}}}"

        private const val AIRING_QUERY = "query(\$mediaId:Int){Page(page:1,perPage:200){airingSchedules(mediaId:\$mediaId,sort:EPISODE){episode airingAt}}}"
    }
}
