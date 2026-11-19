package com.example.data.media

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

data class EncodedChatVideo(
    val mediaBase64: String,
    val thumbnailBase64: String,
    val durationMs: Long,
    val mimeType: String
)

data class ResolvedLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String
)

data class FamilySticker(
    val id: String,
    val category: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val bgColorHex: String
)

object FamilyStickerCatalog {
    val categories = listOf("Greetings", "Family Love", "Blessings", "Daily Life")

    val stickers = listOf(
        FamilySticker("stk_salam", "Greetings", "🤲", "Assalamu Alaikum", "Peace & blessings to family", "#0F5C4D"),
        FamilySticker("stk_morning", "Greetings", "☀️", "Good Morning!", "Have a bright day ahead", "#D96B43"),
        FamilySticker("stk_night", "Greetings", "🌙", "Good Night", "Sweet dreams & rest well", "#2E4A62"),
        FamilySticker("stk_hug", "Family Love", "🤗", "Warm Hug", "Sending a big family hug", "#C84B5A"),
        FamilySticker("stk_love", "Family Love", "❤️", "Love You All", "Always grateful for you", "#B5384B"),
        FamilySticker("stk_miss", "Family Love", "🥹", "Missing You", "Can't wait to see you soon", "#6C4A8D"),
        FamilySticker("stk_proud", "Family Love", "🌟", "Proud of You", "Keep shining bright!", "#0E7490"),
        FamilySticker("stk_dua", "Blessings", "🕌", "In My Prayers", "May Allah keep you safe", "#1B6B54"),
        FamilySticker("stk_eid", "Blessings", "✨", "Celebration!", "Joy & happiness together", "#9A6700"),
        FamilySticker("stk_congrats", "Blessings", "🎉", "Mubarak / Congrats", "So happy for this news!", "#7C3AED"),
        FamilySticker("stk_home", "Daily Life", "🏡", "Reached Home", "Arrived safely at home", "#2563EB"),
        FamilySticker("stk_tea", "Daily Life", "☕", "Tea & Snacks", "Come join for chai time", "#8D5524"),
        FamilySticker("stk_food", "Daily Life", "🍽️", "Dinner Ready!", "Fresh meal on the table", "#D97706"),
        FamilySticker("stk_call", "Daily Life", "📞", "Call Me Free", "Ring when you're free", "#059669")
    )

    fun findById(id: String): FamilySticker? = stickers.firstOrNull { it.id == id }
}

object ChatMediaUtils {
    private const val MAX_CHAT_IMAGE_SIDE_PX = 720
    private const val MAX_CHAT_IMAGE_BASE64_CHARS = 280_000
    private const val MAX_VIDEO_THUMB_BASE64_CHARS = 45_000
    private const val MAX_VIDEO_RAW_BYTES = 460 * 1024 // ~615 KB Base64, safe under 1 MB Firestore limit

    suspend fun encodeChatImageToBase64(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val rawBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val longest = max(w, h)
                    if (longest > MAX_CHAT_IMAGE_SIDE_PX) {
                        val scale = MAX_CHAT_IMAGE_SIDE_PX.toFloat() / longest.toFloat()
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
                val longest = max(boundsOpts.outWidth, boundsOpts.outHeight).coerceAtLeast(1)
                var sample = 1
                while (longest / (sample * 2) >= MAX_CHAT_IMAGE_SIDE_PX) {
                    sample *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream, null, decodeOpts)
                }
            } ?: return@withContext null

