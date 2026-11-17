package com.assessment.movies.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.assessment.movies.data.Genre
import com.assessment.movies.data.Movie
import com.assessment.movies.data.MovieDetail
import com.assessment.movies.data.MovieRepository
import com.assessment.movies.data.Review
import com.assessment.movies.data.Trailer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatalogViewModel(private val repository: MovieRepository) : ViewModel() {
    private val mutableGenres = MutableStateFlow(LoadState<List<Genre>>())
    val genres = mutableGenres.asStateFlow()
    private val mutableMovies = MutableStateFlow(PagedState<Movie>(loading = true))
    val movies = mutableMovies.asStateFlow()
    private val mutableQuery = MutableStateFlow("")
    val query = mutableQuery.asStateFlow()
    private val mutableGenreId = MutableStateFlow<Int?>(null)
    val genreId = mutableGenreId.asStateFlow()
    private var lastGenreId = 28
    private var genresJob: Job? = null
    private var pageJob: Job? = null
    private var collectJob: Job? = null
    private var searchJob: Job? = null
    private var loader: PagedLoader<Movie>? = null

    init { reloadGenres() }

    fun reloadGenres() {
        if (genresJob?.isActive == true) return
        genresJob = viewModelScope.launch {
            mutableGenres.value = LoadState()
            val result = load { repository.genres() }
            mutableGenres.value = result
            if (loader == null && mutableQuery.value.isBlank()) {
                val first = result.data?.firstOrNull { it.id == lastGenreId } ?: result.data?.firstOrNull()
                if (first != null) selectGenre(first.id)
                else mutableMovies.value = PagedState(loaded = true, endReached = true)
            }
        }
    }

    fun selectGenre(id: Int) {
        require(id > 0)
        searchJob?.cancel()
        mutableQuery.value = ""
        mutableGenreId.value = id
        lastGenreId = id
        replaceLoader { page -> repository.movies(id, page) }
    }

    fun search(text: String) {
        if (text == mutableQuery.value) return
        mutableQuery.value = text
        searchJob?.cancel()
        pageJob?.cancel()
        collectJob?.cancel()
        if (text.isBlank()) {
            selectGenre(lastGenreId)
            return
        }
        mutableGenreId.value = null
        mutableMovies.value = PagedState(loading = true)
        searchJob = viewModelScope.launch {
            delay(350)
            replaceLoader { page -> repository.search(text.trim(), page) }
        }
    }

    private fun replaceLoader(fetch: suspend (Int) -> com.assessment.movies.data.Page<Movie>) {
        pageJob?.cancel()
        collectJob?.cancel()
        val next = PagedLoader(Movie::id, fetch)
        loader = next
        mutableMovies.value = PagedState(loading = true)
        collectJob = viewModelScope.launch { next.state.collect { mutableMovies.value = it } }
        loadNext()
    }

    fun loadNext() {
        val current = loader ?: return
        if (pageJob?.isActive == true) return
        pageJob = viewModelScope.launch { current.loadNext() }
    }
}

class ReviewsViewModel(repository: MovieRepository, movieId: Int) : ViewModel() {
    private val loader = PagedLoader(Review::id) { repository.reviews(movieId, it) }
    val state = loader.state
    init { loadNext() }
    fun loadNext() { viewModelScope.launch { loader.loadNext() } }
}

class DetailViewModel(private val repository: MovieRepository, private val movieId: Int) : ViewModel() {
    private val mutableDetail = MutableStateFlow(LoadState<MovieDetail>())
    val detail = mutableDetail.asStateFlow()
    private val mutableTrailer = MutableStateFlow(LoadState<Trailer>())
    val trailer = mutableTrailer.asStateFlow()
    private var detailJob: Job? = null
    private var trailerJob: Job? = null

    init {
        reloadDetail()
        reloadTrailer()
    }

    fun reloadDetail() {
        if (detailJob?.isActive == true) return
        detailJob = viewModelScope.launch {
            mutableDetail.value = LoadState()
            mutableDetail.value = load { repository.detail(movieId) }
        }
    }

    fun reloadTrailer() {
        if (trailerJob?.isActive == true) return
        trailerJob = viewModelScope.launch {
            mutableTrailer.value = LoadState()
            mutableTrailer.value = load { repository.trailer(movieId) }
        }
    }
}

private suspend fun <T> load(fetch: suspend () -> T?): LoadState<T> = try {
    LoadState(loading = false, data = fetch())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    LoadState(loading = false, error = errorMessage(error))
}

inline fun <reified T : ViewModel> movieViewModelFactory(crossinline create: () -> T) = viewModelFactory {
    initializer { create() }
}
