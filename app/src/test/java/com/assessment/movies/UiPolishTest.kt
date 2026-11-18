package com.assessment.movies

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.ui.CatalogScreen
import com.assessment.movies.ui.LoadState
import com.assessment.movies.ui.MovieTheme
import com.assessment.movies.ui.PagedState
import com.assessment.movies.ui.components.GenrePickerDialog
import com.assessment.movies.ui.components.SkeletonBlock
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
class UiPolishTest {
    @get:Rule val compose = createComposeRule()

    private fun pixels(): IntArray {
        val image = compose.onNodeWithTag("probe").captureToImage().asAndroidBitmap()
        return IntArray(image.width * image.height).also { image.getPixels(it, 0, image.width, 0, 0, image.width, image.height) }
    }

    @Test fun `loading highlight moves without changing layout`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MovieTheme { Box(Modifier.size(200.dp, 60.dp).testTag("probe")) { SkeletonBlock(Modifier.fillMaxSize()) } } }
        compose.mainClock.advanceTimeBy(32)
        val before = pixels()
        compose.mainClock.advanceTimeBy(500)
        assertFalse(before.contentEquals(pixels()))
    }

    @Test fun `reduced motion leaves a stable loading placeholder`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MovieTheme { Box(Modifier.size(200.dp, 60.dp).testTag("probe")) { SkeletonBlock(Modifier.fillMaxSize(), motionEnabled = false) } } }
        compose.mainClock.advanceTimeBy(32)
        val before = pixels()
        compose.mainClock.advanceTimeBy(500)
        assertArrayEquals(before, pixels())
    }

    @Test fun `genre picker uses adjacent columns and sends the selected genre`() {
        var selected = 0
        compose.setContent {
            MovieTheme { GenrePickerDialog(listOf(Genre(28, "Action"), Genre(35, "Comedy"), Genre(99, "Documentary")), 28, { selected = it }, {}) }
        }
        val first = compose.onNodeWithTag("genre-28").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("genre-35").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(second.left > first.right)
        val image = compose.onNodeWithTag("genre-dialog").captureToImage().asAndroidBitmap()
        val destination = File("build/reports/ui/genre-grid-fixture.png")
        destination.parentFile!!.mkdirs()
        destination.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithTag("genre-35").performClick()
        assertEquals(35, selected)
    }

    @Test fun `search banner genre and poster share one horizontal content edge`() {
        val movie = Movie(7, "Fixture film", null, "", 8.0, 1, originalLanguage = "en")
        compose.setContent {
            MovieTheme { CatalogScreen(LoadState(loading = false, data = listOf(Genre(28, "Action"))), PagedState(items = listOf(movie), loaded = true, endReached = true), 28, "", {}, {}, {}, {}, {}, {}, {}) }
        }
        val edge = compose.onNodeWithTag("movie-search").fetchSemanticsNode().boundsInRoot.left
        for (tag in listOf("featured-card-7", "genre-28", "movie-7")) {
            compose.onNodeWithTag(tag).assertIsDisplayed()
            assertEquals(tag, edge, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.left, 1f)
        }
    }

    @Test
    @Config(sdk = [35], qualifiers = "w320dp-h640dp")
    fun `narrow genre picker keeps long labels reachable with large text`() {
        var selected = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                MovieTheme {
                    GenrePickerDialog((1..20).map { Genre(it, if (it == 20) "Science Fiction" else "Fixture genre $it") }, 1, { selected = it }, {})
                }
            }
        }
        compose.onNodeWithTag("genre-grid").performScrollToNode(hasTestTag("genre-20"))
        compose.onNodeWithTag("genre-20").assertIsDisplayed().performClick()
        assertEquals(20, selected)
    }
}
