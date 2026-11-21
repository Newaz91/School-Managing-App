package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.util.UUID

object GradeScopes {
    const val ALL_CLASSES = "All Classes"
    const val GRADES_PRE_TO_2 = "Pre-Primary - Class 2"
    const val GRADES_3_TO_5 = "Class 3 - Class 5"

    val SINGLE_CLASSES = listOf(
        "Pre-Primary",
        "Class 1",
        "Class 2",
        "Class 3",
        "Class 4",
        "Class 5"
    )

    val ALL = SINGLE_CLASSES + listOf(
        ALL_CLASSES,
        GRADES_PRE_TO_2,
        GRADES_3_TO_5
    )
}

object CustomFieldTypes {
    const val TEXT = "TEXT"
    const val NUMBER = "NUMBER"
    const val YES_NO = "YES_NO"
    const val LEVEL = "LEVEL"

    // CustomFieldDef types strictly match firestore.rules ['TEXT', 'NUMBER', 'YES_NO']
    val ALL = listOf(TEXT, NUMBER, YES_NO)

    // Subject assessment field types
    val SUBJECT_TYPES = listOf(NUMBER, LEVEL, YES_NO, TEXT)

    val SKILL_LEVEL_OPTIONS = listOf("Mastered", "Good", "Average", "Needs Help")

    fun label(type: String): String = when (type) {
        NUMBER -> "Marks / Number"
        LEVEL -> "Skill Level"
        YES_NO -> "Yes / No"
        else -> "Text / Note"
    }
}

object CustomFieldCategories {
    val SUGGESTED = listOf(
        "General",
        "Birth & Health",
        "Family & Guardian",
        "Stipend & Books",
        "Co-Curricular"
    )
}

fun generateShortId(prefix: String): String =
    "${prefix}_${UUID.randomUUID().toString().replace("-", "").take(10)}"

fun normalizeRoomCode(raw: String): String {
    val cleaned = raw.trim().uppercase()
        .replace(Regex("[^A-Z0-9_\\-]+"), "-")
        .trim('-', '_')
        .take(40)
    return if (cleaned.length >= 2) cleaned else ""
}

@Keep
@IgnoreExtraProperties
data class SubjectFieldDef(
    val fieldId: String = "",
    val label: String = "",
    val fieldType: String = CustomFieldTypes.NUMBER,
    val maxMarks: Long = 100L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "fieldId" to fieldId.ifBlank { generateShortId("sf") }.take(40),
        "label" to label.trim().take(80),
        "fieldType" to if (fieldType in CustomFieldTypes.SUBJECT_TYPES) fieldType else CustomFieldTypes.NUMBER,
        "maxMarks" to maxMarks.coerceIn(0L, 1000L)
    )
}

