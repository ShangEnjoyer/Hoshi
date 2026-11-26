package com.example.tvcatalog.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/movie/week") suspend fun trendingMovies(): TmdbPage
    @GET("movie/popular") suspend fun popularMovies(): TmdbPage
    @GET("movie/top_rated") suspend fun topRatedMovies(): TmdbPage
    @GET("movie/upcoming") suspend fun upcomingMovies(): TmdbPage

    @GET("trending/tv/week") suspend fun trendingTv(): TmdbPage
    @GET("tv/popular") suspend fun popularTv(): TmdbPage
    @GET("tv/top_rated") suspend fun topRatedTv(): TmdbPage
    @GET("tv/on_the_air") suspend fun onTheAirTv(): TmdbPage

    @GET("search/multi") suspend fun search(@Query("query") query: String): TmdbPage

    @GET("genre/movie/list") suspend fun movieGenres(): GenresResponse
    @GET("genre/tv/list") suspend fun tvGenres(): GenresResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("with_genres") genre: Int,
        @Query("page") page: Int,
        @Query("sort_by") sort: String,
    ): TmdbPage

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("with_genres") genre: Int,
        @Query("page") page: Int,
        @Query("sort_by") sort: String,
    ): TmdbPage

    @GET("movie/{id}/external_ids")
    suspend fun movieExternalIds(@Path("id") id: Int): ExternalIds

    @GET("tv/{id}/external_ids")
    suspend fun tvExternalIds(@Path("id") id: Int): ExternalIds

    @GET("movie/{id}/watch/providers")
    suspend fun movieProviders(@Path("id") id: Int): ProvidersResponse

    @GET("tv/{id}/watch/providers")
    suspend fun tvProviders(@Path("id") id: Int): ProvidersResponse
}

interface AniListApi {
    @POST("/") suspend fun query(@Body body: GqlRequest): GqlResponse
}
