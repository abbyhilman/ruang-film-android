package com.assessment.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.assessment.movies.ui.MovieTheme
import com.assessment.movies.ui.MovieStyle
import com.assessment.movies.ui.branding.BrandRevealFrame
import com.assessment.movies.ui.branding.RuangFilmBrand
import com.assessment.movies.ui.branding.StartupRevealHost
import com.assessment.movies.ui.branding.StartupRevealState
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.xmlpull.v1.XmlPullParser
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w430dp-h932dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrandStartupTest {
    @get:Rule val compose = createComposeRule()

    private fun pixels(): IntArray {
        val bitmap = compose.onNodeWithTag("stage").captureToImage().asAndroidBitmap()
        return IntArray(bitmap.width * bitmap.height).also { bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height) }
    }

    @Test fun `logo hole reveals changing live content and finally uncovers every pixel`() {
        val progress = mutableStateOf<Float?>(null)
        val background = mutableStateOf(Color.Red)
        compose.setContent {
            MovieTheme {
                Box(Modifier.fillMaxSize().testTag("stage")) {
                    Box(Modifier.fillMaxSize().background(background.value))
                    BrandRevealFrame(null, progress.value, Modifier.fillMaxSize())
                }
            }
        }
        assertFalse(pixels().any { it == android.graphics.Color.RED })
        compose.runOnIdle { progress.value = .25f }
        val cutout = pixels()
        assertTrue(cutout.any { it == android.graphics.Color.RED })
        assertTrue(cutout.any { it != android.graphics.Color.RED })
        compose.runOnIdle { background.value = Color.Green }
        assertTrue(pixels().any { it == android.graphics.Color.GREEN })
        assertFalse(pixels().any { it == android.graphics.Color.RED })
        compose.runOnIdle { progress.value = 1f }
        assertTrue(pixels().all { it == android.graphics.Color.GREEN })
    }

    @Test fun `handoff completes within reveal duration and restores Home interactions`() {
        compose.mainClock.autoAdvance = false
        val startup = StartupRevealState(true)
        var clicked = false
        compose.setContent {
            MovieTheme {
                StartupRevealHost(startup) {
                    Button(onClick = { clicked = true }) { Text("Fixture Home") }
                }
            }
        }
        compose.onNodeWithText("Fixture Home").assertDoesNotExist()
        compose.runOnIdle { startup.handoff(null, true) }
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("brand-reveal").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        compose.onNodeWithTag("brand-reveal").assertDoesNotExist()
        compose.onNodeWithText("Fixture Home").performClick()
        assertTrue(clicked)
    }

    @Test fun `reduced motion skips the expanding mask`() {
        val startup = StartupRevealState(true)
        compose.setContent { MovieTheme { StartupRevealHost(startup) { Text("Fixture Home") } } }
        compose.runOnIdle { startup.handoff(null, false) }
        compose.onNodeWithTag("brand-reveal").assertDoesNotExist()
        compose.onNodeWithText("Fixture Home").assertIsDisplayed()
    }

    @Test fun `late system callback cannot replay a stopped or restored launch`() {
        val stopped = StartupRevealState(true)
        stopped.finish()
        stopped.handoff(null, true)
        assertFalse(stopped.visible)
        val restored = StartupRevealState(false)
        restored.handoff(null, true)
        assertFalse(restored.visible)
    }

    @Test fun `native icons and animated mask use the approved path`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertEquals(MovieStyle.Gold.toArgb(), context.getColor(R.color.brand_gold))
        assertEquals(MovieStyle.Canvas.toArgb(), context.getColor(R.color.brand_canvas))
        val launcher = context.getDrawable(R.mipmap.ic_launcher) as android.graphics.drawable.AdaptiveIconDrawable
        if (android.os.Build.VERSION.SDK_INT >= 33) assertNotNull(launcher.monochrome)
        for (resource in listOf(R.drawable.brand_mark, R.drawable.ic_splash_logo, R.drawable.ic_launcher_foreground, R.drawable.ic_launcher_monochrome)) {
            context.resources.getXml(resource).use { parser ->
                var found = false
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "path") {
                        assertEquals(RuangFilmBrand.PathData, parser.getAttributeValue("http://schemas.android.com/apk/res/android", "pathData"))
                        found = true
                    }
                    parser.next()
                }
                assertTrue(found)
            }
        }
    }

    @Test
    @Config(sdk = [26])
    @GraphicsMode(GraphicsMode.Mode.LEGACY)
    fun `minimum Android version inflates both adaptive launcher icons`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        assertTrue(context.getDrawable(R.mipmap.ic_launcher) is android.graphics.drawable.AdaptiveIconDrawable)
        assertTrue(context.getDrawable(R.mipmap.ic_launcher_round) is android.graphics.drawable.AdaptiveIconDrawable)
    }

    @Test fun `startup fixture exports the logo and opening for visual inspection`() {
        val progress = mutableStateOf<Float?>(null)
        compose.setContent {
            MovieTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF47474F)).testTag("stage")) {
                    Text("Fixture Home: konten aplikasi di belakang mask", color = Color.White)
                    BrandRevealFrame(null, progress.value, Modifier.fillMaxSize())
                }
            }
        }
        for ((name, value) in listOf("initial" to null, "opening" to .25f, "expanded" to .6f, "complete" to 1f)) {
            compose.runOnIdle { progress.value = value }
            val image = compose.onNodeWithTag("stage").captureToImage().asAndroidBitmap()
            val file = File("build/reports/ui/brand-reveal-$name-fixture.png")
            file.parentFile!!.mkdirs()
            file.outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
