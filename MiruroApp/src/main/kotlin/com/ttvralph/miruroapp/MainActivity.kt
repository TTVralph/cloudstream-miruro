package com.ttvralph.miruroapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import com.ttvralph.miruroapp.data.WatchProgress
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.MiruroTheme
import com.ttvralph.miruroapp.ui.StateMessage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel: MiruroViewModel by viewModels()
    private val featureViewModel: NetflixFeatureViewModel by viewModels()
    private val discoveryViewModel: DiscoveryFeatureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by viewModel.settings.collectAsState()
            val profileState by featureViewModel.profileState.collectAsState()
            MiruroTheme(settings.themeMode, profileState.activeProfile.themeColorId) {
                MiruroApp(viewModel, featureViewModel, discoveryViewModel)
            }
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
    data object Profiles : Routes("profiles")
    data object Details : Routes("details/{${Args.ID}}") {
        fun path(id: Int) = "details/$id"
    }
    data object Extras : Routes("extras/{${Args.ID}}") {
        fun path(id: Int) = "extras/$id"
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
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = false }
        launchSingleTop = true
        restoreState = false
    }
}

private fun NavHostController.backOrHome() {
    if (!popBackStack()) navigateTopLevel(Routes.Home.route)
}

private fun navLabelFor(route: String?): String = when (route) {
    Routes.Home.route -> "Home"
    Routes.Search.route -> "Search"
    Routes.Favorites.route -> "My AniStream"
    Routes.Movies.route -> "Movies"
    Routes.Series.route -> "Anime"
    Routes.Genres.route -> "Discover"
    Routes.Settings.route -> "Settings"
    Routes.Profiles.route -> "Profiles"
    else -> ""
}

