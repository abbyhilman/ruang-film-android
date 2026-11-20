package com.assessment.movies

import com.assessment.movies.ui.components.parsePlaybackState
import com.assessment.movies.ui.components.trailerHtml
import org.junit.Assert.*
import org.junit.Test

class VideoPlaybackTest {
    @Test fun `video identifiers cannot inject markup`() {
        try { trailerHtml("\"><script>"); fail("Expected rejected identifier") } catch (_: IllegalArgumentException) { }
    }
    @Test fun `popup player requests autoplay with controls and valid origin`() {
        val html = trailerHtml("abcdefghijk")
        assertTrue(html.contains("https://www.youtube.com/embed/abcdefghijk?autoplay=1"))
        assertTrue(html.contains("controls=1"))
        assertTrue(html.contains("fs=1"))
        assertFalse(html.contains("fs=0"))
        assertTrue(html.contains("; fullscreen\" allowfullscreen"))
        assertTrue(html.contains("allow=\"autoplay;"))
        assertTrue(html.contains("strict-origin-when-cross-origin"))
        assertFalse(html.contains("getCurrentTime()"))
        assertFalse(html.contains("getDuration()"))
        assertFalse(html.contains("seekTo"))
    }
    @Test fun `YouTube playback errors remain available for retry`() {
        assertEquals(150, parsePlaybackState("{\"ready\":true,\"error\":150}")!!.error)
    }
    @Test fun `native state parses WebView quoted JSON`() {
        val raw = "\"{\\\"ready\\\":true,\\\"playing\\\":true,\\\"position\\\":12.5,\\\"duration\\\":120,\\\"error\\\":0}\""
        val state = parsePlaybackState(raw)!!
        assertTrue(state.ready)
        assertEquals(0, state.error)
    }
    @Test fun `malformed and absent player responses do not fabricate progress`() {
        assertNull(parsePlaybackState(null))
        assertNull(parsePlaybackState("null"))
        assertNull(parsePlaybackState("not json"))
        assertFalse(parsePlaybackState("{}")!!.ready)
    }
}
