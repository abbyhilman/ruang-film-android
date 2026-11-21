@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class, coil3.annotation.DelicateCoilApi::class)

package com.assessment.movies

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import com.assessment.movies.ui.components.ArtworkSubjectMask
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.geometry.Rect
import com.assessment.movies.ui.components.centeredArtworkBounds
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.intercept.Interceptor
import coil3.memory.MemoryCache
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.data.MovieRepository
import com.assessment.movies.data.Page
import com.assessment.movies.data.Review
import com.assessment.movies.data.Trailer
import com.assessment.movies.ui.MovieStyle
import com.assessment.movies.ui.MovieTheme
import com.assessment.movies.ui.components.MovieArtworkOrigin
import com.assessment.movies.ui.components.MovieArtworkTransition
import com.assessment.movies.ui.components.MovieImage
import com.assessment.movies.ui.components.movieArtworkKey
import com.assessment.movies.ui.components.movieArtworkTransition
import com.assessment.movies.ui.components.movieArtworkMask
import com.assessment.movies.ui.components.movieTitleTransition
import com.assessment.movies.ui.components.subjectMaskFromConfidence
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
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
class MovieArtworkTransitionTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var loader: ImageLoader
    private val imageGate = CompletableDeferred<Unit>()

    @Before fun cachedArtwork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        loader = ImageLoader.Builder(context).components {
            add(object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    if (!chain.request.data.toString().startsWith("https://image.tmdb.org/")) return chain.proceed()
                    imageGate.await()
                    return SuccessResult(bitmap(android.graphics.Color.GREEN).asImage(), chain.request, DataSource.NETWORK)
                }
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
        for (id in listOf(7, 8)) {
            loader.memoryCache!![MemoryCache.Key(movieArtworkKey(id, MovieArtworkOrigin.Poster))] = MemoryCache.Value(bitmap(android.graphics.Color.BLUE).asImage())
            loader.memoryCache!![MemoryCache.Key(movieArtworkKey(id, MovieArtworkOrigin.Featured))] = MemoryCache.Value(bitmap(android.graphics.Color.RED).asImage())
        }
    }

    @After fun releaseImages() {
        imageGate.cancel()
        loader.shutdown()
        SingletonImageLoader.reset()
    }

    @Test fun `poster background stays absent throughout the moving subject in both directions`() {
        compose.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        val mask = subjectMaskFromConfidence(128, 192, FloatArray(128 * 192) { if (it % 128 in 48..79) 1f else 0f })!!
        compose.setContent {
            SharedTransitionLayout(Modifier.fillMaxSize().background(Color.Black)) {
                AnimatedContent(expanded.value, transitionSpec = { fadeIn(tween(MovieArtworkTransition.DurationMillis)) togetherWith fadeOut(tween(MovieArtworkTransition.DurationMillis)) }, label = "Background regression") { detail ->
                    val transition = MovieArtworkTransition(this@SharedTransitionLayout, this@AnimatedContent, IntSize(430, 932), mapOf("${movieArtworkKey(321, MovieArtworkOrigin.Poster)}/image" to mask))
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.offset(if (detail) 0.dp else 16.dp, if (detail) 0.dp else 500.dp)) {
                            Box(Modifier.movieArtworkTransition(321, MovieArtworkOrigin.Poster, transition)
                                .size(if (detail) 320.dp else 100.dp, if (detail) 480.dp else 150.dp)
                                .movieArtworkMask(321, MovieArtworkOrigin.Poster, transition).drawBehind {
                                    drawRect(Color.Red)
                                    drawRect(Color.Green, Offset(size.width * .375f, 0f), Size(size.width * .25f, size.height))
                                })
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        for (detail in listOf(true, false)) {
            compose.runOnIdle { expanded.value = detail }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            advanceNativeFrames((MovieArtworkTransition.DurationMillis * .25f).toLong())
            repeat(3) { sample ->
                compose.waitForIdle()
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                val pixels = IntArray(image.width * image.height)
                image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
                val background = pixels.count { android.graphics.Color.red(it) > 8 && android.graphics.Color.green(it) < 5 }
                val subject = pixels.count { android.graphics.Color.green(it) > 100 && android.graphics.Color.red(it) < 5 }
                screenshot("subject-clean-${if (detail) "open" else "back"}-$sample")
                assertTrue("Subject must remain visible at travel sample $sample, detail=$detail count=$subject", subject > 1000)
                assertTrue("Red poster background must stay removed during travel, sample=$sample detail=$detail background=$background subject=$subject", background < subject * .08 + 100)
                advanceNativeFrames((MovieArtworkTransition.DurationMillis * .2f).toLong())
            }
            compose.mainClock.advanceTimeBy(MovieArtworkTransition.DurationMillis.toLong())
        }
    }

    private fun advanceNativeFrames(durationMillis: Long) {
        repeat(((durationMillis + 15) / 16).toInt()) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            compose.onRoot().captureToImage()
        }
    }

    @Test fun `missing mask keeps the whole poster visible in both directions`() {
        compose.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        compose.setContent {
            SharedTransitionLayout(Modifier.fillMaxSize().background(Color.Black)) {
                AnimatedContent(expanded.value, transitionSpec = { fadeIn(tween(MovieArtworkTransition.DurationMillis)) togetherWith fadeOut(tween(MovieArtworkTransition.DurationMillis)) }, label = "Mask pending") { detail ->
                    val transition = MovieArtworkTransition(this@SharedTransitionLayout, this@AnimatedContent, IntSize(430, 932))
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.offset(if (detail) 0.dp else 16.dp, if (detail) 0.dp else 500.dp)
                            .movieArtworkTransition(77, MovieArtworkOrigin.Poster, transition)
                            .size(if (detail) 320.dp else 100.dp, if (detail) 480.dp else 150.dp)
                            .movieArtworkMask(77, MovieArtworkOrigin.Poster, transition).background(Color.Red))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val initial = dominantPixels("red")
        assertTrue(initial > 1000)
        for (detail in listOf(true, false)) {
            compose.runOnIdle { expanded.value = detail }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            advanceNativeFrames(144)
            assertTrue("The fallback poster must remain visible during travel", dominantPixels("red") > 1000)
            compose.mainClock.advanceTimeBy(MovieArtworkTransition.DurationMillis + 160L)
            assertTrue("Resting artwork must return after the transition", dominantPixels("red") > 1000)
        }
        assertEquals(initial, dominantPixels("red"))
    }

    @Test fun `shared artwork grows in intermediate frames and shrinks back to its original size`() {
        compose.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        // A transparent inverse matte isolates the geometry contract from segmentation quality.
        val geometryMask = ArtworkSubjectMask(Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888).asImageBitmap(), Offset(.5f, .5f))
        lateinit var scope: SharedTransitionScope
        compose.setContent {
            SharedTransitionLayout(Modifier.fillMaxSize().background(MovieStyle.Canvas)) {
                scope = this
                AnimatedContent(
                    expanded.value,
                    transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(420)) },
                    modifier = Modifier.fillMaxSize(), label = "Artwork fixture",
                ) { detail ->
                    Box(Modifier.fillMaxSize()) {
                        Box(
                            Modifier.offset(if (detail) 0.dp else 16.dp, if (detail) 0.dp else 500.dp)
                                .movieArtworkTransition(7, MovieArtworkOrigin.Poster, MovieArtworkTransition(this@SharedTransitionLayout, this@AnimatedContent, IntSize(430, 932), mapOf("${movieArtworkKey(7, MovieArtworkOrigin.Poster)}/geometry" to geometryMask)))
                                .size(if (detail) 320.dp else 100.dp, if (detail) 480.dp else 150.dp).background(Color.Red),
                        )
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val initial = dominantPixels("red")
        val initialTop = firstRedRow()
        screenshot("artwork-card")
        compose.runOnIdle { expanded.value = true }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        advanceNativeFrames(80)
        assertEquals("The source stays in place while its background is removed", initialTop, firstRedRow())
        advanceNativeFrames(144)
        val opening = dominantPixels("red")
        assertTrue("Artwork must travel toward the detail hero", firstRedRow() in 1 until initialTop)
        assertTrue("Shared image must animate after layout finds both endpoints", scope.isTransitionActive)
        screenshot("artwork-opening")
        assertCenteredArtwork(scope)
        screenshot("artwork-center")
        compose.mainClock.advanceTimeBy(600)
        val full = dominantPixels("red")
        assertEquals(0, firstRedRow())
        assertFalse(scope.isTransitionActive)
        assertTrue("Artwork must grow through an intermediate size", initial < opening && opening < full)
        screenshot("artwork-expanded")

        compose.runOnIdle { expanded.value = false }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        advanceNativeFrames(80)
        assertEquals("Back also holds the hero during the reveal", 0, firstRedRow())
        advanceNativeFrames(144)
        val closing = dominantPixels("red")
        assertTrue("Back must travel toward the source card", firstRedRow() in 1 until initialTop)
        assertTrue(scope.isTransitionActive)
        assertTrue("Back must shrink through an intermediate size", initial < closing && closing < full)
        screenshot("artwork-returning")
        assertCenteredArtwork(scope)
        screenshot("artwork-center-back")
        compose.mainClock.advanceTimeBy(600)
        assertEquals(initial, dominantPixels("red"))
        assertEquals(initialTop, firstRedRow())
        assertFalse(scope.isTransitionActive)
        screenshot("artwork-returned")
    }

    @Test fun `selected poster stays visible while detail loads and retries after an error`() {
        val repo = ArtworkRepository()
        var shared: Movie? = null
        compose.setContent { MovieTheme { MovieApp(repo, onShare = { shared = it }) } }
        compose.onNodeWithTag("movie-7").performClick()
        compose.onNodeWithTag("detail-hero-7").assertIsDisplayed()
        compose.onNodeWithTag("detail-title-7").assertIsDisplayed()
        compose.onNodeWithTag("detail-skeleton").assertIsDisplayed()
        assertTrue("Cached poster should remain visible", dominantPixels("blue") > 1000)
        compose.onNodeWithContentDescription("Bagikan film").performClick()
        assertEquals(7, shared?.id)
        screenshot("artwork-detail-loading")
        repo.detailGate.completeExceptionally(IOException())
        compose.waitForIdle()
        compose.onNodeWithTag("detail-hero-7").assertIsDisplayed()
        compose.onNodeWithText("Coba lagi").performClick()
        compose.onNodeWithText("Fixture overview 7").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithTag("movie-7").assertIsDisplayed()
    }

    @Test fun `banner artwork is shared independently from the same films poster`() {
        val repo = ArtworkRepository()
        compose.setContent { MovieTheme { MovieApp(repo) } }
        compose.onNodeWithTag("featured-card-7").performClick()
        compose.onNodeWithTag("detail-hero-7").assertIsDisplayed()
        assertTrue("Banner must use the red backdrop rather than the blue poster", dominantPixels("red") > 1000)
        assertEquals(0, dominantPixels("blue"))
        screenshot("artwork-featured-detail")
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithTag("movie-8").performClick()
        compose.onNodeWithTag("detail-hero-8").assertIsDisplayed()
        compose.onNodeWithTag("detail-hero-7").assertDoesNotExist()
        assertTrue(dominantPixels("blue") > 1000)
    }

    @Test fun `cached thumbnail is drawn while higher resolution image loads`() {
        compose.setContent {
            MovieTheme {
                Surface { MovieImage("https://image.tmdb.org/t/p/w780/fixture-poster-7.png", "Fixture artwork", Modifier.size(120.dp, 180.dp), memoryCacheKey = movieArtworkKey(7, MovieArtworkOrigin.Poster)) }
            }
        }
        assertTrue(dominantPixels("blue") > 1000)
        imageGate.complete(Unit)
        compose.waitUntil(5000) { dominantPixels("green") > 1000 }
        assertEquals(0, dominantPixels("blue"))
    }

    @Test fun `reduced motion preserves immediate preview and back navigation`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MovieTheme { MovieApp(ArtworkRepository(), motionEnabled = false) } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithTag("movie-7").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.onNodeWithTag("detail-hero-7").assertIsDisplayed()
        compose.onNodeWithTag("catalog-grid").assertDoesNotExist()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.onNodeWithTag("movie-7").assertIsDisplayed()
        compose.onNodeWithTag("detail-list").assertDoesNotExist()
    }

    @Test fun `repeated taps during navigation cannot replace the selected artwork`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { MovieTheme { MovieApp(ArtworkRepository()) } }
        compose.mainClock.advanceTimeBy(64)
        compose.waitForIdle()
        compose.onNodeWithTag("movie-7").performClick()
        compose.onNodeWithTag("movie-8").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("detail-hero-7").assertIsDisplayed()
        compose.onNodeWithTag("detail-hero-8").assertDoesNotExist()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithTag("movie-8").assertIsDisplayed()
    }

    @Test fun `back returns to the same card after scrolling far down the catalog`() {
        compose.setContent { MovieTheme { MovieApp(ArtworkRepository()) } }
        compose.onNodeWithTag("catalog-grid").performScrollToNode(hasTestTag("movie-24"))
        val before = compose.onNodeWithTag("movie-24").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("movie-24").performClick()
        compose.onNodeWithTag("detail-hero-24").assertIsDisplayed()
        compose.onNodeWithContentDescription("Kembali").performClick()
        compose.onNodeWithTag("movie-24").assertIsDisplayed()
        val after = compose.onNodeWithTag("movie-24").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top, after.top, 1f)
        assertEquals(before.left, after.left, 1f)
    }

    @Test fun `open and back share the screen center for different viewport sizes`() {
        for (viewport in listOf(IntSize(320, 640), IntSize(430, 932), IntSize(800, 1000))) {
            val card = Rect(16f, 200f, 160f, 416f)
            val hero = Rect(0f, 0f, viewport.width.toFloat(), viewport.width / .9f)
            val opening = centeredArtworkBounds(card, hero, viewport)
            val returning = centeredArtworkBounds(hero, card, viewport)
            assertEquals(viewport.width / 2f, opening.center.x, 0f)
            assertEquals(viewport.height / 2f, opening.center.y, 0f)
            assertEquals(opening, returning)
            assertTrue(opening.width in card.width..hero.width)
        }
    }

    @Test fun `subject silhouette and film title travel on open and back without changing resting artwork`() {
        compose.mainClock.autoAdvance = false
        val expanded = mutableStateOf(false)
        val mask = subjectMaskFromConfidence(16, 24, FloatArray(16 * 24) { if (it % 16 in 6..9) 1f else 0f })!!
        compose.setContent {
            SharedTransitionLayout(Modifier.fillMaxSize().background(Color.Black)) {
                AnimatedContent(expanded.value, transitionSpec = { fadeIn(tween(420)) togetherWith fadeOut(tween(420)) }, label = "Masked film") { detail ->
                    val transition = MovieArtworkTransition(this@SharedTransitionLayout, this@AnimatedContent, IntSize(430, 932), mapOf("${movieArtworkKey(999, MovieArtworkOrigin.Poster)}/art" to mask))
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.offset(if (detail) 0.dp else 16.dp, if (detail) 0.dp else 500.dp)
                            .movieArtworkTransition(999, MovieArtworkOrigin.Poster, transition)
                            .size(if (detail) 320.dp else 100.dp, if (detail) 480.dp else 150.dp)
                            .movieArtworkMask(999, MovieArtworkOrigin.Poster, transition).background(Color.Red))
                        Text("Any movie title", Modifier.offset(16.dp, if (detail) 500.dp else 660.dp)
                            .movieTitleTransition(999, MovieArtworkOrigin.Poster, transition),
                            color = Color.White, fontSize = if (detail) 24.sp else 14.sp)
                    }
                }
            }
        }
        compose.mainClock.advanceTimeByFrame()
        val initial = redBounds()
        for (detail in listOf(true, false)) {
            compose.runOnIdle { expanded.value = detail }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            var narrowest = 1f
            var lowestTitle = 0
            repeat(28) {
                compose.mainClock.advanceTimeByFrame()
                compose.waitForIdle()
                val bounds = redBounds()
                val ratio = bounds.width / bounds.height
                if (ratio < narrowest) {
                    narrowest = ratio
                    screenshot(if (detail) "subject-open" else "subject-back")
                }
                lowestTitle = maxOf(lowestTitle, firstWhiteRow())
            }
            assertTrue("The center subject must separate from its rectangular background in both directions: $narrowest", narrowest < .35f)
            assertTrue("The film title must travel through the lower center in both directions: $lowestTitle", lowestTitle > 680)
            compose.mainClock.advanceTimeBy(600)
            assertEquals(2f / 3f, redBounds().width / redBounds().height, .02f)
        }
        assertEquals(initial, redBounds())
    }

    private fun firstWhiteRow(): Int {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val row = IntArray(image.width)
        for (y in 0 until image.height) {
            image.getPixels(row, 0, image.width, 0, y, image.width, 1)
            if (row.any { android.graphics.Color.red(it) > 150 && android.graphics.Color.green(it) > 150 && android.graphics.Color.blue(it) > 150 }) return y
        }
        return 0
    }

    private fun bitmap(color: Int) = Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }

    private fun dominantPixels(channel: String): Int {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return pixels.count {
            val r = android.graphics.Color.red(it); val g = android.graphics.Color.green(it); val b = android.graphics.Color.blue(it)
            when (channel) { "red" -> r > 100 && r > g * 2 && r > b * 2; "blue" -> b > 100 && b > r * 2 && b > g * 2; else -> g > 100 && g > r * 2 && g > b * 2 }
        }
    }

    private fun screenshot(name: String) {
        val file = File("build/reports/ui/$name-fixture.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun firstRedRow(): Int {
        return redBounds().top.toInt()
    }

    private fun assertCenteredArtwork(scope: SharedTransitionScope) {
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        var nearest = Float.POSITIVE_INFINITY
        // Sample the remaining finite transition instead of assuming Android draws on a specific Compose clock tick.
        repeat((MovieArtworkTransition.DurationMillis + 15) / 16 + 2) {
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            val center = redBounds().center
            val distance = maxOf(kotlin.math.abs(root.center.x - center.x), kotlin.math.abs(root.center.y - center.y))
            nearest = minOf(nearest, distance)
            if (distance <= 8f) return
            if (!scope.isTransitionActive) fail("Animation ended without reaching the screen center; nearest distance: $nearest px")
        }
        fail("Artwork must pass through the screen center in both axes; nearest rendered distance: $nearest px")
    }

    private fun redBounds(): Rect {
        val image = compose.onRoot().captureToImage().asAndroidBitmap()
        val row = IntArray(image.width)
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            image.getPixels(row, 0, image.width, 0, y, image.width, 1)
            for (x in row.indices) {
                val pixel = row[x]
                if (android.graphics.Color.red(pixel) > 100 && android.graphics.Color.green(pixel) < 50 && android.graphics.Color.blue(pixel) < 50) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x); bottom = maxOf(bottom, y)
                }
            }
        }
        check(right >= left && bottom >= top) { "Artwork color must be present" }
        return Rect(left.toFloat(), top.toFloat(), (right + 1).toFloat(), (bottom + 1).toFloat())
    }
}

private class ArtworkRepository : MovieRepository {
    val detailGate = CompletableDeferred<Unit>()
    private val movies = (7..30).map { Movie(it, "Fixture film $it", "/fixture-poster-$it.png", "", 0.0, 0, "/fixture-backdrop-$it.png") }
    override suspend fun genres() = listOf(Genre(28, "Action"))
    override suspend fun movies(genreId: Int, page: Int) = Page(movies, page, 1)
    override suspend fun detail(movieId: Int): MovieDetail {
        if (!detailGate.isCompleted) detailGate.await()
        return MovieDetail(movies.first { it.id == movieId }, "Fixture overview $movieId", emptyList(), 120)
    }
    override suspend fun search(query: String, page: Int) = Page(movies, page, 1)
    override suspend fun reviews(movieId: Int, page: Int) = Page(emptyList<Review>(), page, 1)
    override suspend fun trailer(movieId: Int): Trailer? = null
}
