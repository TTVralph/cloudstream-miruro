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
import com.ttvralph.miruroapp.ui.TopBar

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
        fun path(id: Int, season: Int, episode: Int, audio: AudioType) = "episode/$id/$season/$episode/${audio.name}"
    }
    data object Player : Routes("player/{${Args.ID}}/{${Args.SEASON}}/{${Args.EPISODE}}/{${Args.AUDIO}}") {
        fun path(id: Int, season: Int, episode: Int, audio: AudioType) = "player/$id/$season/$episode/${audio.name}"
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun navLabelFor(route: String?): String = when (route) {
    Routes.Home.route -> "Home"
    Routes.Search.route -> "Search"
    Routes.Favorites.route -> "My List"
    Routes.Movies.route -> "Movies"
    Routes.Series.route -> "Anime"
    Routes.Genres.route -> "New & Popular"
    Routes.Settings.route -> "Settings"
    else -> ""
}

@Composable
private fun MiruroApp(viewModel: MiruroViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val fullScreenRoute = currentRoute == Routes.Player.route || currentRoute == Routes.Details.route
    val horizontalPadding = when (currentRoute) {
        Routes.Home.route -> 0.dp
        else -> if (fullScreenRoute) 0.dp else 58.dp
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MiruroColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiruroColors.Background)
        ) {
            if (!fullScreenRoute) {
                TopBar(
                    current = navLabelFor(currentRoute),
                    onHome = { navController.navigateTopLevel(Routes.Home.route) },
                    onAnime = { navController.navigateTopLevel(Routes.Series.route) },
                    onMovies = { navController.navigateTopLevel(Routes.Movies.route) },
                    onNewPopular = { navController.navigateTopLevel(Routes.Genres.route) },
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
                    .padding(horizontal = horizontalPadding, vertical = if (fullScreenRoute) 0.dp else 8.dp)
            ) {
                composable(Routes.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) },
                        onPlayProgress = { progress -> navController.navigate(Routes.Player.path(progress.animeId, progress.seasonNumber, progress.episodeNumber, progress.audioType)) }
                    )
                }
                composable(Routes.Search.route) {
                    SearchScreen(viewModel) { id -> navController.navigate(Routes.Details.path(id)) }
                }
                composable(Routes.Favorites.route) {
                    FavoritesScreen(viewModel) { id -> navController.navigate(Routes.Details.path(id)) }
                }
                composable(Routes.Movies.route) {
                    MoviesScreen(viewModel) { id -> navController.navigate(Routes.Details.path(id)) }
                }
                composable(Routes.Series.route) {
                    SeriesScreen(viewModel) { id -> navController.navigate(Routes.Details.path(id)) }
                }
                composable(Routes.Genres.route) {
                    GenresScreen(viewModel) { id -> navController.navigate(Routes.Details.path(id)) }
                }
                composable(Routes.Settings.route) { SettingsScreen(viewModel) }
                composable(
                    Routes.Details.route,
                    arguments = listOf(navArgument(Args.ID) { type = NavType.IntType })
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    DetailsScreen(
                        viewModel = viewModel,
                        animeId = id,
                        onBack = { navController.popBackStack() },
                        onOpenEpisode = { season, episode, audio -> navController.navigate(Routes.Episode.path(id, season, episode, audio)) },
                        onPlayEpisode = { season, episode, audio -> navController.navigate(Routes.Player.path(id, season, episode, audio)) }
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
                    val audio = entry.arguments?.getString(Args.AUDIO)?.let { runCatching { AudioType.valueOf(it) }.getOrNull() } ?: AudioType.SUB
                    val episode = findEpisode(viewModel, id, season, episodeNumber, audio)
                    EpisodeDetailsScreen(episode, viewModel) {
                        navController.navigate(Routes.Player.path(id, season, episodeNumber, audio))
                    }
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
                    val audio = entry.arguments?.getString(Args.AUDIO)?.let { runCatching { AudioType.valueOf(it) }.getOrNull() } ?: AudioType.SUB
                    val episode = findEpisode(viewModel, id, season, episodeNumber, audio)
                    PlayerScreen(
                        viewModel = viewModel,
                        episode = episode,
                        onBack = { navController.popBackStack() },
                        onNextEpisode = { findNextEpisode(viewModel, id, season, episodeNumber, audio)?.let { next -> navController.navigate(Routes.Player.path(id, next.seasonNumber, next.episodeNumber, next.audioType)) } }
                    )
                }
            }
        }
    }
}

private fun findEpisode(viewModel: MiruroViewModel, animeId: Int, season: Int, episodeNumber: Int, audio: AudioType): AnimeEpisode? {
    val episodes = viewModel.cachedDetails(animeId)?.seasons?.firstOrNull { it.seasonNumber == season }?.episodes.orEmpty()
    return episodes.firstOrNull { it.episodeNumber == episodeNumber && it.audioType == viewModel.settings.value.preferredAudio }
        ?: episodes.firstOrNull { it.episodeNumber == episodeNumber && it.audioType == audio }
        ?: episodes.firstOrNull { it.episodeNumber == episodeNumber && it.sourceCandidates.isNotEmpty() }
}

private fun findNextEpisode(viewModel: MiruroViewModel, animeId: Int, season: Int, episodeNumber: Int, audio: AudioType) =
    viewModel.cachedDetails(animeId)
        ?.seasons
        ?.flatMap { it.episodes }
        ?.filter { it.sourceCandidates.isNotEmpty() }
        ?.sortedWith(compareBy<AnimeEpisode> { it.seasonNumber }.thenBy { it.episodeNumber }.thenBy { if (it.audioType == audio) 0 else 1 })
        ?.firstOrNull { it.seasonNumber > season || (it.seasonNumber == season && it.episodeNumber > episodeNumber) }
