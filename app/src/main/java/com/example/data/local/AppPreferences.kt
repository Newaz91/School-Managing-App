package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

data class LocalSchoolSession(
    val deviceId: String,
    val activeSchoolId: String,
    val teacherName: String,
    val recentSchools: List<String>,
    val selectedYear: Int,
    val upgradedNoticeMessage: String? = null
)

object AppPreferences {
    private const val PREFS_NAME = "my_school_prefs"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_ACTIVE_SCHOOL_ID = "active_school_id"
    private const val KEY_TEACHER_NAME = "teacher_name"
    private const val KEY_RECENT_SCHOOLS = "recent_schools"
    private const val KEY_SELECTED_YEAR = "selected_year"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_MIGRATED_LEGACY_UID = "migrated_legacy_uid"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadSession(context: Context, currentVersionCode: Int = 2): LocalSchoolSession {
        val p = prefs(context)
        var devId = p.getString(KEY_DEVICE_ID, null).orEmpty()
        if (devId.isBlank()) {
            devId = "dev_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            p.edit().putString(KEY_DEVICE_ID, devId).apply()
        }

        val lastVersion = p.getInt(KEY_LAST_VERSION_CODE, 0)
        val activeSchoolId = p.getString(KEY_ACTIVE_SCHOOL_ID, "").orEmpty()
        val upgradeMsg = if (lastVersion in 1 until currentVersionCode) {
            "App updated to v1.1 — all existing school data & settings preserved."
        } else null
        if (lastVersion != currentVersionCode) {
            p.edit().putInt(KEY_LAST_VERSION_CODE, currentVersionCode).apply()
        }

        val recent = p.getString(KEY_RECENT_SCHOOLS, "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return LocalSchoolSession(
            deviceId = devId,
            activeSchoolId = activeSchoolId,
            teacherName = p.getString(KEY_TEACHER_NAME, "").orEmpty(),
            recentSchools = recent,
            selectedYear = p.getInt(KEY_SELECTED_YEAR, 2026).coerceIn(2020, 2050),
            upgradedNoticeMessage = upgradeMsg
        )
    }

    fun saveActiveSchool(context: Context, schoolId: String, teacherName: String) {
        val p = prefs(context)
        val cleanId = schoolId.trim()
        val cleanName = teacherName.trim()
        val existingRecent = p.getString(KEY_RECENT_SCHOOLS, "")
            .orEmpty()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals(cleanId, ignoreCase = true) }
        val nextRecent = (listOf(cleanId) + existingRecent).take(8).joinToString(",")

        p.edit()
            .putString(KEY_ACTIVE_SCHOOL_ID, cleanId)
            .putString(KEY_TEACHER_NAME, cleanName)
            .putString(KEY_RECENT_SCHOOLS, nextRecent)
            .apply()
    }

    fun updateTeacherName(context: Context, teacherName: String) {
        prefs(context).edit().putString(KEY_TEACHER_NAME, teacherName.trim()).apply()
    }

    fun saveSelectedYear(context: Context, year: Int) {
        prefs(context).edit().putInt(KEY_SELECTED_YEAR, year.coerceIn(2020, 2050)).apply()
    }

    fun clearActiveSchool(context: Context) {
        prefs(context).edit().putString(KEY_ACTIVE_SCHOOL_ID, "").apply()
    }

    fun getMigratedLegacyUid(context: Context): String =
        prefs(context).getString(KEY_MIGRATED_LEGACY_UID, "").orEmpty()

    fun markLegacyUidMigrated(context: Context, uid: String) {
        prefs(context).edit().putString(KEY_MIGRATED_LEGACY_UID, uid).apply()
    }
}
