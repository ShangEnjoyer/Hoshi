package com.example.tvcatalog.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import com.example.tvcatalog.R
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import java.util.Base64
import kotlin.concurrent.thread

internal fun newPairingToken(random: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(24)
    random.nextBytes(bytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

internal fun parseTmdbKey(body: String): String? {
    val raw = body.split("&").firstOrNull { it.startsWith("key=") }?.removePrefix("key=") ?: return null
    val key = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()?.trim() ?: return null
    return key.takeIf { Regex("^[A-Za-z0-9]{32}$").matches(it) }
}

/**
 * Pequeno servidor web local, ativo só enquanto o ecrã de Definições está aberto.
 * Permite colar a chave do TMDB no telemóvel (mesma rede Wi-Fi) em vez de a escrever com o comando.
 * O endereço usa um token temporário de uso único para reduzir o risco de acesso por outros aparelhos.
 */
class KeyServer(
    context: Context,
    private val onKey: (String) -> Unit,
) {
    private val context = context.applicationContext
    private val token = newPairingToken()
    private val pairingLock = Any()

    private var expiresAt = 0L
    private var attempts = 0
    private var tokenConsumed = false

    @Volatile
    private var server: ServerSocket? = null

    /** Depois de fechado de vez (ecrã saiu), start() já não abre nada, mesmo que chegue atrasado. */
    @Volatile
    private var closed = false

    /** Abre o servidor e devolve o endereço para abrir no telemóvel, ou null se a TV não tiver rede. */
    fun start(): String? {
        stop()
        val ip = localIp() ?: return null
        val ss = bind() ?: return null
        if (closed) {
            runCatching { ss.close() }
            return null
        }
        synchronized(pairingLock) {
            expiresAt = SystemClock.elapsedRealtime() + TOKEN_TTL_MS
            attempts = 0
            tokenConsumed = false
        }
        server = ss
        thread(isDaemon = true, name = "key-server") {
            while (!ss.isClosed) {
                try {
                    handle(ss.accept())
                } catch (_: Exception) {
                    if (ss.isClosed) break
                }
            }
        }
        return "http://$ip:${ss.localPort}/$token"
    }

    /** Fecha de vez: usar quando o ecrã das Definições sai. */
    fun close() {
        closed = true
        stop()
    }

    fun stop() {
        runCatching { server?.close() }
        server = null
    }

    private fun bind(): ServerSocket? {
        for (port in 8765..8775) {
            try {
                return ServerSocket(port)
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun localIp(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    private fun handle(socket: Socket) {
        thread(isDaemon = true) {
            // Os browsers abrem por vezes ligações vazias (pré-ligação). O timeout ou um
            // erro de rede aqui não pode rebentar a app: uma exceção solta numa thread mata o processo.
            try {
                socket.use { s ->
                    s.soTimeout = 5000
                    val input = s.getInputStream().bufferedReader(Charsets.ISO_8859_1)
                    val requestLine = input.readLine() ?: return@thread
                    val parts = requestLine.split(" ")
                    if (parts.size < 2) return@thread
                    val method = parts[0]
                    val path = parts[1].substringBefore('?')

                    var length = 0
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        if (line.startsWith("content-length:", ignoreCase = true)) {
                            length = line.substringAfter(':').trim().toIntOrNull() ?: 0
                        }
                    }

                    val out = s.getOutputStream()
                    when {
                        method == "GET" && path == "/$token" && tokenIsActive() -> send(out, 200, formPage())
                        method == "POST" && path == "/$token" -> {
                            val buf = CharArray(length.coerceIn(0, 2000))
                            var read = 0
                            while (read < buf.size) {
                                val n = input.read(buf, read, buf.size - read)
                                if (n < 0) break
                                read += n
                            }
                            val key = parseTmdbKey(String(buf, 0, read))
                            val acceptedKey = submitKey(key)
                            if (acceptedKey != null) {
                                Handler(Looper.getMainLooper()).post { onKey(acceptedKey) }
                                stop()
                                send(out, 200, messagePage(context.getString(R.string.phone_page_ok), back = false))
                            } else {
                                send(out, 400, messagePage(context.getString(R.string.phone_page_bad), back = true))
                            }
                        }
                        else -> send(out, 404, "Not found")
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun tokenIsActive(): Boolean = synchronized(pairingLock) {
        !closed && !tokenConsumed && attempts < MAX_ATTEMPTS &&
            SystemClock.elapsedRealtime() < expiresAt
    }

    private fun submitKey(key: String?): String? = synchronized(pairingLock) {
        if (closed || tokenConsumed || attempts >= MAX_ATTEMPTS ||
            SystemClock.elapsedRealtime() >= expiresAt
        ) {
            return@synchronized null
        }
        attempts++
        if (key == null) return@synchronized null
        tokenConsumed = true
        key
    }

    private fun send(out: java.io.OutputStream, code: Int, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val status = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            else -> "Not Found"
        }
        out.write(
            ("HTTP/1.1 $code $status\r\nContent-Type: text/html; charset=utf-8\r\n" +
                "Content-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n")
                .toByteArray(Charsets.ISO_8859_1)
        )
        out.write(bytes)
        out.flush()
    }

    private fun esc(s: String) = TextUtils.htmlEncode(s)

    private val style = "body{font-family:sans-serif;background:#0e0e12;color:#fff;padding:24px;max-width:480px;margin:auto}" +
        "input,button{width:100%;box-sizing:border-box;font-size:18px;padding:14px;border-radius:10px;border:0;margin-top:12px}" +
        "input{background:#24242c;color:#fff}button{background:#7c3aed;color:#fff;font-weight:bold}a{color:#b8b8c2}"

    private fun formPage(): String {
        val title = esc(context.getString(R.string.phone_page_title))
        val label = esc(context.getString(R.string.phone_page_label))
        val button = esc(context.getString(R.string.phone_page_button))
        return "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>$title</title><style>$style</style></head><body>" +
            "<h2>$title</h2>" +
            "<form method=\"post\" action=\"/$token\"><label>$label</label>" +
            "<input name=\"key\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\" required>" +
            "<button type=\"submit\">$button</button></form>" +
            "<p><a href=\"https://www.themoviedb.org/settings/api\" target=\"_blank\">themoviedb.org/settings/api</a></p>" +
            "</body></html>"
    }

    private fun messagePage(message: String, back: Boolean): String {
        val title = esc(context.getString(R.string.phone_page_title))
        return "<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
            "<title>$title</title><style>$style</style></head><body>" +
            "<h2>$title</h2><p>${esc(message)}</p>" +
            (if (back) "<p><a href=\"/$token\">&larr;</a></p>" else "") +
            "</body></html>"
    }

    companion object {
        private const val TOKEN_TTL_MS = 5 * 60 * 1000L
        private const val MAX_ATTEMPTS = 5
    }
}
