package com.assessment.movies

import android.app.Activity
import android.webkit.RenderProcessGoneDetail
import com.assessment.movies.ui.components.TrailerPlayerSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrailerPlayerSessionTest {
    @Test fun `player loads only after attach and layout then releases on close`() {
        val controller = Robolectric.buildActivity(Activity::class.java).create().start().resume()
        val activity = controller.get()
        val session = TrailerPlayerSession("abcdefghijk") {}
        val browser = session.create(activity) {}
        val shadow = shadowOf(browser)
        assertNull(shadow.lastLoadDataWithBaseURL)
        // Robolectric's empty WebView delegate does not apply layout bounds.
        browser.right = 640
        browser.bottom = 360
        activity.setContentView(browser)
        controller.visible()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue("The native view must be attached before loading", browser.isAttachedToWindow)
        assertEquals(640, browser.width)
        assertEquals(360, browser.height)
        assertNotNull(shadow.lastLoadDataWithBaseURL)
        session.background()
        assertEquals("window.moviePlayer?.pauseVideo()", shadow.lastEvaluatedJavascript)
        session.foreground()
        session.release(browser)
        assertTrue(shadow.wasDestroyCalled())
        var calledAfterRelease = false
        session.evaluate("document.title") { calledAfterRelease = true }
        assertFalse(calledAfterRelease)
        controller.pause().stop().destroy()
    }

    @Test fun `renderer termination reports an error and destroys the dead player`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val errors = mutableListOf<Int>()
        val session = TrailerPlayerSession("abcdefghijk", errors::add)
        val browser = session.create(controller.get()) {}
        @Suppress("DEPRECATION")
        val detail = object : RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        }
        val handled = browser.webViewClient.onRenderProcessGone(browser, detail)
        assertTrue(handled)
        assertEquals(listOf(-2), errors)
        assertTrue(shadowOf(browser).wasDestroyCalled())
        session.release(browser)
        controller.pause().stop().destroy()
    }
}
