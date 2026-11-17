package com.assessment.movies.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.data.Review
import com.assessment.movies.ui.components.ReviewCard
import com.assessment.movies.ui.components.ReviewSkeleton

@Composable
fun ReviewsScreen(state: PagedState<Review>, loadNext: () -> Unit) {
    val listState = rememberLazyListState()
    LoadMoreOnScroll(state, listState, loadNext)
    LazyColumn(
        state = listState, contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("reviews-list"),
    ) {
        if (!state.loaded && state.error == null) items(3, key = { "review-skeleton-$it" }) { ReviewSkeleton() }
        items(state.items, key = { it.id }) { ReviewCard(it) }
        item {
            when {
                state.loaded && state.items.isEmpty() -> StatusMessage(stringResource(R.string.no_reviews))
                else -> PaginationFooter(state, loadNext)
            }
        }
    }
}
