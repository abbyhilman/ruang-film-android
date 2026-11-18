package com.assessment.movies

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
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
import com.assessment.movies.ui.MovieStyle
import com.assessment.movies.ui.PagedState
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w430dp-h932dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryScrollingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `last category stays visible and selected while filtering then returning from detail`() {
        val repository = CategoryRepository()
        compose.setContent { MovieTheme { MovieApp(repository) } }
        compose.onNodeWithTag("genre-row").performScrollToNode(hasTestTag("genre-37"))
        compose.onNodeWithTag("genre-37").assertIsDisplayed().performClick()
        compose.onNodeWithTag("genre-37").assertIsDisplayed().assertIsSelected()
        assertEquals(37, repository.requests.last())
        val duringLoading = compose.onNodeWithTag("genre-37").fetchSemanticsNode().boundsInRoot
        screenshot("category-filter-loading")

        repository.lastPage.complete(Page(listOf(repository.movie(37)), 1, 1))
        compose.waitForIdle()
        compose.onNodeWithTag("genre-37").assertIsDisplayed().assertIsSelected()
        val loaded = compose.onNodeWithTag("genre-37").fetchSemanticsNode().boundsInRoot
        assertEquals(duringLoading.left, loaded.left, 1f)
        assertEquals(duringLoading.right, loaded.right, 1f)
        compose.onNodeWithTag("movie-37").assertIsDisplayed().performClick()
        compose.onNodeWithText("Selected genre 37").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithTag("genre-37").assertIsDisplayed().assertIsSelected()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h640dp-mdpi")
    fun `small phone supports large text and both scroll directions`() = verifyScrolling(2f, "small-320-font200")

    @Test
    @Config(sdk = [35], qualifiers = "w360dp-h800dp-xhdpi")
    fun `standard phone supports content sized categories`() = verifyScrolling(1f, "standard-360")

    @Test
    @Config(sdk = [35], qualifiers = "w430dp-h932dp-xxhdpi")
    fun `large phone supports scaled category labels`() = verifyScrolling(1.5f, "large-430-font150")

    @Test
    @Config(sdk = [35], qualifiers = "w800dp-h1000dp-mdpi")
    fun `tablet preserves category order and full last item`() = verifyScrolling(1f, "tablet-800")

    private fun verifyScrolling(fontScale: Float, name: String) {
        var chosen = 28
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                var selected by remember { mutableStateOf(28) }
                MovieTheme {
                    Surface(Modifier.fillMaxSize(), color = MovieStyle.Canvas) {
                    CatalogScreen(
                        LoadState(loading = false, data = categories), PagedState(loaded = true, endReached = true),
                        selected, "", {}, { chosen = it; selected = it }, {}, {}, {}, {}, {},
                    )
                    }
                }
            }
        }
        val row = compose.onNodeWithTag("genre-row")
        val viewport = row.fetchSemanticsNode().boundsInRoot
        val density = compose.density.density
        val first = compose.onNodeWithTag("genre-28").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("genre-12").fetchSemanticsNode().boundsInRoot
        assertEquals("Start margin", 16f * density, viewport.left, 1f)
        assertEquals("End margin", 16f * density, compose.onRoot().fetchSemanticsNode().boundsInRoot.right - viewport.right, 1f)
        assertEquals("Category gap", 8f * density, second.left - first.right, 1f)
        val initialScroll = row.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        compose.onNodeWithTag("genre-28").assertIsSelected()
        assertLabelFits(12, "Adventure")
        screenshot("category-$name-start")
        row.performTouchInput { swipeLeft() }
        val afterSwipe = row.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value()
        assertTrue("Horizontal gesture should move categories", afterSwipe > initialScroll)

        row.performScrollToNode(hasTestTag("genre-878"))
        assertLabelFits(878, "Science Fiction")
        row.performScrollToNode(hasTestTag("genre-37"))
        val last = compose.onNodeWithTag("genre-37").assertIsDisplayed()
        val bounds = last.fetchSemanticsNode().boundsInRoot
        assertTrue("Last item starts inside viewport", bounds.left >= viewport.left - 1f)
        assertTrue("Last item ends inside viewport", bounds.right <= viewport.right + 1f)
        assertTrue("Touch target is at least 48dp", bounds.height >= 48f * density - 1f)
        last.performClick().assertIsSelected()
        assertEquals(37, chosen)
        screenshot("category-$name-end")
        row.performTouchInput { swipeRight() }
        row.performScrollToNode(hasTestTag("genre-28"))
        compose.onNodeWithTag("genre-28").assertIsDisplayed().performClick().assertIsSelected()
        assertEquals(28, chosen)
    }

    private fun screenshot(name: String) {
        val file = File("build/reports/ui/$name-fixture.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun assertLabelFits(id: Int, label: String) {
        compose.onNodeWithTag("genre-$id").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
            val results = mutableListOf<TextLayoutResult>()
            assertTrue(action(results))
            val layout = results.single()
            assertEquals(1, layout.lineCount)
            assertEquals(label.length, layout.getLineEnd(0))
            assertFalse(layout.isLineEllipsized(0))
            assertFalse(layout.didOverflowHeight)
            // Unwrapped text uses an unbounded paragraph; check the actual glyph line against the label box.
            assertTrue("$label must fit its chip", layout.getLineLeft(0) >= 0f && layout.getLineRight(0) <= layout.size.width)
        }
    }
}

private val categories = listOf(
    Genre(28, "Action"), Genre(12, "Adventure"), Genre(16, "Animation"), Genre(35, "Comedy"),
    Genre(80, "Crime"), Genre(99, "Documentary"), Genre(18, "Drama"), Genre(10751, "Family"),
    Genre(14, "Fantasy"), Genre(36, "History"), Genre(27, "Horror"), Genre(10402, "Music"),
    Genre(9648, "Mystery"), Genre(10749, "Romance"), Genre(878, "Science Fiction"),
    Genre(10770, "TV Movie"), Genre(53, "Thriller"), Genre(10752, "War"), Genre(37, "Western"),
)

private class CategoryRepository : MovieRepository {
    val requests = mutableListOf<Int>()
    val lastPage = CompletableDeferred<Page<Movie>>()
    fun movie(id: Int) = Movie(id, "Fixture film $id", null, "", 0.0, 0)
    override suspend fun genres() = categories
    override suspend fun movies(genreId: Int, page: Int): Page<Movie> {
        requests += genreId
        return if (genreId == 37) lastPage.await() else Page(listOf(movie(genreId)), page, 1)
    }
    override suspend fun search(query: String, page: Int) = Page(emptyList<Movie>(), page, 1)
    override suspend fun detail(movieId: Int) = MovieDetail(movie(movieId), "Selected genre $movieId", emptyList(), 90)
    override suspend fun reviews(movieId: Int, page: Int) = Page(emptyList<Review>(), page, 1)
    override suspend fun trailer(movieId: Int): Trailer? = null
}
