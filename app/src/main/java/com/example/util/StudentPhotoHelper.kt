package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

object StudentPhotoHelper {

    /**
     * Loads the selected image from Photo Picker Uri, center-crops it cleanly to a 3:4 passport
     * portrait ratio, scales down to at most 210x280 px, and compresses as JPEG Base64 (< 110 KB)
     * so it persists cleanly in Firestore and offline exports without needing broad storage permissions.
     */
    fun compressUriToPassportBase64(context: Context, uri: Uri): String? {
        return try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            val origW = boundsOpts.outWidth
            val origH = boundsOpts.outHeight
            if (origW <= 0 || origH <= 0) return null

            var sampleSize = 1
            while (origW / sampleSize > 900 || origH / sampleSize > 900) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val rawBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val cropped = centerCropPassportRatio(rawBitmap)
            if (cropped !== rawBitmap) {
                rawBitmap.recycle()
            }

            val targetW = 210
            val targetH = 280
            val scaled = if (cropped.width != targetW || cropped.height != targetH) {
                Bitmap.createScaledBitmap(cropped, targetW, targetH, true).also {
                    if (it !== cropped) cropped.recycle()
                }
            } else {
                cropped
            }

            var quality = 82
            var encodedBytes: ByteArray
            do {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
                encodedBytes = out.toByteArray()
                quality -= 10
            } while (encodedBytes.size > 90_000 && quality >= 35)

            scaled.recycle()

            val b64 = Base64.encodeToString(encodedBytes, Base64.NO_WRAP)
            if (b64.length > 138_000) null else b64
        } catch (_: Exception) {
            null
        }
    }

    fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
        if (base64.isBlank()) return null
        return try {
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    private fun centerCropPassportRatio(src: Bitmap): Bitmap {
        val targetRatio = 3f / 4f
        val srcW = src.width
        val srcH = src.height
        val currentRatio = srcW.toFloat() / srcH.toFloat()

        return if (currentRatio > targetRatio) {
            val desiredW = min(srcW, (srcH * targetRatio).roundToInt()).coerceAtLeast(1)
            val xOffset = ((srcW - desiredW) / 2).coerceAtLeast(0)
            Bitmap.createBitmap(src, xOffset, 0, desiredW, srcH)
        } else {
            val desiredH = min(srcH, (srcW / targetRatio).roundToInt()).coerceAtLeast(1)
            val yOffset = ((srcH - desiredH) / 4).coerceAtLeast(0) // slightly top-weighted for head/face
            Bitmap.createBitmap(src, 0, yOffset, srcW, desiredH)
        }
    }
}
