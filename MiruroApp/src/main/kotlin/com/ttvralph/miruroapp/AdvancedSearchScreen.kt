package com.ttvralph.miruroapp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ttvralph.miruroapp.data.AppSettings
import com.ttvralph.miruroapp.data.DiscoverySearchFilters
import com.ttvralph.miruroapp.data.DiscoverySort
import com.ttvralph.miruroapp.data.StudioOption
import com.ttvralph.miruroapp.ui.ErrorState
import com.ttvralph.miruroapp.ui.FocusableSurface
import com.ttvralph.miruroapp.ui.LoadingState
import com.ttvralph.miruroapp.ui.MiruroColors
import com.ttvralph.miruroapp.ui.PrimaryButton
import com.ttvralph.miruroapp.ui.SecondaryButton
import com.ttvralph.miruroapp.ui.StateMessage
import java.util.Calendar
import kotlinx.coroutines.delay

private val AdvancedGenres = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror",
    "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance",
    "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller"
)

@Composable
fun AdvancedSearchScreen(
    discovery: DiscoveryFeatureViewModel,
    library: MiruroViewModel,
    onOpenDetails: (Int) -> Unit
) {
    var filters by remember { mutableStateOf(DiscoverySearchFilters()) }
    var filterOverlay by remember { mutableStateOf(false) }
    val results by discovery.searchResults.collectAsState()
    val studios by discovery.studios.collectAsState()
    val favorites by library.favoriteIds.collectAsState()
    val settings by library.settings.collectAsState()

    LaunchedEffect(Unit) { discovery.loadStudios() }
    LaunchedEffect(filters, favorites) {
        if (!filters.hasCriteria) {
            discovery.clearSearch()
            return@LaunchedEffect
        }
        delay(if (filters.dubbedOnly) 550L else 350L)
        discovery.search(filters, favorites)
    }

    if (filterOverlay) {
        AdvancedFilterOverlay(
            value = filters,
            studios = studios,
            settings = settings,
            onDismiss = { filterOverlay = false },
            onApply = {
                filters = it.copy(page = 1)
                filterOverlay = false
            }
        )
    }

    Row(
        modifier = Modifier.fillMaxSize().background(MiruroColors.Background).padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        Column(Modifier.width(if (settings.largeUiText) 460.dp else 420.dp)) {
            Text(
                "Advanced Search",
                color = Color.White,
                fontSize = (if (settings.largeUiText) 37 else 32).sp,
                fontWeight = FontWeight.Black
            )
            Text(
                "Search alternate titles and combine detailed AniList filters.",
                color = MiruroColors.Subtle,
                fontSize = (if (settings.largeUiText) 15 else 12).sp
            )
            Spacer(Modifier.height(14.dp))
            AdvancedSearchBox(
                query = filters.query,
                settings = settings,
                onQueryChange = { filters = filters.copy(query = it, page = 1) }
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiscoveryChoice("All", filters.formats.isEmpty(), settings, Modifier.width(88.dp)) {
                    filters = filters.copy(formats = emptySet(), page = 1)
                }
                DiscoveryChoice("Series", filters.formats == setOf("TV"), settings, Modifier.width(102.dp)) {
                    filters = filters.copy(formats = setOf("TV"), page = 1)
                }
                DiscoveryChoice("Movies", filters.formats == setOf("MOVIE"), settings, Modifier.width(108.dp)) {
                    filters = filters.copy(formats = setOf("MOVIE"), page = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
            SecondaryButton(
                "Advanced Filters (${advancedFilterCount(filters)})",
                Modifier.fillMaxWidth()
            ) { filterOverlay = true }
            Spacer(Modifier.height(12.dp))
            AdvancedKeyboard(
                settings = settings,
                onCharacter = { filters = filters.copy(query = filters.query + it, page = 1) },
                onBackspace = {
                    if (filters.query.isNotEmpty()) filters = filters.copy(query = filters.query.dropLast(1), page = 1)
                },
                onSpace = {
                    if (filters.query.isNotEmpty() && !filters.query.endsWith(' ')) {
                        filters = filters.copy(query = filters.query + " ", page = 1)
                    }
                },
                onClear = { filters = DiscoverySearchFilters() },
                onSearch = { discovery.search(filters, favorites) }
            )
            Spacer(Modifier.height(13.dp))
            AdvancedFilterSummary(filters, studios, settings)
        }

        Column(Modifier.weight(1f).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Results",
                    color = Color.White,
                    fontSize = (if (settings.largeUiText) 31 else 27).sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.weight(1f))
                if (filters.page > 1) {
                    SecondaryButton("Previous", Modifier.width(130.dp)) {
                        filters = filters.copy(page = (filters.page - 1).coerceAtLeast(1))
                    }
                    Spacer(Modifier.width(8.dp))
                }
                if (results is UiState.Success && (results as UiState.Success).data.isNotEmpty()) {
                    SecondaryButton("Next page", Modifier.width(145.dp)) {
                        filters = filters.copy(page = filters.page + 1)
                    }
                }
            }
            Text(
                if (filters.hasCriteria) "Page ${filters.page}" else "Enter a title or choose filters",
                color = MiruroColors.Subtle,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))

            when (val current = results) {
                null -> StateMessage("Search by title, studio, season, score, length, source, genres, or library status.")
                is UiState.Loading -> LoadingState(
                    if (filters.dubbedOnly) "Searching and checking dubbed sources…" else "Searching anime…"
                )
                is UiState.Error -> ErrorState(current.message) { discovery.search(filters, favorites) }
                is UiState.Success -> {
                    if (current.data.isEmpty()) {
                        StateMessage(
                            if (filters.dubbedOnly) {
                                "No dubbed titles matched this page. Try fewer filters or turn off Dubbed Only."
                            } else {
                                "No titles matched these filters."
                            }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(if (settings.largeUiText) 200.dp else 174.dp),
                            horizontalArrangement = Arrangement.spacedBy(17.dp),
                            verticalArrangement = Arrangement.spacedBy(17.dp),
                            contentPadding = PaddingValues(bottom = 30.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            gridItems(current.data, key = { it.id }) { anime ->
                                DiscoveryMediaCard(
                                    item = anime,
                                    settings = settings,
                                    width = if (settings.largeUiText) 194.dp else 170.dp,
                                    height = if (settings.largeUiText) 274.dp else 240.dp,
                                    subtitle = listOfNotNull(
                                        anime.type.takeIf { it.name != "UNKNOWN" }?.name?.replace('_', ' '),
                                        anime.year?.toString(),
                                        anime.score?.let { "$it%" }
                                    ).joinToString(" • "),
                                    badge = when {
                                        filters.dubbedOnly -> "DUB"
                                        anime.id in favorites -> "MY LIST"
                                        else -> null
                                    },
                                    onClick = { onOpenDetails(anime.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdvancedSearchBox(
    query: String,
    settings: AppSettings,
    onQueryChange: (String) -> Unit
) {
    val requester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(11.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (settings.largeUiText) 72.dp else 64.dp)
            .background(if (focused) Color.White else Color(0xFF161616), shape)
            .border(
                if (focused || settings.highContrastUi) 2.dp else 1.dp,
                if (focused) MiruroColors.Accent else Color.White.copy(alpha = if (settings.highContrastUi) 0.8f else 0.18f),
                shape
            )
            .clickable { requester.requestFocus() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                color = if (focused) Color.Black else Color.White,
                fontSize = (if (settings.largeUiText) 23 else 20).sp,
                fontWeight = FontWeight.Bold
            ),
            modifier = Modifier.fillMaxWidth().focusRequester(requester).onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (focused) Color.Black else Color.White.copy(alpha = 0.62f),
                        modifier = Modifier.size(25.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isBlank()) {
                            Text(
                                "English, romaji, or Japanese title",
                                color = if (focused) Color.DarkGray else Color.White.copy(alpha = 0.50f),
                                fontSize = (if (settings.largeUiText) 18 else 16).sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                }
            }
        )
    }
}

@Composable
private fun AdvancedKeyboard(
    settings: AppSettings,
    onCharacter: (String) -> Unit,
    onBackspace: () -> Unit,
    onSpace: () -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit
) {
    val rows = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { letter ->
                    FocusableSurface(
                        onClick = { onCharacter(letter.toString()) },
                        modifier = Modifier.size(
                            width = if (settings.largeUiText) 40.dp else 36.dp,
                            height = if (settings.largeUiText) 46.dp else 40.dp
                        ),
                        shape = RoundedCornerShape(6.dp),
                        unfocusedBackground = if (settings.highContrastUi) Color.Black else Color.White.copy(alpha = 0.07f),
                        focusedBackground = Color.White
                    ) { focused ->
                        Box(
                            Modifier.fillMaxSize().then(
                                if (settings.highContrastUi && !focused) {
                                    Modifier.border(1.dp, Color.White.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                                } else Modifier
                            ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                letter.toString(),
                                color = if (focused) Color.Black else Color.White,
                                fontSize = (if (settings.largeUiText) 16 else 14).sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            SecondaryButton("⌫", Modifier.width(70.dp), onBackspace)
            SecondaryButton("Space", Modifier.width(118.dp), onSpace)
            SecondaryButton("Clear", Modifier.width(100.dp), onClear)
            PrimaryButton("Search", Modifier.width(112.dp), onSearch)
        }
    }
}

@Composable
private fun AdvancedFilterSummary(
    filters: DiscoverySearchFilters,
    studios: List<StudioOption>,
    settings: AppSettings
) {
    val summary = buildList {
        filters.formats.takeIf { it.isNotEmpty() }?.let { add(it.joinToString()) }
        filters.statuses.takeIf { it.isNotEmpty() }?.let { add(it.joinToString()) }
        filters.season?.let(::add)
        if (filters.yearFrom != null || filters.yearTo != null) add("${filters.yearFrom ?: "Any"}–${filters.yearTo ?: "Now"}")
        filters.minimumScore?.let { add("$it%+") }
        filters.maximumEpisodes?.let { add("≤$it eps") }
        filters.maximumDurationMinutes?.let { add("≤$it min") }
        filters.studioId?.let { id -> studios.firstOrNull { it.id == id }?.name?.let(::add) }
        if (filters.dubbedOnly) add("Dubbed")
        if (filters.myListOnly) add("My List")
    }
    Text(
        summary.joinToString(" • ").ifBlank { "No advanced filters selected" },
        color = if (summary.isEmpty()) MiruroColors.Subtle else MiruroColors.AccentSoft,
        fontSize = (if (settings.largeUiText) 14 else 12).sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
    if (filters.dubbedOnly) {
        Spacer(Modifier.height(5.dp))
        Text(
            "Dubbed Only checks current provider episode data and may take longer.",
            color = Color.White.copy(alpha = 0.58f),
            fontSize = 11.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun AdvancedFilterOverlay(
    value: DiscoverySearchFilters,
    studios: List<StudioOption>,
    settings: AppSettings,
    onDismiss: () -> Unit,
    onApply: (DiscoverySearchFilters) -> Unit
) {
    BackHandler(onBack = onDismiss)
    var draft by remember(value) { mutableStateOf(value) }
    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
    val shape = RoundedCornerShape(16.dp)

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.84f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(1050.dp)
                .heightIn(max = 650.dp)
                .background(if (settings.highContrastUi) Color.Black else Color(0xFF101010), shape)
                .then(
                    if (settings.highContrastUi) Modifier.border(2.dp, Color.White, shape) else Modifier
                )
                .padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        "Advanced Filters",
                        color = Color.White,
                        fontSize = (if (settings.largeUiText) 31 else 27).sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Include and exclude genres, then narrow by release, score, length, source, studio, and availability.",
                        color = MiruroColors.Subtle,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                SecondaryButton("Reset", Modifier.width(120.dp)) {
                    draft = DiscoverySearchFilters(query = value.query)
                }
                Spacer(Modifier.width(8.dp))
                SecondaryButton("Cancel", Modifier.width(120.dp), onDismiss)
                Spacer(Modifier.width(8.dp))
                PrimaryButton("Apply", Modifier.width(120.dp)) { onApply(draft) }
            }
            Spacer(Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(17.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    FilterSection("Format", settings) {
                        val options = listOf("TV", "TV_SHORT", "MOVIE", "OVA", "SPECIAL")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                DiscoveryChoice("Any", draft.formats.isEmpty(), settings, Modifier.width(88.dp)) {
                                    draft = draft.copy(formats = emptySet())
                                }
                            }
                            items(options) { option ->
                                DiscoveryChoice(option.replace('_', ' '), option in draft.formats, settings, Modifier.width(116.dp)) {
                                    draft = draft.copy(
                                        formats = if (option in draft.formats) draft.formats - option else draft.formats + option
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    FilterSection("Status", settings) {
                        val options = listOf("RELEASING" to "Airing", "FINISHED" to "Finished", "NOT_YET_RELEASED" to "Upcoming")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                DiscoveryChoice("Any", draft.statuses.isEmpty(), settings, Modifier.width(88.dp)) {
                                    draft = draft.copy(statuses = emptySet())
                                }
                            }
                            items(options) { (value, label) ->
                                DiscoveryChoice(label, value in draft.statuses, settings, Modifier.width(122.dp)) {
                                    draft = draft.copy(
                                        statuses = if (value in draft.statuses) draft.statuses - value else draft.statuses + value
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    FilterSection("Season", settings) {
                        val options = listOf("WINTER", "SPRING", "SUMMER", "FALL")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                DiscoveryChoice("Any", draft.season == null, settings, Modifier.width(88.dp)) {
                                    draft = draft.copy(season = null)
                                }
                            }
                            items(options) { option ->
                                DiscoveryChoice(option.lowercase().replaceFirstChar { it.uppercase() }, draft.season == option, settings, Modifier.width(112.dp)) {
                                    draft = draft.copy(season = option.takeUnless { draft.season == option })
                                }
                            }
                        }
                    }
                }
                item {
                    FilterSection("Release years", settings) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("From", color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp)
                            YearChoices(draft.yearFrom, currentYear, settings) { draft = draft.copy(yearFrom = it) }
                            Text("To", color = Color.White.copy(alpha = 0.64f), fontSize = 12.sp)
                            YearChoices(draft.yearTo, currentYear, settings) { draft = draft.copy(yearTo = it) }
                        }
                    }
                }
                item {
                    FilterSection("Score and length", settings) {
                        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            FilterChoiceLine("Minimum score", listOf(null to "Any", 60 to "60%+", 70 to "70%+", 80 to "80%+", 90 to "90%+"), draft.minimumScore, settings) {
                                draft = draft.copy(minimumScore = it)
                            }
                            FilterChoiceLine("Maximum episodes", listOf(null to "Any", 1 to "1", 12 to "12", 24 to "24", 52 to "52"), draft.maximumEpisodes, settings) {
                                draft = draft.copy(maximumEpisodes = it)
                            }
                            FilterChoiceLine("Maximum runtime", listOf(null to "Any", 15 to "15 min", 25 to "25 min", 45 to "45 min", 120 to "120 min"), draft.maximumDurationMinutes, settings) {
                                draft = draft.copy(maximumDurationMinutes = it)
                            }
                        }
                    }
                }
                item {
                    FilterSection("Source material", settings) {
                        val options = listOf(
                            null to "Any", "ORIGINAL" to "Original", "MANGA" to "Manga",
                            "LIGHT_NOVEL" to "Light novel", "VISUAL_NOVEL" to "Visual novel",
                            "VIDEO_GAME" to "Video game", "NOVEL" to "Novel"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(options) { (source, label) ->
                                DiscoveryChoice(label, draft.source == source, settings, Modifier.width(132.dp)) {
                                    draft = draft.copy(source = source)
                                }
                            }
                        }
                    }
                }
                item {
                    FilterSection("Country", settings) {
                        val options = listOf(null to "Any", "JP" to "Japan", "KR" to "Korea", "CN" to "China")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(options) { (code, label) ->
                                DiscoveryChoice(label, draft.country == code, settings, Modifier.width(112.dp)) {
                                    draft = draft.copy(country = code)
                                }
                            }
                        }
                    }
                }
                item {
                    FilterSection("Studio", settings) {
                        if (studios.isEmpty()) {
                            Text("Studio list is loading…", color = MiruroColors.Subtle, fontSize = 13.sp)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    DiscoveryChoice("Any", draft.studioId == null, settings, Modifier.width(88.dp)) {
                                        draft = draft.copy(studioId = null)
                                    }
                                }
                                items(studios, key = { it.id }) { studio ->
                                    DiscoveryChoice(studio.name, draft.studioId == studio.id, settings, Modifier.width(160.dp)) {
                                        draft = draft.copy(studioId = studio.id.takeUnless { draft.studioId == studio.id })
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    FilterSection("Include genres", settings) {
                        GenreChoiceRows(AdvancedGenres, draft.includeGenres, settings) { genre ->
                            draft = draft.copy(
                                includeGenres = if (genre in draft.includeGenres) draft.includeGenres - genre else draft.includeGenres + genre,
                                excludeGenres = draft.excludeGenres - genre
                            )
                        }
                    }
                }
                item {
                    FilterSection("Exclude genres", settings) {
                        GenreChoiceRows(AdvancedGenres, draft.excludeGenres, settings) { genre ->
                            draft = draft.copy(
                                excludeGenres = if (genre in draft.excludeGenres) draft.excludeGenres - genre else draft.excludeGenres + genre,
                                includeGenres = draft.includeGenres - genre
                            )
                        }
                    }
                }
                item {
                    FilterSection("Availability and library", settings) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DiscoveryChoice("Dubbed Only", draft.dubbedOnly, settings, Modifier.width(155.dp)) {
                                draft = draft.copy(dubbedOnly = !draft.dubbedOnly)
                            }
                            DiscoveryChoice("My List Only", draft.myListOnly, settings, Modifier.width(155.dp)) {
                                draft = draft.copy(myListOnly = !draft.myListOnly)
                            }
                        }
                    }
                }
                item {
                    FilterSection("Sort", settings) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(DiscoverySort.entries, key = { it.name }) { option ->
                                DiscoveryChoice(option.label, draft.sort == option, settings, Modifier.width(145.dp)) {
                                    draft = draft.copy(sort = option)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    settings: AppSettings,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (settings.highContrastUi) Color.Black else Color.White.copy(alpha = 0.045f),
                RoundedCornerShape(10.dp)
            )
            .then(
                if (settings.highContrastUi) Modifier.border(1.dp, Color.White.copy(alpha = 0.65f), RoundedCornerShape(10.dp)) else Modifier
            )
            .padding(13.dp)
    ) {
        Text(
            title,
            color = Color.White,
            fontSize = (if (settings.largeUiText) 18 else 16).sp,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun YearChoices(
    selected: Int?,
    currentYear: Int,
    settings: AppSettings,
    onSelected: (Int?) -> Unit
) {
    val options = listOf<Int?>(null, currentYear, currentYear - 1, currentYear - 3, currentYear - 5, 2010, 2000, 1990)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { year ->
            DiscoveryChoice(year?.toString() ?: "Any", selected == year, settings, Modifier.width(94.dp)) {
                onSelected(year)
            }
        }
    }
}

@Composable
private fun FilterChoiceLine(
    label: String,
    options: List<Pair<Int?, String>>,
    selected: Int?,
    settings: AppSettings,
    onSelected: (Int?) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp, modifier = Modifier.width(170.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(options) { (value, text) ->
                DiscoveryChoice(text, selected == value, settings, Modifier.width(105.dp)) { onSelected(value) }
            }
        }
    }
}

@Composable
private fun GenreChoiceRows(
    genres: List<String>,
    selected: Set<String>,
    settings: AppSettings,
    onToggle: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        genres.chunked(6).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                row.forEach { genre ->
                    DiscoveryChoice(genre, genre in selected, settings, Modifier.width(145.dp)) { onToggle(genre) }
                }
            }
        }
    }
}

private fun advancedFilterCount(filters: DiscoverySearchFilters): Int =
    filters.formats.size + filters.statuses.size + filters.includeGenres.size + filters.excludeGenres.size +
        listOf(
            filters.season,
            filters.yearFrom,
            filters.yearTo,
            filters.minimumScore,
            filters.maximumEpisodes,
            filters.maximumDurationMinutes,
            filters.source,
            filters.country,
            filters.studioId
        ).count { it != null } +
        listOf(filters.dubbedOnly, filters.myListOnly).count { it }
