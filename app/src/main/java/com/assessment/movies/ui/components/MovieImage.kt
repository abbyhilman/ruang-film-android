package com.assessment.movies.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.assessment.movies.R
import com.assessment.movies.ui.MovieStyle

@Composable
fun MovieImage(
    model: String?,
    description: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    fallbackText: String = stringResource(R.string.poster_missing),
) {
    val context = LocalContext.current
    val request = remember(model, context) {
        ImageRequest.Builder(context).data(model).apply {
            if (model?.substringBefore('?')?.endsWith(".svg") == true) decoderFactory(SvgDecoder.Factory())
        }.build()
    }
    Box(modifier.background(MovieStyle.SearchSurface), contentAlignment = Alignment.Center) {
        if (model == null) {
            ImageFallback(fallbackText)
        } else {
            SubcomposeAsyncImage(
                model = request,
                contentDescription = description,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                loading = { SkeletonBlock(Modifier.fillMaxSize()) },
                error = { ImageFallback(fallbackText) },
            )
        }
    }
}

@Composable
private fun ImageFallback(text: String) {
    Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MovieStyle.Muted)
    }
}

fun tmdbImage(path: String?, width: String = "w500"): String? =
    path?.takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/$width$it" }
