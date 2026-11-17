package com.example.ellipsuslite

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.hardware.input.InputManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

private const val START_URL = "https://write.ellipsus.com"

// Pretend to be desktop Chrome so the site serves its desktop version.
private const val DESKTOP_UA =
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

// Layout width in CSS pixels. Lower = bigger UI (1024/1100), higher = smaller (1366).
private const val DESKTOP_VIEWPORT_WIDTH = 1280

private const val VIEWPORT_SCRIPT = """
(function () {
  var desired = 'width=$DESKTOP_VIEWPORT_WIDTH';
  function apply() {
    var m = document.querySelector('meta[name=viewport]');
    if (!m) {
      m = document.createElement('meta');
      m.name = 'viewport';
      (document.head || document.documentElement).appendChild(m);
    }
    if (m.content !== desired) m.content = desired;
  }
  apply();
  if (document.head && !window.__vpObs) {
    window.__vpObs = new MutationObserver(apply);
    window.__vpObs.observe(document.head, {
      childList: true, subtree: true, attributes: true, attributeFilter: ['content']
    });
  }
})();
"""

private const val ERROR_HTML = """
<html><body style="font-family:sans-serif;text-align:center;padding-top:30vh">
<p>Couldn't load Ellipsus.<br>Check your connection.</p>
<button style="font-size:18px;padding:10px 24px"
        onclick="location.href='$START_URL'">Retry</button>
</body></html>
"""

class MainActivity : Activity(), InputManager.InputDeviceListener {

    private val autoHideSoftKeyboard = true
    private val reqFile = 1001

    private lateinit var webView: WebView
    private lateinit var inputManager: InputManager
    private var hasKeyboard = false
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        if (autoHideSoftKeyboard && hasKeyboard && isSoftKeyboardShowing()) hideSoftKeyboard()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        window.setBackgroundDrawableResource(android.R.color.white)

        webView = WebView(this)
        webView.setBackgroundColor(Color.WHITE)
        setContentView(webView)
        keepSystemBarsHidden()

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                view.evaluateJavascript(VIEWPORT_SCRIPT, null)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(VIEWPORT_SCRIPT, null)
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    view.loadDataWithBaseURL(null, ERROR_HTML, "text/html", "utf-8", null)
                }
            }
        }

        // Needed so "import file" buttons on the site can open the file picker.
        webView.webChromeClient = object : WebChromeClient() {
            @Suppress("DEPRECATION")
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    startActivityForResult(params.createIntent(), reqFile)
                    true
                } catch (e: Exception) {
                    filePathCallback = null
                    false
                }
            }
        }

        if (savedInstanceState != null) webView.restoreState(savedInstanceState)
        else webView.loadUrl(START_URL)

        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(this, null)
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        refreshKeyboardState()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == reqFile) {
            filePathCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            )
            filePathCallback = null
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    // ---- Fullscreen -------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    }

    // If a bar slides in (e.g. after an edge swipe), hide it again after 2 seconds.
    @Suppress("DEPRECATION")
    private fun keepSystemBarsHidden() {
        val handler = Handler(Looper.getMainLooper())
        window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
            if ((visibility and View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                handler.postDelayed({ hideSystemBars() }, 2000)
            }
        }
        hideSystemBars()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ---- Back button / lifecycle -----------------------------------------

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        refreshKeyboardState()
    }

    override fun onDestroy() {
        inputManager.unregisterInputDeviceListener(this)
        val vto = window.decorView.viewTreeObserver
        if (vto.isAlive) vto.removeOnGlobalLayoutListener(layoutListener)
        webView.destroy()
        super.onDestroy()
    }

    // ---- Hide on-screen keyboard while a physical keyboard is connected ---

    override fun onInputDeviceAdded(deviceId: Int) = refreshKeyboardState()
    override fun onInputDeviceRemoved(deviceId: Int) = refreshKeyboardState()
    override fun onInputDeviceChanged(deviceId: Int) = refreshKeyboardState()

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (autoHideSoftKeyboard) {
            val d = event.device
            if (d != null && !d.isVirtual &&
                d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
            ) {
                hasKeyboard = true
                if (isSoftKeyboardShowing()) hideSoftKeyboard()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun hasPhysicalKeyboard(): Boolean {
        val byDevice = InputDevice.getDeviceIds().any { id ->
            val device = InputDevice.getDevice(id)
            device != null &&
                    !device.isVirtual &&
                    (device.sources and InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD &&
                    device.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC
        }
        val cfg = resources.configuration
        val byConfig = cfg.keyboard == Configuration.KEYBOARD_QWERTY &&
                cfg.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES
        return byDevice || byConfig
    }

    private fun refreshKeyboardState() {
        hasKeyboard = autoHideSoftKeyboard && hasPhysicalKeyboard()
        if (hasKeyboard && isSoftKeyboardShowing()) hideSoftKeyboard()
    }

    private fun isSoftKeyboardShowing(): Boolean {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        val visible = Rect()
        window.decorView.getWindowVisibleDisplayFrame(visible)
        val realHeight = metrics.heightPixels
        return realHeight > 0 && realHeight - visible.bottom > realHeight * 0.15
    }

    private fun hideSoftKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(window.decorView.windowToken, 0)
    }
}