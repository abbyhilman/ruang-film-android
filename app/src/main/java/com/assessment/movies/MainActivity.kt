@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.assessment.movies

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.animation.ValueAnimator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import com.assessment.movies.ui.components.ArtworkMasksViewModel
import com.assessment.movies.ui.components.movieMaskKey
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.assessment.movies.data.Movie
import com.assessment.movies.data.MovieRepository
import com.assessment.movies.data.TmdbRepository
import com.assessment.movies.data.createTmdbApi
import com.assessment.movies.ui.CatalogScreen
import com.assessment.movies.ui.CatalogViewModel
import com.assessment.movies.ui.DetailScreen
import com.assessment.movies.ui.DetailViewModel
import com.assessment.movies.ui.MovieTheme
import com.assessment.movies.ui.ReviewsScreen
import com.assessment.movies.ui.ReviewsViewModel
import com.assessment.movies.ui.components.MovieImage
import com.assessment.movies.ui.components.TrailerDialog
import com.assessment.movies.ui.components.MovieArtworkOrigin
import com.assessment.movies.ui.components.MovieArtworkSelection
import com.assessment.movies.ui.components.MovieArtworkTransition
import com.assessment.movies.ui.movieViewModelFactory
import com.assessment.movies.ui.branding.StartupRevealState
import com.assessment.movies.ui.branding.StartupRevealHost
import androidx.compose.ui.res.painterResource

class MainActivity : ComponentActivity() {
    private lateinit var startup: StartupRevealState

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        val launchPreferences = getSharedPreferences("app_startup", MODE_PRIVATE)
        val lastUpdate = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
        val firstLaunchAfterInstall = launchPreferences.getLong("last_package_update", -1) != lastUpdate
        if (firstLaunchAfterInstall) launchPreferences.edit { putLong("last_package_update", lastUpdate) }
        startup = StartupRevealState(
            initiallyVisible = (savedInstanceState == null || firstLaunchAfterInstall) && ValueAnimator.areAnimatorsEnabled(),
        )
        if (startup.visible) splash.setOnExitAnimationListener { it.remove() }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        val repository = TmdbRepository(createTmdbApi(BuildConfig.TMDB_READ_ACCESS_TOKEN))
        val saved = getSharedPreferences("saved_movies", MODE_PRIVATE)
        val initial = saved.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        val artworkMasks = ViewModelProvider(this)[ArtworkMasksViewModel::class.java]
        setContent {
            MovieTheme {
                StartupRevealHost(startup) {
                    MovieApp(
                        repository, ::openExternal, initial,
                        onFavoritesChanged = { ids -> saved.edit { putStringSet("ids", ids.map(Int::toString).toSet()) } },
                        onShare = ::shareMovie,
                        artworkMasks = artworkMasks,
                    )
                }
            }
        }
    }

    override fun onStop() {
        if (::startup.isInitialized) startup.finish()
        super.onStop()
    }

    private fun openExternal(uri: Uri) {
        if (uri.scheme != "https") return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_LONG).show()
        }
    }

    private fun shareMovie(movie: Movie) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "${movie.title}\nhttps://www.themoviedb.org/movie/${movie.id}")
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_movie)))
    }
}

