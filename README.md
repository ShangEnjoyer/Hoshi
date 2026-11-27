# Hoshi — movie, TV & anime catalog for Android TV

**Hoshi** (星, "star") is a catalog app for Android TV, built with Kotlin and Jetpack Compose for TV.
Made by **ShangEnjoyer**. It helps you find what to watch and where to watch it. It is catalog-only: it does not play any video.

## Features

- **Movies and TV shows** from TMDB: trending, upcoming / on the air, popular and top rated, with
  **where to watch** for your country (subscription, rent / buy).
- **Anime** from AniList: trending, popular, top rated and **everything airing today**, whether or not it is on a platform.
- **Week tab:** a calendar of anime episodes for the whole week, opened on today.
- **Search** across movies, TV shows and anime.
- **Titles under every poster**, even when the artwork has no text.
- **Remembers where you were:** when you leave a detail screen you come back to the same row,
  scroll position and poster (and your search results).
- **Works in your language:** the interface and TMDB titles / synopses follow the TV's language and region.
  Interface translations: English, Portuguese (PT and BR), Spanish, French, German, Italian, Dutch, Polish,
  Russian, Turkish, Japanese, Korean, Chinese, Arabic, Hindi and Indonesian. Anime synopses come from AniList (English).
- **My list:** add any title to **Watchlist** or **Favorites** from its detail screen. Both are saved on the TV
  (they survive restarts) and live in the **My list** tab.
- **Genre filters:** in Movies, TV Shows and Anime, pick a genre from the bar at the top to see only that genre.
- **Open in an app:** on a title's detail screen, open it directly in an installed app (**Stremio**, Netflix,
  Prime Video, Disney+, Crunchyroll). Stremio opens the exact title (via its IMDb id). The streaming platforms in
  "Where to watch" also open their app when it is installed.
- **Links open anywhere:** the platform links in "Where to watch" open in their app when it is installed; otherwise in the
  TV's browser or, if the TV has none, in Hoshi's **built-in browser** (arrows move a cursor, **OK** clicks, **Back** goes
  back / closes; near the screen edge the cursor scrolls the page).
- **Your own apps and sites, straight to the title:** in **Settings → Apps to open titles in** pick any app
  installed on the TV (optionally with a link template). Sites and links use markers:
  `{query}` title · `{slug}` title-with-dashes · `{year}` · `{imdb}` IMDb id · `{tmdb}` TMDB id (movies/TV) ·
  `{anilist}` AniList id (anime) · `{type}` `movie` or `tv`.
  Examples: `https://www.imdb.com/title/{imdb}/`, `https://www.themoviedb.org/{type}/{tmdb}`,
  `https://example.com/search?q={query}`. A link only goes straight to the title if the site accepts one of
  these ids or the title slug; otherwise use `{query}` to open its search. Entries that need an id a title
  does not have (e.g. `{imdb}` on an anime) are hidden for that title.
- **Easy key setup from your phone:** in the **Settings** tab, scan the QR code (or open the address shown) on a phone
  on the same Wi-Fi, paste your TMDB key there and it is sent to the TV. You can also type it with the remote.
  Anime works without a key.

## Install on Android TV

1. Download `app-debug.apk` from **Releases** (or from a build's **Artifacts**, see below).
2. Install it with the **Downloader** app on the TV (open the APK link), or with ADB:
   `adb connect TV_IP` then `adb install -r app-debug.apk`.

You may need to allow "install unknown apps" for the app you use.

## Get a TMDB API key (free)

Movies and TV shows need a TMDB key; anime (AniList) works without one.

1. Create an account at https://www.themoviedb.org and request a key under Settings → API.
2. Use the **"API Key"** (v3).
3. In Hoshi, open the **Settings** tab and either scan the QR code with your phone (same Wi-Fi) and paste the key
   there, or type it with the remote.

## Build it yourself

With GitHub (no Android Studio needed):

1. Fork this repository (or upload the files to a new one, on the `main` branch).
2. Go to **Actions → Build APK → Run workflow** (it also runs on every push to `main`).
3. Download `Hoshi-debug` from **Artifacts**. It contains `app-debug.apk`.

To attach the APK to a release, push a version tag such as `v1.5`.

> **Do not set a `TMDB_API_KEY` secret for builds you share.** It would be embedded in the APK and anyone could extract
> it. Leave it empty and enter the key in the app instead.

Locally: use `.\gradlew.bat testDebugUnitTest assembleDebug` on Windows, or `bash ./gradlew testDebugUnitTest assembleDebug` on macOS/Linux (JDK 17 and the Android SDK are required). You can also open the folder in Android Studio and run it on a "Television (1080p)" emulator or a real TV.

## Credits

This product uses the TMDB API but is not endorsed or certified by TMDB. Availability data provided by JustWatch.
Anime data provided by AniList.

## License

MIT © ShangEnjoyer. See [LICENSE](LICENSE).
