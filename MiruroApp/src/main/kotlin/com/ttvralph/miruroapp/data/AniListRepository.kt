package com.ttvralph.miruroapp.data

import com.fasterxml.jackson.databind.JsonNode
import android.util.Base64
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

class AniListRepository {
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()
    private val jsonType = "application/json".toMediaType()
    private val dateCache = mutableMapOf<Int, Map<Int, String>>()
    private val pipeHosts = listOf("https://www.miruro.to", "https://www.miruro.tv", "https://www.miruro.bz", "https://www.miruro.ru")
    private val providerPriority = listOf("kiwi", "pewe", "bee", "bonk", "bun", "ally", "nun", "twin", "cog", "moo", "hop", "telli")

    suspend fun homeRows(): List<HomeRow> = listOf(
        "Trending Now" to mapOf("sort" to listOf("TRENDING_DESC")),
        "Currently Airing" to mapOf("status" to "RELEASING", "sort" to listOf("POPULARITY_DESC")),
        "Popular This Season" to mapOf("season" to currentSeason(), "seasonYear" to currentYear(), "sort" to listOf("POPULARITY_DESC")),
        "Top Rated Anime" to mapOf("sort" to listOf("SCORE_DESC")),
        "Anime Movies" to mapOf("format" to "MOVIE", "sort" to listOf("POPULARITY_DESC"))
    ).mapNotNull { (title, vars) -> runCatching { HomeRow(title, mediaPage(vars + mapOf("page" to 1, "perPage" to 20))) }.getOrNull()?.takeIf { it.items.isNotEmpty() } }

    suspend fun search(query: String): List<AnimeItem> = mediaPage(mapOf("search" to query, "page" to 1, "perPage" to 30, "sort" to listOf("SEARCH_MATCH")))

    suspend fun details(id: Int): AnimeDetails = withContext(Dispatchers.IO) {
        val media = anilist(MEDIA_QUERY, mapOf("id" to id)).path("Media")
        val title = preferredTitle(media.path("title")) ?: error("Missing title")
        val root = media.toSeasonEntry() ?: error("Missing media")
        val chain = runCatching { findSeasonChain(root) }.getOrDefault(listOf(root)).ifEmpty { listOf(root) }
        val seasons = chain.mapIndexed { index, entry ->
            val dates = episodeAirDates(entry.id)
            val miruroEpisodes = runCatching { miruroEpisodes(entry.id, index + 1, entry.duration, dates) }.getOrDefault(emptyList())
            val episodes = miruroEpisodes.ifEmpty {
                (1..(entry.episodes ?: 0).coerceAtMost(2000)).map { ep ->
                    AnimeEpisode(index + 1, ep, "Episode $ep", null, entry.duration, dates[ep], AudioType.SUB)
                }
            }
            AnimeSeason(entry.id, index + 1, entry.title, entry.year, episodes)
        }
        AnimeDetails(id, title, text(media, "coverImage", "extraLarge") ?: text(media, "coverImage", "large"), text(media, "bannerImage"), clean(text(media, "description")), text(media, "status")?.pretty(), media.path("startDate").path("year").asInt(0).takeIf { it > 0 } ?: media.path("seasonYear").asInt(0).takeIf { it > 0 }, media.path("averageScore").asInt(0).takeIf { it > 0 }?.let { "$it% AniList" }, media.path("genres").mapNotNull { it.asText(null) }, seasons)
    }


    suspend fun playableStream(playback: EpisodePlayback): StreamSource? = withContext(Dispatchers.IO) {
        val candidates = sourceCandidates(playback)
        candidates.firstNotNullOfOrNull { candidate ->
            runCatching { sources(candidate).firstOrNull() }.getOrNull()
        }
    }

    private data class SourceCandidate(val provider: String, val anilistId: Int, val category: String, val episodeId: String, val episodeNumber: Int)

