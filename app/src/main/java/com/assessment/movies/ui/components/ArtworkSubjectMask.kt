package com.assessment.movies.ui.components

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import coil3.BitmapImage
import coil3.Image
import coil3.toBitmap
import com.assessment.movies.data.Movie
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

data class ArtworkSubjectMask(val inverseAlpha: ImageBitmap, val focus: Offset)

fun movieMaskKey(movie: Movie, origin: MovieArtworkOrigin): String {
    val path = if (origin == MovieArtworkOrigin.Featured) movie.backdropPath ?: movie.posterPath else movie.posterPath
    return "${movieArtworkKey(movie.id, origin)}/$path"
}

/** Owns a small, in-memory mask cache; navigation never waits for model installation or inference. */
class ArtworkMasksViewModel(application: Application) : AndroidViewModel(application) {
    private val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build(),
    )
    private val modules = ModuleInstall.getClient(application)
    private val pending = LinkedHashMap<String, Image>()
    private val attempted = LinkedHashSet<String>()
    private val _masks = MutableStateFlow<Map<String, ArtworkSubjectMask>>(emptyMap())
    val masks = _masks.asStateFlow()
    private var worker: Job? = null
    private var retryModelAfter = 0L

    fun prepare(key: String, image: Image) {
        if (key in _masks.value || key in attempted || key in pending) return
        pending[key] = image
        while (pending.size > 12) pending.remove(pending.keys.first())
        if (worker?.isActive == true) return
        worker = viewModelScope.launch {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now < retryModelAfter) {
                pending.clear()
                return@launch
            }
            if (!modelAvailable()) {
                retryModelAfter = android.os.SystemClock.elapsedRealtime() + 30_000
                pending.clear()
                return@launch
            }
            while (pending.isNotEmpty()) {
                val (nextKey, nextImage) = pending.entries.first().let { it.key to it.value }
                pending.remove(nextKey)
                attempted.add(nextKey)
                while (attempted.size > 48) attempted.remove(attempted.first())
                try {
                    val mask = withContext(Dispatchers.Default) { extract(nextImage) }
                    if (mask != null) {
                        attempted.remove(nextKey)
                        val updated = LinkedHashMap(_masks.value).apply { put(nextKey, mask) }
                        while (updated.size > 12) updated.remove(updated.keys.first())
                        _masks.value = updated
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    attempted.remove(nextKey)
                }
            }
        }
    }

    private suspend fun modelAvailable(): Boolean = try {
        withTimeoutOrNull(30_000) {
            if (modules.areModulesAvailable(segmenter).awaitResult().areModulesAvailable()) return@withTimeoutOrNull true
            modules.installModules(ModuleInstallRequest.newBuilder().addApi(segmenter).build()).awaitResult()
            while (!modules.areModulesAvailable(segmenter).awaitResult().areModulesAvailable()) delay(1_000)
            true
        } ?: false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        false
    }

    private suspend fun extract(image: Image): ArtworkSubjectMask? {
        val scale = minOf(1f, 512f / maxOf(image.width, image.height).coerceAtLeast(1))
        val width = (image.width * scale).roundToInt().coerceAtLeast(1)
        val height = (image.height * scale).roundToInt().coerceAtLeast(1)
        val source = (image as? BitmapImage)?.bitmap
        val software = if (source?.config == Bitmap.Config.HARDWARE) source.copy(Bitmap.Config.ARGB_8888, false) else null
        val input = if (software != null) software.scale(width, height, true)
        else image.toBitmap(width, height, Bitmap.Config.ARGB_8888)
        fun releaseInput() {
            // Coil owns its decoded bitmap; only recycle the copies made for inference.
            if (input !== source) input.recycle()
            if (software != null && software !== input) software.recycle()
        }
        val task = try { segmenter.process(InputImage.fromBitmap(input, 0)) }
        catch (failure: Exception) { releaseInput(); throw failure }
        // A cancelled screen must not recycle the input while the native task is still reading it.
        task.addOnCompleteListener { releaseInput() }
        val result = task.awaitResult()
        val buffer = result.foregroundConfidenceMask ?: return null
        val confidence = FloatArray(width * height) { buffer.get(it) }
        return subjectMaskFromConfidence(width, height, confidence)
    }

    override fun onCleared() {
        worker?.cancel()
        pending.clear()
        segmenter.close()
    }
}

internal fun subjectMaskFromConfidence(width: Int, height: Int, confidence: FloatArray): ArtworkSubjectMask? {
    if (width <= 0 || height <= 0 || confidence.size != width * height) return null
    val pixels = IntArray(confidence.size)
    val alphaValues = FloatArray(confidence.size)
    for (i in confidence.indices) {
        val value = confidence[i]
        if (!value.isFinite()) return null
        val t = ((value.coerceIn(0f, 1f) - .55f) / .3f).coerceIn(0f, 1f)
        alphaValues[i] = t * t * (3f - 2f * t)
    }
    var mass = 0.0
    var xMass = 0.0
    var yMass = 0.0
    for (i in confidence.indices) {
        val x = i % width
        val y = i / width
        var alpha = alphaValues[i]
        // Keep the resampling feather inside the subject instead of retaining a rim of poster color.
        for (row in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
            for (column in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
                alpha = minOf(alpha, alphaValues[row * width + column])
            }
        }
        pixels[i] = ((1f - alpha) * 255).roundToInt().shl(24) or 0x00ffffff
        mass += alpha
        xMass += (i % width + .5) * alpha
        yMass += (i / width + .5) * alpha
    }
    val coverage = mass / confidence.size
    if (coverage < .02 || coverage > .97) return null
    return ArtworkSubjectMask(
        Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap(),
        Offset((xMass / mass / width).toFloat(), (yMass / mass / height).toFloat()),
    )
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
    addOnFailureListener { if (continuation.isActive) continuation.resumeWithException(it) }
    addOnCanceledListener { continuation.cancel() }
}
