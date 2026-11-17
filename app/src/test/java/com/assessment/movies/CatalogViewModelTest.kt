package com.assessment.movies

import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.data.MovieRepository
import com.assessment.movies.data.Page
import com.assessment.movies.data.Review
import com.assessment.movies.data.Trailer
import com.assessment.movies.ui.CatalogViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun `official genres select Action and load its first page`() = runTest(dispatcher) {
        val repo = CatalogFixture()
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        assertEquals(28, model.genreId.value)
        assertEquals(listOf(28), model.movies.value.items.map { it.id })
        assertEquals(listOf(28 to 1), repo.movieCalls)
    }

    @Test fun `search debounces rapid typing and cancels old results`() = runTest(dispatcher) {
        val repo = CatalogFixture()
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        model.search("old")
        advanceTimeBy(350); runCurrent()
        model.search("n")
        advanceTimeBy(100)
        model.search("new")
        advanceTimeBy(350); runCurrent()
        repo.oldSearch.complete(Page(listOf(repo.movie(100)), 1, 1))
        advanceUntilIdle()
        assertEquals(listOf("old", "new"), repo.searchCalls)
        assertEquals("new", model.query.value)
        assertEquals(listOf(200), model.movies.value.items.map { it.id })
        assertNull(model.genreId.value)
    }

    @Test fun `changing genre cancels pagination from the old genre`() = runTest(dispatcher) {
        val repo = CatalogFixture()
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        model.loadNext(); runCurrent()
        model.selectGenre(35)
        repo.oldPage.complete(Page(listOf(repo.movie(999)), 2, 2))
        advanceUntilIdle()
        assertEquals(35, model.genreId.value)
        assertEquals(listOf(35), model.movies.value.items.map { it.id })
    }

    @Test fun `clearing search returns to the chosen genre`() = runTest(dispatcher) {
        val repo = CatalogFixture()
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        model.selectGenre(35); advanceUntilIdle()
        model.search("new"); advanceUntilIdle()
        model.search(""); advanceUntilIdle()
        assertEquals(35, model.genreId.value)
        assertEquals(listOf(35), model.movies.value.items.map { it.id })
        assertEquals("", model.query.value)
    }

    @Test fun `failed genre loading can retry without leaving skeleton stuck`() = runTest(dispatcher) {
        val repo = CatalogFixture().apply { failGenres = true }
        val model = CatalogViewModel(repo)
        advanceUntilIdle()
        assertEquals(R.string.error_network, model.genres.value.error)
        assertFalse(model.movies.value.loading)
        repo.failGenres = false
        model.reloadGenres(); advanceUntilIdle()
        assertEquals(28, model.movies.value.items.single().id)
        assertNull(model.genres.value.error)
    }
}

private class CatalogFixture : MovieRepository {
    var failGenres = false
    val movieCalls = mutableListOf<Pair<Int, Int>>()
    val searchCalls = mutableListOf<String>()
    val oldPage = CompletableDeferred<Page<Movie>>()
    val oldSearch = CompletableDeferred<Page<Movie>>()
    fun movie(id: Int) = Movie(id, "Fixture $id", null, "", 0.0, 0)
    override suspend fun genres(): List<Genre> {
        if (failGenres) throw IOException()
        return listOf(Genre(28, "Action"), Genre(35, "Comedy"))
    }
    override suspend fun movies(genreId: Int, page: Int): Page<Movie> {
        movieCalls += genreId to page
        if (genreId == 28 && page == 2) return oldPage.await()
        return Page(listOf(movie(genreId)), page, if (genreId == 28) 2 else 1)
    }
    override suspend fun search(query: String, page: Int): Page<Movie> {
        searchCalls += query
        return if (query == "old") oldSearch.await() else Page(listOf(movie(200)), page, 1)
    }
    override suspend fun detail(movieId: Int): MovieDetail = error("Not used")
    override suspend fun reviews(movieId: Int, page: Int): Page<Review> = error("Not used")
    override suspend fun trailer(movieId: Int): Trailer? = null
}
