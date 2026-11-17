package com.assessment.movies.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import com.assessment.movies.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TrailerScreen(videoKey: String, title: String, openExternal: (Uri) -> Unit) {
    val validKey = videoKey.matches(Regex("[A-Za-z0-9_-]{11}"))
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var failed by rememberSaveable(videoKey) { mutableStateOf(false) }
    val webView = remember(videoKey) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (!request.isForMainFrame) return false
                    val uri = request.url
                    if (uri.scheme == "https" && uri.host == "com.assessment.movies") return false
                    if (uri.scheme == "https") openExternal(uri)
                    return true
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) failed = true
                }
            }
            if (validKey) loadDataWithBaseURL(PLAYER_ORIGIN, trailerHtml(videoKey), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(webView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView.evaluateJavascript("document.querySelector('iframe')?.contentWindow.postMessage(JSON.stringify({event:'command',func:'pauseVideo',args:[]}), 'https://www.youtube.com');", null)
                    webView.onPause()
                }
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            webView.stopLoading()
            webView.destroy()
        }
    }
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        if (validKey) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                AndroidView(
                    factory = { webView },
                    modifier = Modifier.fillMaxWidth().height(maxOf(maxWidth * 9f / 16f, 200.dp)),
                )
            }
            Text(stringResource(R.string.player_hint))
            if (failed) StatusMessage(stringResource(R.string.trailer_error), retry = {
                failed = false
                webView.loadDataWithBaseURL(PLAYER_ORIGIN, trailerHtml(videoKey), "text/html", "utf-8", null)
            })
            Button(onClick = { openExternal("https://www.youtube.com/watch?v=$videoKey".toUri()) }) {
                Text(stringResource(R.string.open_youtube))
            }
        } else Text(stringResource(R.string.no_trailer))
    }
}

private const val PLAYER_ORIGIN = "https://com.assessment.movies/"

internal fun trailerHtml(key: String): String {
    require(key.matches(Regex("[A-Za-z0-9_-]{11}")))
    return """
        <!doctype html>
        <html lang="en"><head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin">
        <title>Movie trailer</title>
        <style>html,body{margin:0;width:100%;height:100%;background:#000}iframe{display:block;border:0;width:100%;height:100%}</style>
        </head><body><main style="width:100%;height:100%">
        <iframe title="YouTube movie trailer" width="640" height="360"
        src="https://www.youtube.com/embed/$key?playsinline=1&amp;enablejsapi=1&amp;origin=https%3A%2F%2Fcom.assessment.movies"
        allow="encrypted-media; picture-in-picture" referrerpolicy="strict-origin-when-cross-origin"></iframe>
        </main></body></html>
    """.trimIndent()
}
