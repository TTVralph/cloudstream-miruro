package com.ttvralph.miruroapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ttvralph.miruroapp.data.AnimeEpisode
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.MiruroTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MiruroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            MiruroTheme(settings.themeMode) { MiruroApp(viewModel) }
        }
    }
}

private object Args {
    const val ID = "id"
    const val SEASON = "season"
    const val EPISODE = "episode"
    const val AUDIO = "audio"
}

private sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Search : Routes("search")
    data object Favorites : Routes("favorites")
    data object Movies : Routes("movies")
    data object Series : Routes("series")
    data object Genres : Routes("genres")
    data object Settings : Routes("settings")
    data object Details : Routes("details/{${Args.ID}}") {
        fun path(id: Int) = "details/$id"
    }
    data object Episode : Routes("episode/{${Args.ID}}/{${Args.SEASON}}/{${Args.EPISODE}}/{${Args.AUDIO}}") {
        fun path(id: Int, season: Int, episode: Int, audio: AudioType) =
            "episode/$id/$season/$episode/${audio.name}"
    }
    data object Player : Routes("player/{${Args.ID}}/{${Args.SEASON}}/{${Args.EPISODE}}/{${Args.AUDIO}}") {
        fun path(id: Int, season: Int, episode: Int, audio: AudioType) =
            "player/$id/$season/$episode/${audio.name}"
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavHostController.backOrHome() {
    if (!popBackStack()) {
        navigateTopLevel(Routes.Home.route)
    }
}

private fun navLabelFor(route: String?): String = when (route) {
    Routes.Home.route -> "Home"
    Routes.Search.route -> "Search"
    Routes.Favorites.route -> "My List"
    Routes.Movies.route -> "Movies"
    Routes.Series.route -> "Anime"
    Routes.Genres.route -> "Discover"
    Routes.Settings.route -> "Settings"
    else -> ""
}

@Composable
private fun MiruroApp(viewModel: MiruroViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val fullScreenRoute = currentRoute == Routes.Player.route ||
        currentRoute == Routes.Details.route ||
        currentRoute == Routes.Episode.route
    val homeRoute = currentRoute == Routes.Home.route
    val edgeToEdgeRoute = fullScreenRoute || homeRoute
    val showTopBar = !fullScreenRoute && !homeRoute
    val horizontalPadding = if (edgeToEdgeRoute) 0.dp else 58.dp
    val verticalPadding = if (edgeToEdgeRoute) 0.dp else 8.dp

    Surface(modifier = Modifier.fillMaxSize(), color = MiruroColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiruroColors.Background)
        ) {
            if (showTopBar) {
                ReliableTopBar(
                    current = navLabelFor(currentRoute),
                    onHome = { navController.navigateTopLevel(Routes.Home.route) },
                    onAnime = { navController.navigateTopLevel(Routes.Series.route) },
                    onMovies = { navController.navigateTopLevel(Routes.Movies.route) },
                    onDiscover = { navController.navigateTopLevel(Routes.Genres.route) },
                    onMyList = { navController.navigateTopLevel(Routes.Favorites.route) },
                    onSearch = { navController.navigateTopLevel(Routes.Search.route) },
                    onSettings = { navController.navigateTopLevel(Routes.Settings.route) }
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            ) {
                composable(Routes.Home.route) {
                    ReliableHomeScreen(
                        viewModel = viewModel,
                        onHome = { navController.navigateTopLevel(Routes.Home.route) },
                        onAnime = { navController.navigateTopLevel(Routes.Series.route) },
                        onMovies = { navController.navigateTopLevel(Routes.Movies.route) },
                        onDiscover = { navController.navigateTopLevel(Routes.Genres.route) },
                        onMyList = { navController.navigateTopLevel(Routes.Favorites.route) },
                        onSearch = { navController.navigateTopLevel(Routes.Search.route) },
                        onSettings = { navController.navigateTopLevel(Routes.Settings.route) },
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) },
                        onPlayProgress = { progress ->
                            navController.navigate(
                                Routes.Player.path(
                                    progress.animeId,
                                    progress.seasonNumber,
                                    progress.episodeNumber,
                                    progress.audioType
                                )
                            )
                        }
                    )
                }
                composable(Routes.Search.route) {
                    AuditSearchScreen(viewModel) { id ->
                        navController.navigate(Routes.Details.path(id))
                    }
                }
                composable(Routes.Favorites.route) {
                    FavoritesScreen(viewModel) { id ->
                        navController.navigate(Routes.Details.path(id))
                    }
                }
                composable(Routes.Movies.route) {
                    ReliableBrowseScreen(
                        title = "Movies",
                        format = "MOVIE",
                        viewModel = viewModel,
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) }
                    )
                }
                composable(Routes.Series.route) {
                    ReliableBrowseScreen(
                        title = "Anime",
                        format = "TV",
                        viewModel = viewModel,
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) }
                    )
                }
                composable(Routes.Genres.route) {
                    ReliableDiscoverScreen(viewModel) { id ->
                        navController.navigate(Routes.Details.path(id))
                    }
                }
                composable(Routes.Settings.route) {
                    AuditSettingsScreen(viewModel)
                }
                composable(
                    Routes.Details.route,
                    arguments = listOf(navArgument(Args.ID) { type = NavType.IntType })
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    AuditDetailsScreen(
                        viewModel = viewModel,
                        animeId = id,
                        onBack = { navController.backOrHome() },
                        onOpenEpisode = { season, episode, audio ->
                            navController.navigate(Routes.Episode.path(id, season, episode, audio))
                        },
                        onPlayEpisode = { season, episode, audio ->
                            navController.navigate(Routes.Player.path(id, season, episode, audio))
                        }
                    )
                }
                composable(
                    Routes.Episode.route,
                    arguments = listOf(
                        navArgument(Args.ID) { type = NavType.IntType },
                        navArgument(Args.SEASON) { type = NavType.IntType },
                        navArgument(Args.EPISODE) { type = NavType.IntType },
                        navArgument(Args.AUDIO) { type = NavType.StringType }
                    )
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    val season = entry.arguments?.getInt(Args.SEASON) ?: return@composable
                    val episodeNumber = entry.arguments?.getInt(Args.EPISODE) ?: return@composable
                    val audio = entry.arguments?.getString(Args.AUDIO)
                        ?.let { runCatching { AudioType.valueOf(it) }.getOrNull() }
                        ?: AudioType.SUB
                    val episode = findEpisode(viewModel, id, season, episodeNumber, audio)
                    AutomaticEpisodeDetailsScreen(
                        episode = episode,
                        viewModel = viewModel,
                        onBack = { navController.backOrHome() },
                        onPlay = {
                            navController.navigate(Routes.Player.path(id, season, episodeNumber, audio))
                        }
                    )
                }
                composable(
                    Routes.Player.route,
                    arguments = listOf(
                        navArgument(Args.ID) { type = NavType.IntType },
                        navArgument(Args.SEASON) { type = NavType.IntType },
                        navArgument(Args.EPISODE) { type = NavType.IntType },
                        navArgument(Args.AUDIO) { type = NavType.StringType }
                    )
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    val season = entry.arguments?.getInt(Args.SEASON) ?: return@composable
                    val episodeNumber = entry.arguments?.getInt(Args.EPISODE) ?: return@composable
                    val audio = entry.arguments?.getString(Args.AUDIO)
                        ?.let { runCatching { AudioType.valueOf(it) }.getOrNull() }
                        ?: AudioType.SUB
                    val episode = findEpisode(viewModel, id, season, episodeNumber, audio)
                    val nextEpisode = findNextEpisode(viewModel, id, season, episodeNumber, audio)
                    GuardedTvPlayerScreen(
                        viewModel = viewModel,
                        episode = episode,
                        nextEpisode = nextEpisode,
                        onBack = { navController.backOrHome() },
                        onPlayNext = { next ->
                            navController.popBackStack()
                            navController.navigate(
                                Routes.Player.path(
                                    id,
                                    next.seasonNumber,
                                    next.episodeNumber,
                                    next.audioType
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

private fun findEpisode(
    viewModel: MiruroViewModel,
    animeId: Int,
    season: Int,
    episodeNumber: Int,
    audio: AudioType
): AnimeEpisode? {
    val episodes = viewModel.cachedDetails(animeId)
        ?.seasons
        ?.firstOrNull { it.seasonNumber == season }
        ?.episodes
        .orEmpty()
    return episodes.firstOrNull { it.episodeNumber == episodeNumber && it.audioType == audio }
        ?: episodes.firstOrNull {
            it.episodeNumber == episodeNumber &&
                it.audioType == viewModel.settings.value.preferredAudio
        }
        ?: episodes.firstOrNull {
            it.episodeNumber == episodeNumber && it.sourceCandidates.isNotEmpty()
        }
}

private fun findNextEpisode(
    viewModel: MiruroViewModel,
    animeId: Int,
    season: Int,
    episodeNumber: Int,
    audio: AudioType
): AnimeEpisode? {
    val details = viewModel.cachedDetails(animeId) ?: return null
    return details.seasons
        .flatMap { animeSeason ->
            animeSeason.episodes
                .filter { it.sourceCandidates.isNotEmpty() }
                .groupBy { it.episodeNumber }
                .mapNotNull { (_, versions) ->
                    versions.firstOrNull { it.audioType == audio }
                        ?: versions.firstOrNull {
                            it.audioType == viewModel.settings.value.preferredAudio
                        }
                        ?: versions.firstOrNull()
                }
        }
        .sortedWith(
            compareBy<AnimeEpisode> { it.seasonNumber }
                .thenBy { it.episodeNumber }
        )
        .firstOrNull {
            it.seasonNumber > season ||
                (it.seasonNumber == season && it.episodeNumber > episodeNumber)
        }
}
