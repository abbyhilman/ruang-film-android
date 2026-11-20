package com.assessment.movies

import android.app.Activity
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import com.assessment.movies.ui.components.TrailerPlayerSession
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TrailerPlayerSessionTest {
    @Suppress("DEPRECATION")
    @Test fun `native fullscreen opens above the popup and Back restores the same WebView`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val session = TrailerPlayerSession("abcdefghijk") {}
        val browser = session.create(activity) {}
        val inline = FrameLayout(activity).apply { addView(browser) }
        activity.setContentView(inline)
        val originalOrientation = activity.requestedOrientation
        var exited = 0
        val video = View(activity)
        browser.webChromeClient!!.onShowCustomView(video, WebChromeClient.CustomViewCallback { exited++ })
        val fullscreen = ShadowDialog.getLatestDialog()
        assertNotNull("The YouTube fullscreen callback must have a native host", fullscreen)
        assertTrue(fullscreen.isShowing)
        assertNotNull(video.parent)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, fullscreen.window!!.attributes.width)
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, fullscreen.window!!.attributes.height)
        assertTrue(fullscreen.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0)
        assertSame("The inline WebView stays attached and is not recreated for fullscreen", inline, browser.parent)
        fullscreen.onBackPressed()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertFalse(fullscreen.isShowing)
        assertNull(video.parent)
        assertEquals(1, exited)
        assertFalse(shadowOf(browser).wasDestroyCalled())
        assertEquals(originalOrientation, activity.requestedOrientation)
        browser.webChromeClient!!.onHideCustomView()
        assertEquals(1, exited)
        val nextVideo = View(activity)
        browser.webChromeClient!!.onShowCustomView(nextVideo, WebChromeClient.CustomViewCallback { exited++ })
        val nextFullscreen = ShadowDialog.getLatestDialog()
        assertTrue(nextFullscreen.isShowing)
        browser.webChromeClient!!.onHideCustomView()
        assertFalse(nextFullscreen.isShowing)
        assertNull(nextVideo.parent)
        assertEquals(2, exited)
        assertFalse(shadowOf(browser).wasDestroyCalled())
        session.release(browser)
        controller.pause().stop().destroy()
    }

    @Test fun `fullscreen rejects duplicate entry and releases exactly once when popup closes`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val session = TrailerPlayerSession("abcdefghijk") {}
        val browser = session.create(activity) {}
        var firstExited = 0
        var rejected = 0
        val video = View(activity)
        browser.webChromeClient!!.onShowCustomView(video, WebChromeClient.CustomViewCallback { firstExited++ })
        val fullscreen = ShadowDialog.getLatestDialog()
        browser.webChromeClient!!.onShowCustomView(View(activity), WebChromeClient.CustomViewCallback { rejected++ })
        assertEquals(1, rejected)
        assertTrue(fullscreen.isShowing)
        session.release(browser)
        assertFalse(fullscreen.isShowing)
        assertNull(video.parent)
        assertEquals(1, firstExited)
        session.release(browser)
        assertEquals(1, firstExited)
        assertTrue(shadowOf(browser).wasDestroyCalled())
        controller.pause().stop().destroy()
    }

    @Suppress("DEPRECATION")
    @Test fun `renderer death also closes fullscreen and returns its custom view`() {
        val controller = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = controller.get()
        val session = TrailerPlayerSession("abcdefghijk") {}
        val browser = session.create(activity) {}
        var exited = 0
        val video = View(activity)
        browser.webChromeClient!!.onShowCustomView(video, WebChromeClient.CustomViewCallback { exited++ })
        val fullscreen = ShadowDialog.getLatestDialog()
        browser.webViewClient.onRenderProcessGone(browser, object : RenderProcessGoneDetail() {
            override fun didCrash() = true
            override fun rendererPriorityAtExit() = 0
        })
        assertFalse(fullscreen.isShowing)
        assertEquals(1, exited)
        assertNull(video.parent)
        controller.pause().stop().destroy()
    }

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
