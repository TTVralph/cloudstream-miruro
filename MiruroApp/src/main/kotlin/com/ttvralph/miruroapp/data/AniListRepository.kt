package com.ttvralph.miruroapp.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class AniListRepository {
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()
    private val jsonType = "application/json".toMediaType()
    private val dateCache = mutableMapOf<Int, Map<Int, String>>()

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
            val sub = (1..(entry.episodes ?: 0).coerceAtMost(2000)).map { ep -> AnimeEpisode(index + 1, ep, "Episode $ep", null, entry.duration, dates[ep], AudioType.SUB) }
            AnimeSeason(entry.id, index + 1, entry.title, entry.year, sub)
        }
        AnimeDetails(id, title, text(media, "coverImage", "extraLarge") ?: text(media, "coverImage", "large"), text(media, "bannerImage"), clean(text(media, "description")), text(media, "status")?.pretty(), media.path("startDate").path("year").asInt(0).takeIf { it > 0 } ?: media.path("seasonYear").asInt(0).takeIf { it > 0 }, media.path("averageScore").asInt(0).takeIf { it > 0 }?.let { "$it% AniList" }, media.path("genres").mapNotNull { it.asText(null) }, seasons)
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
