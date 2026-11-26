package com.example.tvcatalog.data

import com.google.gson.annotations.SerializedName

enum class MediaType { MOVIE, TV, ANIME }

/** Uma ligação mostrada no ecrã de detalhes (plataforma, site oficial, etc.). */
data class WatchLink(val name: String, val url: String? = null)

/** Modelo unificado usado pela interface. */
data class CatalogItem(
    val key: String,
    val sourceId: Int,
    val type: MediaType,
    val title: String,
    val overview: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val rating: Double?,
    val year: String?,
    /** Plataformas de streaming (animes: vêm do AniList). */
    val whereToWatch: List<WatchLink> = emptyList(),
    /** Outras ligações (site oficial, página no AniList, etc.). */
    val otherLinks: List<WatchLink> = emptyList(),
    /** Texto curto sobre o poster, ex.: "Ep. 5 · 18:30" */
    val badge: String? = null,
)

data class Availability(
    val stream: List<WatchLink>,
    val rentBuy: List<WatchLink>,
    val other: List<WatchLink> = emptyList(),
)

// ---------- TMDB ----------
data class TmdbPage(val results: List<TmdbItem>?)

data class TmdbItem(
    val id: Int,
    val title: String?,
    val name: String?,
    val overview: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("vote_average") val voteAverage: Double?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("media_type") val mediaType: String?,
)

/** Género de filmes/séries (TMDB, com id) ou de animes (AniList, id = 0 e o nome é o filtro). */
data class Genre(val id: Int, val name: String)

data class GenresResponse(val genres: List<Genre>?)

/** App adicionada pelo utilizador (Definições). `template` é opcional: ligação direta ao título. */
data class CustomApp(val pkg: String, val label: String, val template: String? = null)

data class ExternalIds(@SerializedName("imdb_id") val imdbId: String?)

data class ProvidersResponse(val results: Map<String, CountryProviders>?)

data class CountryProviders(
    val link: String?,
    val flatrate: List<Provider>?,
    val rent: List<Provider>?,
    val buy: List<Provider>?,
)

data class Provider(@SerializedName("provider_name") val name: String?)

// ---------- AniList (GraphQL) ----------
data class GqlRequest(val query: String, val variables: Map<String, Any?>)

data class GqlResponse(val data: GqlData?)

data class GqlData(@SerializedName("Page") val page: GqlPage?)

data class GqlPage(
    val pageInfo: GqlPageInfo?,
    val media: List<AniMedia>?,
    val airingSchedules: List<AiringSchedule>?,
)

data class GqlPageInfo(val hasNextPage: Boolean?)

data class AiringSchedule(val airingAt: Long?, val episode: Int?, val media: AniMedia?)

data class AniMedia(
    val id: Int,
    val title: AniTitle?,
    val description: String?,
    val coverImage: AniCover?,
    val bannerImage: String?,
    val averageScore: Int?,
    val seasonYear: Int?,
    val isAdult: Boolean?,
    val externalLinks: List<AniLink>?,
)

data class AniTitle(val romaji: String?, val english: String?)

data class AniCover(val extraLarge: String?)

data class AniLink(val site: String?, val url: String?, val type: String?)

/**
 * O Gson cria objetos sem correr os valores por defeito do Kotlin, por isso um título guardado
 * por uma versão antiga pode voltar com listas/textos a null. Isto repõe valores seguros.
 */
@Suppress("USELESS_CAST")
fun CatalogItem.sanitized(): CatalogItem = copy(
    title = (title as String?) ?: "—",
    overview = (overview as String?).orEmpty(),
    whereToWatch = (whereToWatch as List<WatchLink>?).orEmpty(),
    otherLinks = (otherLinks as List<WatchLink>?).orEmpty(),
)
