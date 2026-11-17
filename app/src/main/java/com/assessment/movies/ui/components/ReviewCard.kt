package com.assessment.movies.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.data.Review
import com.assessment.movies.ui.MovieStyle

@Composable
fun ReviewCard(review: Review, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(10.dp), color = MovieStyle.SearchSurface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).background(MovieStyle.Genre, CircleShape), contentAlignment = Alignment.Center) {
                    Text(review.author.take(1).uppercase().ifBlank { "?" }, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.review_author, review.author.ifBlank { stringResource(R.string.anonymous) }),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    review.rating?.let { RatingStars(it) }
                }
            }
            ExpandableText(review.content)
        }
    }
}
