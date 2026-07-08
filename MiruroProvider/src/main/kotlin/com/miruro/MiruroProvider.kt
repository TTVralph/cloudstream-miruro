package com.miruro

import android.util.Base64
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.nicehttp.JsonAsString
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.Locale
import java.util.zip.GZIPInputStream

class MiruroProvider : MainAPI() {
    override var mainUrl = "https://www.miruro.tv"
    override var name = "Miruro"
    override var lang = "en"

    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie
    )

    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        "TRENDING_DESC" to "Trending Anime",
        "POPULARITY_DESC" to "Popular Anime",
        "SCORE_DESC" to "Top Rated Anime",
        "START_DATE_DESC" to "Recently Added Anime",
        "FAVOURITES_DESC" to "Fan Favorites"
    )
    override val hasDownloadSupport = false

    private val mapper = jacksonObjectMapper()
    private val anilistUrl = "https://graphql.anilist.co"
    private val pipeUrl = "$mainUrl/api/secure/pipe"
    private val providerPriority = listOf("zoro", "animepahe", "gogoanime", "kiwi")
    private val requestHeaders = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "en-US,en;q=0.9",
        "Content-Type" to "application/json",
        "Origin" to mainUrl,
        "Referer" to "$mainUrl/",
        "Sec-Fetch-Dest" to "empty",
        "Sec-Fetch-Mode" to "cors",
        "Sec-Fetch-Site" to "same-origin",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/110.0.0.0 Safari/537.36"
    )
    private val anilistHeaders = mapOf(
        "Accept" to "application/json",
        "Content-Type" to "application/json",
        "User-Agent" to requestHeaders.getValue("User-Agent")
    )

    private fun encodeUrl(value: String): String {
        return URLEncoder.encode(value, "UTF-8")
    }

    private fun slugify(value: String): String {
        return value
            .lowercase(Locale.ROOT)
            .replace("&", "and")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    private fun textOrNull(node: JsonNode?): String? {
        val value = node?.asText() ?: return null
        return value.takeIf { it.isNotBlank() && it != "null" }
    }

    private fun cleanDescription(value: String?): String? {
        return value
            ?.replace(Regex("<[^>]*>"), "")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractAniListId(url: String): Int? {
        return Regex("""/anilist/(\d+)""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }

    private fun errorResult(message: String): List<SearchResponse> {
        return listOf(
            newAnimeSearchResponse(
                "MIRURO ERROR: ${message.take(120)}",
                "$mainUrl/error",
                TvType.Anime
            ) {
                posterUrl = null
            }
        )
    }

    private fun urlSafeBase64(value: ByteArray): String {
        return Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun encodePipeRequest(payload: JsonNode): String {
        // Miruro's pipe endpoint expects the JSON request payload in the `e` query parameter as URL-safe Base64.
        return urlSafeBase64(mapper.writeValueAsBytes(payload))
    }

    private fun decodePipeResponse(value: String): JsonNode {
        // Pipe responses are URL-safe Base64 encoded, then GZIP compressed JSON.
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        val compressed = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        val decoded = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
        return mapper.readTree(decoded)
    }

    private fun decodeUrlSafeBase64Text(value: String): String? {
        return runCatching {
            val padded = value + "=".repeat((4 - value.length % 4) % 4)
            String(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP))
        }.getOrNull()
    }

    private fun normalizeEpisodeId(value: String): String {
        val decoded = decodeUrlSafeBase64Text(value)
        return if (decoded != null && ":" in decoded) decoded else value
    }

    private fun firstArray(node: JsonNode, vararg names: String): JsonNode? {
        return names.firstNotNullOfOrNull { name ->
            node.path(name).takeIf { it.isArray }
        }
    }

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

    private fun firstText(node: JsonNode, vararg names: String): String? {
        return names.firstNotNullOfOrNull { name -> textOrNull(node.get(name)) }
    }

    private fun streamUrl(node: JsonNode): String? {
        return firstText(node, "url", "file", "stream")
            ?: node.path("source").takeIf { it.isObject }?.let { firstText(it, "url", "file", "stream") }
    }

    private fun streamType(node: JsonNode): String? {
        return firstText(node, "type", "format")
            ?: node.path("source").takeIf { it.isObject }?.let { firstText(it, "type", "format") }
    }

    private fun streamQuality(node: JsonNode): String {
        return firstText(node, "quality", "label", "resolution")
            ?: node.path("source").takeIf { it.isObject }?.let { firstText(it, "quality", "label", "resolution") }
            ?: "Auto"
    }

    private fun generatedEpisodeSlug(episodeId: String, number: Int): String {
        val prefix = episodeId.substringBefore(":")
        return "$prefix-$number"
    }

    private fun episodeNumberFromId(episodeId: String): Int? {
        val slug = episodeId.substringAfterLast("/")
        return Regex("""(?:^|[-_])(\d+)$""")
            .find(slug)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
    }

    private fun watchPath(provider: String, anilistId: Int, category: String, episodeId: String, number: Int): String {
        return "watch/$provider/$anilistId/$category/${generatedEpisodeSlug(episodeId, number)}"
    }

    private suspend fun anilistQuery(query: String, variables: Map<String, Any?>): JsonNode {
        val body = mapper.createObjectNode().apply {
            put("query", query)
            set<JsonNode>("variables", mapper.valueToTree(variables))
        }
        val json = app.post(
            anilistUrl,
            json = JsonAsString(mapper.writeValueAsString(body)),
            headers = anilistHeaders
        ).text
        return mapper.readTree(json).path("data")
    }

    private suspend fun fetchRawEpisodes(anilistId: Int): JsonNode {
        val payload = mapper.createObjectNode().apply {
            put("path", "episodes")
            put("method", "GET")
            set<JsonNode>("query", mapper.createObjectNode().apply { put("anilistId", anilistId) })
            putNull("body")
            put("version", "0.1.0")
        }
        val encoded = encodePipeRequest(payload)
        val response = app.get("$pipeUrl?e=${encodeUrl(encoded)}", headers = requestHeaders).text.trim()
        return decodePipeResponse(response)
    }

    private suspend fun resolveSourceEpisodeId(
        episodeId: String,
        provider: String,
        anilistId: Int,
        category: String
    ): String {
        if (!episodeId.startsWith("watch/")) return episodeId
        val slug = episodeId.substringAfterLast("/")
        val episodes = fetchRawEpisodes(anilistId)
            .path("providers")
            .path(provider)
            .path("episodes")
            .path(category)
        if (!episodes.isArray) return episodeId
        episodes.forEach { ep ->
            val rawId = normalizeEpisodeId(textOrNull(ep.get("id")) ?: return@forEach)
            val number = ep.path("number").asInt(0)
            if (number > 0 && generatedEpisodeSlug(rawId, number) == slug) return rawId
        }
        return episodeId
    }

    private suspend fun pipeGet(path: String, query: JsonNode? = null): JsonNode {
        val payload = mapper.createObjectNode().apply {
            put("path", path)
            put("method", "GET")
            if (query == null) {
                putNull("query")
            } else {
                set<JsonNode>("query", query)
            }
            putNull("body")
            put("version", "0.1.0")
        }
        val encoded = encodePipeRequest(payload)
        val response = app.get("$pipeUrl?e=${encodeUrl(encoded)}", headers = requestHeaders).text.trim()
        return decodePipeResponse(response)
    }

    private fun hasPlayableStreams(node: JsonNode): Boolean {
        return findFirstArray(node, "streams", "sources")?.any { streamUrl(it) != null } == true
    }

    private suspend fun fetchSources(
        episodeId: String,
        provider: String,
        anilistId: Int,
        category: String
    ): JsonNode {
        if (episodeId.startsWith("watch/")) {
            val directSources = runCatching { pipeGet(episodeId) }.getOrNull()
            if (directSources != null && hasPlayableStreams(directSources)) return directSources
        }

        val sourceEpisodeId = resolveSourceEpisodeId(episodeId, provider, anilistId, category)
        val encodedEpisodeId = urlSafeBase64(sourceEpisodeId.toByteArray())
        return pipeGet(
            "sources",
            mapper.createObjectNode().apply {
                put("episodeId", encodedEpisodeId)
                put("provider", provider)
                put("category", category)
                put("anilistId", anilistId)
            }
        )
    }


    private data class SourceCandidate(
        val provider: String,
        val episodeId: String
    )

    private data class MediaSeasonEntry(
        val id: Int,
        val title: String,
        val poster: String?,
        val description: String?,
        val episodes: Int?,
        val status: String?,
        val seasonYear: Int?,
        val startYear: Int?,
        val startMonth: Int?,
        val startDay: Int?,
        val format: String?,
        val prequelIds: List<Int>,
        val sequelIds: List<Int>
    ) {
        val sortKey: Int
            get() = ((startYear ?: seasonYear ?: 9999) * 10_000) + ((startMonth ?: 12) * 100) + (startDay ?: 31)
    }

    private suspend fun sourceCandidates(
        selectedProvider: String,
        anilistId: Int,
        category: String,
        selectedEpisodeId: String,
        episodeNumber: Int?
    ): List<SourceCandidate> {
        val candidates = mutableListOf(SourceCandidate(selectedProvider, selectedEpisodeId))
        val rawEpisodes = runCatching { fetchRawEpisodes(anilistId) }.getOrNull()
            ?.path("providers")
            ?: return candidates
        if (!rawEpisodes.isObject) return candidates

        val normalizedSelectedId = normalizeEpisodeId(selectedEpisodeId)
        rawEpisodes.fields().forEach { providerEntry ->
            val provider = providerEntry.key
            val episodes = providerEntry.value.path("episodes").path(category)
            if (!episodes.isArray) return@forEach

            episodes.forEach { ep ->
                val rawEpisodeId = textOrNull(ep.get("id")) ?: return@forEach
                val sourceEpisodeId = if (rawEpisodeId.startsWith("watch/")) {
                    rawEpisodeId
                } else {
                    normalizeEpisodeId(rawEpisodeId)
                }
                val number = ep.path("number").asInt(0).takeIf { it > 0 }
                val sameEpisode = when {
                    episodeNumber != null && number == episodeNumber -> true
                    sourceEpisodeId == selectedEpisodeId -> true
                    sourceEpisodeId == normalizedSelectedId -> true
                    else -> false
                }
                if (sameEpisode) {
                    candidates.add(SourceCandidate(provider, sourceEpisodeId))
                }
            }
        }

        return candidates
            .distinct()
            .sortedBy { providerRank(it.provider) }
    }

    private fun preferredTitle(titleNode: JsonNode): String? {
        return textOrNull(titleNode.get("english"))
            ?: textOrNull(titleNode.get("romaji"))
            ?: textOrNull(titleNode.get("native"))
    }

    private fun tvTypeFromFormat(format: String?): TvType {
        return if (format == "MOVIE") TvType.AnimeMovie else TvType.Anime
    }

    private fun providerRank(provider: String): Int {
        val index = providerPriority.indexOf(provider.lowercase(Locale.ROOT))
        return if (index == -1) providerPriority.size else index
    }

    private fun runtimeMinutes(value: Int): Int? {
        if (value <= 0) return null
        // AniList durations are already minutes, while Miruro episode payloads often return seconds.
        return if (value > 300) ((value + 59) / 60) else value
    }

    private fun availabilityLines(subCount: Int, dubCount: Int): List<String> {
        return listOfNotNull(
            subCount.takeIf { it > 0 }?.let { "Sub: $it episodes" },
            dubCount.takeIf { it > 0 }?.let { "Dub: $it episodes" }
        )
    }


    private fun mediaSeasonEntry(media: JsonNode): MediaSeasonEntry? {
        val id = media.path("id").asInt(0).takeIf { it > 0 } ?: return null
        val title = preferredTitle(media.path("title")) ?: return null
        val poster = textOrNull(media.path("coverImage").get("extraLarge"))
            ?: textOrNull(media.path("coverImage").get("large"))
        val relations = media.path("relations").path("edges")
        val prequels = mutableListOf<Int>()
        val sequels = mutableListOf<Int>()
        if (relations.isArray) {
            relations.forEach { edge ->
                val relationType = textOrNull(edge.get("relationType")) ?: return@forEach
                val node = edge.path("node")
                if (textOrNull(node.get("type")) != "ANIME") return@forEach
                val relatedId = node.path("id").asInt(0).takeIf { it > 0 } ?: return@forEach
                when (relationType) {
                    "PREQUEL" -> prequels.add(relatedId)
                    "SEQUEL" -> sequels.add(relatedId)
                }
            }
        }

        return MediaSeasonEntry(
            id = id,
            title = title,
            poster = poster,
            description = cleanDescription(textOrNull(media.get("description"))),
            episodes = media.path("episodes").asInt(0).takeIf { it > 0 },
            status = textOrNull(media.get("status")),
            seasonYear = media.path("seasonYear").asInt(0).takeIf { it > 0 },
            startYear = media.path("startDate").path("year").asInt(0).takeIf { it > 0 },
            startMonth = media.path("startDate").path("month").asInt(0).takeIf { it > 0 },
            startDay = media.path("startDate").path("day").asInt(0).takeIf { it > 0 },
            format = textOrNull(media.get("format")),
            prequelIds = prequels.distinct(),
            sequelIds = sequels.distinct()
        )
    }

    private suspend fun fetchSeasonEntry(anilistId: Int): MediaSeasonEntry? {
        val gql = """
            query (${ '$' }id: Int) {
                Media(id: ${ '$' }id, type: ANIME) {
                    id
                    title { romaji english native }
                    coverImage { large extraLarge }
                    description(asHtml: false)
                    format
                    episodes
                    status
                    seasonYear
                    startDate { year month day }
                    relations {
                        edges {
                            relationType
                            node {
                                id
                                type
                                format
                                title { romaji english native }
                                coverImage { large extraLarge }
                                description(asHtml: false)
                                episodes
                                status
                                seasonYear
                                startDate { year month day }
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        return mediaSeasonEntry(anilistQuery(gql, mapOf("id" to anilistId)).path("Media"))
    }

    private fun isSeasonFormat(format: String?): Boolean {
        return format == "TV" || format == "TV_SHORT"
    }

    private fun franchiseTokens(title: String): Set<String> {
        val generic = setOf(
            "the", "a", "an", "season", "part", "cour", "ova", "special", "movie",
            "episode", "arc", "chapter", "final", "new", "second", "third", "fourth",
            "2nd", "3rd", "4th", "ii", "iii", "iv"
        )
        return title
            .lowercase(Locale.ROOT)
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in generic && it.toIntOrNull() == null }
            .toSet()
    }

    private fun sameMainFranchise(root: MediaSeasonEntry, candidate: MediaSeasonEntry): Boolean {
        val rootTokens = franchiseTokens(root.title)
        if (rootTokens.isEmpty()) return false
        val candidateTokens = franchiseTokens(candidate.title)
        return rootTokens.intersect(candidateTokens).isNotEmpty()
    }

    private suspend fun findSeasonChain(anilistId: Int, maxSeasons: Int = 12): List<MediaSeasonEntry> {
        val root = runCatching { fetchSeasonEntry(anilistId) }.getOrNull() ?: return emptyList()
        if (!isSeasonFormat(root.format)) return listOf(root).filter { isSeasonFormat(it.format) }
        if (isLongRunningContinuousSeries(root)) return listOf(root)

        val entries = linkedMapOf(root.id to root)
        val visited = mutableSetOf<Int>()

        suspend fun walk(id: Int): Unit {
            if (!visited.add(id) || entries.size >= maxSeasons) return
            val entry = if (id == root.id) root else runCatching { fetchSeasonEntry(id) }.getOrNull() ?: return
            if (!isSeasonFormat(entry.format) || !sameMainFranchise(root, entry)) return
            entries[id] = entry
            (entry.prequelIds + entry.sequelIds).forEach { relatedId ->
                if (entries.size < maxSeasons) walk(relatedId)
            }
        }

        walk(anilistId)
        return entries.values
            .filter { isSeasonFormat(it.format) }
            .sortedWith(compareBy<MediaSeasonEntry> { it.sortKey }.thenBy { it.id })
            .take(maxSeasons)
    }

    private fun isLongRunningContinuousSeries(entry: MediaSeasonEntry): Boolean {
        val normalizedTitle = entry.title
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()
        val strongTitleMatch = listOf(
            "one piece",
            "detective conan",
            "case closed",
            "pokemon",
            "pocket monsters",
            "boruto"
        ).any { normalizedTitle.contains(it) }
        val conservativeNarutoMatch = normalizedTitle == "naruto" ||
            normalizedTitle.contains("naruto shippuden") ||
            normalizedTitle.contains("naruto shippuuden")
        val longRunningHints = (entry.episodes ?: 0) >= 100 || entry.status == "RELEASING" || entry.episodes == null
        return (strongTitleMatch && longRunningHints) || (conservativeNarutoMatch && longRunningHints)
    }

    private fun mediaSearchResponse(item: JsonNode): SearchResponse? {
        val id = item.path("id").asInt(0).takeIf { it > 0 } ?: return null
        val title = preferredTitle(item.path("title")) ?: return null
        val poster = textOrNull(item.path("coverImage").get("extraLarge"))
            ?: textOrNull(item.path("coverImage").get("large"))
        val type = tvTypeFromFormat(textOrNull(item.get("format")))
        val dataUrl = "$mainUrl/anilist/$id/${slugify(title)}"

        return newAnimeSearchResponse(title, dataUrl, type) {
            posterUrl = poster
        }
    }

    private suspend fun anilistMediaPage(sort: String, page: Int, perPage: Int): List<SearchResponse> {
        val gql = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort]) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(type: ANIME, sort: ${'$'}sort, isAdult: false) {
                        id
                        title { romaji english native }
                        coverImage { large extraLarge }
                        format
                    }
                }
            }
        """.trimIndent()
        return anilistQuery(gql, mapOf("page" to page, "perPage" to perPage, "sort" to listOf(sort)))
            .path("Page")
            .path("media")
            .takeIf { it.isArray }
            ?.mapNotNull { mediaSearchResponse(it) }
            ?: emptyList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val results = anilistMediaPage(request.data, page, 20)
        return newHomePageResponse(request.name, results, hasNext = results.isNotEmpty())
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val gql = """
                query (${'$'}search: String, ${'$'}page: Int, ${'$'}perPage: Int) {
                    Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                        media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                            id
                            title { romaji english native }
                            coverImage { large extraLarge }
                            format
                        }
                    }
                }
            """.trimIndent()
            val media = anilistQuery(gql, mapOf("search" to query, "page" to 1, "perPage" to 20))
                .path("Page")
                .path("media")

            if (!media.isArray) return errorResult("AniList returned no media array")

            val results = media.mapNotNull { item -> mediaSearchResponse(item) }

            results.ifEmpty { errorResult("AniList returned 0 results for $query") }
        } catch (e: Exception) {
            errorResult("${e::class.java.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    private fun detailLines(
        seasons: List<MediaSeasonEntry>,
        totalEpisodes: Int,
        fallbackStatus: String?,
        rating: String?,
        genres: List<String>
    ): List<String> {
        val years = seasons.mapNotNull { it.startYear ?: it.seasonYear }.filter { it > 0 }
        val firstYear = years.minOrNull()
        val lastYear = years.maxOrNull()
        val status = seasons.firstOrNull { it.status == "RELEASING" }?.status
            ?: seasons.lastOrNull()?.status
            ?: fallbackStatus
        val formattedStatus = status
            ?.lowercase(Locale.ROOT)
            ?.replace('_', ' ')
            ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
        return listOfNotNull(
            seasons.size.takeIf { it > 1 }?.let { "Seasons: $it" },
            totalEpisodes.takeIf { it > 0 }?.let { "Episodes: $it total" },
            formattedStatus?.let { "Status: $it" },
            when {
                firstYear != null && lastYear != null && firstYear != lastYear -> "Years: $firstYear–$lastYear"
                firstYear != null -> "Years: $firstYear"
                else -> null
            },
            rating?.let { "Rating: $it" },
            genres.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Genres: ", separator = ", ")
        )
    }

    private data class ProviderEpisodeList(
        val provider: String,
        val category: String,
        val dubStatus: DubStatus,
        val episodes: JsonNode
    )

    private fun providerEpisodeNumbers(episodes: JsonNode): Set<Int> {
        if (!episodes.isArray) return emptySet()
        return episodes.mapNotNull { ep -> ep.path("number").asInt(0).takeIf { it > 0 } }.toSet()
    }

    private fun isValidProviderEpisodeList(episodes: JsonNode, anilistEpisodeCount: Int?): Boolean {
        val numbers = providerEpisodeNumbers(episodes)
        if (numbers.isEmpty()) return false
        val uniqueCount = numbers.size
        val maxNumber = numbers.maxOrNull() ?: return false
        if (maxNumber > 2000 || uniqueCount > 2000) return false
        if (anilistEpisodeCount != null) {
            val saneLimit = anilistEpisodeCount + 10
            if (uniqueCount > saneLimit || maxNumber > saneLimit) return false
        }
        return true
    }

    private fun bestProviderEpisodeList(candidates: List<ProviderEpisodeList>, anilistEpisodeCount: Int?): ProviderEpisodeList? {
        val available = candidates.filter { isValidProviderEpisodeList(it.episodes, anilistEpisodeCount) }
        return available
            .filter { providerRank(it.provider) < providerPriority.size }
            .minByOrNull { providerRank(it.provider) }
            ?: available.maxByOrNull { providerEpisodeNumbers(it.episodes).size }
    }

    private suspend fun addSeasonEpisodes(
        season: MediaSeasonEntry,
        seasonNumber: Int,
        episodeMap: MutableMap<DubStatus, MutableList<Episode>>
    ) {
        val rawEpisodes = runCatching { fetchRawEpisodes(season.id) }.getOrNull() ?: return
        val providers = rawEpisodes.path("providers")
        if (!providers.isObject) return

        val candidates = mutableListOf<ProviderEpisodeList>()
        providers.fields().forEach { providerEntry ->
            val provider = providerEntry.key
            val episodes = providerEntry.value.path("episodes")
            if (episodes.isArray) {
                candidates.add(ProviderEpisodeList(provider, "sub", DubStatus.Subbed, episodes))
            } else {
                episodes.path("sub").takeIf { it.isArray }?.let {
                    candidates.add(ProviderEpisodeList(provider, "sub", DubStatus.Subbed, it))
                }
                episodes.path("dub").takeIf { it.isArray }?.let {
                    candidates.add(ProviderEpisodeList(provider, "dub", DubStatus.Dubbed, it))
                }
            }
        }

        listOf(DubStatus.Subbed to "sub", DubStatus.Dubbed to "dub").forEach { (dubStatus, category) ->
            val selected = bestProviderEpisodeList(
                candidates.filter { it.dubStatus == dubStatus && it.category == category },
                season.episodes
            ) ?: return@forEach
            val bucket = episodeMap.getOrPut(dubStatus) { mutableListOf() }
            val seenKeys = bucket.mapNotNull { episode ->
                val number = episode.episode ?: return@mapNotNull null
                "${episode.season ?: 1}-${dubStatus.name}-$number"
            }.toMutableSet()

            selected.episodes.forEach { ep ->
                val rawEpisodeId = textOrNull(ep.get("id")) ?: return@forEach
                val sourceEpisodeId = if (rawEpisodeId.startsWith("watch/")) rawEpisodeId else normalizeEpisodeId(rawEpisodeId)
                val number = ep.path("number").asInt(0).takeIf { it > 0 } ?: return@forEach
                val dedupeKey = "$seasonNumber-${dubStatus.name}-$number"
                if (!seenKeys.add(dedupeKey)) return@forEach
                val episodeId = if (sourceEpisodeId.startsWith("watch/")) {
                    sourceEpisodeId
                } else {
                    watchPath(selected.provider, season.id, category, sourceEpisodeId, number)
                }
                val rawTitle = textOrNull(ep.get("title")) ?: "Episode $number"
                val episodeTitle = if (seasonNumber > 1 && rawTitle == "Episode $number") {
                    "Season $seasonNumber Episode $number"
                } else {
                    rawTitle
                }
                val data = listOf(selected.provider, season.id.toString(), category, number.toString(), episodeId).joinToString("|")
                bucket.add(
                    newEpisode(data) {
                        name = episodeTitle
                        episode = number
                        this.season = seasonNumber
                        posterUrl = firstText(ep, "image", "thumbnail", "img")
                        runTime = runtimeMinutes(ep.path("duration").asInt(0))
                    }
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val anilistId = extractAniListId(url) ?: return null

        return try {
            val gql = """
                query (${ '$' }id: Int) {
                    Media(id: ${ '$' }id, type: ANIME) {
                        id
                        title { romaji english native }
                        description(asHtml: false)
                        coverImage { large extraLarge }
                        format
                        episodes
                        duration
                        averageScore
                        genres
                        status
                        seasonYear
                        startDate { year month day }
                    }
                }
            """.trimIndent()
            val media = anilistQuery(gql, mapOf("id" to anilistId)).path("Media")
            val title = preferredTitle(media.path("title")) ?: return null
            val poster = textOrNull(media.path("coverImage").get("extraLarge"))
                ?: textOrNull(media.path("coverImage").get("large"))
            val openedDescription = cleanDescription(textOrNull(media.get("description")))
            val type = tvTypeFromFormat(textOrNull(media.get("format")))
            val chain = if (type == TvType.AnimeMovie) emptyList() else runCatching { findSeasonChain(anilistId) }.getOrNull().orEmpty()
            val seasons = chain.ifEmpty { mediaSeasonEntry(media)?.let { listOf(it) }.orEmpty() }
            val mainSeason = seasons.firstOrNull() ?: mediaSeasonEntry(media)
            val description = mainSeason?.description ?: openedDescription

            val rating = media.path("averageScore").asInt(0).takeIf { it > 0 }?.let { "$it% AniList" }
            val genres = media.path("genres").takeIf { it.isArray }?.mapNotNull { textOrNull(it) }.orEmpty()

            val episodeMap = mutableMapOf<DubStatus, MutableList<Episode>>()
            seasons.forEachIndexed { index, season ->
                runCatching { addSeasonEpisodes(season, index + 1, episodeMap) }
            }

            if (type == TvType.AnimeMovie && episodeMap.isEmpty()) {
                return newMovieLoadResponse(title, url, type, "kiwi|$anilistId|sub|1|movie-1") {
                    posterUrl = poster
                    plot = listOfNotNull(
                        description,
                        rating?.let { "Rating: $it" },
                        genres.takeIf { it.isNotEmpty() }?.joinToString(prefix = "Genres: ", separator = ", ")
                    ).joinToString("\n\n")
                }
            }

            if (episodeMap.isEmpty()) {
                val fallbackSeason = mainSeason
                val episodeCount = fallbackSeason?.episodes?.takeIf { it in 1..2000 }
                if (episodeCount != null) {
                    val fallbackSeasonNumber = seasons.indexOfFirst { it.id == (fallbackSeason.id) }.takeIf { it >= 0 }?.plus(1) ?: 1
                    episodeMap[DubStatus.Subbed] = (1..episodeCount).map { episodeNumber ->
                        newEpisode("kiwi|${fallbackSeason.id}|sub|$episodeNumber|animepahe-$episodeNumber") {
                            name = "Episode $episodeNumber"
                            episode = episodeNumber
                            season = fallbackSeasonNumber
                        }
                    }.toMutableList()
                }
            }

            val finalEpisodes = mutableMapOf<DubStatus, List<Episode>>().apply {
                episodeMap.forEach { (dubStatus, episodeList) ->
                    this[dubStatus] = episodeList
                        .distinctBy { "${it.season ?: 1}-${dubStatus.name}-${it.episode ?: 0}" }
                        .sortedWith(compareBy<Episode> { it.season ?: 1 }.thenBy { it.episode ?: 0 })
                }
            }
            val subCount = finalEpisodes[DubStatus.Subbed].orEmpty()
                .map { "${it.season ?: 1}-${it.episode ?: 0}" }
                .distinct()
                .size
            val dubCount = finalEpisodes[DubStatus.Dubbed].orEmpty()
                .map { "${it.season ?: 1}-${it.episode ?: 0}" }
                .distinct()
                .size
            val totalEpisodes = finalEpisodes.values
                .flatten()
                .distinctBy { "${it.season ?: 1}-${it.episode ?: 0}" }
                .size
            val detailLines = detailLines(seasons, totalEpisodes, textOrNull(media.get("status")), rating, genres)
            val finalPlot = buildList {
                description?.let { add(it) }
                availabilityLines(subCount, dubCount).takeIf { it.isNotEmpty() }?.let { add("Availability:\n" + it.joinToString("\n")) }
                detailLines.takeIf { it.isNotEmpty() }?.let { add("Details:\n" + it.joinToString("\n")) }
            }.joinToString("\n\n")

            newAnimeLoadResponse(title, url, type) {
                posterUrl = mainSeason?.poster ?: poster
                plot = finalPlot
                episodes = finalEpisodes
            }
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val parts = data.split("|", limit = 5)
            if (parts.size != 4 && parts.size != 5) return false
            val provider = parts[0]
            val anilistId = parts[1].toIntOrNull() ?: return false
            val category = parts[2]
            val episodeNumber = if (parts.size == 5) parts[3].toIntOrNull() else null
            val episodeId = if (parts.size == 5) parts[4] else parts[3]
            val candidates = sourceCandidates(
                provider,
                anilistId,
                category,
                episodeId,
                episodeNumber ?: episodeNumberFromId(episodeId)
            )

            val subtitleUrls = mutableSetOf<String>()
            val streamUrls = mutableSetOf<String>()
            var found = false

            candidates.forEach { candidate ->
                val sources = runCatching {
                    fetchSources(candidate.episodeId, candidate.provider, anilistId, category)
                }.getOrNull() ?: return@forEach

                findFirstArray(sources, "subtitles", "tracks")?.forEach { subtitle ->
                    val url = firstText(subtitle, "file", "url") ?: return@forEach
                    if (!subtitleUrls.add(url)) return@forEach
                    val label = firstText(subtitle, "label", "lang", "language") ?: "Subtitle"
                    subtitleCallback(newSubtitleFile(label, url))
                }

                findFirstArray(sources, "streams", "sources")?.forEach { stream ->
                    val url = streamUrl(stream) ?: return@forEach
                    if (!streamUrls.add(url)) return@forEach
                    val qualityLabel = streamQuality(stream)
                    val streamType = streamType(stream)?.lowercase(Locale.ROOT)
                    callback(
                        newExtractorLink(
                            source = name,
                            name = "$name ${candidate.provider.uppercase(Locale.ROOT)} $qualityLabel",
                            url = url,
                            type = if (streamType == "dash" || streamType == "mpd") ExtractorLinkType.DASH else ExtractorLinkType.M3U8
                        ) {
                            referer = mainUrl
                            quality = getQualityFromName(qualityLabel).takeIf { it != Qualities.Unknown.value }
                                ?: Qualities.Unknown.value
                            headers = mapOf("Referer" to "$mainUrl/", "Origin" to mainUrl)
                        }
                    )
                    found = true
                }
            }

            found
        } catch (_: Exception) {
            false
        }
    }
}
