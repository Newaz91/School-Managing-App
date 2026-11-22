package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt

object StudentPhotoHelper {

    fun createTempCameraPhotoUri(context: Context): Uri {
        val dir = File(context.cacheDir, "camera_photos").apply { mkdirs() }
        val file = File(dir, "student_cam_${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * Loads the selected or camera-captured image Uri, applies EXIF orientation,
     * center-crops cleanly to a 3:4 passport portrait ratio, scales to at most 210x280 px,
     * and compresses as JPEG Base64 (< 110 KB).
     */
    fun compressUriToPassportBase64(context: Context, uri: Uri): String? {
        return try {
            val rotationDegrees = readExifRotationDegrees(context, uri)
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
            val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return null

            val uprightBitmap = if (rotationDegrees != 0f) {
                val matrix = Matrix().apply { postRotate(rotationDegrees) }
                Bitmap.createBitmap(
                    decodedBitmap, 0, 0, decodedBitmap.width, decodedBitmap.height, matrix, true
                ).also {
                    if (it !== decodedBitmap) decodedBitmap.recycle()
                }
            } else {
                decodedBitmap
            }

            val cropped = centerCropPassportRatio(uprightBitmap)
            if (cropped !== uprightBitmap) {
                uprightBitmap.recycle()
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

    private fun readExifRotationDegrees(context: Context, uri: Uri): Float {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) {
            0f
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
