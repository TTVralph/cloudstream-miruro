package com.miruro

import android.util.Base64
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
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

    override val hasMainPage = false
    override val hasQuickSearch = false
    override val hasDownloadSupport = false

    private val mapper = jacksonObjectMapper()
    private val anilistUrl = "https://graphql.anilist.co"
    private val pipeUrl = "$mainUrl/api/secure/pipe"
    private val requestHeaders = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Content-Type" to "application/json",
        "Origin" to mainUrl,
        "Referer" to "$mainUrl/",
        "User-Agent" to "Mozilla/5.0"
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
        return urlSafeBase64(mapper.writeValueAsBytes(payload))
    }

    private fun decodePipeResponse(value: String): JsonNode {
        val padded = value + "=".repeat((4 - value.length % 4) % 4)
        val compressed = Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP)
        val decoded = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader().use { it.readText() }
        return mapper.readTree(decoded)
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

    private suspend fun fetchSources(
        episodeId: String,
        provider: String,
        anilistId: Int,
        category: String
    ): JsonNode {
        val encodedEpisodeId = urlSafeBase64(episodeId.toByteArray())
        val payload = mapper.createObjectNode().apply {
            put("path", "sources")
            put("method", "GET")
            set<JsonNode>("query", mapper.createObjectNode().apply {
                put("episodeId", encodedEpisodeId)
                put("provider", provider)
                put("category", category)
                put("anilistId", anilistId)
            })
            putNull("body")
            put("version", "0.1.0")
        }
        val encoded = encodePipeRequest(payload)
        val response = app.get("$pipeUrl?e=${encodeUrl(encoded)}", headers = requestHeaders).text.trim()
        return decodePipeResponse(response)
    }

    private fun preferredTitle(titleNode: JsonNode): String? {
        return textOrNull(titleNode.get("english"))
            ?: textOrNull(titleNode.get("romaji"))
            ?: textOrNull(titleNode.get("native"))
    }

    private fun tvTypeFromFormat(format: String?): TvType {
        return if (format == "MOVIE") TvType.AnimeMovie else TvType.Anime
    }

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

            val results = media.mapNotNull { item ->
                val id = item.path("id").asInt(0).takeIf { it > 0 } ?: return@mapNotNull null
                val title = preferredTitle(item.path("title")) ?: return@mapNotNull null
                val poster = textOrNull(item.path("coverImage").get("extraLarge"))
                    ?: textOrNull(item.path("coverImage").get("large"))
                val type = tvTypeFromFormat(textOrNull(item.get("format")))
                val dataUrl = "$mainUrl/anilist/$id/${slugify(title)}"

                newAnimeSearchResponse(title, dataUrl, type) {
                    posterUrl = poster
                }
            }

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
                    }
                }
            """.trimIndent()
            val media = anilistQuery(gql, mapOf("id" to anilistId)).path("Media")
            val title = preferredTitle(media.path("title")) ?: return null
            val poster = textOrNull(media.path("coverImage").get("extraLarge"))
                ?: textOrNull(media.path("coverImage").get("large"))
            val description = cleanDescription(textOrNull(media.get("description")))
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
                                val episodeId = textOrNull(ep.get("id")) ?: return@forEach
                                val number = ep.path("number").asInt(0).takeIf { it > 0 } ?: return@forEach
                                val episodeTitle = textOrNull(ep.get("title")) ?: "Episode $number"
                                val data = listOf(provider, anilistId.toString(), category, episodeId)
                                    .joinToString("|")
                                bucket.add(
                                    newEpisode(data) {
                                        name = episodeTitle
                                        episode = number
                                        posterUrl = textOrNull(ep.get("image"))
                                        runTime = ep.path("duration").asInt(0).takeIf { it > 0 }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (type == TvType.AnimeMovie && episodeMap.isEmpty()) {
                return newMovieLoadResponse(title, url, type, "kiwi|$anilistId|sub|movie-1") {
                    posterUrl = poster
                    plot = description
                }
            }

            if (episodeMap.isEmpty()) {
                val episodeCount = media.path("episodes").asInt(1).coerceIn(1, 500)
                episodeMap[DubStatus.Subbed] = (1..episodeCount).map { episodeNumber ->
                    newEpisode("kiwi|$anilistId|sub|animepahe-$episodeNumber") {
                        name = "Episode $episodeNumber"
                        episode = episodeNumber
                    }
                }.toMutableList()
            }

            newAnimeLoadResponse(title, url, type) {
                posterUrl = poster
                plot = description
                episodes = episodeMap.mapValues { (_, episodeList) ->
                    episodeList.distinctBy { it.data }.sortedBy { it.episode }
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
            val parts = data.split("|", limit = 4)
            if (parts.size != 4) return false
            val provider = parts[0]
            val anilistId = parts[1].toIntOrNull() ?: return false
            val category = parts[2]
            val episodeId = parts[3]
            val sources = fetchSources(episodeId, provider, anilistId, category)

            sources.path("subtitles").takeIf { it.isArray }?.forEach { subtitle ->
                val url = textOrNull(subtitle.get("file")) ?: textOrNull(subtitle.get("url")) ?: return@forEach
                val label = textOrNull(subtitle.get("label")) ?: textOrNull(subtitle.get("lang")) ?: "Subtitle"
                subtitleCallback(newSubtitleFile(label, url))
            }

            var found = false
            sources.path("streams").takeIf { it.isArray }?.forEach { stream ->
                val url = textOrNull(stream.get("url")) ?: return@forEach
                val qualityLabel = textOrNull(stream.get("quality")) ?: "Auto"
                val streamType = textOrNull(stream.get("type"))?.lowercase(Locale.ROOT)
                callback(
                    ExtractorLink(
                        source = name,
                        name = "$name ${provider.uppercase(Locale.ROOT)} $qualityLabel",
                        url = url,
                        referer = mainUrl,
                        quality = getQualityFromName(qualityLabel).takeIf { it != Qualities.Unknown.value }
                            ?: Qualities.Unknown.value,
                        isM3u8 = streamType != "dash",
                        headers = mapOf("Referer" to "$mainUrl/", "Origin" to mainUrl)
                    )
                )
                found = true
            }

            found
        } catch (_: Exception) {
            false
        }
    }
}
