package com.assessment.movies.ui

import com.assessment.movies.R
import com.assessment.movies.data.MissingTokenException
import com.assessment.movies.data.Page
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

data class LoadState<T>(val loading: Boolean = true, val data: T? = null, val error: Int? = null)

data class PagedState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val error: Int? = null,
    val endReached: Boolean = false,
    val loaded: Boolean = false,
)

class PagedLoader<T>(private val key: (T) -> Any, private val fetch: suspend (Int) -> Page<T>) {
    private val mutableState = MutableStateFlow(PagedState<T>())
    val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var nextPage = 1

    suspend fun loadNext() {
        if (!mutex.tryLock()) return
        try {
            if (mutableState.value.endReached) return
            mutableState.value = mutableState.value.copy(loading = true, error = null)
            val page = fetch(nextPage)
            check(page.page == nextPage) { "Unexpected page" }
            val items = (mutableState.value.items + page.items).distinctBy(key)
            mutableState.value = PagedState(
                items = items,
                endReached = page.items.isEmpty() || nextPage >= minOf(page.totalPages, 500),
                loaded = true,
            )
            nextPage++
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = mutableState.value.copy(error = errorMessage(error))
        } finally {
            mutableState.value = mutableState.value.copy(loading = false)
            mutex.unlock()
        }
    }
}

internal fun errorMessage(error: Exception): Int = when (error) {
    is MissingTokenException -> R.string.error_setup
    is SocketTimeoutException -> R.string.error_timeout
    is HttpException -> when (error.code()) {
        401, 403 -> R.string.error_auth
        404 -> R.string.error_not_found
        429 -> R.string.error_rate_limit
        in 500..599 -> R.string.error_server
        else -> R.string.error_generic
    }
    is IOException -> R.string.error_network
    else -> R.string.error_generic
}
