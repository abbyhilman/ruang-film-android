package com.assessment.movies.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.assessment.movies.R
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.data.Movie
import com.assessment.movies.data.Trailer
import com.assessment.movies.ui.components.DetailSkeleton
import com.assessment.movies.ui.components.ExpandableText
import com.assessment.movies.ui.components.MovieImage
import com.assessment.movies.ui.components.PlayButton
import com.assessment.movies.ui.components.tmdbImage
import com.assessment.movies.ui.components.MovieArtworkOrigin
import com.assessment.movies.ui.components.MovieArtworkTransition
import com.assessment.movies.ui.components.movieArtworkKey
import com.assessment.movies.ui.components.movieArtworkTransition
import com.assessment.movies.ui.components.movieArtworkMask
import com.assessment.movies.ui.components.aboveMovieArtwork
import com.assessment.movies.ui.components.movieTitleTransition
import com.assessment.movies.ui.components.movieMaskKey

@Composable
fun DetailScreen(
    detail: LoadState<MovieDetail>,
    trailer: LoadState<Trailer>,
    retryDetail: () -> Unit,
    onReviews: () -> Unit,
    onPlay: () -> Unit,
    onBack: () -> Unit,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onSource: () -> Unit,
    onShare: () -> Unit,
    previewMovie: Movie? = null,
    artworkOrigin: MovieArtworkOrigin = MovieArtworkOrigin.Poster,
    artworkTransition: MovieArtworkTransition? = null,
) {
    val heroMovie = detail.data?.movie ?: previewMovie
    LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.testTag("detail-list")) {
        if (heroMovie != null) item(key = "hero") {
            MovieHero(heroMovie, detail.data?.runtime, trailer, onBack, onPlay, onShare, artworkOrigin, artworkTransition)
        }
        if (heroMovie != null) item(key = "title") {
            Box(Modifier.padding(horizontal = 16.dp)) {
                Text(
                    heroMovie.title, Modifier.movieTitleTransition(heroMovie.id, artworkOrigin, artworkTransition).testTag("detail-title-${heroMovie.id}"),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
        }
        when {
            detail.loading -> item {
                Box {
                    DetailSkeleton(showHero = heroMovie == null)
                    if (heroMovie == null) IconButton(onClick = onBack, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            }
            detail.error != null -> item {
                Column {
                    if (heroMovie == null) TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
                    StatusMessage(stringResource(detail.error), retryDetail)
                }
            }
            detail.data != null -> {
                val data = detail.data
                item(key = "metadata") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            data.genres.firstOrNull()?.let { MetadataLabel(it.name) }
                            if (data.movie.voteCount > 0) Surface(color = MovieStyle.RaisedSurface, shape = RoundedCornerShape(5.dp)) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Star, null, Modifier.size(16.dp), tint = MovieStyle.Rating)
                                    Text(stringResource(R.string.rating_value, data.movie.rating), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                            Box(Modifier.weight(1f))
                            IconButton(onClick = onFavorite, modifier = Modifier.testTag("favorite-toggle")) {
                                Icon(
                                    if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    stringResource(if (favorite) R.string.unsave_movie else R.string.save_movie),
                                    tint = if (favorite) MovieStyle.Play else Color.White,
                                )
                            }
                        }
                        if (data.movie.releaseDate.isNotBlank()) Text(data.movie.releaseDate, style = MaterialTheme.typography.bodySmall, color = MovieStyle.Muted)
                        ExpandableText(data.overview.ifBlank { stringResource(R.string.no_synopsis) }, collapsedLines = 3)
                        if (!trailer.loading && trailer.data == null && trailer.error == null) Text(stringResource(R.string.no_trailer), style = MaterialTheme.typography.bodySmall, color = MovieStyle.Muted)
                        TextButton(onClick = onReviews) { Text(stringResource(R.string.reviews_action)) }
                    }
                }
                if (data.cast.isNotEmpty()) item(key = "cast") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        SectionHeading(stringResource(R.string.cast))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(data.cast, key = { it.id }) { actor ->
                                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MovieImage(tmdbImage(actor.profilePath, "w185"), actor.name, Modifier.size(96.dp, 104.dp).clip(RoundedCornerShape(6.dp)), fallbackText = actor.name.take(1))
                                    Text(actor.name, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item(key = "source") {
                    Button(onClick = onSource, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(6.dp)) {
                        Text(stringResource(if (data.imdbId != null) R.string.open_imdb else R.string.open_tmdb))
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieHero(
    movie: Movie, runtime: Int?, trailer: LoadState<Trailer>, onBack: () -> Unit, onPlay: () -> Unit,
    onShare: () -> Unit, origin: MovieArtworkOrigin, transition: MovieArtworkTransition?,
) {
    val path = if (origin == MovieArtworkOrigin.Featured) movie.backdropPath ?: movie.posterPath else movie.posterPath
    Box(Modifier.fillMaxWidth().aspectRatio(.9f).testTag("detail-hero-${movie.id}")) {
        MovieImage(
            tmdbImage(path, "w780"), stringResource(R.string.poster_description, movie.title),
            Modifier.movieArtworkTransition(movie.id, origin, transition).fillMaxSize()
                .movieArtworkMask(movie.id, origin, transition).testTag("detail-artwork-${movie.id}"),
            memoryCacheKey = movieArtworkKey(movie.id, origin),
            onImageLoaded = transition?.let { context -> { image -> context.prepareMask(movieMaskKey(movie, origin), image) } },
        )
        Box(Modifier.fillMaxSize().aboveMovieArtwork(transition).background(Brush.verticalGradient(
            listOf(Color.Black.copy(alpha = .35f), Color.Transparent, MovieStyle.Canvas),
        )))
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).aboveMovieArtwork(transition).padding(8.dp).background(Color.Black.copy(alpha = .60f), CircleShape)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
        }
        IconButton(onClick = onShare, modifier = Modifier.align(Alignment.TopEnd).aboveMovieArtwork(transition).padding(8.dp).background(Color.Black.copy(alpha = .60f), CircleShape)) {
            Icon(Icons.Default.Share, stringResource(R.string.share_movie), tint = Color.White)
        }
        if (trailer.loading || trailer.data != null || trailer.error != null) PlayButton(onPlay, Modifier.align(Alignment.Center).aboveMovieArtwork(transition))
        runtime?.let {
            Text(
                pluralStringResource(R.plurals.runtime, it, it),
                Modifier.align(Alignment.BottomEnd).aboveMovieArtwork(transition).padding(16.dp), style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MetadataLabel(text: String) {
    Surface(color = MovieStyle.RaisedSurface, shape = RoundedCornerShape(5.dp)) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
    }
}
