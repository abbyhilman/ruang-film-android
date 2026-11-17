package com.assessment.movies

import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.data.MovieRepository
import com.assessment.movies.data.Page
import com.assessment.movies.data.Review
import com.assessment.movies.data.Trailer
import com.assessment.movies.ui.DetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `trailer failure does not hide movie information and can retry independently`() = runTest(dispatcher) {
        val repository = DetailFixture()
        val model = DetailViewModel(repository, 7)
        advanceUntilIdle()
        assertEquals("Fixture film", model.detail.value.data?.movie?.title)
        assertEquals(R.string.error_network, model.trailer.value.error)
        repository.failTrailer = false
        model.reloadTrailer()
        advanceUntilIdle()
        assertEquals("abcdefghijk", model.trailer.value.data?.key)
        assertEquals(1, repository.detailRequests)
        assertEquals(2, repository.trailerRequests)
    }

    @Test fun `rapid repeated retry requests are coalesced`() = runTest(dispatcher) {
        val repository = DetailFixture()
        val model = DetailViewModel(repository, 7)
        repeat(5) { model.reloadDetail(); model.reloadTrailer() }
        advanceUntilIdle()
        assertEquals(1, repository.detailRequests)
        assertEquals(1, repository.trailerRequests)
    }
}

private class DetailFixture : MovieRepository {
    var failTrailer = true
    var detailRequests = 0
    var trailerRequests = 0
    override suspend fun genres(): List<Genre> = emptyList()
    override suspend fun movies(genreId: Int, page: Int): Page<Movie> = Page(emptyList(), page, 0)
    override suspend fun reviews(movieId: Int, page: Int): Page<Review> = Page(emptyList(), page, 0)
    override suspend fun detail(movieId: Int): MovieDetail {
        detailRequests++
        return MovieDetail(Movie(movieId, "Fixture film", null, "", 0.0, 0), "", emptyList(), null)
    }
    override suspend fun trailer(movieId: Int): Trailer? {
        trailerRequests++
        if (failTrailer) throw IOException()
        return Trailer("abcdefghijk", "Fixture trailer")
    }
}
