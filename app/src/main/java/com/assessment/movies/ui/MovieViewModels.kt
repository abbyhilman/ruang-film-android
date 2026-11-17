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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class GenresViewModel(private val repository: MovieRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(LoadState<List<Genre>>())
    val state = mutableState.asStateFlow()
    private var job: Job? = null

    init { reload() }

    fun reload() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            mutableState.value = LoadState()
            mutableState.value = load { repository.genres() }
        }
    }
}

class MoviesViewModel(repository: MovieRepository, genreId: Int) : ViewModel() {
    private val loader = PagedLoader(Movie::id) { repository.movies(genreId, it) }
    val state = loader.state
    init { loadNext() }
    fun loadNext() { viewModelScope.launch { loader.loadNext() } }
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
