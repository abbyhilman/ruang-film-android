package com.assessment.movies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.data.Review
import com.assessment.movies.data.Trailer

@Composable
fun DetailScreen(
    detail: LoadState<MovieDetail>,
    trailer: LoadState<Trailer>,
    retryDetail: () -> Unit,
    retryTrailer: () -> Unit,
    onReviews: () -> Unit,
    onTrailer: (Trailer) -> Unit,
) {
    LazyColumn(contentPadding = ScreenPadding, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        when {
            detail.loading -> item { Loading() }
            detail.error != null -> item { StatusMessage(stringResource(detail.error), retryDetail) }
            detail.data != null -> {
                val data = detail.data
                item {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Poster(data.movie.posterPath, data.movie.title, Modifier.widthIn(max = 260.dp).fillMaxWidth())
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(data.movie.title, style = MaterialTheme.typography.headlineLarge)
                        Text(
                            listOfNotNull(
                                data.movie.releaseDate.take(4).takeIf { it.isNotBlank() },
                                data.runtime?.let { pluralStringResource(R.plurals.runtime, it, it) },
                            ).joinToString(" · "),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (data.genres.isNotEmpty()) Text(data.genres.joinToString(" · ") { it.name })
                        Text(
                            if (data.movie.voteCount > 0) stringResource(R.string.rating, data.movie.rating)
                            else stringResource(R.string.no_rating),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeading(stringResource(R.string.synopsis))
                        Text(
                            data.overview.ifBlank { stringResource(R.string.no_synopsis) },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
                item {
                    when {
                        trailer.loading -> Loading()
                        trailer.error != null -> StatusMessage(stringResource(trailer.error), retryTrailer)
                        trailer.data != null -> Button(
                            onClick = { onTrailer(trailer.data) }, modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.watch_trailer)) }
                        else -> Text(stringResource(R.string.no_trailer))
                    }
                }
                item {
                    OutlinedButton(onClick = onReviews, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.reviews_action))
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewsScreen(state: PagedState<Review>, loadNext: () -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreOnScroll(state, listState, loadNext)
    LazyColumn(
        state = listState,
        contentPadding = ScreenPadding,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(state.items, key = { it.id }) { review -> ReviewItem(review) }
        item {
            when {
                state.loaded && state.items.isEmpty() -> StatusMessage(stringResource(R.string.no_reviews))
                else -> PaginationFooter(state, loadNext)
            }
        }
    }
}

@Composable
private fun ReviewItem(review: Review) {
    var expanded by rememberSaveable(review.id) { mutableStateOf(false) }
    var canExpand by rememberSaveable(review.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            stringResource(R.string.review_author, review.author.ifBlank { stringResource(R.string.anonymous) }),
            style = MaterialTheme.typography.titleMedium,
        )
        review.rating?.let { Text(stringResource(R.string.rating, it), style = MaterialTheme.typography.labelLarge) }
        Text(
            review.content,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 7,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) canExpand = it.hasVisualOverflow },
        )
        if (canExpand || expanded) TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.collapse_review else R.string.expand_review))
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
    }
}
