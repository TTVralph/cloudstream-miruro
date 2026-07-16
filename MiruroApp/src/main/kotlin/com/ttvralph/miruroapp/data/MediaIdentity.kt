package com.ttvralph.miruroapp.data

/**
 * Builds the single playback season represented by one AniList media ID.
 *
 * AniList's related-media graph is useful for an anime guide, but those related IDs are not
 * interchangeable playback seasons. Miruro resolves episodes by the exact AniList ID, so the
 * selected entry must remain the identity carried into every episode and source request.
 */
internal fun exactPlaybackSeason(
    id: Int,
    title: String,
    year: Int?,
    synopsis: String?,
    episodeCount: Int?,
    runtimeMinutes: Int?
): AnimeSeason = AnimeSeason(
    id = id,
    seasonNumber = 1,
    title = title,
    year = year,
    episodes = emptyList(),
    synopsis = synopsis,
    episodeCount = episodeCount,
    runtimeMinutes = runtimeMinutes,
    episodesLoaded = false
)