            val longest = max(rawBitmap.width, rawBitmap.height).coerceAtLeast(1)
            val scaled = if (longest > MAX_CHAT_IMAGE_SIDE_PX) {
                val scale = MAX_CHAT_IMAGE_SIDE_PX.toFloat() / longest.toFloat()
                Bitmap.createScaledBitmap(
                    rawBitmap,
                    (rawBitmap.width * scale).toInt().coerceAtLeast(1),
                    (rawBitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                ).also {
                    if (it !== rawBitmap) rawBitmap.recycle()
                }
            } else {
                rawBitmap
            }

            var quality = 78
            var encoded: String
            do {
                val baos = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                quality -= 12
            } while (encoded.length > MAX_CHAT_IMAGE_BASE64_CHARS && quality >= 28)

            scaled.recycle()
            if (encoded.length <= 680_000) encoded else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun encodeChatVideo(context: Context, uri: Uri): EncodedChatVideo? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        var durationMs = 0L
        var thumbBase64 = ""
        try {
            retriever.setDataSource(context, uri)
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (frame != null) {
                thumbBase64 = encodeThumbnailBitmap(frame).orEmpty()
                frame.recycle()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        val rawBytes = readUriBytesIfUnderLimit(context, uri, MAX_VIDEO_RAW_BYTES)
        val playableBase64 = if (rawBytes != null) {
            Base64.encodeToString(rawBytes, Base64.NO_WRAP)
        } else {
            trimVideoClipToBase64(context, uri) ?: ""
        }

        if (playableBase64.isBlank() && thumbBase64.isBlank()) {
            return@withContext null
        }

        val mime = context.contentResolver.getType(uri)?.take(64) ?: "video/mp4"
        EncodedChatVideo(
            mediaBase64 = if (playableBase64.length <= 680_000) playableBase64 else "",
            thumbnailBase64 = thumbBase64.take(58_000),
            durationMs = durationMs.coerceAtLeast(0L),
            mimeType = mime
        )
    }

    private fun encodeThumbnailBitmap(bmp: Bitmap): String? {
        val longest = max(bmp.width, bmp.height).coerceAtLeast(1)
        val target = 360
        val scaled = if (longest > target) {
            val scale = target.toFloat() / longest.toFloat()
            Bitmap.createScaledBitmap(
                bmp,
                (bmp.width * scale).toInt().coerceAtLeast(1),
                (bmp.height * scale).toInt().coerceAtLeast(1),
                true
            )
        } else {
            bmp
        }
        var quality = 72
        var encoded: String
        do {
            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            encoded = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
            quality -= 12
        } while (encoded.length > MAX_VIDEO_THUMB_BASE64_CHARS && quality >= 30)
        if (scaled !== bmp) scaled.recycle()
        return encoded
    }

    private fun readUriBytesIfUnderLimit(context: Context, uri: Uri, maxBytes: Int): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val baos = ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    if (total > maxBytes) return null
                    baos.write(buf, 0, read)
                }
                baos.toByteArray()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun trimVideoClipToBase64(context: Context, uri: Uri): String? {
        val tempOut = File(context.cacheDir, "chat_trim_${System.currentTimeMillis()}.mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        return try {
            extractor.setDataSource(context, uri, null)
            muxer = MediaMuxer(tempOut.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val trackMap = mutableMapOf<Int, Int>()
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                if (mime.startsWith("video/") || mime.startsWith("audio/")) {
                    extractor.selectTrack(i)
                    trackMap[i] = muxer.addTrack(format)
                }
            }
            if (trackMap.isEmpty()) return null

            muxer.start()
            val buffer = ByteBuffer.allocate(256 * 1024)
            val bufferInfo = MediaCodec.BufferInfo()
            var writtenBytes = 0L
            val maxBytes = 420 * 1024L
            var firstPtsUs = -1L

            while (writtenBytes < maxBytes) {
                val trackIndex = extractor.sampleTrackIndex
                if (trackIndex < 0) break
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val ptsUs = extractor.sampleTime
                if (firstPtsUs < 0L) firstPtsUs = ptsUs
                if (ptsUs - firstPtsUs > 7_000_000L) break

                val destTrack = trackMap[trackIndex]
                if (destTrack != null) {
                    bufferInfo.offset = 0
                    bufferInfo.size = size
                    bufferInfo.presentationTimeUs = (ptsUs - firstPtsUs).coerceAtLeast(0L)
                    @Suppress("DEPRECATION")
                    bufferInfo.flags = extractor.sampleFlags
                    muxer.writeSampleData(destTrack, buffer, bufferInfo)
                    writtenBytes += size
                }
                extractor.advance()
            }
            muxer.stop()
            muxer.release()
            muxer = null

            val bytes = tempOut.readBytes()
            if (bytes.isNotEmpty() && bytes.size <= MAX_VIDEO_RAW_BYTES) {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } else null
        } catch (_: Exception) {
            null
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
            runCatching { tempOut.delete() }
        }
    }

    suspend fun decodeVideoBase64ToTempFile(
        context: Context,
        messageId: String,
        mediaBase64: String
    ): File? = withContext(Dispatchers.IO) {
        if (mediaBase64.isBlank()) return@withContext null
        try {
            val safeId = messageId.filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "clip" }
            val outFile = File(context.cacheDir, "chat_vid_$safeId.mp4")
            if (!outFile.exists() || outFile.length() == 0L) {
                val bytes = Base64.decode(mediaBase64, Base64.DEFAULT)
                outFile.writeBytes(bytes)
            }
            outFile
        } catch (_: Exception) {
            null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchCurrentLocation(context: Context): ResolvedLocation? = withContext(Dispatchers.IO) {
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val location: Location? = suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }
            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        cont.resume(loc)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last ?: fallbackLocationManager(context)) }
                            .addOnFailureListener { cont.resume(fallbackLocationManager(context)) }
                    }
                }
                .addOnFailureListener {
                    fusedClient.lastLocation
                        .addOnSuccessListener { last -> cont.resume(last ?: fallbackLocationManager(context)) }
                        .addOnFailureListener { cont.resume(fallbackLocationManager(context)) }
                }
        }

        val resolved = location ?: return@withContext null
        val label = reverseGeocodeLabel(context, resolved.latitude, resolved.longitude)
        ResolvedLocation(
            latitude = resolved.latitude,
            longitude = resolved.longitude,
            label = label
        )
    }

    @SuppressLint("MissingPermission")
    private fun fallbackLocationManager(context: Context): Location? {
        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            providers.firstNotNullOfOrNull { provider ->
                runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun reverseGeocodeLabel(context: Context, lat: Double, lng: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            val addr = addresses?.firstOrNull()
            val parts = listOfNotNull(
                addr?.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) },
                addr?.subLocality?.takeIf(String::isNotBlank),
                addr?.locality?.takeIf(String::isNotBlank),
                addr?.countryName?.takeIf(String::isNotBlank)
            ).distinct()
            if (parts.isNotEmpty()) {
                parts.joinToString(", ").take(180)
            } else {
                String.format(Locale.US, "%.5f, %.5f", lat, lng)
            }
        } catch (_: Exception) {
            String.format(Locale.US, "%.5f, %.5f", lat, lng)
        }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
        val mins = totalSeconds / 60L
        val secs = totalSeconds % 60L
        return String.format(Locale.US, "%d:%02d", mins, secs)
    }
}
