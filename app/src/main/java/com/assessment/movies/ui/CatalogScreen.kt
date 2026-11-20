package com.assessment.movies.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.ui.components.GenrePickerDialog
import com.assessment.movies.ui.components.FeaturedMovieCard
import com.assessment.movies.ui.components.GenreCategoryRow
import com.assessment.movies.ui.components.MoviePosterCard
import com.assessment.movies.ui.components.MovieSearchField
import com.assessment.movies.ui.components.PosterCardSkeleton
import com.assessment.movies.ui.components.ProfileHeader
import com.assessment.movies.ui.components.SkeletonBlock
import com.assessment.movies.ui.components.MovieArtworkTransition

@Composable
fun CatalogScreen(
    genres: LoadState<List<Genre>>,
    movies: PagedState<Movie>,
    selectedGenreId: Int?,
    query: String,
    onQuery: (String) -> Unit,
    onGenre: (Int) -> Unit,
    retryGenres: () -> Unit,
    loadNext: () -> Unit,
    onMovie: (Movie) -> Unit,
    onPlay: (Movie) -> Unit,
    onProfile: () -> Unit,
    featuredDetail: MovieDetail? = null,
    onFeaturedVisible: (Int) -> Unit = {},
    onFeaturedMovie: (Movie) -> Unit = onMovie,
    artworkTransition: MovieArtworkTransition? = null,
) {
    var chooseGenre by rememberSaveable { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val genreRowState = rememberLazyListState()
    var appliedQuery by rememberSaveable { mutableStateOf(query) }
    var appliedGenreId by rememberSaveable { mutableStateOf(selectedGenreId) }
    LoadMoreOnScroll(movies, gridState, loadNext)
    LaunchedEffect(query, selectedGenreId) {
        if (query != appliedQuery || selectedGenreId != appliedGenreId) {
            appliedQuery = query
            appliedGenreId = selectedGenreId
            gridState.scrollToItem(0)
        }
    }
    val initialLoading = genres.loading || (!movies.loaded && movies.error == null && (genres.error == null || query.isNotBlank()))
    val loadingLabel = stringResource(R.string.loading_catalog)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp), state = gridState,
        contentPadding = PaddingValues(MovieStyle.PagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.testTag("catalog-grid"),
    ) {
        item(key = "profile", span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeader(onProfile, onGenres = { chooseGenre = true }, genresEnabled = !genres.data.isNullOrEmpty())
        }
        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            MovieSearchField(query, onQuery, loading = initialLoading || (movies.loading && query.isNotBlank()))
        }
        if (initialLoading) {
            item(key = "featured-loading", span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.testTag("catalog-skeleton").semantics {
                        contentDescription = loadingLabel
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    },
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    SkeletonBlock(Modifier.fillMaxWidth().height(160.dp))
                    if (genres.data.isNullOrEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(MovieStyle.CategoryGap)) {
                        repeat(4) { SkeletonBlock(Modifier.size(72.dp, 48.dp)) }
                    }
                }
            }
            if (!genres.data.isNullOrEmpty()) item(key = "genres", span = { GridItemSpan(maxLineSpan) }) {
                GenreCategoryRow(genres.data, selectedGenreId, onGenre, state = genreRowState)
            }
            items(6, key = { "poster-loading-$it" }) { PosterCardSkeleton() }
        } else if (genres.error != null && query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) { StatusMessage(stringResource(genres.error), retryGenres) }
        } else if (genres.data.isNullOrEmpty() && query.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) { StatusMessage(stringResource(R.string.no_genres)) }
        } else {
            if (query.isBlank() && movies.items.isNotEmpty()) item(key = "featured", span = { GridItemSpan(maxLineSpan) }) {
                FeaturedCarousel(movies.items.take(5), onFeaturedMovie, onPlay, featuredDetail, onFeaturedVisible, artworkTransition)
            }
            if (!genres.data.isNullOrEmpty()) item(key = "genres", span = { GridItemSpan(maxLineSpan) }) {
                GenreCategoryRow(genres.data, selectedGenreId, onGenre, state = genreRowState)
            }
            if (query.isNotBlank()) item(key = "results-title", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeading(stringResource(R.string.search_results))
            }
            items(movies.items, key = { it.id }) { movie -> MoviePosterCard(movie, onClick = { onMovie(movie) }, artworkTransition = artworkTransition) }
            if (movies.loading && movies.items.isNotEmpty()) items(2, key = { "append-skeleton-$it" }) { PosterCardSkeleton() }
            item(key = "footer", span = { GridItemSpan(maxLineSpan) }) {
                when {
                    movies.error != null -> StatusMessage(stringResource(movies.error), loadNext)
                    movies.loaded && movies.items.isEmpty() -> StatusMessage(stringResource(if (query.isBlank()) R.string.no_movies else R.string.no_search_results))
                    !movies.loading && movies.endReached && movies.items.isNotEmpty() -> Text(
                        stringResource(R.string.end_of_list), Modifier.padding(vertical = 16.dp),
                        style = MaterialTheme.typography.bodySmall, color = MovieStyle.Muted,
                    )
                }
            }
        }
    }
    if (chooseGenre) GenrePickerDialog(
        genres.data.orEmpty(), selectedGenreId,
        onGenre = { onGenre(it); chooseGenre = false },
        onDismiss = { chooseGenre = false },
    )
}

@Composable
private fun FeaturedCarousel(movies: List<Movie>, onMovie: (Movie) -> Unit, onPlay: (Movie) -> Unit, detail: MovieDetail?, onVisible: (Int) -> Unit, artworkTransition: MovieArtworkTransition?) {
    val pager = rememberPagerState { movies.size }
    val selected = movies[pager.settledPage.coerceIn(movies.indices)]
    LaunchedEffect(selected.id) { onVisible(selected.id) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pager, contentPadding = PaddingValues(end = 24.dp), pageSpacing = MovieStyle.Gap,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val metadata = detail?.takeIf { it.movie.id == movies[page].id }
            FeaturedMovieCard(
                movies[page].copy(originalLanguage = movies[page].originalLanguage ?: metadata?.movie?.originalLanguage),
                { onMovie(movies[page]) }, { onPlay(movies[page]) }, Modifier.fillMaxWidth(), runtime = metadata?.runtime,
                artworkTransition = artworkTransition,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(movies.size) { index ->
                Box(Modifier.size(if (pager.currentPage == index) 30.dp else 7.dp, 7.dp).background(
                    if (pager.currentPage == index) Color(0xFF4D438A) else Color(0xFF2A2740), CircleShape,
                ))
            }
        }
    }
}
