package com.assessment.movies.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.assessment.movies.R
import com.assessment.movies.data.Genre
import com.assessment.movies.ui.MovieStyle

@Composable
fun GenrePickerDialog(genres: List<Genre>, selectedId: Int?, onGenre: (Int) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.safeDrawingPadding().padding(MovieStyle.PagePadding)) {
            val columns = if (maxWidth <= 440.dp) {
                GridCells.Fixed(if (maxWidth < 340.dp && LocalDensity.current.fontScale > 1.3f) 1 else 2)
            } else GridCells.Adaptive(144.dp)
            Surface(shape = RoundedCornerShape(20.dp), color = MovieStyle.Canvas) {
                Column(
                    Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = maxHeight).padding(20.dp).testTag("genre-dialog"),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.choose_genre), style = MaterialTheme.typography.titleLarge)
                    LazyVerticalGrid(
                        columns = columns,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp).weight(1f, fill = false).testTag("genre-grid"),
                        horizontalArrangement = Arrangement.spacedBy(MovieStyle.Gap),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(genres, key = { it.id }) { genre ->
                            GenreChip(genre, genre.id == selectedId, { onGenre(genre.id) }, Modifier.fillMaxWidth())
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                    }
                }
            }
        }
    }
}
