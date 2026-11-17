package com.assessment.movies.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.ui.MovieStyle

@Composable
fun ProfileHeader(onProfile: () -> Unit, onGenres: () -> Unit) {
    val profileLabel = stringResource(R.string.demo_avatar)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(onClick = onProfile, modifier = Modifier.size(48.dp).testTag("profile-button").semantics { contentDescription = profileLabel }) {
            MovieImage(
                "file:///android_asset/guest-avatar.svg", stringResource(R.string.demo_avatar),
                Modifier.size(40.dp).clip(CircleShape), fallbackText = "RF",
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.welcome_back), style = MaterialTheme.typography.bodySmall, color = MovieStyle.Muted)
            Text(stringResource(R.string.guest_name), style = MaterialTheme.typography.titleMedium)
        }
        IconButton(onClick = onGenres) { Icon(Icons.Default.Menu, stringResource(R.string.choose_genre), tint = MovieStyle.Muted) }
    }
}

@Composable
fun MovieSearchField(query: String, onQueryChanged: (String) -> Unit, modifier: Modifier = Modifier) {
    val searchLabel = stringResource(R.string.search_movies)
    TextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier.fillMaxWidth().testTag("movie-search").semantics { contentDescription = searchLabel },
        placeholder = { Text(stringResource(R.string.search_movies)) },
        leadingIcon = { Icon(Icons.Default.Search, null, tint = MovieStyle.Muted) },
        trailingIcon = if (query.isNotEmpty()) ({
            IconButton(onClick = { onQueryChanged("") }) { Icon(Icons.Default.Clear, stringResource(R.string.clear_search)) }
        }) else null,
        singleLine = true,
        shape = RoundedCornerShape(MovieStyle.Corner),
        textStyle = MaterialTheme.typography.bodyMedium,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MovieStyle.SearchSurface,
            unfocusedContainerColor = MovieStyle.SearchSurface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedPlaceholderColor = MovieStyle.Muted,
            unfocusedPlaceholderColor = MovieStyle.Muted,
            cursorColor = MovieStyle.Gold,
        ),
    )
}

@Composable
fun GenreChip(genre: Genre, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(genre.name, style = MaterialTheme.typography.labelMedium) },
        modifier = Modifier.heightIn(min = 44.dp).testTag("genre-${genre.id}"),
        shape = RoundedCornerShape(8.dp),
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MovieStyle.Genre,
            selectedContainerColor = MovieStyle.SelectedGenre,
            labelColor = Color.White,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
fun RatingStars(rating: Double, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.rating, rating)
    Row(modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(5) { index ->
            val fraction = ((rating / 2.0) - index).coerceIn(0.0, 1.0).toFloat()
            Box(Modifier.size(14.dp)) {
                Icon(Icons.Default.Star, null, Modifier.fillMaxSize(), tint = Color(0xFF777780))
                Icon(
                    Icons.Default.Star, null,
                    Modifier.fillMaxSize().drawWithContent { clipRect(right = size.width * fraction) { this@drawWithContent.drawContent() } },
                    tint = MovieStyle.Rating,
                )
            }
        }
    }
}

@Composable
fun MoviePosterCard(movie: Movie, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.testTag("movie-${movie.id}"),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MovieStyle.Canvas),
    ) {
        MovieImage(
            tmdbImage(movie.posterPath), stringResource(R.string.poster_description, movie.title),
            Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
        )
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                movie.title, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (movie.voteCount > 0) RatingStars(movie.rating)
            else Text(stringResource(R.string.no_rating), style = MaterialTheme.typography.bodySmall, color = MovieStyle.Muted)
        }
    }
}

@Composable
fun PlayButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = MovieStyle.Play, contentColor = Color.White),
    ) { Icon(Icons.Default.PlayArrow, stringResource(R.string.watch_trailer), Modifier.size(32.dp)) }
}

@Composable
fun FeaturedMovieCard(movie: Movie, onMovie: () -> Unit, onPlay: () -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = onMovie, modifier = modifier.height(160.dp), shape = RoundedCornerShape(10.dp)) {
        Box(Modifier.fillMaxSize()) {
            MovieImage(tmdbImage(movie.backdropPath ?: movie.posterPath, "w780"), null, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .85f), Color.Black.copy(alpha = .60f))),
                ),
            )
            Column(Modifier.align(Alignment.TopStart).padding(12.dp).fillMaxWidth(.72f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(movie.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (movie.releaseDate.isNotBlank()) Text(movie.releaseDate, style = MaterialTheme.typography.bodySmall)
            }
            if (movie.voteCount > 0) Row(
                Modifier.align(Alignment.TopEnd).padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MovieStyle.Rating)
                Text(stringResource(R.string.rating_value, movie.rating), style = MaterialTheme.typography.labelSmall)
            }
            PlayButton(onPlay, Modifier.align(Alignment.BottomEnd).padding(8.dp).testTag("featured-play-${movie.id}"))
        }
    }
}
