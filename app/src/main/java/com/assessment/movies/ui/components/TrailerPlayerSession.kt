package com.assessment.movies.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.ViewGroup
import android.view.View.OnLayoutChangeListener
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.assessment.movies.BuildConfig

internal class TrailerPlayerSession(private val key: String, private val onError: (Int) -> Unit) {
    private var browser: WebView? = null
    private var destroyed = false

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context, openExternal: (android.net.Uri) -> Unit): WebView {
        check(browser == null && !destroyed)
        return object : WebView(context) {
            private var loaded = false
            private val layoutReady = OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> loadWhenReady() }
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                addOnLayoutChangeListener(layoutReady)
                post { loadWhenReady() }
            }
            override fun onDetachedFromWindow() {
                removeOnLayoutChangeListener(layoutReady)
                super.onDetachedFromWindow()
            }
            private fun loadWhenReady() {
                if (!loaded && !destroyed && isAttachedToWindow && width > 0 && height > 0) {
                    loaded = true
                    if (BuildConfig.DEBUG) Log.d("TrailerPlayer", "attached hardware=$isHardwareAccelerated size=${width}x$height provider=${getCurrentWebViewPackage()?.versionName}")
                    loadDataWithBaseURL(PLAYER_ORIGIN, trailerHtml(key), "text/html", "utf-8", null)
                }
            }
        }.apply {
            browser = this
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.BLACK)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = false
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "com.assessment.movies") return false
                    if (uri.scheme == "https") { pause(); openExternal(uri) }
                    return true
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) onError(-1)
                }
                override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                    onError(-2)
                    (view.parent as? ViewGroup)?.removeView(view)
                    destroy(view, stopLoading = false)
                    return true
                }
            }
        }
    }

    fun evaluate(script: String, result: ((String?) -> Unit)? = null) {
        if (!destroyed) browser?.evaluateJavascript(script) { if (!destroyed) result?.invoke(it) }
    }

    fun pause() { evaluate("window.moviePlayer?.pauseVideo()") }
    fun background() { pause(); if (!destroyed) browser?.onPause() }
    fun foreground() { if (!destroyed) browser?.onResume() }

    fun release(view: WebView) {
        destroy(view, stopLoading = true)
    }

    private fun destroy(view: WebView, stopLoading: Boolean) {
        if (destroyed) return
        destroyed = true
        browser = null
        if (stopLoading) view.stopLoading()
        view.destroy()
    }
}
