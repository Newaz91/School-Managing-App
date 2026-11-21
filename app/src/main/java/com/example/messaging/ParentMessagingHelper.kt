package com.example.messaging

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object ParentMessagingHelper {

    fun normalizeBdPhoneForWhatsApp(rawPhone: String): String {
        val digits = rawPhone.filter { it.isDigit() }
        return when {
            digits.startsWith("880") && digits.length >= 13 -> digits
            digits.startsWith("01") && digits.length == 11 -> "88$digits"
            digits.startsWith("1") && digits.length == 10 -> "880$digits"
            else -> digits
        }
    }

    fun sendSmsToRecipients(context: Context, phones: List<String>, message: String) {
        val validPhones = phones.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        val uriString = if (validPhones.isNotEmpty()) {
            "smsto:${validPhones.joinToString(";")}"
        } else {
            "smsto:"
        }
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse(uriString)
            putExtra("sms_body", message)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No SMS app available on device.", Toast.LENGTH_SHORT).show()
        }
    }

    fun sendWhatsAppToParentOrBroadcast(context: Context, singlePhone: String?, message: String) {
        if (!singlePhone.isNullOrBlank()) {
            val waNumber = normalizeBdPhoneForWhatsApp(singlePhone)
            if (waNumber.isNotBlank()) {
                val uri = Uri.parse("https://wa.me/$waNumber?text=${Uri.encode(message)}")
                val directIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.whatsapp")
                }
                try {
                    context.startActivity(directIntent)
                    return
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        return
                    } catch (_: Exception) {
                        // Fall through to ACTION_SEND
                    }
                }
            }
        }

        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            shareGeneric(context, message)
        }
    }

    fun shareGeneric(context: Context, message: String) {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(sendIntent, "Send Message to Parents"))
    }
}