    private suspend fun miruroEpisodes(anilistId: Int, seasonNumber: Int, fallbackRuntime: Int?, dates: Map<Int, String>): List<AnimeEpisode> {
        val providers = pipeGet("episodes", mapper.createObjectNode().apply { put("anilistId", anilistId) }).path("providers")
        if (!providers.isObject) return emptyList()
        val out = mutableListOf<AnimeEpisode>()
        listOf(AudioType.SUB to "sub", AudioType.DUB to "dub").forEach { (audio, category) ->
            val providerEntry = providers.fields().asSequence().mapNotNull { entry ->
                val episodes = entry.value.path("episodes").let { if (it.isArray) it else it.path(category) }
                if (episodes.isArray) entry.key to episodes else null
            }.filter { (_, episodes) -> episodes.any { it.path("number").asInt(0) > 0 } }
                .sortedWith(compareBy<Pair<String, JsonNode>> { providerRank(it.first) }.thenByDescending { it.second.size() })
                .firstOrNull() ?: return@forEach
            val (provider, episodes) = providerEntry
            episodes.forEach { ep ->
                val number = ep.path("number").asInt(0).takeIf { it > 0 } ?: return@forEach
                val rawId = text(ep, "id") ?: return@forEach
                out += AnimeEpisode(
                    seasonNumber,
                    number,
                    text(ep, "title") ?: "Episode $number",
                    text(ep, "image") ?: text(ep, "thumbnail") ?: text(ep, "img"),
                    runtimeMinutes(ep.path("duration").asInt(0)) ?: fallbackRuntime,
                    episodeReleaseDate(ep) ?: dates[number],
                    audio,
                    EpisodePlayback(provider, anilistId, category, normalizeEpisodeId(rawId), number)
                )
            }
        }
        return out.distinctBy { "${it.audioType}-${it.episodeNumber}" }.sortedWith(compareBy<AnimeEpisode> { it.audioType.ordinal }.thenBy { it.episodeNumber })
    }

    private suspend fun sourceCandidates(playback: EpisodePlayback): List<SourceCandidate> {
        val candidates = mutableListOf(SourceCandidate(playback.provider, playback.anilistId, playback.category, playback.episodeId, playback.episodeNumber))
        val providers = runCatching { pipeGet("episodes", mapper.createObjectNode().apply { put("anilistId", playback.anilistId) }).path("providers") }.getOrNull()
        if (providers?.isObject == true) {
            providers.fields().forEach { entry ->
                val episodes = entry.value.path("episodes").let { if (it.isArray) it else it.path(playback.category) }
                if (episodes.isArray) episodes.forEach { ep ->
                    if (ep.path("number").asInt(0) == playback.episodeNumber) {
                        text(ep, "id")?.let { candidates += SourceCandidate(entry.key, playback.anilistId, playback.category, normalizeEpisodeId(it), playback.episodeNumber) }
                    }
                }
            }
        }
        return candidates.distinct().sortedBy { providerRank(it.provider) }
    }

    private suspend fun sources(candidate: SourceCandidate): List<StreamSource> {
        val id = candidate.episodeId
        val direct = if (id.startsWith("watch/")) runCatching { pipeGet(id) }.getOrNull() else null
        val node = if (direct != null && streamNodes(direct).any { isPlayable(it) }) direct else pipeGet("sources", mapper.createObjectNode().apply {
            put("episodeId", urlSafeBase64(id.toByteArray()))
            put("provider", candidate.provider)
            put("category", candidate.category)
            put("anilistId", candidate.anilistId)
        })
        return streamNodes(node).mapNotNull { stream ->
            if (!isPlayable(stream)) return@mapNotNull null
            StreamSource(streamUrl(stream) ?: return@mapNotNull null, streamQuality(stream), streamType(stream), text(stream, "referer") ?: text(stream, "referrer"))
        }
    }

