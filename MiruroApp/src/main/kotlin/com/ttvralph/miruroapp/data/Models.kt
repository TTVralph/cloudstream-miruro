package com.ttvralph.miruroapp.data

data class AnimeItem(val id: Int, val title: String, val posterUrl: String?, val bannerUrl: String?, val type: AnimeType, val year: Int? = null, val score: Int? = null)
enum class AnimeType { TV, MOVIE, OVA, SPECIAL, UNKNOWN }
data class AnimeDetails(val id: Int, val title: String, val posterUrl: String?, val bannerUrl: String?, val description: String?, val status: String?, val year: Int?, val rating: String?, val genres: List<String>, val seasons: List<AnimeSeason>)
data class AnimeSeason(val id: Int, val seasonNumber: Int, val title: String, val year: Int?, val episodes: List<AnimeEpisode>)
data class AnimeEpisode(val seasonNumber: Int, val episodeNumber: Int, val title: String?, val thumbnailUrl: String?, val runtimeMinutes: Int?, val releaseDate: String?, val audioType: AudioType, val anilistId: Int, val sourceCandidates: List<EpisodeSourceCandidate> = emptyList())
data class EpisodeSourceCandidate(val provider: String, val episodeId: String, val category: String)

data class PlaybackSource(
    val url: String,
    val label: String,
    val type: PlaybackType,
    val headers: Map<String, String> = emptyMap(),
    val subtitleTracks: List<SubtitleTrack> = emptyList()
)

enum class PlaybackType { HLS, DASH, MP4, UNKNOWN }

data class SubtitleTrack(
    val url: String,
    val label: String,
    val language: String? = null
)

enum class AudioType { SUB, DUB }
data class HomeRow(val title: String, val items: List<AnimeItem>)

sealed interface SourceResolution {
    data class Found(val source: PlaybackSource) : SourceResolution
    data class NotFound(val reason: String) : SourceResolution
}
