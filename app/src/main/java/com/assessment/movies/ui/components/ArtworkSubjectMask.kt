package com.assessment.movies.ui.components

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
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
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest

data class ArtworkSubjectMask(val inverseAlpha: ImageBitmap, val focus: Offset)

fun movieMaskKey(movie: Movie, origin: MovieArtworkOrigin): String {
    val path = if (origin == MovieArtworkOrigin.Featured) movie.backdropPath ?: movie.posterPath else movie.posterPath
    return "${movieArtworkKey(movie.id, origin)}/$path"
}

/** Owns a small, in-memory mask cache; navigation never waits for model installation or inference. */
class ArtworkMasksViewModel(application: Application) : AndroidViewModel(application) {
    private val diskCache = File(application.cacheDir, "artwork-masks-v2")
    private val segmenter = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundConfidenceMask().build(),
    )
    private val modules = ModuleInstall.getClient(application)
    private val pending = LinkedHashMap<String, Image>()
    private val attempted = LinkedHashSet<String>()
    private val _masks = MutableStateFlow<Map<String, ArtworkSubjectMask>>(emptyMap())
    val masks = _masks.asStateFlow()
    private var worker: Job? = null
    private var priorityKey: String? = null

    fun prioritize(key: String) {
        priorityKey = key
    }

    fun prepare(key: String, image: Image) {
        if (key in _masks.value || key in attempted || key in pending) return
        pending[key] = image
        while (pending.size > 12) pending.remove(pending.keys.first())
        if (worker?.isActive == true) return
        worker = viewModelScope.launch {
            var modelReady = false
            while (pending.isNotEmpty()) {
                val nextKey = priorityKey?.takeIf(pending::containsKey) ?: pending.keys.last()
                val nextImage = pending.remove(nextKey) ?: continue
                attempted.add(nextKey)
                while (attempted.size > 48) attempted.remove(attempted.first())
                try {
                    val cached = withContext(Dispatchers.IO) { readMask(nextKey) }
                    val mask = if (cached != null) cached else {
                        if (!modelReady) {
                            modelReady = modelAvailable()
                            if (!modelReady) {
                                delay(5_000)
                                modelReady = modelAvailable()
                            }
                        }
                        if (!modelReady) {
                            attempted.remove(nextKey)
                            pending.clear()
                            break
                        }
                        withContext(Dispatchers.Default) { extract(nextImage) }?.also {
                            withContext(Dispatchers.IO) { writeMask(nextKey, it) }
                        }
                    }
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

    private fun cacheFile(key: String): File {
        val name = MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(diskCache, "$name.mask")
    }

    private fun readMask(key: String): ArtworkSubjectMask? {
        val file = cacheFile(key)
        if (!file.isFile) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { input ->
                val focus = Offset(input.readFloat(), input.readFloat())
                val bitmap = BitmapFactory.decodeStream(input)
                if (bitmap == null || focus.x !in 0f..1f || focus.y !in 0f..1f) null
                else ArtworkSubjectMask(bitmap.asImageBitmap(), focus)
            }?.also { file.setLastModified(System.currentTimeMillis()) }
        } catch (_: java.io.IOException) {
            null
        }
    }

    private fun writeMask(key: String, mask: ArtworkSubjectMask) {
        val target = cacheFile(key)
        val temporary = File(diskCache, "pending")
        try {
            diskCache.mkdirs()
            val written = DataOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeFloat(mask.focus.x)
                output.writeFloat(mask.focus.y)
                mask.inverseAlpha.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            if (written && temporary.renameTo(target)) {
                diskCache.listFiles { file -> file.extension == "mask" }?.sortedByDescending { it.lastModified() }
                    ?.drop(48)?.forEach { it.delete() }
            }
        } catch (_: java.io.IOException) {
            // The in-memory result is still usable if the cache directory cannot be written.
        } finally {
            temporary.delete()
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
        val scale = minOf(1f, 1024f / maxOf(image.width, image.height).coerceAtLeast(1))
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
