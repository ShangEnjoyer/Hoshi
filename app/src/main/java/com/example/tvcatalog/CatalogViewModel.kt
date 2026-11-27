package com.example.tvcatalog

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tvcatalog.data.CatalogItem
import com.example.tvcatalog.data.Genre
import com.example.tvcatalog.data.MediaType
import com.example.tvcatalog.data.Repository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

class CatalogViewModel : ViewModel() {

    data class Row(val order: Int, @StringRes val titleRes: Int, val items: List<CatalogItem>)

    /** Um dia do calendário semanal com os episódios desse dia. */
    data class DayRow(val date: LocalDate, val items: List<CatalogItem>)

    /** Filtro por género de um separador (Filmes, Séries ou Animes). */
    data class GenreState(
        val genres: List<Genre> = emptyList(),
        val selected: Genre? = null,
        val items: List<CatalogItem> = emptyList(),
        val loading: Boolean = false,
    )

    private class RowDef(
        val tab: String,
        val order: Int,
        @StringRes val titleRes: Int,
        val loader: suspend () -> List<CatalogItem>,
    )

    private val _rows = MutableStateFlow<Map<String, List<Row>>>(emptyMap())
    val rows: StateFlow<Map<String, List<Row>>> = _rows.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _loadError = MutableStateFlow(false)
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    private val _searchResults = MutableStateFlow<List<CatalogItem>>(emptyList())
    val searchResults: StateFlow<List<CatalogItem>> = _searchResults.asStateFlow()

    private val _searchError = MutableStateFlow(false)
    val searchError: StateFlow<Boolean> = _searchError.asStateFlow()

    private val _missingKey = MutableStateFlow(!Repository.hasKey)
    val missingKey: StateFlow<Boolean> = _missingKey.asStateFlow()

    /** O TMDB respondeu 401: a chave existe mas está errada. */
    private val _keyInvalid = MutableStateFlow(false)
    val keyInvalid: StateFlow<Boolean> = _keyInvalid.asStateFlow()

    private val _genreStates = MutableStateFlow<Map<String, GenreState>>(emptyMap())
    val genreStates: StateFlow<Map<String, GenreState>> = _genreStates.asStateFlow()
    private val genreJobs = mutableMapOf<String, Job>()

    private val _weekRows = MutableStateFlow<List<DayRow>>(emptyList())
    val weekRows: StateFlow<List<DayRow>> = _weekRows.asStateFlow()

    private val _weekLoading = MutableStateFlow(true)
    val weekLoading: StateFlow<Boolean> = _weekLoading.asStateFlow()

    private var weekStarted = false

    private var searchJob: Job? = null
    private val loadJobs = mutableListOf<Job>()

    private val defs = listOf(
        RowDef("movies", 0, R.string.row_movies_trending) { Repository.tmdbList(MediaType.MOVIE) { trendingMovies() } },
        RowDef("movies", 1, R.string.row_movies_upcoming) { Repository.tmdbList(MediaType.MOVIE) { upcomingMovies() } },
        RowDef("movies", 2, R.string.row_movies_popular) { Repository.tmdbList(MediaType.MOVIE) { popularMovies() } },
        RowDef("movies", 3, R.string.row_movies_top) { Repository.tmdbList(MediaType.MOVIE) { topRatedMovies() } },

        RowDef("tv", 0, R.string.row_tv_trending) { Repository.tmdbList(MediaType.TV) { trendingTv() } },
        RowDef("tv", 1, R.string.row_tv_airing) { Repository.tmdbList(MediaType.TV) { onTheAirTv() } },
        RowDef("tv", 2, R.string.row_tv_popular) { Repository.tmdbList(MediaType.TV) { popularTv() } },
        RowDef("tv", 3, R.string.row_tv_top) { Repository.tmdbList(MediaType.TV) { topRatedTv() } },

        RowDef("anime", 0, R.string.row_anime_today) { Repository.airingToday() },
        RowDef("anime", 1, R.string.row_anime_trending) { Repository.anime("TRENDING_DESC") },
        RowDef("anime", 2, R.string.row_anime_popular) { Repository.anime("POPULARITY_DESC") },
        RowDef("anime", 3, R.string.row_anime_top) { Repository.anime("SCORE_DESC") },
    )

    init {
        load()
    }

