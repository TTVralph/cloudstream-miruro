package com.miruro

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
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.net.URLEncoder
import java.util.Locale

class MiruroProvider : MainAPI() {
    override var mainUrl = "https://www.miruro.to"
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

    private fun extractKitsuId(url: String): String? {
        return Regex("""/kitsu/([^/]+)""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun errorResult(message: String): List<SearchResponse> {
        return listOf(
            newAnimeSearchResponse(
                "KITSU ERROR: ${message.take(120)}",
                "$mainUrl/error",
                TvType.Anime
            ) {
                posterUrl = null
            }
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val apiUrl =
                "https://kitsu.io/api/edge/anime?filter[text]=${encodeUrl(query)}&page[limit]=20"

            val json = app.get(
                apiUrl,
                headers = mapOf(
                    "Accept" to "application/vnd.api+json",
                    "User-Agent" to "Mozilla/5.0"
                )
            ).text

            val root = mapper.readTree(json)
            val data = root.path("data")

            if (!data.isArray) {
                return errorResult("No data array: ${json.take(120)}")
            }

            val results = data.mapNotNull { item ->
                val id = textOrNull(item.get("id")) ?: return@mapNotNull null
                val attr = item.path("attributes")

                val titles = attr.path("titles")

                val title = textOrNull(titles.get("en"))
                    ?: textOrNull(titles.get("en_jp"))
                    ?: textOrNull(titles.get("ja_jp"))
                    ?: textOrNull(attr.get("canonicalTitle"))
                    ?: return@mapNotNull null

                val poster = textOrNull(
                    attr.path("posterImage").get("large")
                ) ?: textOrNull(
                    attr.path("posterImage").get("medium")
                ) ?: textOrNull(
                    attr.path("posterImage").get("original")
                )

                val subtype = textOrNull(attr.get("subtype"))
                val type = if (subtype == "movie") TvType.AnimeMovie else TvType.Anime

                val dataUrl = "$mainUrl/kitsu/$id/${slugify(title)}"

                newAnimeSearchResponse(title, dataUrl, type) {
                    posterUrl = poster
                }
            }

            if (results.isEmpty()) {
                errorResult("Kitsu returned 0 results for $query")
            } else {
                results
            }
        } catch (e: Exception) {
            errorResult("${e::class.java.simpleName}: ${e.message ?: "unknown"}")
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = extractKitsuId(url) ?: return null

        return try {
            val apiUrl = "https://kitsu.io/api/edge/anime/$id"

            val json = app.get(
                apiUrl,
                headers = mapOf(
                    "Accept" to "application/vnd.api+json",
                    "User-Agent" to "Mozilla/5.0"
                )
            ).text

            val item = mapper.readTree(json).path("data")
            val attr = item.path("attributes")

            val titles = attr.path("titles")

            val title = textOrNull(titles.get("en"))
                ?: textOrNull(titles.get("en_jp"))
                ?: textOrNull(titles.get("ja_jp"))
                ?: textOrNull(attr.get("canonicalTitle"))
                ?: return null

            val poster = textOrNull(
                attr.path("posterImage").get("large")
            ) ?: textOrNull(
                attr.path("posterImage").get("medium")
            ) ?: textOrNull(
                attr.path("posterImage").get("original")
            )

            val description = cleanDescription(textOrNull(attr.get("synopsis")))
            val subtype = textOrNull(attr.get("subtype"))
            val type = if (subtype == "movie") TvType.AnimeMovie else TvType.Anime

            if (type == TvType.AnimeMovie) {
                return newMovieLoadResponse(title, url, type, url) {
                    posterUrl = poster
                    plot = description
                }
            }

            val episodeCount = attr.path("episodeCount").asInt(0)

            val safeEpisodeCount = when {
                episodeCount <= 0 -> 1
                episodeCount > 500 -> 500
                else -> episodeCount
            }

            val episodeList = mutableListOf<Episode>()

            for (episodeNumber in 1..safeEpisodeCount) {
                episodeList.add(
                    newEpisode("$url#episode-$episodeNumber") {
                        name = "Episode $episodeNumber"
                        episode = episodeNumber
                    }
                )
            }

            newAnimeLoadResponse(title, url, type) {
                posterUrl = poster
                plot = description
                episodes[DubStatus.Subbed] = episodeList
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
        return false
    }
}