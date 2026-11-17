package com.assessment.movies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie

@Composable
fun GenresScreen(state: LoadState<List<Genre>>, retry: () -> Unit, onGenre: (Genre) -> Unit) {
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.genres_intro), style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(R.string.genres_description),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        when {
            state.loading -> item { Loading() }
            state.error != null -> item { StatusMessage(stringResource(state.error), retry) }
            state.data.isNullOrEmpty() -> item { StatusMessage(stringResource(R.string.no_genres)) }
            else -> items(state.data, key = { it.id }) { genre ->
                OutlinedCard(onClick = { onGenre(genre) }, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        genre.name, Modifier.padding(20.dp).heightIn(min = 28.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
fun MoviesScreen(state: PagedState<Movie>, loadNext: () -> Unit, onMovie: (Movie) -> Unit) {
    val gridState = rememberLazyGridState()
    LoadMoreOnScroll(state, gridState, loadNext)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(148.dp),
        state = gridState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.items, key = { it.id }) { movie ->
            Card(
                onClick = { onMovie(movie) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Poster(movie.posterPath, movie.title, Modifier.fillMaxWidth())
                Column(Modifier.padding(top = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        movie.title, style = MaterialTheme.typography.titleMedium,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        movie.releaseDate.take(4).ifBlank { stringResource(R.string.year_unknown) },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (movie.voteCount > 0) stringResource(R.string.rating, movie.rating)
                        else stringResource(R.string.no_rating),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            when {
                state.loaded && state.items.isEmpty() -> StatusMessage(stringResource(R.string.no_movies))
                else -> PaginationFooter(state, loadNext)
            }
        }
    }
}
