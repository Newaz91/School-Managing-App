package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

@Keep
@IgnoreExtraProperties
data class TeacherProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val teacherName: String = "",
    val phoneNumber: String = "",
    val passwordHash: String = "",
    val role: String = ROLE_TEACHER, // "admin" or "teacher"
    val phoneVerified: Boolean = false,
    val schoolName: String = "",
    val emisCode: String = "",
    val upazilaDistrict: String = "",
    val schoolRoomId: String = "",
    val defaultAcademicYear: Long = 2026L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val isAdmin: Boolean
        get() = role.equals(ROLE_ADMIN, ignoreCase = true)

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "teacherName" to teacherName.trim().ifBlank { "Teacher" }.take(100),
        "phoneNumber" to normalizePhone(phoneNumber).take(25),
        "passwordHash" to passwordHash.trim().take(128),
        "role" to if (isAdmin) ROLE_ADMIN else ROLE_TEACHER,
        "phoneVerified" to phoneVerified,
        "schoolName" to schoolName.trim().take(150),
        "emisCode" to emisCode.trim().take(30),
        "upazilaDistrict" to upazilaDistrict.trim().take(100),
        "schoolRoomId" to schoolRoomId.trim().uppercase().take(40),
        "defaultAcademicYear" to defaultAcademicYear.coerceIn(2020L, 2050L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "teacherName" to teacherName.trim().ifBlank { "Teacher" }.take(100),
        "phoneNumber" to normalizePhone(phoneNumber).take(25),
        "role" to if (isAdmin) ROLE_ADMIN else ROLE_TEACHER,
        "phoneVerified" to phoneVerified,
        "schoolName" to schoolName.trim().take(150),
        "emisCode" to emisCode.trim().take(30),
        "upazilaDistrict" to upazilaDistrict.trim().take(100),
        "schoolRoomId" to schoolRoomId.trim().uppercase().take(40),
        "defaultAcademicYear" to defaultAcademicYear.coerceIn(2020L, 2050L),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_TEACHER = "teacher"

        /** Normalizes Bangladesh & international numbers so 017XXXXXXXX and +88017XXXXXXXX match cleanly */
        fun normalizePhone(raw: String): String {
            val digitsOnly = raw.trim().replace(Regex("[^0-9+]"), "")
            return when {
                digitsOnly.startsWith("+880") -> digitsOnly
                digitsOnly.startsWith("880") && digitsOnly.length == 13 -> "+$digitsOnly"
                digitsOnly.startsWith("01") && digitsOnly.length == 11 -> "+88$digitsOnly"
                else -> digitsOnly
            }
        }

        /** One-way SHA-256 hash so raw passwords are never stored in plain text */
        fun hashPassword(plainPassword: String): String {
            val bytes = MessageDigest.getInstance("SHA-256")
                .digest(plainPassword.trim().toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun verifyPassword(plainPassword: String, storedHash: String): Boolean {
            if (storedHash.isBlank()) return false
            return hashPassword(plainPassword) == storedHash
        }
    }
}