    private fun load() {
        // Filmes/séries precisam da chave TMDB; os animes (AniList) não.
        val active = if (_missingKey.value) defs.filter { it.tab == "anime" } else defs
        if (active.isEmpty()) {
            _loading.value = false
            return
        }
        var pending = active.size
        active.forEach { def ->
            loadJobs += viewModelScope.launch {
                val items = try {
                    def.loader()
                } catch (e: CancellationException) {
                    // Ao recarregar, os pedidos antigos são cancelados. Sem este rethrow, o job
                    // continuava e podia pôr "loading = false" a meio do novo carregamento.
                    throw e
                } catch (e: HttpException) {
                    if (e.code() == 401 && def.tab != "anime") _keyInvalid.value = true
                    _loadError.value = true
                    emptyList()
                } catch (e: Exception) {
                    _loadError.value = true
                    emptyList()
                }
                if (items.isNotEmpty()) {
                    _rows.update { m ->
                        val updated = (m[def.tab].orEmpty() + Row(def.order, def.titleRes, items))
                            .sortedBy { it.order }
                        m + (def.tab to updated)
                    }
                }
                pending--
                if (pending == 0) _loading.value = false
            }
        }
    }

    /** Guarda a chave do TMDB introduzida na app e recarrega o catálogo. */
    fun saveKey(key: String) {
        if (key.isBlank()) return
        Repository.saveKey(key)
        _missingKey.value = !Repository.hasKey
        reload()
    }

    private fun reload() {
        loadJobs.forEach { it.cancel() }
        loadJobs.clear()
        _rows.value = emptyMap()
        _keyInvalid.value = false
        _loadError.value = false
        _loading.value = true
        load()
    }

    // ---------- Géneros ----------

    private fun tabType(tab: String): MediaType? = when (tab) {
        "movies" -> MediaType.MOVIE
        "tv" -> MediaType.TV
        "anime" -> MediaType.ANIME
        else -> null
    }

    private fun updateGenre(tab: String, change: (GenreState) -> GenreState) {
        _genreStates.update { m -> m + (tab to change(m[tab] ?: GenreState())) }
    }

    /** Carrega a lista de géneros do separador (uma vez; tenta outra vez se tiver falhado). */
    fun loadGenres(tab: String) {
        val type = tabType(tab) ?: return
        if (_genreStates.value[tab]?.genres?.isNotEmpty() == true) return
        if (type != MediaType.ANIME && _missingKey.value) return
        viewModelScope.launch {
            val list = try {
                Repository.genres(type)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            if (list.isNotEmpty()) updateGenre(tab) { it.copy(genres = list) }
        }
    }

    /** Escolhe um género (ou null = "Todos") e carrega os títulos desse género. */
    fun selectGenre(tab: String, genre: Genre?) {
        val type = tabType(tab) ?: return
        genreJobs[tab]?.cancel()
        if (genre == null) {
            updateGenre(tab) { it.copy(selected = null, items = emptyList(), loading = false) }
            return
        }
        updateGenre(tab) { it.copy(selected = genre, items = emptyList(), loading = true) }
        genreJobs[tab] = viewModelScope.launch {
            val items = try {
                Repository.byGenre(type, genre)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            updateGenre(tab) { it.copy(items = items, loading = false) }
        }
    }

    /** Carrega o calendário da semana (só quando se abre a aba, para não atrasar o arranque). */
    fun loadWeek() {
        if (weekStarted) return
        weekStarted = true
        _weekLoading.value = true
        viewModelScope.launch {
            val results = Repository.weekDays().map { day ->
                async {
                    var items = try {
                        Repository.airingOn(day)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        null
                    }
                    if (items == null) {
                        // O AniList limita pedidos seguidos: espera um pouco e tenta outra vez.
                        delay(2000)
                        items = try {
                            Repository.airingOn(day)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    DayRow(day, items.orEmpty())
                }
            }.awaitAll()
            _weekRows.value = results.filter { it.items.isNotEmpty() }
            _weekLoading.value = false
            // Sem resultados (ex.: sem internet): deixa tentar de novo da próxima vez.
            if (_weekRows.value.isEmpty()) weekStarted = false
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _searchResults.value = emptyList()
            _searchError.value = false
            return
        }
        _searchError.value = false
        searchJob = viewModelScope.launch {
            try {
                val result = Repository.search(query.trim())
                _searchResults.value = result.items
                _searchError.value = result.hasErrors
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _searchError.value = true
            }
        }
    }
}
