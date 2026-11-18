package com.assessment.movies.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.assessment.movies.data.Genre
import com.assessment.movies.ui.MovieStyle

@Composable
fun GenreCategoryRow(
    genres: List<Genre>,
    selectedId: Int?,
    onGenre: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
) {
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().testTag("genre-row"),
        horizontalArrangement = Arrangement.spacedBy(MovieStyle.CategoryGap),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(genres, key = { it.id }) { genre ->
            GenreChip(genre, genre.id == selectedId, { onGenre(genre.id) }, singleLine = true)
        }
    }
}
