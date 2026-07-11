package com.ttvralph.miruroapp.data

enum class DiscoveryMode(val label: String, val description: String) {
    SURPRISE_ME("Surprise Me", "A broad pick from popular and trending anime."),
    CONTINUE_SOMETHING("Continue Something", "Resume one of your unfinished episodes."),
    PICK_A_MOVIE("Pick a Movie", "Choose a highly rated anime movie."),
    ONE_EPISODE_WATCH("One-Episode Watch", "Choose a movie, OVA, or special that can be finished quickly."),
    START_SOMETHING_NEW("Start Something New", "Choose a title you have not started or saved yet.")
}

enum class DiscoverySort(val aniListValue: String, val label: String) {
    BEST_MATCH("SEARCH_MATCH", "Best match"),
    POPULARITY("POPULARITY_DESC", "Popularity"),
    SCORE("SCORE_DESC", "Highest score"),
    NEWEST("START_DATE_DESC", "Newest"),
    SHORTEST("DURATION", "Shortest")
}

data class DiscoverySearchFilters(
    val query: String = "",
    val formats: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val season: String? = null,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val includeGenres: Set<String> = emptySet(),
    val excludeGenres: Set<String> = emptySet(),
    val minimumScore: Int? = null,
    val maximumEpisodes: Int? = null,
    val maximumDurationMinutes: Int? = null,
    val source: String? = null,
    val country: String? = null,
    val studioId: Int? = null,
    val dubbedOnly: Boolean = false,
    val myListOnly: Boolean = false,
    val sort: DiscoverySort = DiscoverySort.BEST_MATCH,
    val page: Int = 1
) {
    val hasCriteria: Boolean
        get() = query.isNotBlank() || formats.isNotEmpty() || statuses.isNotEmpty() ||
            season != null || yearFrom != null || yearTo != null || includeGenres.isNotEmpty() ||
            excludeGenres.isNotEmpty() || minimumScore != null || maximumEpisodes != null ||
            maximumDurationMinutes != null || source != null || country != null || studioId != null ||
            dubbedOnly || myListOnly
}

data class StudioOption(val id: Int, val name: String)

data class DiscoveryPick(
    val mode: DiscoveryMode,
    val anime: AnimeItem?,
    val reason: String,
    val resumeProgress: WatchProgress? = null
)

data class DiscoveryRelation(
    val relationType: String,
    val anime: AnimeItem,
    val format: String?,
    val episodes: Int?,
    val startDate: Int?
)

data class DiscoveryPerson(
    val id: Int,
    val name: String,
    val nativeName: String?,
    val imageUrl: String?,
    val role: String,
    val voiceActor: String? = null,
    val voiceActorImageUrl: String? = null
)

data class DiscoveryFranchiseEntry(
    val anime: AnimeItem,
    val format: String?,
    val episodes: Int?,
    val startDate: Int?,
    val relationship: String
)

data class DiscoveryTitleInfo(
    val anime: AnimeItem,
    val malId: Int?,
    val description: String?,
    val format: String?,
    val status: String?,
    val episodes: Int?,
    val durationMinutes: Int?,
    val source: String?,
    val country: String?,
    val season: String?,
    val year: Int?,
    val genres: List<String>,
    val synonyms: List<String>,
    val studios: List<String>,
    val relations: List<DiscoveryRelation>,
    val releaseOrder: List<DiscoveryFranchiseEntry>,
    val storyOrder: List<DiscoveryFranchiseEntry>,
    val characters: List<DiscoveryPerson>,
    val staff: List<DiscoveryPerson>,
    val openingThemes: List<String>,
    val endingThemes: List<String>
)
