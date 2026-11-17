package com.assessment.movies.data

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

internal interface TmdbApi {
    @GET("genre/movie/list")
    suspend fun genres(): GenreResponse

    @GET("discover/movie")
    suspend fun movies(@Query("with_genres") genreId: Int, @Query("page") page: Int): PageDto<MovieDto>

    @GET("movie/{id}")
    suspend fun detail(@Path("id") movieId: Int): MovieDetailDto

    @GET("movie/{id}/reviews")
    suspend fun reviews(@Path("id") movieId: Int, @Query("page") page: Int): PageDto<ReviewDto>

    @GET("movie/{id}/videos")
    suspend fun videos(@Path("id") movieId: Int): VideoResponse
}

internal fun createTmdbApi(token: String, baseUrl: String = "https://api.themoviedb.org/3/"): TmdbApi {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            if (token.isBlank()) throw MissingTokenException()
            val request = chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .build()
            chain.proceed(request)
        }
        .build()
    return Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)
}

internal data class GenreDto(val id: Int?, val name: String?) {
    fun toModel(): Genre? = id?.takeIf { it > 0 }?.let { Genre(it, name.orEmpty()) }
}
internal data class GenreResponse(val genres: List<GenreDto>?)
internal data class PageDto<T>(
    val results: List<T>?,
    val page: Int?,
    @SerializedName("total_pages") val totalPages: Int?,
)
internal data class MovieDto(
    val id: Int?,
    val title: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val rating: Double?,
    @SerializedName("vote_count") val voteCount: Int?,
) {
    fun toModel(): Movie? = id?.takeIf { it > 0 }?.let {
        Movie(it, title.orEmpty(), posterPath, releaseDate.orEmpty(), rating ?: 0.0, voteCount ?: 0)
    }
}
internal data class MovieDetailDto(
    val id: Int?,
    val title: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("vote_average") val rating: Double?,
    @SerializedName("vote_count") val voteCount: Int?,
    val overview: String?,
    val genres: List<GenreDto>?,
    val runtime: Int?,
)
internal data class AuthorDetailsDto(val rating: Double?)
internal data class ReviewDto(
    val id: String?,
    val author: String?,
    val content: String?,
    @SerializedName("author_details") val authorDetails: AuthorDetailsDto?,
)
internal data class VideoDto(
    val key: String?,
    val name: String?,
    val site: String?,
    val type: String?,
    val official: Boolean?,
)
internal data class VideoResponse(val results: List<VideoDto>?)
