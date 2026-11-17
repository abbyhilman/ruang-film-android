package com.assessment.movies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.ui.components.ReviewSkeleton
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
}

@Composable
fun StatusMessage(message: String, retry: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
        if (retry != null) Button(onClick = retry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
fun <T> PaginationFooter(state: PagedState<T>, retry: () -> Unit) {
    when {
        state.loading -> ReviewSkeleton(Modifier.padding(16.dp))
        state.error != null -> StatusMessage(stringResource(state.error), retry)
        state.endReached && state.items.isNotEmpty() -> Text(
            stringResource(R.string.end_of_list), Modifier.padding(24.dp),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun <T> LoadMoreOnScroll(state: PagedState<T>, listState: LazyListState, loadNext: () -> Unit) {
    val action by rememberUpdatedState(loadNext)
    LaunchedEffect(state.items.size, state.loading, state.error, state.endReached) {
        if (!state.loading && state.error == null && !state.endReached) {
            snapshotFlow {
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= state.items.size - 3
            }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) action() }
        }
    }
}

@Composable
fun <T> LoadMoreOnScroll(state: PagedState<T>, gridState: LazyGridState, loadNext: () -> Unit) {
    val action by rememberUpdatedState(loadNext)
    LaunchedEffect(state.items.size, state.loading, state.error, state.endReached) {
        if (!state.loading && state.error == null && !state.endReached) {
            snapshotFlow {
                (gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= state.items.size - 4
            }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) action() }
        }
    }
}
