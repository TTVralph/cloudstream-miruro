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
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.data.AudioType
import com.ttvralph.miruroapp.ui.MiruroTheme
import com.ttvralph.miruroapp.ui.TopNavBar

class MainActivity : ComponentActivity() {
    private val viewModel: MiruroViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiruroTheme { MiruroApp(viewModel) } }
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

private fun navLabelFor(route: String?): String = when {
    route == Routes.Home.route -> "Home"
    route == Routes.Search.route -> "Search"
    route == Routes.Favorites.route -> "Favorites"
    route == Routes.Settings.route -> "Settings"
    else -> ""
}

@Composable
private fun MiruroApp(viewModel: MiruroViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showChrome = currentRoute != Routes.Player.route

    Surface(modifier = Modifier.fillMaxSize(), color = MiruroColors.Background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MiruroColors.Background)
                .padding(horizontal = if (showChrome) 40.dp else 0.dp, vertical = if (showChrome) 20.dp else 0.dp)
        ) {
            if (showChrome) {
                TopNavBar(
                    current = navLabelFor(currentRoute),
                    onHome = { navController.navigateTopLevel(Routes.Home.route) },
                    onSearch = { navController.navigateTopLevel(Routes.Search.route) },
                    onFavorites = { navController.navigateTopLevel(Routes.Favorites.route) },
                    onSettings = { navController.navigateTopLevel(Routes.Settings.route) }
                )
            }
            NavHost(
                navController = navController,
                startDestination = Routes.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = if (showChrome) 20.dp else 0.dp)
            ) {
                composable(Routes.Home.route) {
                    HomeScreen(viewModel) { id -> navController.navigate(Routes.Details.path(id)) }
                }
                composable(Routes.Search.route) {
                    SearchScreen(viewModel) { id -> navController.navigate(Routes.Details.path(id)) }
                }
                composable(Routes.Favorites.route) {
                    FavoritesScreen(viewModel) { id -> navController.navigate(Routes.Details.path(id)) }
                }
                composable(Routes.Settings.route) { SettingsScreen() }
                composable(
                    Routes.Details.route,
                    arguments = listOf(navArgument(Args.ID) { type = NavType.IntType })
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    DetailsScreen(
                        viewModel = viewModel,
                        animeId = id,
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
                    EpisodeDetailsScreen(episode) {
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
                    PlayerScreen(viewModel, episode) { navController.popBackStack() }
                }
            }
        }
    }
}

private fun findEpisode(viewModel: MiruroViewModel, animeId: Int, season: Int, episodeNumber: Int, audio: AudioType) =
    viewModel.cachedDetails(animeId)
        ?.seasons
        ?.firstOrNull { it.seasonNumber == season }
        ?.episodes
        ?.firstOrNull { it.episodeNumber == episodeNumber && it.audioType == audio }
