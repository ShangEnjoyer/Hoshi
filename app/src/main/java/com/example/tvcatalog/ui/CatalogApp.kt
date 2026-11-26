package com.example.tvcatalog.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme
import coil.compose.AsyncImage
import com.example.tvcatalog.CatalogViewModel
import com.example.tvcatalog.BuildConfig
import com.example.tvcatalog.R
import com.example.tvcatalog.data.Availability
import com.example.tvcatalog.data.CatalogItem
import com.example.tvcatalog.data.CustomApp
import com.example.tvcatalog.data.ExternalOpen
import com.example.tvcatalog.data.Genre
import com.example.tvcatalog.data.Library
import com.example.tvcatalog.data.KeyServer
import com.example.tvcatalog.data.MediaType
import com.example.tvcatalog.data.Repository
import com.example.tvcatalog.data.WatchLink
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Bg = Color(0xFF0B0B18)
private val StarGold = Color(0xFFFFD27A)

/** A tua assinatura: aparece no fundo das Definições. Muda aqui se quiseres outro nome. */
private const val AUTHOR = "ShangEnjoyer"

/**
 * Memória de navegação: guarda a posição de scroll de cada lista, o texto da pesquisa e o
 * último cartão aberto, para que ao sair de um detalhe voltes exatamente ao mesmo sítio.
 */
private class NavMemory {
    private val columns = mutableMapOf<String, LazyListState>()
    private val rows = mutableMapOf<String, LazyListState>()
    val grid = LazyGridState()
    private val grids = mutableMapOf<String, LazyGridState>()

    /** Grelha de um género (uma por separador), para voltar ao mesmo sítio depois de um detalhe. */
    fun gridFor(key: String): LazyGridState = grids.getOrPut(key) { LazyGridState() }
    var query: String = ""
    var lastSearched: String = ""

    /** Identificador do cartão que deve recuperar o foco quando se volta do detalhe. */
    var restoreFocusId: String? = null

    fun column(key: String, firstIndex: Int = 0): LazyListState =
        columns.getOrPut(key) { LazyListState(firstIndex) }

    fun row(key: String): LazyListState = rows.getOrPut(key) { LazyListState() }
}
private val Grey = Color(0xFFB8B8C2)
private val Accent = Color(0xFF7C3AED)

/**
 * Guarda o título aberto (em JSON) para que um recriar da atividade (mudança de idioma,
 * HDMI, ecrã, etc.) não te leve de volta ao catálogo e perca o detalhe.
 */