@Composable
private fun MiruroApp(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    discovery: DiscoveryFeatureViewModel
) {
    val profileState by features.profileState.collectAsState()
    var profileChosenThisSession by remember { mutableStateOf(false) }
    if (profileState.profiles.size > 1 && !profileChosenThisSession) {
        Surface(modifier = Modifier.fillMaxSize(), color = MiruroColors.Background) {
            ProfilePickerScreen(
                state = profileState,
                onSelect = { profile ->
                    features.switchProfile(profile)
                    profileChosenThisSession = true
                },
                onCreate = { name, avatarId, themeColorId ->
                    features.createProfile(name, avatarId, themeColorId)
                }
            )
        }
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val fullScreenRoute = currentRoute == Routes.Player.route ||
        currentRoute == Routes.Details.route ||
        currentRoute == Routes.Extras.route ||
        currentRoute == Routes.Episode.route
    val homeRoute = currentRoute == Routes.Home.route
    val currentLabel = navLabelFor(currentRoute)
    val topLevelRoute = currentLabel.isNotEmpty()
    val horizontalPadding = if (fullScreenRoute || homeRoute) 0.dp else 58.dp
    val topPadding = if (topLevelRoute && !homeRoute) ReliableNavHeight + 8.dp else 0.dp
    val bottomPadding = if (topLevelRoute && !homeRoute) 8.dp else 0.dp

    fun playProgress(progress: WatchProgress) {
        navController.navigate(
            Routes.Player.path(
                progress.animeId,
                progress.seasonNumber,
                progress.episodeNumber,
                progress.audioType
            )
        )
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MiruroColors.Background) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Routes.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = horizontalPadding,
                        top = topPadding,
                        end = horizontalPadding,
                        bottom = bottomPadding
                    ),
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable(Routes.Home.route) {
                    ReliableHomeScreen(
                        viewModel = viewModel,
                        features = features,
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) },
                        onPlayProgress = ::playProgress
                    )
                }
                composable(Routes.Search.route) {
                    AdvancedSearchScreen(
                        discovery = discovery,
                        library = viewModel,
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) }
                    )
                }
                composable(Routes.Favorites.route) {
                    MyAniStreamScreen(
                        viewModel = viewModel,
                        features = features,
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) },
                        onPlayProgress = ::playProgress
                    )
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
                    DiscoveryHubScreen(
                        viewModel = viewModel,
                        features = features,
                        discovery = discovery,
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) },
                        onPlayProgress = ::playProgress,
                        onOpenSearch = { navController.navigateTopLevel(Routes.Search.route) }
                    )
                }
                composable(Routes.Settings.route) {
                    EnhancedSettingsScreen(viewModel, features)
                }
                composable(Routes.Profiles.route) {
                    MyAniStreamScreen(
                        viewModel = viewModel,
                        features = features,
                        onOpenDetails = { id -> navController.navigate(Routes.Details.path(id)) },
                        onPlayProgress = ::playProgress,
                        openProfiles = true
                    )
                }
                composable(
                    Routes.Details.route,
                    arguments = listOf(navArgument(Args.ID) { type = NavType.IntType })
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    EnhancedDetailsScreen(
                        viewModel = viewModel,
                        features = features,
                        animeId = id,
                        onBack = { navController.backOrHome() },
                        onOpenEpisode = { season, episode, audio ->
                            navController.navigate(Routes.Episode.path(id, season, episode, audio))
                        },
                        onPlayEpisode = { season, episode, audio ->
                            navController.navigate(Routes.Player.path(id, season, episode, audio))
                        },
                        onMoreLikeThis = { navController.navigate(Routes.Extras.path(id)) }
                    )
                }
                composable(
                    Routes.Extras.route,
                    arguments = listOf(navArgument(Args.ID) { type = NavType.IntType })
                ) { entry ->
                    val id = entry.arguments?.getInt(Args.ID) ?: return@composable
                    DiscoveryTitleScreen(
                        discovery = discovery,
                        library = viewModel,
                        animeId = id,
                        onBack = { navController.backOrHome() },
                        onOpenDetails = { relatedId ->
                            navController.navigate(Routes.Details.path(relatedId))
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
                        rootAnimeId = id,
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
                    ResolvedPlayerRoute(
                        viewModel = viewModel,
                        features = features,
                        animeId = id,
                        season = season,
                        episodeNumber = episodeNumber,
                        audio = audio,
                        onBack = { navController.backOrHome() },
                        onPlayNext = { next ->
                            navController.navigate(
                                Routes.Player.path(id, next.seasonNumber, next.episodeNumber, next.audioType)
                            ) {
                                popUpTo(entry.destination.id) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }

            if (topLevelRoute) {
                ReliableTopBar(
                    current = currentLabel,
                    profileName = profileState.activeProfile.name,
                    profileAvatarId = profileState.activeProfile.avatarId,
                    onHome = { navController.navigateTopLevel(Routes.Home.route) },
                    onAnime = { navController.navigateTopLevel(Routes.Series.route) },
                    onMovies = { navController.navigateTopLevel(Routes.Movies.route) },
                    onDiscover = { navController.navigateTopLevel(Routes.Genres.route) },
                    onMyList = { navController.navigateTopLevel(Routes.Favorites.route) },
                    onSearch = { navController.navigateTopLevel(Routes.Search.route) },
                    onSettings = { navController.navigateTopLevel(Routes.Settings.route) },
                    onProfiles = { navController.navigateTopLevel(Routes.Profiles.route) },
                    modifier = Modifier.align(Alignment.TopCenter).zIndex(100f)
                )
            }
        }
    }
}

@Composable
private fun ResolvedPlayerRoute(
    viewModel: MiruroViewModel,
    features: NetflixFeatureViewModel,
    animeId: Int,
    season: Int,
    episodeNumber: Int,
    audio: AudioType,
    onBack: () -> Unit,
    onPlayNext: (AnimeEpisode) -> Unit
) {
    val detailsState by viewModel.details.collectAsState()
    val metadataVersion by viewModel.itemMetadataVersion.collectAsState()
    val seasonLoading by viewModel.seasonLoading.collectAsState()
    val seasonErrors by viewModel.seasonErrors.collectAsState()
    var requestSettled by remember(animeId, season, episodeNumber, audio) { mutableStateOf(false) }

    LaunchedEffect(animeId, season, episodeNumber, audio) {
        requestSettled = false
        if (findEpisode(viewModel, animeId, season, episodeNumber, audio) == null) {
            viewModel.ensureSeasonLoaded(animeId, season)
            delay(120L)
        }
        requestSettled = true
    }

    val episode = remember(animeId, season, episodeNumber, audio, detailsState, metadataVersion) {
        findEpisode(viewModel, animeId, season, episodeNumber, audio)
    }
    val detailsAvailable = remember(animeId, detailsState, metadataVersion) {
        viewModel.cachedDetails(animeId) != null
    }
    val targetSeasonId = remember(animeId, season, detailsState, metadataVersion) {
        viewModel.cachedDetails(animeId)
            ?.seasons
            ?.firstOrNull { it.seasonNumber == season }
            ?.id
    }
    val targetSeasonLoading = targetSeasonId?.let { it in seasonLoading } == true
    val targetSeasonError = targetSeasonId?.let { seasonErrors[it] }

    if (episode == null) BackHandler(onBack = onBack)

    when {
        episode != null -> {
            val nextEpisode = remember(episode, metadataVersion) {
                findNextEpisode(
                    viewModel,
                    animeId,
                    episode.seasonNumber,
                    episode.episodeNumber,
                    episode.audioType
                )
            }
            GuardedTvPlayerScreen(
                viewModel = viewModel,
                features = features,
                episode = episode,
                nextEpisode = nextEpisode,
                onBack = onBack,
                onPlayNext = onPlayNext
            )
        }
        !requestSettled || detailsState is UiState.Loading || targetSeasonLoading ->
            LoadingState("Loading saved episode…")
        targetSeasonError != null -> ErrorState(targetSeasonError) {
            viewModel.ensureSeasonLoaded(animeId, season)
        }
        detailsState is UiState.Error -> ErrorState("Could not load this saved episode.") {
            viewModel.ensureSeasonLoaded(animeId, season)
        }
        detailsAvailable -> StateMessage(
            "This saved episode is no longer available. Press Back to choose another episode."
        )
        else -> LoadingState("Loading saved episode…")
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
            it.episodeNumber == episodeNumber && it.audioType == viewModel.settings.value.preferredAudio
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
                        ?: versions.firstOrNull { it.audioType == viewModel.settings.value.preferredAudio }
                        ?: versions.firstOrNull()
                }
        }
        .sortedWith(compareBy<AnimeEpisode> { it.seasonNumber }.thenBy { it.episodeNumber })
        .firstOrNull {
            it.seasonNumber > season ||
                (it.seasonNumber == season && it.episodeNumber > episodeNumber)
        }
}
