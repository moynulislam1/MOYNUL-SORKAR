package com.example.data.security

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.security.MessageDigest
import java.security.SecureRandom

object CryptoUtils {
    private val random = SecureRandom()

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hashPassword(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:$password".toByteArray(Charsets.UTF_8)
        val hash = digest.digest(input)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(password: String, salt: String, expectedHash: String): Boolean {
        if (salt.isBlank() || expectedHash.isBlank()) return false
        val actual = hashPassword(password, salt)
        return MessageDigest.isEqual(
            actual.toByteArray(Charsets.UTF_8),
            expectedHash.toByteArray(Charsets.UTF_8)
        )
    }

    fun normalizePhone(raw: String): String {
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        return if (hasPlus && digits.isNotEmpty()) "+$digits" else digits
    }

    fun maskPhone(phone: String): String {
        val normalized = normalizePhone(phone)
        if (normalized.length <= 4) return normalized
        val tail = normalized.takeLast(4)
        return "•••• $tail"
    }

    fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            append("FAM")
            repeat(6) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    fun generateProfileCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return buildString {
            append("MEM")
            repeat(6) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    fun parseQrPayload(rawText: String): ParsedQrAction? {
        val text = rawText.trim()
        if (text.isBlank()) return null

        // Family Invite QR: familyapps://join?code=FAM123456&family=Sorkar+Family
        if (text.startsWith("familyapps://join")) {
            val params = parseQueryParams(text)
            val code = params["code"]?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }
            if (!code.isNullOrBlank() && code.length in 6..20) {
                return ParsedQrAction.JoinInvite(
                    inviteCode = code,
                    familyName = params["family"]?.take(50).orEmpty()
                )
            }
        }

        // Personal Profile QR: familyapps://profile?uid=<uid>&code=MEM123456
        if (text.startsWith("familyapps://profile")) {
            val params = parseQueryParams(text)
            val uid = params["uid"]?.trim()?.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            val code = params["code"]?.trim()?.uppercase()?.filter { it.isLetterOrDigit() }
            if (!uid.isNullOrBlank() || (!code.isNullOrBlank() && code.length in 6..20)) {
                return ParsedQrAction.ViewProfile(
                    uid = uid.orEmpty(),
                    profileCode = code.orEmpty()
                )
            }
        }

        // Plain code fallback (FAM... or MEM...) or direct User ID
        val sanitized = text.uppercase().filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        if (sanitized.startsWith("FAM") && sanitized.length in 6..20) {
            return ParsedQrAction.JoinInvite(inviteCode = sanitized, familyName = "")
        }
        if (sanitized.startsWith("MEM") && sanitized.length in 6..20) {
            return ParsedQrAction.ViewProfile(uid = "", profileCode = sanitized)
        }
        val rawId = text.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        if (rawId.length in 16..128) {
            return ParsedQrAction.ViewProfile(uid = rawId, profileCode = "")
        }
        return null
    }

    private fun parseQueryParams(url: String): Map<String, String> {
        val query = url.substringAfter("?", "")
        return query.split("&").mapNotNull { part ->
            val idx = part.indexOf('=')
            if (idx > 0) {
                val key = part.substring(0, idx)
                val value = try {
                    java.net.URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                } catch (_: Exception) {
                    part.substring(idx + 1)
                }
                key to value
            } else null
        }.toMap()
    }

    fun buildQrPayload(inviteCode: String, familyName: String): String {
        val encodedFamily = java.net.URLEncoder.encode(familyName, "UTF-8")
        return "familyapps://join?code=$inviteCode&family=$encodedFamily"
    }

    fun buildProfileQrPayload(uid: String, profileCode: String): String {
        return "familyapps://profile?uid=$uid&code=$profileCode"
    }
}

sealed interface ParsedQrAction {
    data class JoinInvite(val inviteCode: String, val familyName: String) : ParsedQrAction
    data class ViewProfile(val uid: String, val profileCode: String) : ParsedQrAction
}

object QrCodeGenerator {
    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 2
            )
            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                sizePx,
                sizePx,
                hints
            )
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            val fg = Color.parseColor("#0F5C4D")
            val bg = Color.parseColor("#FFFFFF")
            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) fg else bg
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (_: Exception) {
            null
        }
    }
}
