package com.example.util

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Zero-permission phone number picker using the system Contacts app (ACTION_PICK on Phone.CONTENT_URI).
 */
@Composable
fun rememberContactPhonePicker(
    onPhonePicked: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val contactUri = result.data?.data ?: return@rememberLauncherForActivityResult
            try {
                context.contentResolver.query(
                    contactUri,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val rawNumber = if (idx >= 0) cursor.getString(idx).orEmpty() else ""
                        val cleaned = rawNumber.replace(Regex("[\\s\\-()]+"), "").trim()
                        if (cleaned.isNotBlank()) {
                            onPhonePicked(cleaned)
                        }
                    }
                }
            } catch (_: Exception) {
                Toast.makeText(context, "Could not read phone number from contact.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    return {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        try {
            launcher.launch(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No Contacts app available on this device.", Toast.LENGTH_SHORT).show()
        }
    }
}

data class CameraOrGalleryActions(
    val launchCamera: () -> Unit,
    val launchGallery: () -> Unit
)

/**
 * Provides both direct camera capture (with runtime CAMERA permission flow) and
 * zero-permission Photo Picker gallery selection, compressing either result to 3:4 passport Base64.
 */
@Composable
fun rememberCameraOrGalleryPhotoPicker(
    onPhotoBase64: (String) -> Unit
): CameraOrGalleryActions {
    val context = LocalContext.current
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = StudentPhotoHelper.compressUriToPassportBase64(context, uri)
            if (encoded != null) {
                onPhotoBase64(encoded)
            } else {
                Toast.makeText(context, "Could not process selected photo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            val encoded = StudentPhotoHelper.compressUriToPassportBase64(context, uri)
            if (encoded != null) {
                onPhotoBase64(encoded)
            } else {
                Toast.makeText(context, "Could not process captured photo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val startCameraCapture: () -> Unit = {
        try {
            val uri = StudentPhotoHelper.createTempCameraPhotoUri(context)
            pendingCameraUri = uri
            cameraLauncher.launch(uri)
        } catch (_: Exception) {
            Toast.makeText(context, "Unable to open camera on this device.", Toast.LENGTH_SHORT).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startCameraCapture()
        } else {
            Toast.makeText(
                context,
                "Camera permission is required to capture a student photograph.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    return CameraOrGalleryActions(
        launchCamera = {
            val hasPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                startCameraCapture()
            } else {
                permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        launchGallery = {
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    )
}
