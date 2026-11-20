package com.assessment.movies

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.IntSize
import com.assessment.movies.data.Movie
import com.assessment.movies.ui.components.MovieArtworkOrigin
import com.assessment.movies.ui.components.centeredArtworkBounds
import com.assessment.movies.ui.components.croppedSubjectFocus
import com.assessment.movies.ui.components.movieMaskKey
import com.assessment.movies.ui.components.subjectMaskFromConfidence
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtworkSubjectMaskTest {
    @Test fun `confidence matte preserves subject and softly removes only the background`() {
        val mask = subjectMaskFromConfidence(6, 1, floatArrayOf(0f, .7f, 1f, 1f, 1f, 0f))!!
        val pixels = mask.inverseAlpha.asAndroidBitmap()
        assertEquals(255, android.graphics.Color.alpha(pixels.getPixel(0, 0)))
        assertEquals(255, android.graphics.Color.alpha(pixels.getPixel(1, 0)))
        assertTrue(android.graphics.Color.alpha(pixels.getPixel(2, 0)) in 127..128)
        assertEquals(0, android.graphics.Color.alpha(pixels.getPixel(3, 0)))
        assertEquals(255, android.graphics.Color.alpha(pixels.getPixel(4, 0)))
        assertEquals(.5277778f, mask.focus.x, .0001f)
        assertEquals(.5f, mask.focus.y, 0f)
    }

    @Test fun `low confidence background cannot leave a translucent poster rectangle`() {
        val mask = subjectMaskFromConfidence(6, 1, floatArrayOf(.2f, .4f, .5f, .55f, 1f, .9f))!!
        val pixels = mask.inverseAlpha.asAndroidBitmap()
        for (x in 0..3) assertEquals("Low confidence must be fully removed", 255, android.graphics.Color.alpha(pixels.getPixel(x, 0)))
        assertEquals(0, android.graphics.Color.alpha(pixels.getPixel(5, 0)))
    }

    @Test fun `ambiguous empty full or corrupt detections fall back to the whole image`() {
        assertNull(subjectMaskFromConfidence(10, 10, FloatArray(100)))
        assertNull(subjectMaskFromConfidence(10, 10, FloatArray(100) { 1f }))
        assertNull(subjectMaskFromConfidence(2, 2, floatArrayOf(0f, 1f, Float.NaN, 0f)))
        assertNull(subjectMaskFromConfidence(2, 2, floatArrayOf(1f)))
        assertNull(subjectMaskFromConfidence(0, 1, floatArrayOf()))
    }

    @Test fun `each movie image and origin gets its own mask regardless of film name`() {
        for (id in listOf(7, 8, 101, 909)) {
            val movie = Movie(id, "Unrelated film $id", "/poster-$id.jpg", "", 0.0, 0, "/backdrop-$id.jpg")
            assertNotEquals(movieMaskKey(movie, MovieArtworkOrigin.Poster), movieMaskKey(movie, MovieArtworkOrigin.Featured))
            assertNotEquals(movieMaskKey(movie, MovieArtworkOrigin.Poster), movieMaskKey(movie.copy(posterPath = "/new.jpg"), MovieArtworkOrigin.Poster))
            assertEquals(movieMaskKey(movie, MovieArtworkOrigin.Poster), movieMaskKey(movie.copy(title = "Translated title"), MovieArtworkOrigin.Poster))
        }
    }

    @Test fun `subject focal point reaches screen center after crop on phones and tablets`() {
        val mask = subjectMaskFromConfidence(8, 8, FloatArray(64) { if (it % 8 in 1..4) 1f else 0f })!!
        for (viewport in listOf(IntSize(320, 640), IntSize(430, 932), IntSize(800, 1000))) {
            val card = Rect(16f, 200f, 160f, 416f)
            val hero = Rect(0f, 0f, viewport.width.toFloat(), viewport.width / .9f)
            val focus = croppedSubjectFocus(mask, hero.width, hero.height)
            assertTrue(focus.x < .5f)
            val mid = centeredArtworkBounds(card, hero, viewport, focus)
            assertEquals(viewport.width / 2f, mid.left + mid.width * focus.x, .001f)
            assertEquals(viewport.height / 2f, mid.top + mid.height * focus.y, .001f)
        }
        assertEquals(Offset(.5f, .5f), croppedSubjectFocus(mask, 0f, 0f))
    }
}
