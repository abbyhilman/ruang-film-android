package com.assessment.movies.data

import java.io.IOException

class MissingTokenException : IOException("TMDB access is not configured")

internal class TmdbRepository(private val api: TmdbApi) : MovieRepository {
    private val details = object : LinkedHashMap<Int, MovieDetail>(40, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, MovieDetail>?) = size > 40
    }
    override suspend fun genres(): List<Genre> = api.genres().genres.orEmpty()
        .mapNotNull { it.toModel() }.filter { it.name.isNotBlank() }.distinctBy { it.id }

    override suspend fun movies(genreId: Int, page: Int): Page<Movie> {
        require(genreId > 0 && page in 1..500)
        val response = api.movies(genreId, page)
        return Page(response.results.orEmpty().mapNotNull { it.toModel() }, response.page ?: page, response.totalPages ?: 0)
    }

    override suspend fun detail(movieId: Int): MovieDetail {
        require(movieId > 0)
        synchronized(details) { details[movieId] }?.let { return it }
        val dto = api.detail(movieId)
        val id = dto.id?.takeIf { it == movieId } ?: throw IOException("Unexpected movie response")
        val detail = MovieDetail(
            Movie(id, dto.title.orEmpty(), dto.posterPath, dto.releaseDate.orEmpty(), dto.rating ?: 0.0, dto.voteCount ?: 0, dto.backdropPath, dto.originalLanguage),
            dto.overview.orEmpty(),
            dto.genres.orEmpty().mapNotNull { it.toModel() },
            dto.runtime?.takeIf { it > 0 },
            dto.credits?.cast.orEmpty().mapNotNull { cast ->
                cast.id?.takeIf { it > 0 }?.let { CastMember(it, cast.name.orEmpty(), cast.character.orEmpty(), cast.profilePath) }
            }.distinctBy { it.id }.take(20),
            dto.imdbId?.takeIf { it.matches(Regex("tt[0-9]+")) },
        )
        synchronized(details) { details[movieId] = detail }
        return detail
    }

    override suspend fun search(query: String, page: Int): Page<Movie> {
        require(query.isNotBlank() && page in 1..500)
        val response = api.search(query.trim(), page)
        return Page(response.results.orEmpty().mapNotNull { it.toModel() }, response.page ?: page, response.totalPages ?: 0)
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
