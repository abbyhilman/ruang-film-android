package com.assessment.movies

import com.assessment.movies.data.Page
import com.assessment.movies.ui.PagedLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class PagedLoaderTest {
    @Test fun `merges pages removes duplicates and stops at last page`() = runTest {
        val requests = mutableListOf<Int>()
        val loader = PagedLoader<Int>({ it }) { page ->
            requests += page
            Page(if (page == 1) listOf(1, 2) else listOf(2, 3), page, 2)
        }
        repeat(3) { loader.loadNext() }
        assertEquals(listOf(1, 2, 3), loader.state.value.items)
        assertEquals(listOf(1, 2), requests)
        assertTrue(loader.state.value.endReached)
    }

    @Test fun `failed append keeps content and retries the same page`() = runTest {
        var fail = true
        val requests = mutableListOf<Int>()
        val loader = PagedLoader<Int>({ it }) { page ->
            requests += page
            if (page == 2 && fail) throw IOException()
            Page(listOf(page), page, 2)
        }
        loader.loadNext()
        loader.loadNext()
        assertEquals(listOf(1), loader.state.value.items)
        assertEquals(R.string.error_network, loader.state.value.error)
        assertFalse(loader.state.value.loading)
        fail = false
        loader.loadNext()
        assertEquals(listOf(1, 2), loader.state.value.items)
        assertEquals(listOf(1, 2, 2), requests)
        assertNull(loader.state.value.error)
    }

    @Test fun `empty result stops subsequent requests`() = runTest {
        var calls = 0
        val loader = PagedLoader<Int>({ it }) { page -> calls++; Page(emptyList(), page, 10) }
        repeat(3) { loader.loadNext() }
        assertEquals(1, calls)
        assertTrue(loader.state.value.loaded)
        assertTrue(loader.state.value.endReached)
    }

    @Test fun `concurrent scrolling does not send duplicate requests`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val loader = PagedLoader<Int>({ it }) { page ->
            calls++
            gate.await()
            Page(listOf(1), page, 2)
        }
        val first = launch { loader.loadNext() }
        runCurrent()
        val second = launch { loader.loadNext() }
        runCurrent()
        assertEquals(1, calls)
        assertTrue(loader.state.value.loading)
        gate.complete(Unit)
        first.join(); second.join()
        assertEquals(listOf(1), loader.state.value.items)
    }

    @Test fun `cancellation leaves the page available for retry`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val loader = PagedLoader<Int>({ it }) { page ->
            calls++
            if (calls == 1) gate.await()
            Page(listOf(1), page, 1)
        }
        val request = launch { loader.loadNext() }
        runCurrent()
        request.cancelAndJoin()
        assertFalse(loader.state.value.loading)
        assertNull(loader.state.value.error)
        loader.loadNext()
        assertEquals(listOf(1), loader.state.value.items)
    }

    @Test fun `never requests a page beyond TMDB limit`() = runTest {
        val requests = mutableListOf<Int>()
        val loader = PagedLoader<Int>({ it }) { page -> requests += page; Page(listOf(page), page, 1000) }
        repeat(502) { loader.loadNext() }
        assertEquals(500, requests.size)
        assertTrue(loader.state.value.endReached)
    }

    @Test fun `unexpected page is an error and does not corrupt content`() = runTest {
        val loader = PagedLoader<Int>({ it }) { Page(listOf(9), 8, 10) }
        loader.loadNext()
        assertTrue(loader.state.value.items.isEmpty())
        assertEquals(R.string.error_generic, loader.state.value.error)
    }
}
