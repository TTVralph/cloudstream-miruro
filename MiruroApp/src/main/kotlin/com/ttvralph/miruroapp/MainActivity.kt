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
}

private sealed class Routes(val route: String) {
    data object Home : Routes("home")
    data object Search : Routes("search")
    data object Favorites : Routes("favorites")
    data object Settings : Routes("settings")
    data object Details : Routes("details/{${Args.ID}}") {
        fun path(id: Int) = "details/$id"
    }
    data object Episode : Routes("episode/{${Args.ID}}/{${Args.SEASON}}/{${Args.EPISODE}}") {
        fun path(id: Int, season: Int, episode: Int) = "episode/$id/$season/$episode"
    }
    data object Player : Routes("player/{${Args.ID}}/{${Args.SEASON}}/{${Args.EPISODE}}") {
        fun path(id: Int, season: Int, episode: Int) = "player/$id/$season/$episode"
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
                .padding(horizontal = if (showChrome) 36.dp else 0.dp, vertical = if (showChrome) 22.dp else 0.dp)
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
                modifier = Modifier.fillMaxSize().padding(top = if (showChrome) 16.dp else 0.dp)
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
                        onOpenEpisode = { season, episode -> navController.navigate(Routes.Episode.path(id, season, episode)) },
                        onPlayEpisode = { season, episode -> navController.navigate(Routes.Player.path(id, season, episode)) }
                    )
                }
                composable(
                    Routes.Episode.route,
                    arguments = listOf(
                        navArgument(Args.ID) { type = NavType.IntType },
                        navArgument(Args.SEASON) { type = NavType.IntType },
                        navArgument(Args.EPISODE) { type = NavType.IntType }
                    )
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    val season = entry.arguments?.getInt(Args.SEASON) ?: return@composable
                    val episodeNumber = entry.arguments?.getInt(Args.EPISODE) ?: return@composable
                    val episode = findEpisode(viewModel, id, season, episodeNumber)
                    EpisodeDetailsScreen(episode) {
                        navController.navigate(Routes.Player.path(id, season, episodeNumber))
                    }
                }
                composable(
                    Routes.Player.route,
                    arguments = listOf(
                        navArgument(Args.ID) { type = NavType.IntType },
                        navArgument(Args.SEASON) { type = NavType.IntType },
                        navArgument(Args.EPISODE) { type = NavType.IntType }
                    )
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    val season = entry.arguments?.getInt(Args.SEASON) ?: return@composable
                    val episodeNumber = entry.arguments?.getInt(Args.EPISODE) ?: return@composable
                    val episode = findEpisode(viewModel, id, season, episodeNumber)
                    PlayerScreen(viewModel, episode) { navController.popBackStack() }
                }
            }
        }
    }
}

private fun findEpisode(viewModel: MiruroViewModel, animeId: Int, season: Int, episodeNumber: Int) =
    viewModel.cachedDetails(animeId)
        ?.seasons
        ?.firstOrNull { it.seasonNumber == season }
        ?.episodes
        ?.firstOrNull { it.episodeNumber == episodeNumber }