    private fun streamNodes(node: JsonNode): List<JsonNode> = when {
        node.isObject -> {
            val direct = listOf("streams", "sources").firstNotNullOfOrNull { node.path(it).takeIf { arr -> arr.isArray } }
            direct?.toList() ?: node.fields().asSequence().flatMap { streamNodes(it.value).asSequence() }.toList()
        }
        node.isArray -> node.flatMap { streamNodes(it) }
        else -> emptyList()
    }
    private fun streamUrl(n: JsonNode) = text(n, "url") ?: text(n, "file") ?: text(n, "stream") ?: n.path("source").takeIf { it.isObject }?.let { text(it, "url") ?: text(it, "file") ?: text(it, "stream") }
    private fun streamType(n: JsonNode) = text(n, "type") ?: text(n, "format") ?: n.path("source").takeIf { it.isObject }?.let { text(it, "type") ?: text(it, "format") }
    private fun streamQuality(n: JsonNode) = text(n, "quality") ?: text(n, "label") ?: text(n, "resolution") ?: "Auto"
    private fun isPlayable(n: JsonNode): Boolean { val url = streamUrl(n) ?: return false; val type = streamType(n)?.lowercase(Locale.ROOT); return type in setOf("hls", "m3u8") || url.contains(".m3u8") }
    private fun providerRank(provider: String) = providerPriority.indexOf(provider.lowercase(Locale.ROOT)).takeIf { it >= 0 } ?: providerPriority.size
    private fun runtimeMinutes(value: Int?) = value?.takeIf { it > 0 }?.let { if (it > 300) (it + 59) / 60 else it }
    private fun episodeReleaseDate(ep: JsonNode) = listOf("airDate", "airedAt", "releaseDate", "released", "date").firstNotNullOfOrNull { text(ep, it)?.takeIf { d -> d.matches(Regex("^\\d{4}-\\d{2}-\\d{2}.*")) } }
    private fun normalizeEpisodeId(value: String) = decodeUrlSafeBase64Text(value)?.takeIf { ":" in it } ?: value
    private fun urlSafeBase64(value: ByteArray) = Base64.encodeToString(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun decodeUrlSafeBase64Text(value: String): String? = runCatching { String(Base64.decode(value + "=".repeat((4 - value.length % 4) % 4), Base64.URL_SAFE or Base64.NO_WRAP)) }.getOrNull()
    private fun decodePipeResponse(value: String): JsonNode { val padded = value + "=".repeat((4 - value.length % 4) % 4); return mapper.readTree(GZIPInputStream(ByteArrayInputStream(Base64.decode(padded, Base64.URL_SAFE or Base64.NO_WRAP))).bufferedReader().use { it.readText() }) }
    private fun encodePipeRequest(path: String, query: JsonNode?) = urlSafeBase64(mapper.writeValueAsBytes(mapper.createObjectNode().apply { put("path", path); put("method", "GET"); if (query == null) putNull("query") else set<JsonNode>("query", query); putNull("body"); put("version", "0.1.0") }))
    private fun pipeGet(path: String, query: JsonNode? = null): JsonNode {
        val encoded = encodePipeRequest(path, query)
        var last: Throwable? = null
        pipeHosts.forEach { host ->
            val req = Request.Builder().url("$host/api/secure/pipe?e=${URLEncoder.encode(encoded, "UTF-8")}").header("Accept", "application/json, text/plain, */*").header("Origin", host).header("Referer", "$host/").build()
            val result = runCatching { client.newCall(req).execute().use { decodePipeResponse(it.body?.string().orEmpty().trim()) } }
            result.getOrNull()?.let { return it }
            last = result.exceptionOrNull()
        }
        throw last ?: IllegalStateException("Miruro pipe failed")
    }

    private suspend fun mediaPage(vars: Map<String, Any?>): List<AnimeItem> = withContext(Dispatchers.IO) {
        anilist(PAGE_QUERY, vars).path("Page").path("media").mapNotNull { it.toAnimeItem() }
    }

    private fun JsonNode.toAnimeItem(): AnimeItem? {
        val id = path("id").asInt(0).takeIf { it > 0 } ?: return null
        return AnimeItem(id, preferredTitle(path("title")) ?: return null, text(this, "coverImage", "extraLarge") ?: text(this, "coverImage", "large"), text(this, "bannerImage"), animeType(text(this, "format")), path("seasonYear").asInt(0).takeIf { it > 0 } ?: path("startDate").path("year").asInt(0).takeIf { it > 0 })
    }

    private suspend fun findSeasonChain(root: SeasonEntry, max: Int = 12): List<SeasonEntry> {
        if (root.format !in setOf("TV", "TV_SHORT") || root.isLongRunning()) return listOf(root)
        val out = linkedMapOf(root.id to root); val seen = mutableSetOf<Int>()
        suspend fun walk(entry: SeasonEntry) {
            if (!seen.add(entry.id) || out.size >= max) return
            if (entry.format !in setOf("TV", "TV_SHORT") || !sameFranchise(root.title, entry.title)) return
            out[entry.id] = entry
            (entry.prequels + entry.sequels).forEach { if (out.size < max) fetchSeason(it)?.let { e -> walk(e) } }
        }
        walk(root)
        return out.values.sortedWith(compareBy<SeasonEntry> { it.sortKey }.thenBy { it.id })
    }

    private suspend fun fetchSeason(id: Int) = anilist(SEASON_QUERY, mapOf("id" to id)).path("Media").toSeasonEntry()
    private fun JsonNode.toSeasonEntry(): SeasonEntry? { val id = path("id").asInt(0).takeIf { it > 0 } ?: return null; val rel = path("relations").path("edges"); return SeasonEntry(id, preferredTitle(path("title")) ?: return null, path("episodes").asInt(0).takeIf { it > 0 }, path("duration").asInt(0).takeIf { it > 0 }, text(this,"format"), path("seasonYear").asInt(0).takeIf { it > 0 } ?: path("startDate").path("year").asInt(0).takeIf { it > 0 }, path("startDate").path("month").asInt(12), path("startDate").path("day").asInt(31), rel.filter { text(it,"node","type") == "ANIME" && text(it,"relationType") == "PREQUEL" }.map { it.path("node").path("id").asInt() }, rel.filter { text(it,"node","type") == "ANIME" && text(it,"relationType") == "SEQUEL" }.map { it.path("node").path("id").asInt() }) }

    private suspend fun episodeAirDates(id: Int): Map<Int, String> = dateCache[id] ?: runCatching { anilist(AIRING_QUERY, mapOf("mediaId" to id)).path("Page").path("airingSchedules").associate { it.path("episode").asInt() to epochDate(it.path("airingAt").asLong()) } }.getOrDefault(emptyMap()).also { dateCache[id] = it }
    private fun anilist(query: String, vars: Map<String, Any?>): JsonNode { val body = mapper.writeValueAsString(mapOf("query" to query, "variables" to vars)); val req = Request.Builder().url("https://graphql.anilist.co").post(body.toRequestBody(jsonType)).header("Accept", "application/json").build(); return client.newCall(req).execute().use { mapper.readTree(it.body?.string().orEmpty()).path("data") } }
    private fun preferredTitle(n: JsonNode) = n.path("english").asText(null) ?: n.path("romaji").asText(null) ?: n.path("native").asText(null)
    private fun text(n: JsonNode, vararg path: String): String? = path.fold(n) { a, p -> a.path(p) }.asText(null)?.takeIf { it.isNotBlank() && it != "null" }
    private fun clean(s: String?) = s?.replace(Regex("<[^>]*>"), "")?.trim()?.takeIf { it.isNotBlank() }
    private fun animeType(format: String?) = when(format) { "MOVIE" -> AnimeType.MOVIE; "OVA" -> AnimeType.OVA; "SPECIAL" -> AnimeType.SPECIAL; "TV", "TV_SHORT" -> AnimeType.TV; else -> AnimeType.UNKNOWN }
    private fun String.pretty() = lowercase(Locale.ROOT).replace('_',' ').replaceFirstChar { it.titlecase(Locale.ROOT) }
    private fun epochDate(v: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(v * 1000))
    private fun currentYear() = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    private fun currentSeason() = when(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1) { in 1..3 -> "WINTER"; in 4..6 -> "SPRING"; in 7..9 -> "SUMMER"; else -> "FALL" }
    private fun tokens(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), " ").split(Regex("\\s+")).filter { it.length > 2 && it !in setOf("the","season","part","movie","ova","special","final") }.toSet()
    private fun sameFranchise(a: String, b: String) = tokens(a).intersect(tokens(b)).isNotEmpty()
    private data class SeasonEntry(val id:Int,val title:String,val episodes:Int?,val duration:Int?,val format:String?,val year:Int?,val month:Int,val day:Int,val prequels:List<Int>,val sequels:List<Int>) { val sortKey = (year ?: 9999) * 10000 + month * 100 + day; fun isLongRunning() = (episodes ?: 0) >= 100 || title.lowercase(Locale.ROOT).let { listOf("one piece","detective conan","case closed","pokemon","boruto","naruto").any(it::contains) } }
    companion object {
        private const val PAGE_QUERY = "query(\$page:Int,\$perPage:Int,\$search:String,\$sort:[MediaSort],\$status:MediaStatus,\$season:MediaSeason,\$seasonYear:Int,\$format:MediaFormat){Page(page:\$page,perPage:\$perPage){media(search:\$search,type:ANIME,sort:\$sort,status:\$status,season:\$season,seasonYear:\$seasonYear,format:\$format,isAdult:false){id title{romaji english native} coverImage{large extraLarge} bannerImage format seasonYear startDate{year}}}}"
        private const val MEDIA_QUERY = "query(\$id:Int){Media(id:\$id,type:ANIME){id title{romaji english native} description(asHtml:false) coverImage{large extraLarge} bannerImage format episodes duration averageScore genres status seasonYear startDate{year month day} relations{edges{relationType node{id type format title{romaji english native} episodes duration seasonYear startDate{year month day}}}}}}"
        private const val SEASON_QUERY = MEDIA_QUERY
        private const val AIRING_QUERY = "query(\$mediaId:Int){Page(page:1,perPage:200){airingSchedules(mediaId:\$mediaId,sort:EPISODE){episode airingAt}}}"
    }
}
