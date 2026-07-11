package com.ttvralph.miruroapp

import com.ttvralph.miruroapp.data.AnimeDetails
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.data.WatchProgress
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal data class DailyEpisodeTarget(
    val seasonNumber: Int,
    val episode: AnimeEpisode
)

internal data class DailyTitleProgress(
    val total: Int,
    val watched: Int,
    val unwatched: Int,
    val recentUnwatched: Int
) {
    val percent: Float = if (total > 0) watched.toFloat() / total.toFloat() else 0f
    val allWatched: Boolean = total > 0 && watched >= total

    val badge: String?
        get() = when {
            recentUnwatched > 0 -> if (recentUnwatched == 1) "NEW" else "NEW $recentUnwatched"
            unwatched > 0 -> "$unwatched UNWATCHED"
            allWatched -> "✓ COMPLETE"
            else -> null
        }
}

internal fun dailyUniqueEpisodes(
    details: AnimeDetails,
    preferredAudio: AudioType
): List<DailyEpisodeTarget> = details.seasons.flatMap { season ->
    season.episodes
        .groupBy { it.episodeNumber }
        .mapNotNull { (_, versions) ->
            val playable = versions.filter { it.sourceCandidates.isNotEmpty() }
            val selected = playable.firstOrNull { it.audioType == preferredAudio }
                ?: playable.firstOrNull()
                ?: versions.firstOrNull { it.audioType == preferredAudio }
                ?: versions.firstOrNull()
            selected?.let { DailyEpisodeTarget(season.seasonNumber, it) }
        }
}.sortedWith(compareBy<DailyEpisodeTarget> { it.seasonNumber }.thenBy { it.episode.episodeNumber })

internal fun dailyProgressKey(
    animeId: Int,
    seasonNumber: Int,
    episodeNumber: Int
): Triple<Int, Int, Int> = Triple(animeId, seasonNumber, episodeNumber)

internal fun dailyWatchedKeys(progress: List<WatchProgress>): Set<Triple<Int, Int, Int>> =
    progress.filter { it.watched }
        .map { dailyProgressKey(it.animeId, it.seasonNumber, it.episodeNumber) }
        .toSet()

internal fun dailyLatestProgress(
    progress: List<WatchProgress>,
    target: DailyEpisodeTarget
): WatchProgress? = progress
    .filter {
        it.animeId == target.episode.anilistId &&
            it.seasonNumber == target.seasonNumber &&
            it.episodeNumber == target.episode.episodeNumber
    }
    .maxByOrNull { it.updatedAtMs }

internal fun dailyTitleProgress(
    details: AnimeDetails,
    progress: List<WatchProgress>,
    preferredAudio: AudioType
): DailyTitleProgress {
    val targets = dailyUniqueEpisodes(details, preferredAudio)
    val watched = dailyWatchedKeys(progress)
    val watchedCount = targets.count { target ->
        dailyProgressKey(
            target.episode.anilistId,
            target.seasonNumber,
            target.episode.episodeNumber
        ) in watched
    }
    val recentUnwatched = targets.count { target ->
        val key = dailyProgressKey(
            target.episode.anilistId,
            target.seasonNumber,
            target.episode.episodeNumber
        )
        key !in watched && dailyIsRecentEpisode(target.episode)
    }
    return DailyTitleProgress(
        total = targets.size,
        watched = watchedCount,
        unwatched = (targets.size - watchedCount).coerceAtLeast(0),
        recentUnwatched = recentUnwatched
    )
}

internal fun dailySpoilerHiddenKeys(
    details: AnimeDetails,
    progress: List<WatchProgress>,
    preferredAudio: AudioType,
    noSpoilerMode: Boolean
): Set<Triple<Int, Int, Int>> {
    if (!noSpoilerMode) return emptySet()
    val targets = dailyUniqueEpisodes(details, preferredAudio)
    val watched = dailyWatchedKeys(progress)
    val firstUnwatchedIndex = targets.indexOfFirst { target ->
        dailyProgressKey(
            target.episode.anilistId,
            target.seasonNumber,
            target.episode.episodeNumber
        ) !in watched
    }
    if (firstUnwatchedIndex < 0) return emptySet()
    return targets.drop(firstUnwatchedIndex + 1).map { target ->
        dailyProgressKey(
            target.episode.anilistId,
            target.seasonNumber,
            target.episode.episodeNumber
        )
    }.toSet()
}

internal fun dailyIsRecentEpisode(episode: AnimeEpisode, days: Int = 8): Boolean {
    val releaseDate = episode.releaseDate?.take(10)?.takeIf { it.length == 10 } ?: return false
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
    val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.time
    val released = runCatching { formatter.parse(releaseDate) }.getOrNull() ?: return false
    return !released.before(cutoff) && !released.after(Calendar.getInstance().time)
}