@Keep
@IgnoreExtraProperties
data class SubjectDefinition(
    @DocumentId val id: String = "",
    val userId: String = "",
    val schoolId: String = "",
    val name: String = "",
    val gradeScope: String = GradeScopes.GRADES_3_TO_5,
    val fields: List<SubjectFieldDef> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun appliesToClass(classLevel: String): Boolean = when (gradeScope) {
        GradeScopes.ALL_CLASSES -> true
        GradeScopes.GRADES_PRE_TO_2 -> classLevel in listOf("Pre-Primary", "Class 1", "Class 2")
        GradeScopes.GRADES_3_TO_5 -> classLevel in listOf("Class 3", "Class 4", "Class 5")
        else -> gradeScope.equals(classLevel, ignoreCase = true)
    }

    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(80),
        "gradeScope" to gradeScope.trim().ifBlank { GradeScopes.ALL_CLASSES }.take(50),
        "fields" to fields.take(30).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toCreateMapForSchool(targetSchoolId: String): Map<String, Any> = mapOf(
        "schoolId" to targetSchoolId.take(32),
        "name" to name.trim().take(80),
        "gradeScope" to gradeScope.trim().ifBlank { GradeScopes.ALL_CLASSES }.take(50),
        "fields" to fields.take(30).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "name" to name.trim().take(80),
        "gradeScope" to gradeScope.trim().ifBlank { GradeScopes.ALL_CLASSES }.take(50),
        "fields" to fields.take(30).map { it.toMap() },
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class CustomFieldDef(
    @DocumentId val id: String = "",
    val userId: String = "",
    val schoolId: String = "",
    val category: String = "General",
    val label: String = "",
    val fieldType: String = CustomFieldTypes.TEXT,
    val resetsYearly: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "category" to category.trim().ifBlank { "General" }.take(60),
        "label" to label.trim().take(80),
        "fieldType" to if (fieldType in CustomFieldTypes.ALL) fieldType else CustomFieldTypes.TEXT,
        "resetsYearly" to resetsYearly,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toCreateMapForSchool(targetSchoolId: String): Map<String, Any> = mapOf(
        "schoolId" to targetSchoolId.take(32),
        "category" to category.trim().ifBlank { "General" }.take(60),
        "label" to label.trim().take(80),
        "fieldType" to if (fieldType in CustomFieldTypes.ALL) fieldType else CustomFieldTypes.TEXT,
        "resetsYearly" to resetsYearly,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "category" to category.trim().ifBlank { "General" }.take(60),
        "label" to label.trim().take(80),
        "fieldType" to if (fieldType in CustomFieldTypes.ALL) fieldType else CustomFieldTypes.TEXT,
        "resetsYearly" to resetsYearly,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ParentNotice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val schoolId: String = "",
    val audienceType: String = "INDIVIDUAL", // INDIVIDUAL, CLASS, ALL_SCHOOL
    val targetLabel: String = "",
    val recipientCount: Long = 1L,
    val channel: String = "WHATSAPP", // SMS, WHATSAPP, SHARE
    val title: String = "",
    val messageBody: String = "",
    val sentByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "audienceType" to if (audienceType in listOf("INDIVIDUAL", "CLASS", "ALL_SCHOOL")) audienceType else "INDIVIDUAL",
        "targetLabel" to targetLabel.trim().ifBlank { "Parents" }.take(120),
        "recipientCount" to recipientCount.coerceIn(0L, 1000L),
        "channel" to if (channel in listOf("SMS", "WHATSAPP", "SHARE")) channel else "SHARE",
        "title" to title.trim().take(120),
        "messageBody" to messageBody.trim().take(1500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toCreateMapForSchool(targetSchoolId: String, senderName: String): Map<String, Any> = mapOf(
        "schoolId" to targetSchoolId.take(32),
        "audienceType" to if (audienceType in listOf("INDIVIDUAL", "CLASS", "ALL_SCHOOL")) audienceType else "INDIVIDUAL",
        "targetLabel" to targetLabel.trim().ifBlank { "Parents" }.take(120),
        "recipientCount" to recipientCount.coerceIn(0L, 1000L),
        "channel" to if (channel in listOf("SMS", "WHATSAPP", "SHARE")) channel else "SHARE",
        "title" to title.trim().take(120),
        "messageBody" to messageBody.trim().take(1500),
        "sentByName" to senderName.trim().ifBlank { "Teacher" }.take(100),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "audienceType" to if (audienceType in listOf("INDIVIDUAL", "CLASS", "ALL_SCHOOL")) audienceType else "INDIVIDUAL",
        "targetLabel" to targetLabel.trim().ifBlank { "Parents" }.take(120),
        "recipientCount" to recipientCount.coerceIn(0L, 1000L),
        "channel" to if (channel in listOf("SMS", "WHATSAPP", "SHARE")) channel else "SHARE",
        "title" to title.trim().take(120),
        "messageBody" to messageBody.trim().take(1500),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class RecentChatEntry(
    val id: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val tag: String = "",
    val text: String = "",
    val sentAtEpochMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "id" to id.take(80),
        "senderUid" to senderUid.take(128),
        "senderName" to senderName.take(100),
        "tag" to tag.take(40),
        "text" to text.take(1000),
        "sentAtEpochMs" to sentAtEpochMs
    )
}

@Keep
@IgnoreExtraProperties
data class SchoolChatRoom(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val schoolName: String = "",
    val emisCode: String = "",
    val createdByUid: String = "",
    val memberUids: List<String> = emptyList(),
    val recentMessages: List<RecentChatEntry> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SchoolChatMessage(
    @DocumentId val id: String = "",
    val roomId: String = "",
    val schoolId: String = "",
    val senderUid: String = "",
    val senderName: String = "",
    val tag: String = "General",
    val text: String = "",
    val editedFlag: Boolean = false,
    val memberUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "roomId" to roomId.take(40),
        "senderUid" to senderUid.take(128),
        "senderName" to senderName.trim().ifBlank { "Teacher" }.take(100),
        "tag" to tag.trim().take(40),
        "text" to text.trim().take(1000),
        "editedFlag" to false,
        "memberUids" to memberUids.distinct().take(100),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toCreateMapForSchool(targetSchoolId: String): Map<String, Any> = mapOf(
        "schoolId" to targetSchoolId.take(32),
        "roomId" to targetSchoolId.take(40),
        "senderUid" to senderUid.take(128),
        "senderName" to senderName.trim().ifBlank { "Teacher" }.take(100),
        "tag" to tag.trim().take(40),
        "text" to text.trim().take(1000),
        "editedFlag" to false,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toEditMap(newText: String, newTag: String): Map<String, Any> = mapOf(
        "text" to newText.trim().take(1000),
        "tag" to newTag.trim().take(40),
        "editedFlag" to true,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}
