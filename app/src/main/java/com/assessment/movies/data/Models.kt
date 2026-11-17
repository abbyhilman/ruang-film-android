package com.assessment.movies.data

data class Genre(val id: Int, val name: String)

data class Movie(
    val id: Int,
    val title: String,
    val posterPath: String?,
    val releaseDate: String,
    val rating: Double,
    val voteCount: Int,
    val backdropPath: String? = null,
)

data class MovieDetail(
    val movie: Movie,
    val overview: String,
    val genres: List<Genre>,
    val runtime: Int?,
    val cast: List<CastMember> = emptyList(),
    val imdbId: String? = null,
)

data class CastMember(val id: Int, val name: String, val character: String, val profilePath: String?)

data class Review(val id: String, val author: String, val content: String, val rating: Double?)
data class Trailer(val key: String, val title: String)
data class Page<T>(val items: List<T>, val page: Int, val totalPages: Int)

interface MovieRepository {
    suspend fun genres(): List<Genre>
    suspend fun movies(genreId: Int, page: Int): Page<Movie>
    suspend fun search(query: String, page: Int): Page<Movie>
    suspend fun detail(movieId: Int): MovieDetail
    suspend fun reviews(movieId: Int, page: Int): Page<Review>
    suspend fun trailer(movieId: Int): Trailer?
}