private val ItemSaver: Saver<CatalogItem?, String> = Saver(
    save = { item -> if (item == null) "" else Gson().toJson(item) },
    restore = { json ->
        if (json.isEmpty()) null
        else runCatching { Gson().fromJson(json, CatalogItem::class.java) }.getOrNull()
    },
)

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun CatalogApp(vm: CatalogViewModel = viewModel()) {
    val rows by vm.rows.collectAsState()
    val loading by vm.loading.collectAsState()
    val missingKey by vm.missingKey.collectAsState()
    val keyInvalid by vm.keyInvalid.collectAsState()
    var selected by rememberSaveable(stateSaver = ItemSaver) { mutableStateOf<CatalogItem?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val memory = remember { NavMemory() }
    val tabs = listOf(
        stringResource(R.string.tab_home),
        stringResource(R.string.tab_movies),
        stringResource(R.string.tab_tv),
        stringResource(R.string.tab_anime),
        stringResource(R.string.tab_week),
        stringResource(R.string.tab_mylist),
        stringResource(R.string.tab_search),
        stringResource(R.string.tab_settings),
    )

    val movies = rows["movies"].orEmpty()
    val tv = rows["tv"].orEmpty()
    val anime = rows["anime"].orEmpty()

    // Se o foco não puder ser restaurado, deixa de bloquear a troca de separadores.
    LaunchedEffect(selected) {
        if (selected == null) {
            delay(1500)
            memory.restoreFocusId = null
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            secondary = StarGold,
            background = Bg,
            surface = Color(0xFF1B1A33),
            border = StarGold,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF17123A), Bg)))) {
                val current = selected
                if (current != null) {
                    BackHandler { selected = null }
                    DetailScreen(current)
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(horizontal = 48.dp, vertical = 16.dp)) {
                            TabRow(selectedTabIndex = tab) {
                                tabs.forEachIndexed { i, label ->
                                    Tab(
                                        selected = i == tab,
                                        // Enquanto o foco volta ao cartão aberto, ignora o foco temporário nos separadores.
                                        onFocus = { if (memory.restoreFocusId == null) tab = i },
                                        onClick = { tab = i },
                                    ) {
                                        Text(
                                            label,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }

                        when (tab) {
                            0 -> RowsScreen(
                                rows = anime.take(1) + movies.take(1) + tv.take(1) + anime.drop(1).take(1) +
                                    movies.drop(1).take(1) + tv.drop(1).take(1),
                                loading = loading,
                                missingKey = missingKey,
                                keyInvalid = keyInvalid,
                                tabKey = "home",
                                memory = memory,
                                onClick = { selected = it },
                            )
                            1 -> CategoryScreen(vm, "movies", movies, loading, missingKey, keyInvalid, memory) { selected = it }
                            2 -> CategoryScreen(vm, "tv", tv, loading, missingKey, keyInvalid, memory) { selected = it }
                            3 -> CategoryScreen(vm, "anime", anime, loading, false, false, memory) { selected = it }
                            4 -> WeekScreen(vm, memory, onClick = { selected = it })
                            5 -> MyListScreen(memory) { selected = it }
                            6 -> SearchScreen(vm, memory, onClick = { selected = it })
                            else -> SettingsScreen(vm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowsScreen(
    rows: List<CatalogViewModel.Row>,
    loading: Boolean,
    missingKey: Boolean,
    keyInvalid: Boolean,
    tabKey: String,
    memory: NavMemory,
    onClick: (CatalogItem) -> Unit,
    emptyRes: Int? = null,
    header: (@Composable () -> Unit)? = null,
) {
    if (rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(48.dp)) {
            val msg = when {
                emptyRes != null && !loading -> stringResource(emptyRes)
                loading -> stringResource(R.string.msg_loading)
                keyInvalid -> stringResource(R.string.msg_invalid_key)
                missingKey -> stringResource(R.string.msg_missing_key)
                else -> stringResource(R.string.msg_load_error)
            }
            Text(msg, style = MaterialTheme.typography.titleLarge)
        }
        return
    }
    LazyColumn(
        state = memory.column("tab-$tabKey"),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (header != null) {
            item(key = "header") { header() }
        }
        items(rows, key = { it.titleRes }) { row ->
            val rowKey = "$tabKey/${row.titleRes}"
            Column {
                SectionTitle(stringResource(row.titleRes))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    state = memory.row(rowKey),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(row.items, key = { it.key }) {
                        PosterCard(it, "$rowKey/${it.key}", memory, onClick)
                    }
                }
            }
        }
    }
}

/**
 * Filmes / Séries / Animes: as filas de sempre, com uma barra de géneros no topo.
 * Ao escolher um género, as filas dão lugar a uma grelha só com esse género.
 */
@Composable
private fun CategoryScreen(
    vm: CatalogViewModel,
    tabKey: String,
    rows: List<CatalogViewModel.Row>,
    loading: Boolean,
    missingKey: Boolean,
    keyInvalid: Boolean,
    memory: NavMemory,
    onClick: (CatalogItem) -> Unit,
) {
    val states by vm.genreStates.collectAsState()
    val gs = states[tabKey] ?: CatalogViewModel.GenreState()

    // Também corre quando a chave do TMDB passa a existir, para os géneros aparecerem logo.
    LaunchedEffect(tabKey, missingKey) { vm.loadGenres(tabKey) }

    val chips: @Composable () -> Unit = {
        GenreChips(gs.genres, gs.selected, memory.row("genres/$tabKey")) { vm.selectGenre(tabKey, it) }
    }

    if (gs.selected == null) {
        RowsScreen(
            rows, loading, missingKey, keyInvalid, tabKey, memory, onClick,
            header = if (gs.genres.isEmpty()) null else chips,
        )
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 16.dp)) {
            chips()
            Spacer(Modifier.height(16.dp))
            when {
                gs.loading -> Text(stringResource(R.string.msg_loading), style = MaterialTheme.typography.titleLarge)
                gs.items.isEmpty() -> Text(stringResource(R.string.msg_genre_empty), style = MaterialTheme.typography.titleLarge)
                else -> LazyVerticalGrid(
                    state = memory.gridFor("genre/$tabKey"),
                    columns = GridCells.Adaptive(150.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(gs.items, key = { it.key }) { PosterCard(it, "genre/$tabKey/${it.key}", memory, onClick) }
                }
            }
        }
    }
}

@Composable
private fun GenreChips(
    genres: List<Genre>,
    selected: Genre?,
    state: LazyListState,
    onSelect: (Genre?) -> Unit,
) {
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item(key = "all") {
            PillButton(stringResource(R.string.genre_all), selected == null) { onSelect(null) }
        }
        items(genres, key = { "${it.id}/${it.name}" }) { g ->
            val active = selected != null && selected.id == g.id && selected.name == g.name
            PillButton(g.name, active) { onSelect(g) }
        }
    }
}

/** Botão pequeno em forma de pílula (género, app, site, Para ver…). Fica roxo quando está ativo. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PillButton(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Card(
        onClick = onClick,
        shape = CardDefaults.shape(shape),
        colors = CardDefaults.colors(
            containerColor = if (active) Accent else Color(0xFF22213F),
            focusedContainerColor = if (active) Accent else Color(0xFF34325E),
        ),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, StarGold), shape = shape)),
        scale = CardDefaults.scale(focusedScale = 1f),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
        )
    }
}

/**
 * Cartão da Hoshi: cantos redondos e, quando tem o foco, um contorno dourado e um pequeno aumento.
 * É leve de propósito: só forma, borda e escala (sem sombras nem desfocados).
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusScale: Float = 1f,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = CardDefaults.shape(shape),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, StarGold), shape = shape)),
        scale = CardDefaults.scale(focusedScale = focusScale),
        content = content,
    )
}

/** Título de uma fila: uma barrinha dourada→roxa e o texto. */
@Composable
private fun SectionTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(StarGold, Accent))),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** "A minha lista": o que guardaste em Para ver e nos Favoritos. */
@Composable
private fun MyListScreen(memory: NavMemory, onClick: (CatalogItem) -> Unit) {
    val watchlist by Library.watchlist.collectAsState()
    val favorites by Library.favorites.collectAsState()
    val rows = buildList {
        if (watchlist.isNotEmpty()) add(CatalogViewModel.Row(0, R.string.row_watchlist, watchlist))
        if (favorites.isNotEmpty()) add(CatalogViewModel.Row(1, R.string.row_favorites, favorites))
    }
    RowsScreen(
        rows = rows,
        loading = false,
        missingKey = false,
        keyInvalid = false,
        tabKey = "mylist",
        memory = memory,
        onClick = onClick,
        emptyRes = R.string.msg_mylist_empty,
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PosterCard(
    item: CatalogItem,
    focusId: String,
    memory: NavMemory,
    onClick: (CatalogItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val requester = remember { FocusRequester() }

    // Ao voltar do detalhe, o cartão que abriste recupera o foco.
    LaunchedEffect(Unit) {
        if (memory.restoreFocusId == focusId) {
            delay(80)
            runCatching { requester.requestFocus() }
            memory.restoreFocusId = null
        }
    }

    Column(modifier.width(150.dp)) {
        HCard(
            onClick = {
                memory.restoreFocusId = focusId
                onClick(item)
            },
            focusScale = 1.06f,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .focusRequester(requester),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (item.posterUrl != null) {
                    AsyncImage(
                        model = item.posterUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Sem capa: mostra só a inicial; o nome completo aparece sempre por baixo.
                    Text(
                        item.title.take(1).uppercase(),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.displaySmall,
                        color = Grey,
                    )
                }
                if (item.badge != null) {
                    Text(
                        item.badge,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        // Nome por baixo da capa (filmes, séries e animes), até 2 linhas.
        Text(
            item.title,
            modifier = Modifier.fillMaxWidth().height(50.dp).padding(top = 14.dp, start = 2.dp, end = 2.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WeekScreen(vm: CatalogViewModel, memory: NavMemory, onClick: (CatalogItem) -> Unit) {
    val rows by vm.weekRows.collectAsState()
    val loading by vm.weekLoading.collectAsState()

    LaunchedEffect(Unit) { vm.loadWeek() }

    if (loading || rows.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(48.dp)) {
            Text(
                stringResource(if (loading) R.string.msg_loading else R.string.msg_week_empty),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    } else {
        WeekList(rows, memory, onClick)
    }
}

@Composable
private fun WeekList(rows: List<CatalogViewModel.DayRow>, memory: NavMemory, onClick: (CatalogItem) -> Unit) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val fmt = remember { DateTimeFormatter.ofPattern("EEEE, d MMM", locale) }
    val todayLabel = stringResource(R.string.week_today)
    // Abre já posicionado no dia de hoje (ou no próximo dia com episódios).
    val firstUpcoming = rows.indexOfFirst { !it.date.isBefore(today) }
    val startIndex = if (firstUpcoming >= 0) firstUpcoming else rows.lastIndex.coerceAtLeast(0)
    val state = memory.column("week", startIndex)

    LazyColumn(
        state = state,
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(rows, key = { it.date.toString() }) { row ->
            val rowKey = "week/${row.date}"
            val label = row.date.format(fmt).replaceFirstChar { it.titlecase(locale) }
            val title = if (row.date == today) "$label · $todayLabel" else label
            Column {
                SectionTitle(title)
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    state = memory.row(rowKey),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(row.items, key = { it.key }) {
                        PosterCard(it, "$rowKey/${it.key}", memory, onClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchScreen(vm: CatalogViewModel, memory: NavMemory, onClick: (CatalogItem) -> Unit) {
    var q by remember { mutableStateOf(memory.query) }
    val results by vm.searchResults.collectAsState()

    LaunchedEffect(q) {
        // Ao voltar de um detalhe, mantém os resultados em vez de pesquisar outra vez.
        if (q == memory.lastSearched) return@LaunchedEffect
        delay(500)
        vm.search(q)
        memory.lastSearched = q
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 8.dp)) {
        TextBox(
            value = q,
            onValueChange = { q = it; memory.query = it },
            placeholder = stringResource(R.string.search_placeholder),
            imeAction = ImeAction.Search,
            onDone = { vm.search(q) },
        )
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            state = memory.grid,
            columns = GridCells.Adaptive(150.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(results, key = { it.key }) { PosterCard(it, "search/${it.key}", memory, onClick) }
        }
    }
}

@Composable
private fun TextBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    imeAction: ImeAction,
    onDone: () -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 22.sp),
        cursorBrush = SolidColor(Color.White),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onSearch = { onDone() }, onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF22213F))
                    .padding(16.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, color = Color(0xFF9A9AA5))
                inner()
            }
        },
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsScreen(vm: CatalogViewModel) {
    val missing by vm.missingKey.collectAsState()
    val context = LocalContext.current
    var key by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    var received by remember { mutableStateOf(false) }
    var phoneUrl by remember { mutableStateOf<String?>(null) }
    var phoneChecked by remember { mutableStateOf(false) }

    // Servidor local para receber a chave do telemóvel; só fica ativo neste ecrã.
    val server = remember { KeyServer(context) { k -> vm.saveKey(k); received = true } }
    DisposableEffect(server) { onDispose { server.stop() } }
    LaunchedEffect(server) {
        phoneUrl = withContext(Dispatchers.IO) { server.start() }
        phoneChecked = true
    }

    // Com a lista de sites a crescer, o ecrã passa a deslizar com as setas do comando.
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(48.dp),
        ) {
            // Maneira fácil: QR code -> colar a chave no telemóvel.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_phone_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.settings_phone_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grey,
                )
                val url = phoneUrl
                when {
                    received -> Text(
                        stringResource(R.string.settings_phone_received),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    url != null -> {
                        QrCode(url, Modifier.size(190.dp))
                        Text(url, style = MaterialTheme.typography.titleMedium)
                    }
                    phoneChecked -> Text(
                        stringResource(R.string.settings_phone_noip),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Grey,
                    )
                }
            }

            // Maneira manual: escrever a chave com o comando.
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.settings_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Grey,
                )
                Text(
                    stringResource(if (missing) R.string.settings_status_missing else R.string.settings_status_ok),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextBox(
                    value = key,
                    onValueChange = { key = it; saved = false },
                    placeholder = stringResource(R.string.settings_key_placeholder),
                    imeAction = ImeAction.Done,
                    onDone = { if (key.isNotBlank()) { vm.saveKey(key); saved = true } },
                )
                HCard(
                    onClick = { if (key.isNotBlank()) { vm.saveKey(key); saved = true } },
                    modifier = Modifier.width(260.dp),
                ) {
                    Text(stringResource(R.string.settings_save), modifier = Modifier.padding(16.dp))
                }
                if (saved) {
                    Text(stringResource(R.string.settings_saved), color = Grey)
                }
            }
        }
        AppsSection()
        AboutFooter()
    }
}

/** Apps que queres ver em "Abrir numa app": escolhe entre as instaladas na TV. */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppsSection() {
    val context = LocalContext.current
    val installed = remember { ExternalOpen.launchableApps(context) }
    var apps by remember { mutableStateOf(Repository.customApps()) }
    var template by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    fun add(app: ExternalOpen.InstalledApp) {
        if (Repository.addCustomApp(CustomApp(app.pkg, app.label, template))) {
            apps = Repository.customApps()
            invalid = false
        } else {
            invalid = true
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_apps_title), style = MaterialTheme.typography.titleLarge)
        Text(
            stringResource(R.string.settings_apps_help),
            style = MaterialTheme.typography.bodyMedium,
            color = Grey,
        )
        TextBox(
            value = template,
            onValueChange = { template = it; invalid = false },
            placeholder = stringResource(R.string.settings_apps_placeholder),
            imeAction = ImeAction.Done,
            onDone = {},
        )
        if (invalid) {
            Text(stringResource(R.string.settings_apps_invalid), color = Grey)
        }
        Text(stringResource(R.string.settings_apps_pick), style = MaterialTheme.typography.titleMedium, color = Grey)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(installed, key = { it.pkg }) { a ->
                PillButton(a.label, apps.any { it.pkg == a.pkg }) { add(a) }
            }
        }
        apps.forEach { app ->
            HCard(
                onClick = {
                    Repository.removeCustomApp(app.pkg)
                    apps = Repository.customApps()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text(
                        app.template ?: stringResource(R.string.settings_apps_no_link),
                        style = MaterialTheme.typography.labelMedium,
                        color = Grey,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.settings_sites_remove),
                        style = MaterialTheme.typography.labelMedium,
                        color = Grey,
                    )
                }
            }
        }
    }
}

/** Assinatura e créditos (a TMDB pede esta frase em apps que usam a API dela). */
@Composable
private fun AboutFooter() {
    Column(
        Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "Hoshi ${BuildConfig.VERSION_NAME} · by $AUTHOR",
            style = MaterialTheme.typography.titleSmall,
            color = StarGold,
        )
        Text(
            "This product uses the TMDB API but is not endorsed or certified by TMDB. " +
                "Availability data by JustWatch. Anime data by AniList.",
            style = MaterialTheme.typography.labelMedium,
            color = Grey,
            textAlign = TextAlign.Center,
        )
    }
}

/** Desenha um código QR (branco com módulos pretos, com margem) para o texto dado. */
@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching { QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0) }.getOrNull()
    } ?: return
    Canvas(modifier) {
        drawRect(Color.White)
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) {
                    drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun DetailScreen(item: CatalogItem) {
    val context = LocalContext.current
    var availability by remember(item.key) { mutableStateOf<Availability?>(null) }
    var failed by remember(item.key) { mutableStateOf(false) }
    var imdbId by remember(item.key) { mutableStateOf<String?>(null) }

    val watchlist by Library.watchlist.collectAsState()
    val favorites by Library.favorites.collectAsState()
    val baseKey = Library.baseKey(item)
    val inWatchlist = watchlist.any { it.key == baseKey }
    val isFavorite = favorites.any { it.key == baseKey }

    // Apps de streaming que estão mesmo instaladas na TV.
    val installedApps = remember(item.key) {
        ExternalOpen.allApps().filter { ExternalOpen.installedPackage(context, it) != null }
    }

    // Textos lidos aqui porque o conteúdo da LazyColumn não é @Composable.
    val sStream = stringResource(R.string.section_stream)
    val sRentBuy = stringResource(R.string.section_rent_buy)
    val sMore = stringResource(R.string.section_more_links)

    LaunchedEffect(item.key) {
        runCatching { Repository.availability(item) }
            .onSuccess { availability = it }
            .onFailure { failed = true }
    }

    // O id do IMDb permite ao Stremio abrir exatamente este título (senão pesquisa pelo nome).
    LaunchedEffect(item.key) {
        imdbId = runCatching { Repository.imdbId(item) }.getOrNull()
    }

    val openUrl: (String) -> Unit = { url ->
        if (!ExternalOpen.openUrl(context, url)) {
            Toast.makeText(context, context.getString(R.string.no_browser), Toast.LENGTH_LONG).show()
        }
    }

    val openApp: (ExternalOpen.AppTarget) -> Unit = { app ->
        if (!ExternalOpen.openInApp(context, app, item, imdbId)) {
            Toast.makeText(context, context.getString(R.string.app_open_failed, app.label), Toast.LENGTH_LONG).show()
        }
    }

    // Plataforma da lista "Onde ver": abre a app dela se estiver instalada; senão, a ligação web.
    val openLink: (WatchLink) -> Unit = { link ->
        val app = ExternalOpen.appForProvider(link.name)
        val opened = app != null && ExternalOpen.openInApp(context, app, item, imdbId)
        if (!opened) link.url?.let(openUrl)
    }

    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.45f,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Bg, Bg.copy(alpha = 0.85f), Color.Transparent))
            )
        )

        // Lista navegável: com as setas para baixo passas pelas ligações e carregas OK para abrir.
        LazyColumn(
            modifier = Modifier.fillMaxHeight().fillMaxWidth(0.6f),
            contentPadding = PaddingValues(start = 64.dp, top = 48.dp, end = 16.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Cabeçalho focável: permite ao foco (setas do comando) voltar a subir até ao topo.
            item {
                Column(
                    modifier = Modifier.focusable(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(item.title, style = MaterialTheme.typography.displaySmall)

                    val kind = when (item.type) {
                        MediaType.MOVIE -> stringResource(R.string.kind_movie)
                        MediaType.TV -> stringResource(R.string.kind_tv)
                        MediaType.ANIME -> stringResource(R.string.kind_anime)
                    }
                    val meta = listOfNotNull(
                        kind,
                        item.year,
                        item.rating?.let { "★ %.1f".format(it) },
                        item.badge,
                    ).joinToString("  ·  ")
                    Text(meta, style = MaterialTheme.typography.titleMedium, color = Grey)

                    Text(
                        item.overview.ifBlank { stringResource(R.string.no_overview) },
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 8,
                    )
                }
            }

            // Para ver / Favorito
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(
                        stringResource(if (inWatchlist) R.string.watchlist_remove else R.string.watchlist_add),
                        inWatchlist,
                    ) { Library.toggleWatchlist(item) }
                    PillButton(
                        stringResource(if (isFavorite) R.string.favorite_remove else R.string.favorite_add),
                        isFavorite,
                    ) { Library.toggleFavorite(item) }
                }
            }

            // Abrir numa app instalada na TV (Stremio, Netflix, …)
            item {
                Text(
                    stringResource(R.string.open_in_app),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            item {
                if (installedApps.isEmpty()) {
                    Text(stringResource(R.string.no_apps_found), color = Grey)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(installedApps, key = { it.label }) { app ->
                            PillButton(app.label, false) { openApp(app) }
                        }
                    }
                }
            }

            item {
                Text(
                    stringResource(R.string.where_to_watch),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            val a = availability
            if (a != null) {
                if (a.stream.isEmpty() && a.rentBuy.isEmpty()) {
                    item { Text(stringResource(R.string.no_platform), color = Grey) }
                }
                linkSection(sStream, a.stream, openLink)
                linkSection(sRentBuy, a.rentBuy, openLink)
                linkSection(sMore, a.other, openLink)
            } else if (failed) {
                item { Text(stringResource(R.string.availability_error), color = Grey) }
            } else {
                item { Text(stringResource(R.string.searching), color = Grey) }
            }
        }

        Text(
            stringResource(R.string.attribution),
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF8A8A95),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 64.dp, bottom = 24.dp, end = 64.dp),
        )
    }
}

private fun LazyListScope.linkSection(
    title: String,
    links: List<WatchLink>,
    open: (WatchLink) -> Unit,
) {
    if (links.isEmpty()) return
    item(key = "title-$title") {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Grey)
    }
    // distinctBy: duas ligações com o mesmo nome rebentariam a lista (chaves repetidas).
    items(links.distinctBy { it.name }, key = { "$title-${it.name}" }) { LinkCard(it, open) }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LinkCard(link: WatchLink, open: (WatchLink) -> Unit) {
    val context = LocalContext.current
    // Se a plataforma tem app instalada na TV, este cartão abre a app em vez da página web.
    val app = remember(link.name) {
        ExternalOpen.appForProvider(link.name)?.takeIf { ExternalOpen.installedPackage(context, it) != null }
    }
    HCard(
        onClick = { open(link) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(link.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (app != null) stringResource(R.string.open_in_named, app.label)
                else link.url ?: stringResource(R.string.no_link),
                style = MaterialTheme.typography.labelMedium,
                color = Grey,
                maxLines = 1,
            )
        }
    }
}
