package com.example.tvcatalog.data

import android.content.Context
import android.content.SharedPreferences
import com.example.tvcatalog.BuildConfig
import com.example.tvcatalog.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

object Repository {

    data class SearchResult(val items: List<CatalogItem>, val hasErrors: Boolean)

    private const val POSTER = "https://image.tmdb.org/t/p/w342"
    private const val BACKDROP = "https://image.tmdb.org/t/p/w1280"

    @Volatile
    private var prefs: SharedPreferences? = null

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    /** Chave guardada na app (Definições) ou, se não houver, a que veio na compilação. */
    fun apiKey(): String =
        prefs?.getString("tmdb_key", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.TMDB_API_KEY

    val hasKey: Boolean get() = apiKey().isNotBlank()

    /** Língua da TV (ex.: "pt-PT", "en-US") para o TMDB devolver títulos e sinopses traduzidos. */
    private fun language(): String {
        val l = Locale.getDefault()
        // O Java ainda usa códigos antigos para algumas línguas; o TMDB espera os atuais.
        val lang = when (l.language) {
            "in" -> "id"
            "iw" -> "he"
            "ji" -> "yi"
            else -> l.language
        }
        return if (l.country.isBlank()) lang else "$lang-${l.country}"
    }

    /** País da TV, usado para mostrar as plataformas disponíveis nesse país. */
    private fun region(): String = Locale.getDefault().country.ifBlank { "US" }

    fun saveKey(key: String) {
        prefs?.edit()?.putString("tmdb_key", key.trim())?.apply()
    }

    /** Marcadores que o utilizador pode usar nos endereços das apps. */
    val PLACEHOLDERS = listOf("{query}", "{title}", "{imdb}", "{tmdb}", "{anilist}", "{type}", "{year}", "{slug}")

    private fun hasPlaceholder(t: String) = PLACEHOLDERS.any { t.contains(it) }

    // ---------- Apps adicionadas pelo utilizador ----------

    fun customApps(): List<CustomApp> {
        val json = prefs?.getString("custom_apps", null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<CustomApp>>() {}.type
            Gson().fromJson<List<CustomApp>>(json, type).orEmpty().filterNotNull()
        }.getOrDefault(emptyList())
    }

    /** Devolve false se a ligação opcional for inválida (precisa de "://" e de um marcador, ex.: {imdb}). */
    fun addCustomApp(app: CustomApp): Boolean {
        val t = app.template?.trim()?.ifEmpty { null }
        if (t != null && (!t.contains("://") || !hasPlaceholder(t))) return false
        val list = customApps().filter { it.pkg != app.pkg } + app.copy(template = t)
        prefs?.edit()?.putString("custom_apps", Gson().toJson(list.take(12)))?.apply()
        return true
    }

    fun removeCustomApp(pkg: String) {
        val list = customApps().filter { it.pkg != pkg }
        prefs?.edit()?.putString("custom_apps", Gson().toJson(list))?.apply()
    }

    private val tmdbClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val url = chain.request().url.newBuilder()
                .addQueryParameter("api_key", apiKey())
                .addQueryParameter("language", language())
                .build()
            chain.proceed(chain.request().newBuilder().url(url).build())
        }
        .build()

