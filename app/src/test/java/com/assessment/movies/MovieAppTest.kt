package com.assessment.movies

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.data.MovieRepository
import com.assessment.movies.data.Page
import com.assessment.movies.data.Review
import com.assessment.movies.data.Trailer
import com.assessment.movies.ui.MovieTheme
import com.assessment.movies.ui.PagedState
import com.assessment.movies.ui.ReviewsScreen
import com.assessment.movies.ui.MoviesScreen
import com.assessment.movies.ui.trailerHtml
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.IOException
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w360dp-h800dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MovieAppTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `browse genres film detail reviews and return to film list`() {
        compose.setContent { MovieTheme { MovieApp(FixtureRepository()) } }
        screenshot("genres")
        compose.onNodeWithText("Action").performClick()
        compose.onNodeWithText("Fixture film").assertIsDisplayed().performClick()
        compose.onNodeWithText("Fixture synopsis").performScrollTo().assertIsDisplayed()
        screenshot("detail")
        compose.onNodeWithText("Baca ulasan penonton").performScrollTo().performClick()
        compose.onNodeWithText("Fixture review content").assertIsDisplayed()
        screenshot("reviews")
        compose.onNodeWithText("Kembali").performClick()
        compose.onNodeWithText("Kembali").performClick()
        compose.onNodeWithText("Fixture film").assertIsDisplayed()
    }

    @Test fun `initial network error offers retry and recovers`() {
        val repository = FixtureRepository().apply { failGenres = true }
        compose.setContent { MovieTheme { MovieApp(repository) } }
        compose.onNodeWithText("Tidak dapat terhubung. Periksa koneksi internet lalu coba lagi.").assertIsDisplayed()
        repository.failGenres = false
        compose.onNodeWithText("Coba lagi").performClick()
        compose.onNodeWithText("Action").assertIsDisplayed()
    }

    @Test fun `empty reviews and absent trailer are shown explicitly`() {
        val repository = FixtureRepository().apply { emptyReviews = true }
        compose.setContent { MovieTheme { MovieApp(repository) } }
        compose.onNodeWithText("Action").performClick()
        compose.onNodeWithText("Fixture film").performClick()
        compose.onNodeWithText("Trailer YouTube belum tersedia.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Baca ulasan penonton").performScrollTo().performClick()
        compose.onNodeWithText("Belum ada ulasan untuk film ini.").assertIsDisplayed()
    }

    @Test fun `about opens closes and its source link has an external destination`() {
        var opened = ""
        compose.setContent { MovieTheme { MovieApp(FixtureRepository(), { opened = it.toString() }) } }
        compose.onNodeWithText("Tentang aplikasi").performClick()
        compose.onNodeWithText("Kunjungi TMDB").performClick()
        assertEquals("https://www.themoviedb.org", opened)
        compose.onNodeWithText("Tutup").performClick()
        compose.onNodeWithText("Action").assertIsDisplayed()
    }

    @Test fun `trailer screen opens YouTube for the selected film and can return`() {
        val repository = FixtureRepository().apply { availableTrailer = Trailer("abcdefghijk", "Fixture trailer") }
        var opened = ""
        compose.setContent { MovieTheme { MovieApp(repository, { opened = it.toString() }) } }
        compose.onNodeWithText("Action").performClick()
        compose.onNodeWithText("Fixture film").performClick()
        compose.onNodeWithText("Tonton trailer").performScrollTo().performClick()
        compose.onNodeWithText("Fixture trailer").assertIsDisplayed()
        compose.onNodeWithText("Buka di YouTube").performScrollTo().performClick()
        assertEquals("https://www.youtube.com/watch?v=abcdefghijk", opened)
        compose.onNodeWithText("Kembali").performClick()
        compose.onNodeWithText("Fixture film").assertIsDisplayed()
    }

    @Test fun `player refuses keys that could inject markup`() {
        try {
            trailerHtml("\"><script>")
            fail("Expected rejected key")
        } catch (_: IllegalArgumentException) { }
        val html = trailerHtml("abcdefghijk")
        assertTrue(html.contains("https://www.youtube.com/embed/abcdefghijk"))
        assertTrue(html.contains("strict-origin-when-cross-origin"))
        assertFalse(html.contains("autoplay=1"))
    }

    @Test fun `long review can expand and collapse`() {
        val review = Review("long-fixture", "Fixture author", "Fixture review paragraph. ".repeat(100), null)
        compose.setContent {
            MovieTheme { ReviewsScreen(PagedState(items = listOf(review), loaded = true, endReached = true), {}) }
        }
        compose.onNodeWithText("Baca selengkapnya").performScrollTo().performClick()
        compose.onNodeWithText("Tampilkan lebih sedikit").performScrollTo().performClick()
        compose.onNodeWithText("Baca selengkapnya").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h640dp")
    fun `narrow screen with large text keeps navigation usable`() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MovieTheme { MovieApp(FixtureRepository()) }
            }
        }
        screenshot("narrow-large-text")
        compose.onNodeWithText("Action").performScrollTo().performClick()
        compose.onNodeWithText("Fixture film").performScrollTo().performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Baca ulasan penonton"))
        compose.onNodeWithText("Baca ulasan penonton").performClick()
        compose.onNodeWithText("Fixture review content").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w600dp-h800dp")
    fun `wide catalog supports multiple columns and selects the correct movie`() {
        val movies = (1..6).map { Movie(it, "Fixture film $it", null, "2026-01-01", 8.0, 1) }
        var selected = 0
        compose.setContent {
            MovieTheme { MoviesScreen(PagedState(items = movies, loaded = true, endReached = true), {}, { selected = it.id }) }
        }
        screenshot("wide-catalog")
        compose.onNodeWithText("Fixture film 2").performClick()
        assertEquals(2, selected)
    }

    private fun screenshot(name: String) {
        val file = File("build/reports/ui/$name-fixture.png")
        checkNotNull(file.parentFile).mkdirs()
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

// Test fixtures are isolated from the application and never shown as live TMDB data.
private class FixtureRepository : MovieRepository {
    var failGenres = false
    var emptyReviews = false
    var availableTrailer: Trailer? = null
    private val movie = Movie(7, "Fixture film", null, "2026-01-01", 8.0, 10)
    override suspend fun genres(): List<Genre> {
        if (failGenres) throw IOException()
        return listOf(Genre(28, "Action"))
    }
    override suspend fun movies(genreId: Int, page: Int) = Page(listOf(movie), page, 1)
    override suspend fun detail(movieId: Int) = MovieDetail(movie, "Fixture synopsis", listOf(Genre(28, "Action")), 120)
    override suspend fun reviews(movieId: Int, page: Int) = Page(
        if (emptyReviews) emptyList() else listOf(Review("r1", "Fixture author", "Fixture review content", 9.0)), page, 1,
    )
    override suspend fun trailer(movieId: Int): Trailer? = availableTrailer
}