@Composable
fun MovieApp(
    repository: MovieRepository,
    openExternal: (Uri) -> Unit = {},
    initialFavorites: List<Int> = emptyList(),
    onFavoritesChanged: (List<Int>) -> Unit = {},
    onShare: (Movie) -> Unit = {},
    motionEnabled: Boolean = ValueAnimator.areAnimatorsEnabled(),
    artworkMasks: ArtworkMasksViewModel? = null,
) {
    val nav = rememberNavController()
    val current by nav.currentBackStackEntryAsState()
    val routeLifecycle = current?.lifecycle?.currentStateFlow?.collectAsStateWithLifecycle()?.value
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var favorites by rememberSaveable { mutableStateOf(initialFavorites) }
    var artworkSelection by remember { mutableStateOf<MovieArtworkSelection?>(null) }
    var artworkViewport by remember { mutableStateOf(IntSize.Zero) }
    val masks = artworkMasks?.masks?.collectAsStateWithLifecycle()?.value.orEmpty()
    fun openMovie(movie: Movie, origin: MovieArtworkOrigin, play: Boolean = false) {
        if (nav.currentBackStackEntry?.lifecycle?.currentState != Lifecycle.State.RESUMED) return
        val maskKey = movieMaskKey(movie, origin)
        artworkMasks?.prioritize(maskKey)
        artworkSelection = MovieArtworkSelection(movie, origin, masks[maskKey])
        nav.navigate("detail/${movie.id}?play=$play") { launchSingleTop = true }
    }
    val reviewsRoute = current?.destination?.route?.startsWith("reviews/") == true
    Scaffold(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        topBar = {
            if (reviewsRoute) Row(
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                Text(stringResource(R.string.reviews), style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        SharedTransitionLayout(Modifier.padding(padding).onSizeChanged { artworkViewport = it }) {
            LaunchedEffect(isTransitionActive, routeLifecycle, masks) {
                val selected = artworkSelection
                if (!isTransitionActive && routeLifecycle == Lifecycle.State.RESUMED && selected != null && selected.mask == null) {
                    masks[movieMaskKey(selected.movie, selected.origin)]?.let { artworkSelection = selected.copy(mask = it) }
                }
            }
            NavHost(
                navController = nav, startDestination = "catalog",
                enterTransition = { if (motionEnabled) fadeIn(tween(300)) else EnterTransition.None },
                exitTransition = { if (motionEnabled) fadeOut(tween(300)) else ExitTransition.None },
            ) {
                composable("catalog") { entry ->
                    val model: CatalogViewModel = viewModel(entry, factory = movieViewModelFactory { CatalogViewModel(repository) })
                    val genres by model.genres.collectAsStateWithLifecycle()
                    val movies by model.movies.collectAsStateWithLifecycle()
                    val query by model.query.collectAsStateWithLifecycle()
                    val genreId by model.genreId.collectAsStateWithLifecycle()
                    val featuredDetail by model.featuredDetail.collectAsStateWithLifecycle()
                    CatalogScreen(
                        genres, movies, genreId, query, model::search, model::selectGenre,
                        model::reloadGenres, model::loadNext,
                        onMovie = { openMovie(it, MovieArtworkOrigin.Poster) },
                        onPlay = { openMovie(it, MovieArtworkOrigin.Featured, play = true) },
                        onProfile = { showAbout = true },
                        featuredDetail = featuredDetail, onFeaturedVisible = model::loadFeatured,
                        onFeaturedMovie = { openMovie(it, MovieArtworkOrigin.Featured) },
                        artworkTransition = if (motionEnabled) MovieArtworkTransition(this@SharedTransitionLayout, this, artworkViewport, masks, artworkSelection, artworkMasks?.let { it::prepare } ?: { _, _ -> }) else null,
                    )
                }
                composable(
                    "detail/{id}?play={play}",
                    arguments = listOf(navArgument("id") { type = NavType.IntType }, navArgument("play") { type = NavType.BoolType; defaultValue = false }),
                ) { entry ->
                    val id = entry.arguments!!.getInt("id")
                    val model: DetailViewModel = viewModel(entry, factory = movieViewModelFactory { DetailViewModel(repository, id) })
                    val detail by model.detail.collectAsStateWithLifecycle()
                    val trailer by model.trailer.collectAsStateWithLifecycle()
                    var videoOpen by rememberSaveable(id) { mutableStateOf(entry.arguments!!.getBoolean("play")) }
                    val preview = artworkSelection?.takeIf { it.movie.id == id }
                    DetailScreen(
                        detail, trailer, model::reloadDetail,
                        onReviews = { nav.navigate("reviews/$id") { launchSingleTop = true } },
                        onPlay = { videoOpen = true }, onBack = { nav.popBackStack() },
                        favorite = id in favorites,
                        onFavorite = {
                            favorites = if (id in favorites) favorites - id else favorites + id
                            onFavoritesChanged(favorites)
                        },
                        onSource = {
                            openExternal((detail.data?.imdbId?.let { "https://www.imdb.com/title/$it/" } ?: "https://www.themoviedb.org/movie/$id").toUri())
                        },
                        onShare = { (detail.data?.movie ?: preview?.movie)?.let(onShare) },
                        previewMovie = preview?.movie,
                        artworkOrigin = preview?.origin ?: MovieArtworkOrigin.Poster,
                        artworkTransition = if (motionEnabled) MovieArtworkTransition(this@SharedTransitionLayout, this, artworkViewport, masks, artworkSelection, artworkMasks?.let { it::prepare } ?: { _, _ -> }) else null,
                    )
                    if (videoOpen) TrailerDialog(
                        trailer, detail.data?.movie?.title ?: stringResource(R.string.trailer),
                        onDismiss = { videoOpen = false }, onRetry = model::reloadTrailer, openExternal = openExternal,
                    )
                }
                composable("reviews/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                    val model: ReviewsViewModel = viewModel(entry, factory = movieViewModelFactory { ReviewsViewModel(repository, entry.arguments!!.getInt("id")) })
                    val state by model.state.collectAsStateWithLifecycle()
                    ReviewsScreen(state, model::loadNext)
                }
            }
        }
    }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false }, openExternal)
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit, openExternal: (Uri) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.brand_mark), null, Modifier.size(32.dp), tint = com.assessment.movies.ui.MovieStyle.Gold)
                Text(stringResource(R.string.app_name))
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MovieImage("file:///android_asset/tmdb-logo.svg", stringResource(R.string.tmdb_logo), Modifier.size(124.dp, 32.dp), contentScale = ContentScale.Fit, fallbackText = "TMDB")
                Text(stringResource(R.string.data_source))
                Text(stringResource(R.string.tmdb_attribution))
                Text(stringResource(R.string.demo_profile_note), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = { openExternal("https://www.themoviedb.org".toUri()) }) { Text(stringResource(R.string.visit_tmdb)) } },
    )
}
