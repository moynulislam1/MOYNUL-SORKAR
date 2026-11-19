package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min

object ProfilePhotoUtils {
    private const val TARGET_SIZE_PX = 200
    private const val MAX_BASE64_CHARS = 55000

    private val decodeCache = object : LruCache<String, ImageBitmap>(48) {}

    suspend fun encodeUriToBase64Avatar(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val minSide = min(w, h)
                    if (minSide > TARGET_SIZE_PX * 2) {
                        val scale = (TARGET_SIZE_PX * 2f) / minSide.toFloat()
                        decoder.setTargetSize(
                            (w * scale).toInt().coerceAtLeast(1),
                            (h * scale).toInt().coerceAtLeast(1)
                        )
                    }
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, boundsOpts)
                }
                val minSide = min(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
                var sample = 1
                while (minSide / (sample * 2) >= TARGET_SIZE_PX) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
            } ?: return@withContext null

            val side = min(rawBitmap.width, rawBitmap.height).coerceAtLeast(1)
            val xOffset = (rawBitmap.width - side) / 2
            val yOffset = (rawBitmap.height - side) / 2
            val cropped = Bitmap.createBitmap(rawBitmap, xOffset, yOffset, side, side)
            if (cropped !== rawBitmap) rawBitmap.recycle()

            val scaled = if (cropped.width != TARGET_SIZE_PX) {
                Bitmap.createScaledBitmap(cropped, TARGET_SIZE_PX, TARGET_SIZE_PX, true).also {
                    if (it !== cropped) cropped.recycle()
                }
            } else {
                cropped
            }

            var quality = 74
            var encoded: String
            do {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                quality -= 14
            } while (encoded.length > MAX_BASE64_CHARS && quality >= 30)

            scaled.recycle()
            if (encoded.length <= 59000) encoded else null
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        val cacheKey = "${base64.length}_${base64.hashCode()}"
        decodeCache.get(cacheKey)?.let { return it }

        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
            val imageBmp = bmp.asImageBitmap()
            decodeCache.put(cacheKey, imageBmp)
            imageBmp
        } catch (_: Exception) {
            null
        }
    }
}
