package com.assessment.movies

import com.assessment.movies.ui.components.formatPlaybackTime
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
        assertTrue(html.contains("allow=\"autoplay;"))
        assertTrue(html.contains("strict-origin-when-cross-origin"))
        assertTrue(html.contains("getCurrentTime()"))
        assertTrue(html.contains("getDuration()"))
    }
    @Test fun `time indicator uses elapsed minutes and hours`() {
        assertEquals("0:00", formatPlaybackTime(0.0))
        assertEquals("2:05", formatPlaybackTime(125.0))
        assertEquals("1:02:03", formatPlaybackTime(3723.0))
        assertEquals("0:00", formatPlaybackTime(Double.NaN))
    }
    @Test fun `native state parses WebView quoted JSON`() {
        val raw = "\"{\\\"ready\\\":true,\\\"playing\\\":true,\\\"position\\\":12.5,\\\"duration\\\":120,\\\"error\\\":0}\""
        val state = parsePlaybackState(raw)!!
        assertTrue(state.ready)
        assertTrue(state.playing)
        assertEquals(12.5, state.position, 0.0)
        assertEquals(120.0, state.duration, 0.0)
    }
    @Test fun `malformed and absent player responses do not fabricate progress`() {
        assertNull(parsePlaybackState(null))
        assertNull(parsePlaybackState("null"))
        assertNull(parsePlaybackState("not json"))
        assertEquals(0.0, parsePlaybackState("{\"position\":-5}")!!.position, 0.0)
    }
}
