package com.assessment.movies

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.net.toUri
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.svg.SvgDecoder
import com.assessment.movies.data.MovieRepository
import com.assessment.movies.data.TmdbRepository
import com.assessment.movies.data.createTmdbApi
import com.assessment.movies.ui.DetailScreen
import com.assessment.movies.ui.DetailViewModel
import com.assessment.movies.ui.GenresScreen
import com.assessment.movies.ui.GenresViewModel
import com.assessment.movies.ui.MovieTheme
import com.assessment.movies.ui.MoviesScreen
import com.assessment.movies.ui.MoviesViewModel
import com.assessment.movies.ui.ReviewsScreen
import com.assessment.movies.ui.ReviewsViewModel
import com.assessment.movies.ui.TrailerScreen
import com.assessment.movies.ui.movieViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val repository = TmdbRepository(createTmdbApi(BuildConfig.TMDB_READ_ACCESS_TOKEN))
        setContent { MovieTheme { MovieApp(repository, ::openExternal) } }
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme != "https") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun MovieApp(repository: MovieRepository, openExternal: (Uri) -> Unit = {}) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val route = current?.destination?.route
    val title = when {
        route?.startsWith("movies/") == true -> current?.arguments?.getString("name").orEmpty()
        route?.startsWith("detail/") == true -> stringResource(R.string.movie_detail)
        route?.startsWith("reviews/") == true -> stringResource(R.string.reviews)
        route?.startsWith("trailer/") == true -> stringResource(R.string.trailer)
        else -> stringResource(R.string.app_name)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = {
            Column {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (nav.previousBackStackEntry != null) TextButton(onClick = { nav.popBackStack() }) {
                        Text(stringResource(R.string.back))
                    }
                    Text(title, Modifier.weight(1f).padding(vertical = 12.dp), style = MaterialTheme.typography.titleLarge)
                    if (route == null || route == "genres") TextButton(onClick = { showAbout = true }) {
                        Text(stringResource(R.string.about))
                    }
                }
                HorizontalDivider()
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavHost(navController = nav, startDestination = "genres") {
                composable("genres") { entry ->
                    val model: GenresViewModel = viewModel(entry, factory = movieViewModelFactory { GenresViewModel(repository) })
                    val state by model.state.collectAsStateWithLifecycle()
                    GenresScreen(state, model::reload) { genre ->
                        nav.navigate("movies/${genre.id}/${Uri.encode(genre.name)}") { launchSingleTop = true }
                    }
                }
                composable(
                    "movies/{id}/{name}",
                    arguments = listOf(navArgument("id") { type = NavType.IntType }),
                ) { entry ->
                    val id = entry.arguments!!.getInt("id")
                    val model: MoviesViewModel = viewModel(entry, factory = movieViewModelFactory { MoviesViewModel(repository, id) })
                    val state by model.state.collectAsStateWithLifecycle()
                    MoviesScreen(state, model::loadNext) { movie ->
                        nav.navigate("detail/${movie.id}") { launchSingleTop = true }
                    }
                }
                composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                    val id = entry.arguments!!.getInt("id")
                    val model: DetailViewModel = viewModel(entry, factory = movieViewModelFactory { DetailViewModel(repository, id) })
                    val detail by model.detail.collectAsStateWithLifecycle()
                    val trailer by model.trailer.collectAsStateWithLifecycle()
                    DetailScreen(
                        detail, trailer, model::reloadDetail, model::reloadTrailer,
                        onReviews = { nav.navigate("reviews/$id") { launchSingleTop = true } },
                        onTrailer = { nav.navigate("trailer/${it.key}/${Uri.encode(it.title.ifBlank { detail.data?.movie?.title.orEmpty() })}") { launchSingleTop = true } },
                    )
                }
                composable("reviews/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                    val id = entry.arguments!!.getInt("id")
                    val model: ReviewsViewModel = viewModel(entry, factory = movieViewModelFactory { ReviewsViewModel(repository, id) })
                    val state by model.state.collectAsStateWithLifecycle()
                    ReviewsScreen(state, model::loadNext)
                }
                composable("trailer/{key}/{title}") { entry ->
                    TrailerScreen(entry.arguments!!.getString("key").orEmpty(), entry.arguments!!.getString("title").orEmpty(), openExternal)
                }
            }
        }
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false }, openExternal = openExternal)
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, openExternal: (Uri) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data("file:///android_asset/tmdb-logo.svg")
                        .decoderFactory(SvgDecoder.Factory())
                        .build(),
                    contentDescription = stringResource(R.string.tmdb_logo),
                    modifier = Modifier.width(124.dp).height(32.dp),
                )
                Text(stringResource(R.string.data_source))
                Text(stringResource(R.string.tmdb_attribution))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = {
            TextButton(onClick = { openExternal("https://www.themoviedb.org".toUri()) }) {
                Text(stringResource(R.string.visit_tmdb))
            }
        },
    )
}
