package com.assessment.movies.data

import java.io.IOException

class MissingTokenException : IOException("TMDB access is not configured")

internal class TmdbRepository(private val api: TmdbApi) : MovieRepository {
    override suspend fun genres(): List<Genre> = api.genres().genres.orEmpty()
        .mapNotNull { it.toModel() }.filter { it.name.isNotBlank() }.distinctBy { it.id }

    override suspend fun movies(genreId: Int, page: Int): Page<Movie> {
        require(genreId > 0 && page in 1..500)
        val response = api.movies(genreId, page)
        return Page(response.results.orEmpty().mapNotNull { it.toModel() }, response.page ?: page, response.totalPages ?: 0)
    }

    override suspend fun detail(movieId: Int): MovieDetail {
        require(movieId > 0)
        val dto = api.detail(movieId)
        val id = dto.id?.takeIf { it == movieId } ?: throw IOException("Unexpected movie response")
        return MovieDetail(
            Movie(id, dto.title.orEmpty(), dto.posterPath, dto.releaseDate.orEmpty(), dto.rating ?: 0.0, dto.voteCount ?: 0),
            dto.overview.orEmpty(),
            dto.genres.orEmpty().mapNotNull { it.toModel() },
            dto.runtime?.takeIf { it > 0 },
        )
    }

    override suspend fun reviews(movieId: Int, page: Int): Page<Review> {
        require(movieId > 0 && page in 1..500)
        val response = api.reviews(movieId, page)
        val reviews = response.results.orEmpty().mapNotNull { dto ->
            dto.id?.takeIf { it.isNotBlank() }?.let {
                Review(it, dto.author.orEmpty(), dto.content.orEmpty(), dto.authorDetails?.rating)
            }
        }
        return Page(reviews, response.page ?: page, response.totalPages ?: 0)
    }

    override suspend fun trailer(movieId: Int): Trailer? {
        require(movieId > 0)
        return api.videos(movieId).results.orEmpty()
            .filter { it.site == "YouTube" && it.type == "Trailer" && it.key?.matches(YOUTUBE_KEY) == true }
            .sortedByDescending { it.official == true }
            .firstOrNull()?.let { Trailer(it.key!!, it.name.orEmpty()) }
    }

    private companion object {
        val YOUTUBE_KEY = Regex("[A-Za-z0-9_-]{11}")
    }
}