    private val tmdb: TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(tmdbClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    private val anilist: AniListApi = Retrofit.Builder()
        .baseUrl("https://graphql.anilist.co/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AniListApi::class.java)

    // ---------- Filmes e séries (TMDB) ----------

    suspend fun tmdbList(type: MediaType, call: suspend TmdbApi.() -> TmdbPage): List<CatalogItem> =
        tmdb.call().results.orEmpty().map { it.toCatalog(type) }.distinctBy { it.key }

    private fun TmdbItem.toCatalog(type: MediaType) = CatalogItem(
        key = "${type.name}-$id",
        sourceId = id,
        type = type,
        title = title ?: name ?: "—",
        overview = overview.orEmpty(),
        posterUrl = posterPath?.let { POSTER + it },
        backdropUrl = backdropPath?.let { BACKDROP + it },
        rating = voteAverage?.takeIf { it > 0 },
        year = (releaseDate ?: firstAirDate)?.take(4)?.takeIf { it.isNotBlank() },
    )

    // ---------- Animes (AniList) ----------

    private const val MEDIA_FIELDS = """
        id
        title { romaji english }
        description(asHtml: false)
        coverImage { extraLarge }
        bannerImage
        averageScore
        seasonYear
        isAdult
        externalLinks { site url type }
    """

    private const val LIST_QUERY = """
        query(${'$'}sort: [MediaSort], ${'$'}search: String, ${'$'}genre: String) {
          Page(perPage: 30) {
            media(type: ANIME, sort: ${'$'}sort, search: ${'$'}search, genre: ${'$'}genre, isAdult: false) { $MEDIA_FIELDS }
          }
        }
    """

    private const val SCHEDULE_QUERY = """
        query(${'$'}from: Int, ${'$'}to: Int, ${'$'}page: Int) {
          Page(page: ${'$'}page, perPage: 50) {
            pageInfo { hasNextPage }
            airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
              airingAt
              episode
              media { $MEDIA_FIELDS }
            }
          }
        }
    """

    suspend fun anime(sort: String, search: String? = null, genre: String? = null): List<CatalogItem> {
        val vars = mutableMapOf<String, Any?>("sort" to listOf(sort))
        if (search != null) vars["search"] = search
        if (genre != null) vars["genre"] = genre
        val res = aniQuery(GqlRequest(LIST_QUERY, vars))
        return res.data?.page?.media.orEmpty().map { it.toCatalog() }
    }

    /** No máximo 2 pedidos ao AniList ao mesmo tempo (o AniList responde 429 se houver rajadas). */
    private val aniLimiter = Semaphore(2)

    /** Pedido ao AniList que espera e repete quando o AniList diz "demasiados pedidos" (429). */
    private suspend fun aniQuery(req: GqlRequest): GqlResponse {
        var attempt = 0
        while (true) {
            try {
                return aniLimiter.withPermit { anilist.query(req) }
            } catch (e: HttpException) {
                if (e.code() != 429 || attempt >= 3) throw e
                val wait = e.response()?.headers()?.get("Retry-After")?.toLongOrNull() ?: 2L
                delay(wait.coerceIn(1L, 15L) * 1000L)
                attempt++
            }
        }
    }

    /**
     * Todos os episódios que estreiam hoje (dia local do dispositivo),
     * independentemente de estarem ou não em alguma plataforma.
     */
    suspend fun airingToday(): List<CatalogItem> = airingOn(LocalDate.now(ZoneId.systemDefault()))

    /** Dias da semana atual (começa no primeiro dia da semana da língua/região da TV). */
    fun weekDays(): List<LocalDate> {
        val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
        val start = LocalDate.now(ZoneId.systemDefault()).with(TemporalAdjusters.previousOrSame(first))
        return (0..6).map { start.plusDays(it.toLong()) }
    }

    /** Todos os episódios que estreiam (ou estrearam) num dado dia local, estejam ou não numa plataforma. */
    suspend fun airingOn(date: LocalDate): List<CatalogItem> {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone)
        val from = (start.toEpochSecond() - 1).toInt()
        val to = start.plusDays(1).toEpochSecond().toInt()
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

        val out = mutableListOf<CatalogItem>()
        var page = 1
        while (page <= 6) {
            val res = aniQuery(
                GqlRequest(SCHEDULE_QUERY, mapOf("from" to from, "to" to to, "page" to page))
            )
            val p = res.data?.page
            for (s in p?.airingSchedules.orEmpty()) {
                val m = s.media ?: continue
                if (m.isAdult == true) continue
                val time = Instant.ofEpochSecond(s.airingAt ?: 0L).atZone(zone).format(timeFmt)
                out += m.toCatalog().copy(
                    key = "ANIME-${m.id}-ep${s.episode}",
                    badge = appContext?.getString(R.string.episode_short, s.episode ?: 0, time)
                        ?: "Ep. ${s.episode} · $time",
                )
            }
            if (p?.pageInfo?.hasNextPage != true) break
            page++
        }
        return out.distinctBy { it.key }
    }

    private fun AniMedia.toCatalog(): CatalogItem {
        val links = externalLinks.orEmpty().filter { !it.site.isNullOrBlank() }
        val streaming = links
            .filter { it.type == "STREAMING" }
            .map { WatchLink(it.site.orEmpty(), it.url) }
            .distinctBy { it.name }
        val others = links
            .filter { it.type != "STREAMING" }
            .map { WatchLink(it.site.orEmpty(), it.url) }
            .distinctBy { it.name } +
            WatchLink("AniList", "https://anilist.co/anime/$id")
        return CatalogItem(
            key = "ANIME-$id",
            sourceId = id,
            type = MediaType.ANIME,
            title = title?.english ?: title?.romaji ?: "—",
            overview = description.orEmpty().replace(Regex("<[^>]*>"), " ").trim(),
            posterUrl = coverImage?.extraLarge,
            backdropUrl = bannerImage ?: coverImage?.extraLarge,
            rating = averageScore?.let { it / 10.0 },
            year = seasonYear?.toString(),
            whereToWatch = streaming,
            otherLinks = others,
        )
    }

    // ---------- Pesquisa e disponibilidade ----------

    suspend fun search(q: String): SearchResult = coroutineScope {
        val t = async<Result<List<CatalogItem>>> {
            try {
                Result.success(tmdb.search(q).results.orEmpty().mapNotNull {
                    when (it.mediaType) {
                        "movie" -> it.toCatalog(MediaType.MOVIE)
                        "tv" -> it.toCatalog(MediaType.TV)
                        else -> null
                    }
                })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val a = async<Result<List<CatalogItem>>> {
            try {
                Result.success(anime("SEARCH_MATCH", q))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
        val tmdbResults = t.await()
        val animeResults = a.await()
        SearchResult(
            items = (tmdbResults.getOrDefault(emptyList()) + animeResults.getOrDefault(emptyList()))
                .distinctBy { it.key },
            hasErrors = tmdbResults.isFailure || animeResults.isFailure,
        )
    }

    // ---------- Géneros ----------

    private val ANIME_GENRES = listOf(
        "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo",
        "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
        "Sports", "Supernatural", "Thriller",
    )

    /** Géneros disponíveis (TMDB traduz-os para a língua da TV; os do AniList são em inglês). */
    suspend fun genres(type: MediaType): List<Genre> = when (type) {
        MediaType.MOVIE -> tmdb.movieGenres().genres.orEmpty()
        MediaType.TV -> tmdb.tvGenres().genres.orEmpty()
        MediaType.ANIME -> ANIME_GENRES.map { Genre(0, it) }
    }

    /** Títulos de um género, os mais populares primeiro (2 páginas no TMDB). */
    suspend fun byGenre(type: MediaType, genre: Genre): List<CatalogItem> = when (type) {
        MediaType.MOVIE -> coroutineScope {
            (1..2).map { p -> async { tmdb.discoverMovies(genre.id, p, "popularity.desc") } }
                .awaitAll()
                .flatMap { it.results.orEmpty() }
                .map { it.toCatalog(MediaType.MOVIE) }
                .distinctBy { it.key }
        }
        MediaType.TV -> coroutineScope {
            (1..2).map { p -> async { tmdb.discoverTv(genre.id, p, "popularity.desc") } }
                .awaitAll()
                .flatMap { it.results.orEmpty() }
                .map { it.toCatalog(MediaType.TV) }
                .distinctBy { it.key }
        }
        MediaType.ANIME -> anime("POPULARITY_DESC", genre = genre.name)
    }

    /** Id do IMDb (usado para abrir o título direto no Stremio). Animes não têm. */
    suspend fun imdbId(item: CatalogItem): String? = when (item.type) {
        MediaType.MOVIE -> tmdb.movieExternalIds(item.sourceId).imdbId
        MediaType.TV -> tmdb.tvExternalIds(item.sourceId).imdbId
        MediaType.ANIME -> null
    }?.takeIf { it.isNotBlank() }

    private fun List<Provider>?.toLinks(link: String?): List<WatchLink> =
        orEmpty().mapNotNull { p -> p.name?.let { WatchLink(it, link) } }.distinctBy { it.name }

    /** Onde ver no país da TV (dados TMDB/JustWatch; animes usam os links do AniList). */
    suspend fun availability(item: CatalogItem): Availability {
        if (item.type == MediaType.ANIME) {
            return Availability(item.whereToWatch, emptyList(), item.otherLinks)
        }
        val isMovie = item.type == MediaType.MOVIE
        val r = if (isMovie) tmdb.movieProviders(item.sourceId) else tmdb.tvProviders(item.sourceId)
        val tmdbPage = WatchLink(
            "TMDB",
            "https://www.themoviedb.org/${if (isMovie) "movie" else "tv"}/${item.sourceId}",
        )
        val pt = r.results?.get(region()) ?: return Availability(emptyList(), emptyList(), listOf(tmdbPage))
        val link = pt.link
        return Availability(
            stream = pt.flatrate.toLinks(link),
            rentBuy = (pt.rent.toLinks(link) + pt.buy.toLinks(link)).distinctBy { it.name },
            other = listOfNotNull(link?.let { WatchLink("JustWatch", it) }, tmdbPage),
        )
    }
}
