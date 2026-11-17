package com.assessment.movies

import com.assessment.movies.data.MissingTokenException
import com.assessment.movies.data.TmdbRepository
import com.assessment.movies.data.createTmdbApi
import com.assessment.movies.ui.errorMessage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.net.SocketTimeoutException

class TmdbRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: TmdbRepository

    @Before fun setup() {
        server = MockWebServer()
        server.start()
        repository = TmdbRepository(createTmdbApi("fixture-token", server.url("/3/").toString()))
    }
    @After fun teardown() { server.shutdown() }
    private fun respond(body: String, status: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(status).setBody(body).addHeader("Content-Type", "application/json"))
    }

    @Test fun `genre screen receives valid unique official genres`() = runTest {
        respond("""{"genres":[{"id":28,"name":"Action"},{"id":28,"name":"Action"},{"id":null,"name":"Invalid"},{"id":2,"name":null}]}""")
        val genres = repository.genres()
        assertEquals(listOf(28), genres.map { it.id })
        val request = server.takeRequest()
        assertEquals("/3/genre/movie/list", request.path)
        assertEquals("Bearer fixture-token", request.getHeader("Authorization"))
    }

    @Test fun `discover sends genre and page and maps nullable metadata`() = runTest {
        respond("""{"page":2,"total_pages":3,"results":[{"id":7,"title":"Fixture film","poster_path":null,"release_date":null,"vote_average":null}]}""")
        val page = repository.movies(28, 2)
        assertEquals(2, page.page)
        assertEquals(3, page.totalPages)
        assertEquals("", page.items.single().releaseDate)
        assertNull(page.items.single().posterPath)
        val url = server.takeRequest().requestUrl!!
        assertEquals("28", url.queryParameter("with_genres"))
        assertEquals("2", url.queryParameter("page"))
    }

    @Test fun `detail maps primary information without requiring optional values`() = runTest {
        respond("""{"id":7,"title":"Fixture film","overview":null,"genres":null,"runtime":0,"imdb_id":"tt0000001","credits":{"cast":[{"id":1,"name":"Fixture actor","profile_path":null,"character":"Fixture role"}]}}""")
        val detail = repository.detail(7)
        assertEquals("Fixture film", detail.movie.title)
        assertEquals("", detail.overview)
        assertTrue(detail.genres.isEmpty())
        assertNull(detail.runtime)
        assertEquals("tt0000001", detail.imdbId)
        assertEquals("Fixture actor", detail.cast.single().name)
        assertEquals("/3/movie/7?append_to_response=credits", server.takeRequest().path)
    }

    @Test fun `reviews use requested film and page`() = runTest {
        respond("""{"page":1,"total_pages":2,"results":[{"id":"review-1","author":"Fixture author","content":"Fixture review","author_details":{"rating":8}}]}""")
        val page = repository.reviews(7, 1)
        assertEquals(8.0, page.items.single().rating!!, 0.0)
        assertEquals("/3/movie/7/reviews?page=1", server.takeRequest().path)
    }

    @Test fun `trailer prefers official YouTube trailer and ignores other sites and invalid keys`() = runTest {
        respond("""{"results":[
          {"site":"YouTube","type":"Trailer","key":"abcdefghijk","official":false,"name":"Unofficial fixture"},
          {"site":"Vimeo","type":"Trailer","key":"12345678901","official":true},
          {"site":"YouTube","type":"Teaser","key":"12345678901","official":true},
          {"site":"YouTube","type":"Trailer","key":"bad<script>","official":true},
          {"site":"YouTube","type":"Trailer","key":"12345678901","official":true,"name":"Official fixture"}
        ]}""")
        assertEquals("12345678901", repository.trailer(7)?.key)
        assertEquals("/3/movie/7/videos", server.takeRequest().path)
    }

    @Test fun `movie with no trailers has an honest empty state`() = runTest {
        respond("""{"results":[]}""")
        assertNull(repository.trailer(7))
    }

    @Test fun `unauthorized response remains an actionable error`() = runTest {
        respond("{}", 401)
        try {
            repository.genres()
            fail("Expected HTTP failure")
        } catch (error: HttpException) {
            assertEquals(R.string.error_auth, errorMessage(error))
        }
    }

    @Test fun `missing access token fails before any request`() = runTest {
        val missingToken = TmdbRepository(createTmdbApi("", server.url("/").toString()))
        try {
            missingToken.genres()
            fail("Expected configuration failure")
        } catch (error: MissingTokenException) {
            assertEquals(R.string.error_setup, errorMessage(error))
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `invalid page cannot be sent to the API`() = runTest {
        try {
            repository.movies(28, 501)
            fail("Expected invalid page failure")
        } catch (_: IllegalArgumentException) {
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun `timeout is reported distinctly`() {
        assertEquals(R.string.error_timeout, errorMessage(SocketTimeoutException()))
    }

    @Test fun `search encodes query and paginates results`() = runTest {
        respond("""{"page":2,"total_pages":3,"results":[{"id":9,"title":"Fixture result"}]}""")
        val result = repository.search(" science fiction ", 2)
        assertEquals(9, result.items.single().id)
        val request = server.takeRequest().requestUrl!!
        assertEquals("science fiction", request.queryParameter("query"))
        assertEquals("2", request.queryParameter("page"))
        assertEquals("/3/search/movie", request.encodedPath)
    }
}
