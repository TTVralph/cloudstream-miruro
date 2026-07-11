package com.ttvralph.miruroapp.data

data class LocalProfile(
    val id: String,
    val name: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

data class ProfileState(
    val profiles: List<LocalProfile> = listOf(LocalProfile(DEFAULT_PROFILE_ID, "Main", 0L)),
    val activeId: String = DEFAULT_PROFILE_ID
) {
    val activeProfile: LocalProfile
        get() = profiles.firstOrNull { it.id == activeId } ?: profiles.first()
}

const val DEFAULT_PROFILE_ID = "default"

enum class TitleReaction(val label: String) {
    LIKE("Like"),
    LOVE("Love this"),
    DISLIKE("Not for me")
}

enum class TrackingStatus(val label: String) {
    WATCHING("Watching"),
    PLANNING("Planning"),
    COMPLETED("Completed"),
    ON_HOLD("On Hold"),
    DROPPED("Dropped"),
    REWATCHING("Rewatching")
}

data class UpcomingEpisode(
    val anime: AnimeItem,
    val episodeNumber: Int,
    val airingAtEpochSeconds: Long
)

enum class SkipKind(val label: String) {
    INTRO("Skip Intro"),
    RECAP("Skip Recap"),
    ENDING("Skip Ending")
}

data class SkipInterval(
    val kind: SkipKind,
    val startMs: Long,
    val endMs: Long
)

data class TitleExtras(
    val animeId: Int,
    val malId: Int?,
    val related: List<AnimeItem>,
    val recommendations: List<AnimeItem>,
    val nextAiring: UpcomingEpisode?
)
