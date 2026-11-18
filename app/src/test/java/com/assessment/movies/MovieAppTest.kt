package com.assessment.movies

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import com.assessment.movies.data.CastMember
import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.data.MovieRepository
import com.assessment.movies.data.Page
import com.assessment.movies.data.Review
import com.assessment.movies.data.Trailer
import com.assessment.movies.ui.CatalogScreen
import com.assessment.movies.ui.LoadState
import com.assessment.movies.ui.MovieTheme
import com.assessment.movies.ui.PagedState
import com.assessment.movies.ui.ReviewsScreen
import com.assessment.movies.ui.components.TrailerDialog
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w430dp-h932dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MovieAppTest {
    @get:Rule val compose = createComposeRule()

    private fun openMovie() {
        compose.onNodeWithTag("movie-7").performScrollTo().performClick()
    }

    private fun openReviews() {
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("Baca ulasan penonton"))
        compose.onNodeWithText("Baca ulasan penonton").performClick()
    }

    @Test fun `catalog detail reviews and back keep working after redesign`() {
        compose.setContent { MovieTheme { MovieApp(FixtureRepository()) } }
        compose.onNodeWithTag("genre-28").assertIsDisplayed()
        screenshot("catalog-figma")
        openMovie()
        compose.onNodeWithText("Fixture synopsis").performScrollTo().assertIsDisplayed()
        screenshot("detail-figma")
        openReviews()
        compose.onNodeWithText("Fixture review content").assertIsDisplayed()
        screenshot("reviews-figma")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithContentDescription("Kembali").performScrollTo().performClick()
        compose.onNodeWithTag("catalog-grid").assertIsDisplayed()
    }

    @Test fun `data loading shows poster card skeletons`() {
        val repo = FixtureRepository().apply { genreGate = CompletableDeferred() }
        compose.setContent { MovieTheme { MovieApp(repo) } }
        compose.onNodeWithTag("catalog-skeleton").assertIsDisplayed()
        screenshot("catalog-skeleton")
    }

    @Test fun `initial network error offers retry and recovers`() {
        val repo = FixtureRepository().apply { failGenres = true }
        compose.setContent { MovieTheme { MovieApp(repo) } }
        compose.onNodeWithText("Tidak dapat terhubung. Periksa koneksi internet lalu coba lagi.").assertIsDisplayed()
        repo.failGenres = false
        compose.onNodeWithText("Coba lagi").performClick()
        compose.onNodeWithTag("genre-28").assertIsDisplayed()
    }

    @Test fun `absent reviews and trailers have explicit empty states`() {
        val repo = FixtureRepository().apply { emptyReviews = true }
        compose.setContent { MovieTheme { MovieApp(repo) } }
        openMovie()
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("Trailer YouTube belum tersedia."))
        compose.onNodeWithText("Trailer YouTube belum tersedia.").assertIsDisplayed()
        openReviews()
        compose.onNodeWithText("Belum ada ulasan untuk film ini.").assertIsDisplayed()
    }

    @Test fun `profile avatar opens attribution and source link`() {
        var opened = ""
        compose.setContent { MovieTheme { MovieApp(FixtureRepository(), { opened = it.toString() }) } }
        compose.onNodeWithTag("profile-button").performClick()
        compose.onNodeWithText("Kunjungi TMDB").performClick()
        assertEquals("https://www.themoviedb.org", opened)
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithTag("catalog-grid").assertIsDisplayed()
    }

    @Test fun `trailer opens in a dismissible popup and external link stays functional`() {
        val repo = FixtureRepository().apply { availableTrailer = Trailer("abcdefghijk", "Fixture trailer") }
        var opened = ""
        compose.setContent { MovieTheme { MovieApp(repo, { opened = it.toString() }) } }
        openMovie()
        compose.onNodeWithContentDescription("Tonton trailer").performClick()
        compose.onNodeWithTag("trailer-dialog").assertIsDisplayed()
        val videoBounds = compose.onNodeWithTag("video-surface").fetchSemanticsNode().boundsInRoot
        assertEquals(16f / 9f, videoBounds.width / videoBounds.height, .02f)
        compose.onNodeWithTag("video-time").assertIsDisplayed()
        screenshot("video-popup")
        compose.onNodeWithText("Buka di YouTube").performScrollTo().performClick()
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", opened)
        compose.onNodeWithContentDescription("Tutup trailer").performScrollTo().performClick()
        compose.onNodeWithTag("trailer-dialog").assertDoesNotExist()
        compose.onNodeWithTag("detail-list").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tonton trailer").performClick()
        compose.onNodeWithTag("trailer-dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tutup trailer").performClick()
    }

    @Test fun `popup remains closable while trailer metadata loads`() {
        var dismissed = false
        compose.setContent {
            MovieTheme { TrailerDialog(LoadState(), "Fixture film", { dismissed = true }, {}, {}) }
        }
        compose.onNodeWithTag("player-skeleton").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tutup trailer").performClick()
        assertTrue(dismissed)
    }

    @Test fun `saving a film updates the shared favorite state`() {
        var saved = emptyList<Int>()
        compose.setContent { MovieTheme { MovieApp(FixtureRepository(), onFavoritesChanged = { saved = it }) } }
        openMovie()
        compose.onNodeWithTag("favorite-toggle").performScrollTo().performClick()
        assertEquals(listOf(7), saved)
        compose.onNodeWithContentDescription("Hapus dari simpanan").assertIsDisplayed().performClick()
        assertTrue(saved.isEmpty())
    }

    @Test fun `IMDb button opens the correct film destination`() {
        var opened = ""
        compose.setContent { MovieTheme { MovieApp(FixtureRepository(), { opened = it.toString() }) } }
        openMovie()
        compose.onNodeWithTag("detail-list").performScrollToNode(hasText("Buka IMDb"))
        compose.onNodeWithText("Buka IMDb").performClick()
        assertEquals("https://www.imdb.com/title/tt0000001/", opened)
    }

    @Test fun `genre menu selection and sharing use the selected film`() {
        val repo = FixtureRepository()
        var shared: Movie? = null
        compose.setContent { MovieTheme { MovieApp(repo, onShare = { shared = it }) } }
        compose.onNodeWithContentDescription("Pilih genre").performClick()
        compose.onAllNodesWithTag("genre-35").onLast().performClick()
        compose.waitForIdle()
        assertEquals(35, repo.requestedGenres.last())
        openMovie()
        compose.onNodeWithContentDescription("Bagikan film").performClick()
        assertEquals(7, shared?.id)
    }

    @Test fun `featured play opens the trailer popup directly`() {
        val repo = FixtureRepository().apply { availableTrailer = Trailer("abcdefghijk", "Fixture trailer") }
        compose.setContent { MovieTheme { MovieApp(repo) } }
        compose.onNodeWithTag("featured-play-7").performClick()
        compose.onNodeWithTag("trailer-dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tutup trailer").performClick()
        compose.onNodeWithTag("trailer-dialog").assertDoesNotExist()
        compose.onNodeWithTag("detail-list").assertIsDisplayed()
    }

    @Test fun `search requests movie results rather than filtering loaded cards`() {
        val repo = FixtureRepository()
        compose.setContent { MovieTheme { MovieApp(repo) } }
        compose.onNodeWithTag("movie-search").performTextInput("space")
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400))
        compose.waitForIdle()
        compose.onNodeWithTag("movie-100").performScrollTo().assertIsDisplayed()
        assertEquals(listOf("space"), repo.searchQueries)
        compose.onNodeWithContentDescription("Hapus pencarian").performClick()
        compose.onNodeWithTag("movie-7").performScrollTo().assertIsDisplayed()
        assertEquals(28, repo.requestedGenres.last())
    }

    @Test fun `long review expands and collapses`() {
        val review = Review("long", "Fixture author", "Fixture paragraph. ".repeat(100), null)
        compose.setContent { MovieTheme { ReviewsScreen(PagedState(items = listOf(review), loaded = true, endReached = true), {}) } }
        compose.onNodeWithText("Baca selengkapnya").performScrollTo().performClick()
        compose.onNodeWithText("Tampilkan lebih sedikit").performScrollTo().performClick()
        compose.onNodeWithText("Baca selengkapnya").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h640dp")
    fun `narrow screen and large text preserve the movie journey`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MovieTheme { MovieApp(FixtureRepository()) }
            }
        }
        screenshot("narrow-figma")
        openMovie()
        openReviews()
        compose.onNodeWithText("Fixture review content").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h900dp")
    fun `wide catalog adapts poster columns and preserves movie selection`() {
        val repo = FixtureRepository()
        var selected = 0
        compose.setContent {
            MovieTheme {
                CatalogScreen(
                    LoadState(loading = false, data = listOf(Genre(28, "Action"))),
                    PagedState(items = repo.fixtureMovies, loaded = true, endReached = true),
                    28, "", {}, {}, {}, {}, { selected = it.id }, {}, {},
                )
            }
        }
        screenshot("wide-figma")
        compose.onNodeWithTag("movie-8").performScrollTo().performClick()
        assertEquals(8, selected)
    }

    private fun screenshot(name: String) {
        val file = File("build/reports/ui/$name-fixture.png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private class FixtureRepository : MovieRepository {
    var failGenres = false
    var emptyReviews = false
    var availableTrailer: Trailer? = null
    var genreGate: CompletableDeferred<List<Genre>>? = null
    val searchQueries = mutableListOf<String>()
    val requestedGenres = mutableListOf<Int>()
    val fixtureMovies = (7..12).map { Movie(it, if (it == 7) "Fixture film" else "Fixture film $it", null, "2026-01-01", 8.0, 10) }
    override suspend fun genres(): List<Genre> {
        genreGate?.let { return it.await() }
        if (failGenres) throw IOException()
        return listOf(Genre(28, "Action"), Genre(35, "Comedy"))
    }
    override suspend fun movies(genreId: Int, page: Int): Page<Movie> {
        requestedGenres += genreId
        return Page(fixtureMovies, page, 1)
    }
    override suspend fun search(query: String, page: Int): Page<Movie> {
        searchQueries += query
        return Page(listOf(Movie(100, "Fixture result", null, "", 0.0, 0)), page, 1)
    }
    override suspend fun detail(movieId: Int) = MovieDetail(
        fixtureMovies.first { it.id == movieId }, "Fixture synopsis", listOf(Genre(28, "Action")), 120,
        cast = listOf(CastMember(1, "Fixture actor", "Fixture role", null)), imdbId = "tt0000001",
    )
    override suspend fun reviews(movieId: Int, page: Int) = Page(
        if (emptyReviews) emptyList() else listOf(Review("r1", "Fixture author", "Fixture review content", 9.0)), page, 1,
    )
    override suspend fun trailer(movieId: Int): Trailer? = availableTrailer
}
