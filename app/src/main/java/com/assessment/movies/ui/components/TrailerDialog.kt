package com.assessment.movies.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.assessment.movies.R
import com.assessment.movies.data.Trailer
import com.assessment.movies.ui.LoadState
import com.assessment.movies.ui.MovieStyle
import com.assessment.movies.ui.StatusMessage
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun TrailerDialog(
    state: LoadState<Trailer>,
    movieTitle: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    openExternal: (Uri) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.padding(16.dp).widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 650.dp).testTag("trailer-dialog"),
            shape = RoundedCornerShape(18.dp), color = MovieStyle.Canvas,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.trailer), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.close_trailer)) }
                }
                Text(movieTitle, style = MaterialTheme.typography.titleMedium)
                when {
                    state.loading -> {
                        PlayerSkeleton(Modifier.fillMaxWidth().height(220.dp))
                        Text(stringResource(R.string.loading_video), style = MaterialTheme.typography.bodySmall, color = MovieStyle.Muted)
                    }
                    state.error != null -> StatusMessage(stringResource(state.error), onRetry)
                    state.data == null -> StatusMessage(stringResource(R.string.no_trailer))
                    else -> YouTubePlayer(state.data.key, openExternal)
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubePlayer(videoKey: String, openExternal: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var playback by remember(videoKey) { mutableStateOf(PlaybackState()) }
    var seekPosition by remember(videoKey) { mutableStateOf<Float?>(null) }
    val released = remember(videoKey) { AtomicBoolean(false) }
    val webView = remember(videoKey) {
        WebView(context).apply {
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
                    if (uri.scheme == "https") openExternal(uri)
                    return true
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) playback = playback.copy(error = -1)
                }
            }
            loadDataWithBaseURL(PLAYER_ORIGIN, trailerHtml(videoKey), "text/html", "utf-8", null)
        }
    }
    DisposableEffect(webView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (!released.get()) when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView.evaluateJavascript("window.moviePlayer?.pauseVideo()", null)
                    webView.onPause()
                }
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(webView, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive && !released.get()) {
                webView.evaluateJavascript("window.readMovieState ? window.readMovieState() : null") { raw ->
                    if (!released.get()) parsePlaybackState(raw)?.let {
                        playback = if (!it.ready && it.error == 0) it.copy(error = playback.error) else it
                    }
                }
                delay(500)
            }
        }
    }
    LaunchedEffect(webView) {
        delay(20_000)
        if (!released.get() && !playback.ready && playback.error == 0) playback = playback.copy(error = -1)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(maxOf(maxWidth * 9f / 16f, 200.dp))) {
            AndroidView(
                factory = { webView }, modifier = Modifier.fillMaxSize(),
                onReset = null,
                onRelease = { released.set(true); it.stopLoading(); it.destroy() },
            )
            if (!playback.ready && playback.error == 0) PlayerSkeleton(Modifier.fillMaxSize())
        }
    }
    val ready = playback.ready && playback.duration > 0 && playback.error == 0
    val seekLabel = stringResource(R.string.video_position)
    Slider(
        value = (seekPosition ?: playback.position.toFloat()).coerceIn(0f, maxOf(1f, playback.duration.toFloat())),
        onValueChange = { seekPosition = it },
        onValueChangeFinished = {
            seekPosition?.let { webView.evaluateJavascript("window.moviePlayer?.seekTo(${it.toDouble()},true)", null) }
            seekPosition = null
        },
        valueRange = 0f..maxOf(1f, playback.duration.toFloat()), enabled = ready,
        modifier = Modifier.testTag("video-seek").semantics { contentDescription = seekLabel },
    )
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ready) "${formatPlaybackTime(playback.position)} / ${formatPlaybackTime(playback.duration)}" else "--:-- / --:--",
            Modifier.weight(1f).testTag("video-time"), style = MaterialTheme.typography.bodySmall,
        )
        TextButton(enabled = ready, onClick = {
            webView.evaluateJavascript(if (playback.playing) "window.moviePlayer?.pauseVideo()" else "window.moviePlayer?.playVideo()", null)
        }) { Text(stringResource(if (playback.playing) R.string.pause_video else R.string.play_video)) }
    }
    if (playback.error != 0) Text(stringResource(R.string.trailer_error), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.player_hint), style = MaterialTheme.typography.bodySmall, color = MovieStyle.Muted)
    TextButton(onClick = { openExternal("https://www.youtube.com/watch?v=$videoKey".toUri()) }) {
        Text(stringResource(R.string.open_youtube))
    }
}

internal data class PlaybackState(val ready: Boolean = false, val playing: Boolean = false, val position: Double = 0.0, val duration: Double = 0.0, val error: Int = 0)

internal fun parsePlaybackState(raw: String?): PlaybackState? {
    if (raw == null) return null
    return runCatching {
        val outer = JsonParser.parseString(raw)
        if (outer.isJsonNull) return@runCatching null
        val data = if (outer.isJsonPrimitive) JsonParser.parseString(outer.asString).asJsonObject else outer.asJsonObject
        fun seconds(name: String): Double = data[name]?.asDouble?.takeIf { it.isFinite() }?.coerceIn(0.0, 172800.0) ?: 0.0
        PlaybackState(data["ready"]?.asBoolean == true, data["playing"]?.asBoolean == true, seconds("position"), seconds("duration"), data["error"]?.asInt ?: 0)
    }.getOrNull()
}

internal fun formatPlaybackTime(seconds: Double): String {
    val value = if (seconds.isFinite()) seconds.coerceIn(0.0, 172800.0).toInt() else 0
    return if (value >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", value / 3600, value / 60 % 60, value % 60)
    else String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60)
}

private const val PLAYER_ORIGIN = "https://com.assessment.movies/"

internal fun trailerHtml(key: String): String {
    require(key.matches(Regex("[A-Za-z0-9_-]{11}")))
    return """
        <!doctype html><html lang="id"><head>
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <meta name="referrer" content="strict-origin-when-cross-origin"><title>Trailer film</title>
        <style>html,body,main{margin:0;width:100%;height:100%;background:#000}iframe{display:block;border:0;width:100%;height:100%}</style>
        </head><body><main>
        <iframe id="player" title="Trailer YouTube" width="640" height="360"
        src="https://www.youtube.com/embed/$key?autoplay=1&amp;controls=1&amp;fs=0&amp;playsinline=1&amp;enablejsapi=1&amp;origin=https%3A%2F%2Fcom.assessment.movies"
        allow="autoplay; encrypted-media; picture-in-picture" referrerpolicy="strict-origin-when-cross-origin"></iframe>
        </main><script>
        window.videoState={ready:false,error:0};window.moviePlayer=null;
        function onYouTubeIframeAPIReady(){window.moviePlayer=new YT.Player('player',{events:{
          onReady:function(event){window.videoState.ready=true;event.target.playVideo();},
          onError:function(event){window.videoState.error=event.data;}
        }});}
        window.readMovieState=function(){var p=window.moviePlayer,s=window.videoState;
          return JSON.stringify({ready:s.ready,error:s.error,playing:s.ready&&p.getPlayerState()===1,
          position:s.ready?p.getCurrentTime():0,duration:s.ready?p.getDuration():0});};
        </script><script async src="https://www.youtube.com/iframe_api"></script></body></html>
    """.trimIndent()
}
