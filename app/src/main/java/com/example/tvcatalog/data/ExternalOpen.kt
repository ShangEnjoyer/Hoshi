package com.example.tvcatalog.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.text.Normalizer
import com.example.tvcatalog.WebViewActivity

/**
 * Abre um título fora da Hoshi: numa app de streaming instalada na TV (Stremio, Netflix, …)
 * ou numa página web.
 *
 * Para a Hoshi conseguir "ver" estas apps no Android 11+, os pacotes têm de estar listados em
 * <queries> no AndroidManifest.xml. Se acrescentares uma app aqui, acrescenta-a lá também.
 */
object ExternalOpen {

    /** Uma app de streaming conhecida. */
    class AppTarget(
        val label: String,
        /** Nomes de pacote possíveis (TV primeiro; o 1.º que estiver instalado é usado). */
        val packages: List<String>,
        /** Palavras (minúsculas) que identificam esta plataforma na lista "Onde ver". */
        val providerKeywords: List<String>,
        /** Ligação direta ao título; recebe o item e o id IMDb (se existir). Null = só abre a app. */
        val deepLink: ((CatalogItem, String?) -> String?)? = null,
    )

    private fun enc(s: String): String = Uri.encode(s)

    private val builtinApps: List<AppTarget> = listOf(
        AppTarget(
            label = "Stremio",
            packages = listOf("com.stremio.one"),
            providerKeywords = emptyList(),
            deepLink = { item, imdb -> stremioLink(item, imdb) },
        ),
        AppTarget(
            label = "Netflix",
            packages = listOf("com.netflix.ninja", "com.netflix.mediaclient"),
            providerKeywords = listOf("netflix"),
            // A Netflix só tem ligação direta com o id interno dela (que o TMDB não dá).
            // Tenta a pesquisa; se a app não a aceitar, abre-se a app.
            deepLink = { item, _ -> "https://www.netflix.com/search?q=${enc(item.title)}" },
        ),
        AppTarget(
            label = "Prime Video",
            packages = listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient"),
            providerKeywords = listOf("prime video", "amazon"),
        ),
        AppTarget(
            label = "Disney+",
            packages = listOf("com.disney.disneyplus"),
            providerKeywords = listOf("disney"),
        ),
        AppTarget(
            label = "Crunchyroll",
            packages = listOf("com.crunchyroll.crunchyroid"),
            providerKeywords = listOf("crunchyroll"),
        ),
    )

    /** Apps conhecidas + as que o utilizador juntou nas Definições. */
    fun allApps(): List<AppTarget> = builtinApps + Repository.customApps().map { c ->
        AppTarget(
            label = c.label,
            packages = listOf(c.pkg),
            // Só usa o nome como palavra-chave se for comprido (evita "TV" apanhar "Apple TV").
            providerKeywords = if (c.label.length >= 4) listOf(c.label.lowercase()) else emptyList(),
            deepLink = c.template?.let { t -> { item, imdb -> fillTemplate(t, item, imdb) } },
        )
    }

    /** App instalada na TV que se pode juntar (aparece em Definições). */
    class InstalledApp(val label: String, val pkg: String)

    /** Apps que aparecem no ecrã inicial da TV (precisa do <queries> com intent no manifest). */
    fun launchableApps(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        return listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)
            .flatMap { c -> pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(c), 0) }
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName
                if (pkg == null || pkg == context.packageName) null
                else InstalledApp(ri.loadLabel(pm).toString(), pkg)
            }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    private fun slug(title: String): String =
        Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')

    /**
     * Preenche um endereço com os dados do título. Devolve null se o endereço precisa de algo que
     * este título não tem (ex.: {imdb} num anime, ou antes de o id IMDb chegar).
     *
     * {query}/{title} nome (codificado) · {slug} nome-em-minúsculas-com-hifens · {year} ano
     * {imdb} id IMDb · {tmdb} id TMDB (filmes/séries) · {anilist} id AniList (animes)
     * {type} "movie" ou "tv"
     */
    fun fillTemplate(template: String, item: CatalogItem, imdbId: String?): String? {
        val anime = item.type == MediaType.ANIME
        if (template.contains("{imdb}") && imdbId == null) return null
        if (template.contains("{tmdb}") && anime) return null
        if (template.contains("{anilist}") && !anime) return null
        return template
            .replace("{query}", enc(item.title))
            .replace("{title}", enc(item.title))
            .replace("{slug}", slug(item.title))
            .replace("{year}", item.year.orEmpty())
            .replace("{imdb}", imdbId.orEmpty())
            .replace("{tmdb}", item.sourceId.toString())
            .replace("{anilist}", item.sourceId.toString())
            .replace("{type}", if (item.type == MediaType.MOVIE) "movie" else "tv")
    }

    /** Stremio: título exato se houver id IMDb (filmes/séries); senão, pesquisa pelo nome. */
    private fun stremioLink(item: CatalogItem, imdb: String?): String = when {
        imdb != null && item.type == MediaType.MOVIE -> "stremio:///detail/movie/$imdb/$imdb"
        imdb != null -> "stremio:///detail/series/$imdb"
        else -> "stremio:///search?search=${enc(item.title)}"
    }

    private fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
    }

    /** Pacote instalado desta app, ou null se não estiver na TV. */
    fun installedPackage(context: Context, target: AppTarget): String? =
        target.packages.firstOrNull { launchIntent(context, it) != null }

    /** Que app conhecida corresponde a uma plataforma da lista "Onde ver" (ex.: "Netflix")? */
    fun appForProvider(providerName: String): AppTarget? {
        val n = providerName.lowercase()
        return allApps().firstOrNull { t -> t.providerKeywords.any { n.contains(it) } }
    }

    /**
     * Abre o título na app: primeiro a ligação direta; se a app não a aceitar, só abre a app.
     * Devolve false se a app não estiver instalada ou nada funcionou.
     */
    fun openInApp(context: Context, target: AppTarget, item: CatalogItem, imdbId: String?): Boolean {
        val pkg = installedPackage(context, target) ?: return false
        val uri = target.deepLink?.invoke(item, imdbId)
        if (uri != null) {
            val ok = runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                        .setPackage(pkg)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (ok) return true
        }
        val launch = launchIntent(context, pkg) ?: return false
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    /**
     * Abre um endereço no navegador (ou na app que o trate). Se a TV não tiver nenhum navegador,
     * abre-o no navegador embutido da Hoshi. False só se nem isso for possível.
     */
    fun openUrl(context: Context, url: String): Boolean {
        val external = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        return external || openInternal(context, url)
    }

    /** Navegador embutido da Hoshi (WebView), para TVs sem navegador. */
    private fun openInternal(context: Context, url: String): Boolean = runCatching {
        context.startActivity(
            Intent(context, WebViewActivity::class.java)
                .putExtra(WebViewActivity.EXTRA_URL, url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
}
