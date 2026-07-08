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
import java.util.Calendar
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
        "TRENDING_DESC" to "Trending Now",
        "POPULARITY_DESC|CURRENT|TV,TV_SHORT,ONA" to "Airing This Season",
        "SCORE_DESC|CURRENT|TV,TV_SHORT,ONA" to "Best Airing Anime",
        "POPULARITY_DESC|MOVIE" to "Popular Anime Movies",
        "SCORE_DESC|COMPLETED|TV,TV_SHORT,ONA,OVA,SPECIAL" to "All-Time Favorites",
        "POPULARITY_DESC|Action,Adventure" to "Action & Adventure",
        "POPULARITY_DESC|Comedy,Slice of Life" to "Easy Watching"
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
            headers = requestHeaders
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

    private fun currentAniListSeason(): Pair<String, Int> {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val season = when (calendar.get(Calendar.MONTH)) {
            Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "WINTER"
            Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "SPRING"
            Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "SUMMER"
            else -> "FALL"
        }
        return season to year
    }

    private fun runtimeMinutes(value: Int): Int? {
        if (value <= 0) return null
        // AniList durations are already minutes, while Miruro episode payloads often return seconds.
        return if (value > 300) ((value + 59) / 60) else value
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

    private fun enumList(value: String?): List<String>? {
        return value
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.takeIf { it.isNotEmpty() }
    }

    private suspend fun anilistMediaPage(data: String, page: Int, perPage: Int): List<SearchResponse> {
        val parts = data.split("|")
        val sort = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: "TRENDING_DESC"
        val filter = parts.getOrNull(1)
        val formats = enumList(parts.getOrNull(2))
        val genres = enumList(filter)
        val (season, year) = currentAniListSeason()
        val variables = mutableMapOf<String, Any?>(
            "page" to page,
            "perPage" to perPage,
            "sort" to listOf(sort),
            "season" to null,
            "seasonYear" to null,
            "status" to null,
            "statusNot" to "NOT_YET_RELEASED",
            "format" to null,
            "formatIn" to formats,
            "genreIn" to genres
        )

        when (filter) {
            "CURRENT" -> {
                variables["season"] = season
                variables["seasonYear"] = year
                variables["status"] = "RELEASING"
                variables["genreIn"] = null
            }
            "UPCOMING" -> {
                variables["status"] = "NOT_YET_RELEASED"
                variables["statusNot"] = null
                variables["genreIn"] = null
            }
            "COMPLETED" -> {
                variables["status"] = "FINISHED"
                variables["genreIn"] = null
            }
            "MOVIE" -> {
                variables["format"] = "MOVIE"
                variables["formatIn"] = null
                variables["genreIn"] = null
            }
        }

        val gql = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort], ${'$'}season: MediaSeason, ${'$'}seasonYear: Int, ${'$'}status: MediaStatus, ${'$'}statusNot: MediaStatus, ${'$'}format: MediaFormat, ${'$'}formatIn: [MediaFormat], ${'$'}genreIn: [String]) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(type: ANIME, sort: ${'$'}sort, season: ${'$'}season, seasonYear: ${'$'}seasonYear, status: ${'$'}status, status_not: ${'$'}statusNot, format: ${'$'}format, format_in: ${'$'}formatIn, genre_in: ${'$'}genreIn, isAdult: false) {
                        id
                        title { romaji english native }
                        coverImage { large extraLarge }
                        format
                    }
                }
            }
        """.trimIndent()
        return anilistQuery(gql, variables)
            .path("Page")
            .path("media")
            .takeIf { it.isArray }
            ?.mapNotNull { mediaSearchResponse(it) }
            ?.distinctBy { it.url }
            ?: emptyList()
    }

    private suspend fun fallbackAniListMediaPage(data: String, page: Int, perPage: Int): List<SearchResponse> {
        val parts = data.split("|")
        val sort = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: "TRENDING_DESC"
        val filter = parts.getOrNull(1)
        val (season, year) = currentAniListSeason()
        val variables = mutableMapOf<String, Any?>(
            "page" to page,
            "perPage" to perPage,
            "sort" to listOf(sort),
            "season" to null,
            "seasonYear" to null,
            "status" to null,
            "statusNot" to "NOT_YET_RELEASED"
        )

        when (filter) {
            "CURRENT" -> {
                variables["season"] = season
                variables["seasonYear"] = year
                variables["status"] = "RELEASING"
            }
            "UPCOMING" -> {
                variables["status"] = "NOT_YET_RELEASED"
                variables["statusNot"] = null
            }
            "COMPLETED" -> variables["status"] = "FINISHED"
        }

        val gql = """
            query (${'$'}page: Int, ${'$'}perPage: Int, ${'$'}sort: [MediaSort], ${'$'}season: MediaSeason, ${'$'}seasonYear: Int, ${'$'}status: MediaStatus, ${'$'}statusNot: MediaStatus) {
                Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                    media(type: ANIME, sort: ${'$'}sort, season: ${'$'}season, seasonYear: ${'$'}seasonYear, status: ${'$'}status, status_not: ${'$'}statusNot, isAdult: false) {
                        id
                        title { romaji english native }
                        coverImage { large extraLarge }
                        format
                    }
                }
            }
        """.trimIndent()
        return anilistQuery(gql, variables)
            .path("Page")
            .path("media")
            .takeIf { it.isArray }
            ?.mapNotNull { mediaSearchResponse(it) }
            ?.distinctBy { it.url }
            ?: emptyList()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val results = runCatching { anilistMediaPage(request.data, page, 18) }
            .getOrDefault(emptyList())
            .ifEmpty {
                runCatching { fallbackAniListMediaPage(request.data, page, 24) }
                    .getOrDefault(emptyList())
            }
            .ifEmpty {
                if (request.data == "TRENDING_DESC") {
                    emptyList()
                } else {
                    runCatching { fallbackAniListMediaPage("TRENDING_DESC", page, 24) }
                        .getOrDefault(emptyList())
                }
            }
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

    override suspend fun load(url: String): LoadResponse? {
        val anilistId = extractAniListId(url) ?: return null

        return try {
            val gql = """
                query (${'$'}id: Int) {
                    Media(id: ${'$'}id, type: ANIME) {
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
                    }
                }
            """.trimIndent()
            val media = anilistQuery(gql, mapOf("id" to anilistId)).path("Media")
            val title = preferredTitle(media.path("title")) ?: return null
            val poster = textOrNull(media.path("coverImage").get("extraLarge"))
                ?: textOrNull(media.path("coverImage").get("large"))
            val description = cleanDescription(textOrNull(media.get("description")))
            val tags = listOfNotNull(
                textOrNull(media.get("status"))
                    ?.lowercase(Locale.ROOT)
                    ?.replace('_', ' ')
                    ?.replaceFirstChar { it.titlecase(Locale.ROOT) },
                media.path("seasonYear").asInt(0).takeIf { it > 0 }?.toString(),
                media.path("averageScore").asInt(0).takeIf { it > 0 }?.let { "$it% AniList" }
            ) + media.path("genres").takeIf { it.isArray }?.mapNotNull { textOrNull(it) }.orEmpty()
            val enrichedDescription = listOfNotNull(
                description,
                tags.takeIf { it.isNotEmpty() }?.joinToString(prefix = "\n\n", separator = " • ")
            ).joinToString("")
            val type = tvTypeFromFormat(textOrNull(media.get("format")))
            val episodeMap = mutableMapOf<DubStatus, MutableList<Episode>>()
            val rawEpisodes = runCatching { fetchRawEpisodes(anilistId) }.getOrNull()
            val providers = rawEpisodes?.path("providers")
            if (providers != null && providers.isObject) {
                providers.fields().forEach { providerEntry ->
                    val provider = providerEntry.key
                    val episodes = providerEntry.value.path("episodes")
                    listOf("sub" to DubStatus.Subbed, "dub" to DubStatus.Dubbed).forEach { (category, dubStatus) ->
                        val list = episodes.path(category)
                        if (list.isArray) {
                            val bucket = episodeMap.getOrPut(dubStatus) { mutableListOf() }
                            list.forEach { ep ->
                                val rawEpisodeId = textOrNull(ep.get("id")) ?: return@forEach
                                val sourceEpisodeId = if (rawEpisodeId.startsWith("watch/")) {
                                    rawEpisodeId
                                } else {
                                    normalizeEpisodeId(rawEpisodeId)
                                }
                                val number = ep.path("number").asInt(0).takeIf { it > 0 } ?: return@forEach
                                val episodeId = if (sourceEpisodeId.startsWith("watch/")) {
                                    sourceEpisodeId
                                } else {
                                    watchPath(provider, anilistId, category, sourceEpisodeId, number)
                                }
                                val episodeTitle = textOrNull(ep.get("title")) ?: "Episode $number"
                                val data = listOf(provider, anilistId.toString(), category, number.toString(), episodeId)
                                    .joinToString("|")
                                bucket.add(
                                    newEpisode(data) {
                                        name = episodeTitle
                                        episode = number
                                        posterUrl = firstText(ep, "image", "thumbnail", "img")
                                        runTime = runtimeMinutes(ep.path("duration").asInt(0))
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (type == TvType.AnimeMovie && episodeMap.isEmpty()) {
                return newMovieLoadResponse(title, url, type, "kiwi|$anilistId|sub|1|movie-1") {
                    posterUrl = poster
                    plot = enrichedDescription.ifBlank { description }
                }
            }

            if (episodeMap.isEmpty()) {
                val episodeCount = media.path("episodes").asInt(1).coerceIn(1, 500)
                episodeMap[DubStatus.Subbed] = (1..episodeCount).map { episodeNumber ->
                    newEpisode("kiwi|$anilistId|sub|$episodeNumber|animepahe-$episodeNumber") {
                        name = "Episode $episodeNumber"
                        episode = episodeNumber
                    }
                }.toMutableList()
            }

            newAnimeLoadResponse(title, url, type) {
                posterUrl = poster
                plot = enrichedDescription.ifBlank { description }
                episodes = mutableMapOf<DubStatus, List<Episode>>().apply {
                    episodeMap.forEach { (dubStatus, episodeList) ->
                        this[dubStatus] = episodeList.distinctBy { it.data }.sortedBy { it.episode }
                    }
                }
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
