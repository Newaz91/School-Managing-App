package com.example.export

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

object AppShareHelper {

    fun buildShareInviteText(
        context: Context,
        schoolName: String?,
        schoolRoomId: String?
    ): String = buildString {
        appendLine("My School — Primary School Student Database & Assessment App")
        if (!schoolName.isNullOrBlank()) {
            appendLine("Shared from: $schoolName")
        }
        if (!schoolRoomId.isNullOrBlank()) {
            appendLine("Unique School ID: $schoolRoomId")
        }
        appendLine()
        appendLine("Open My School app and enter this School ID to join:")
        append("https://play.google.com/store/apps/details?id=${context.packageName}")
    }

    fun shareInviteText(
        context: Context,
        schoolName: String?,
        schoolRoomId: String?
    ) {
        val text = buildShareInviteText(context, schoolName, schoolRoomId)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "My School App")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share My School App"))
    }

    fun shareInviteViaWhatsApp(
        context: Context,
        schoolName: String?,
        schoolRoomId: String?
    ) {
        val text = buildShareInviteText(context, schoolName, schoolRoomId)
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            shareInviteText(context, schoolName, schoolRoomId)
        }
    }

    fun shareAppApk(context: Context) {
        try {
            val sourceApk = File(context.applicationInfo.sourceDir)
            if (!sourceApk.exists()) {
                shareInviteText(context, null, null)
                return
            }
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val destApk = File(exportDir, "My_School.apk")
            sourceApk.copyTo(destApk, overwrite = true)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destApk
            )
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Send My School APK"))
        } catch (e: Exception) {
            Toast.makeText(
                context,
                e.localizedMessage ?: "Could not prepare APK file; sharing link instead.",
                Toast.LENGTH_SHORT
            ).show()
            shareInviteText(context, null, null)
        }
    }
}

@Composable
fun ShareAppDialog(
    schoolName: String?,
    schoolRoomId: String?,
    onDismiss: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onShareSystem: () -> Unit,
    onShareApk: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share My School App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = buildString {
                        append("Share this app with fellow teachers at your school.")
                        if (!schoolRoomId.isNullOrBlank()) {
                            append(" Your Unique School ID ($schoolRoomId) is included automatically so colleagues can join without any password.")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!schoolName.isNullOrBlank()) {
                    Text(
                        text = schoolName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Button(
                    onClick = {
                        onShareWhatsApp()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_app_whatsapp_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share App via WhatsApp")
                }
                FilledTonalButton(
                    onClick = {
                        onShareSystem()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_app_link_button")
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Share App Link & School ID")
                }
                OutlinedButton(
                    onClick = {
                        onShareApk()
                        onDismiss()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("share_app_apk_button")
                ) {
                    Icon(Icons.Rounded.Android, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Send App Installer (.apk)")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
