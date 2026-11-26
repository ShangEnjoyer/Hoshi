package com.example.tvcatalog.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A tua lista: "Para ver" (watchlist) e favoritos. Ficam guardados no dispositivo
 * (SharedPreferences, em JSON), por isso sobrevivem a fechar a app e a reiniciar a TV.
 */
object Library {

    private const val MAX_ITEMS = 200

    private val gson = Gson()

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _watchlist = MutableStateFlow<List<CatalogItem>>(emptyList())
    val watchlist: StateFlow<List<CatalogItem>> = _watchlist.asStateFlow()

    private val _favorites = MutableStateFlow<List<CatalogItem>>(emptyList())
    val favorites: StateFlow<List<CatalogItem>> = _favorites.asStateFlow()

    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences("library", Context.MODE_PRIVATE)
        prefs = p
        _watchlist.value = read(p, "watchlist")
        _favorites.value = read(p, "favorites")
    }

    /**
     * Chave estável de um título. Um episódio da aba "Semana" (ANIME-12-ep5) e o mesmo anime
     * noutra lista (ANIME-12) contam como o mesmo título.
     */
    fun baseKey(item: CatalogItem): String = "${item.type.name}-${item.sourceId}"

    fun toggleWatchlist(item: CatalogItem) = toggle(_watchlist, "watchlist", item)

    fun toggleFavorite(item: CatalogItem) = toggle(_favorites, "favorites", item)

    private fun toggle(flow: MutableStateFlow<List<CatalogItem>>, prefKey: String, item: CatalogItem) {
        // Guarda sem o texto do episódio ("Ep. 5 · 18:30"), que só vale para aquele dia.
        val clean = item.copy(key = baseKey(item), badge = null)
        val current = flow.value
        val next = if (current.any { it.key == clean.key }) {
            current.filterNot { it.key == clean.key }
        } else {
            (listOf(clean) + current).take(MAX_ITEMS)
        }
        flow.value = next
        prefs?.edit()?.putString(prefKey, gson.toJson(next))?.apply()
    }

    private fun read(p: SharedPreferences, key: String): List<CatalogItem> {
        val json = p.getString(key, null) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<CatalogItem>>() {}.type
            gson.fromJson<List<CatalogItem>>(json, type).orEmpty().filterNotNull()
        }.getOrDefault(emptyList())
    }
}
