package com.example.tvcatalog

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback

/**
 * Navegador mínimo dentro da Hoshi, usado quando a TV não tem nenhum navegador instalado.
 *
 * Um WebView não se controla bem só com as setas, por isso há um cursor:
 *  - setas: mexem o cursor (acelera se ficares com a seta premida); nas margens, a página desliza;
 *  - OK / Enter: clica onde está o cursor;
 *  - Voltar: sai do ecrã inteiro, volta à página anterior e, na primeira página, fecha.
 */
class WebViewActivity : ComponentActivity() {

    private lateinit var web: WebView
    private lateinit var root: FrameLayout
    private lateinit var cursor: CursorView
    private lateinit var progressBar: ProgressBar

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val url = intent.getStringExtra(EXTRA_URL)
            ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        if (url == null) {
            finish()
            return
        }

        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        web = WebView(this).apply {
            setBackgroundColor(Color.BLACK)
            isFocusable = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                // Fica dentro da Hoshi em http/https; outros esquemas (intent:, market:, …) são ignorados.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme
                    return scheme != "http" && scheme != "https"
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    this@WebViewActivity.progressBar.progress = newProgress
                    this@WebViewActivity.progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }

                // Vídeo em ecrã inteiro (botão de fullscreen do leitor do site).
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullscreenView != null) {
                        callback.onCustomViewHidden()
                        return
                    }
                    fullscreenView = view
                    fullscreenCallback = callback
                    web.visibility = View.GONE
                    cursor.visibility = View.GONE
                    root.addView(
                        view,
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                }

                override fun onHideCustomView() = exitFullscreen()
            }
        }
        root.addView(
            web,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        cursor = CursorView(this)
        root.addView(
            cursor,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
        }
        root.addView(
            progressBar,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (4 * resources.displayMetrics.density).toInt()),
        )

        setContentView(root)
        // O cursor começa no meio do ecrã.
        root.post { cursor.moveTo(root.width / 2f, root.height / 2f) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    fullscreenView != null -> exitFullscreen()
                    web.canGoBack() -> web.goBack()
                    else -> finish()
                }
            }
        })

        web.loadUrl(url)
    }

    private fun exitFullscreen() {
        val v = fullscreenView ?: return
        root.removeView(v)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        web.visibility = View.VISIBLE
        cursor.visibility = View.VISIBLE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Em ecrã inteiro, as teclas vão para o leitor do site.
        if (fullscreenView != null) return super.dispatchKeyEvent(event)

        val density = resources.displayMetrics.density
        val step = (10 + minOf(event.repeatCount, 25) * 2) * density

        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.action == KeyEvent.ACTION_DOWN) moveCursor(-step, 0f)
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) moveCursor(step, 0f)
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (event.action == KeyEvent.ACTION_DOWN) moveCursor(0f, -step)
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (event.action == KeyEvent.ACTION_DOWN) moveCursor(0f, step)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (event.action == KeyEvent.ACTION_UP) tap()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /** Move o cursor; se já está na margem, em vez de sair do ecrã faz deslizar a página. */
    private fun moveCursor(dx: Float, dy: Float) {
        val margin = 24 * resources.displayMetrics.density
        val nx = cursor.cx + dx
        val ny = cursor.cy + dy
        val maxX = root.width.toFloat()
        val maxY = root.height.toFloat()

        var scrollX = 0
        var scrollY = 0
        if (ny > maxY - margin && dy > 0) scrollY = (dy * 2).toInt()
        if (ny < margin && dy < 0) scrollY = (dy * 2).toInt()
        if (nx > maxX - margin && dx > 0) scrollX = (dx * 2).toInt()
        if (nx < margin && dx < 0) scrollX = (dx * 2).toInt()
        if (scrollX != 0 || scrollY != 0) web.scrollBy(scrollX, scrollY)

        cursor.moveTo(nx.coerceIn(0f, maxX), ny.coerceIn(0f, maxY))
    }

    /** Um toque do dedo no sítio do cursor (carrega em ligações, botões, caixas de texto…). */
    private fun tap() {
        val x = cursor.cx
        val y = cursor.cy
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, x, y, 0)
        web.dispatchTouchEvent(down)
        down.recycle()
        web.postDelayed({
            val up = MotionEvent.obtain(t, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, x, y, 0)
            web.dispatchTouchEvent(up)
            up.recycle()
        }, 60)
    }

    override fun onPause() {
        super.onPause()
        web.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        if (::root.isInitialized) {
            root.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }

    /** O ponteiro: um círculo claro com contorno roxo. */
    private class CursorView(context: Context) : View(context) {
        var cx = 0f
            private set
        var cy = 0f
            private set

        private val radius = 9 * resources.displayMetrics.density
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xE6FFFFFF.toInt() }
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * resources.displayMetrics.density
            color = 0xFF7C3AED.toInt()
        }

        init {
            isFocusable = false
            isClickable = false
        }

        fun moveTo(x: Float, y: Float) {
            cx = x
            cy = y
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            canvas.drawCircle(cx, cy, radius, fill)
            canvas.drawCircle(cx, cy, radius, ring)
        }
    }

    companion object {
        const val EXTRA_URL = "url"
    }
}
