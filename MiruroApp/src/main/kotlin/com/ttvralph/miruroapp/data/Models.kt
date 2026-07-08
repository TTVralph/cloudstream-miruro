package com.ttvralph.miruroapp.data

data class AnimeItem(val id: Int, val title: String, val posterUrl: String?, val bannerUrl: String?, val type: AnimeType, val year: Int? = null)
enum class AnimeType { TV, MOVIE, OVA, SPECIAL, UNKNOWN }
data class AnimeDetails(val id: Int, val title: String, val posterUrl: String?, val bannerUrl: String?, val description: String?, val status: String?, val year: Int?, val rating: String?, val genres: List<String>, val seasons: List<AnimeSeason>)
data class AnimeSeason(val id: Int, val seasonNumber: Int, val title: String, val year: Int?, val episodes: List<AnimeEpisode>)
data class AnimeEpisode(val seasonNumber: Int, val episodeNumber: Int, val title: String?, val thumbnailUrl: String?, val runtimeMinutes: Int?, val releaseDate: String?, val audioType: AudioType, val playback: EpisodePlayback? = null)
data class EpisodePlayback(val provider: String, val anilistId: Int, val category: String, val episodeId: String, val episodeNumber: Int)
data class StreamSource(val url: String, val label: String, val type: String?, val referer: String?)
enum class AudioType { SUB, DUB }
data class HomeRow(val title: String, val items: List<AnimeItem>)
