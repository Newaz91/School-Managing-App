package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.roundToInt

object ClassLevels {
    val ALL = listOf(
        "Pre-Primary",
        "Class 1",
        "Class 2",
        "Class 3",
        "Class 4",
        "Class 5"
    )

    fun shortLabel(classLevel: String): String = when (classLevel) {
        "Pre-Primary" -> "Pre"
        else -> classLevel.replace("Class ", "C-")
    }

    fun sortOrder(classLevel: String): Int = ALL.indexOf(classLevel).let { if (it == -1) 99 else it }
}

@Keep
@IgnoreExtraProperties
data class StudentRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val schoolId: String = "",
    val lastUpdatedByName: String = "",

    // 1. FIXED CORE FIELDS (Never removed, shared across years)
    val studentNameEn: String = "",
    val studentNameBn: String = "",
    val fatherNameEn: String = "",
    val motherNameEn: String = "",
    val dateOfBirth: String = "",
    val guardianPhone: String = "",
    val address: String = "",
    val photoBase64: String = "",

    // Legacy address sub-fields preserved only for backwards-compatible reading
    val villageWard: String = "",
    val unionMunicipality: String = "",
    val upazilaDistrict: String = "",

    // 2. YEARLY FIELDS (Changeable every academic year)
    val rollNumber: String = "",
    val nextYearRollNumber: String = "",
    val classLevel: String = "Class 1",
    val section: String = "A",
    val academicYear: Long = 2026L,
    val totalSchoolDays: Long = 0L,
    val daysPresent: Long = 0L,
    val promotionStatus: String = "Studying",

    // Legacy marks preserved only for fallback score reading on older docs
    val annualTotalMarks: Long = 0L,
    val banglaMarks: Long = 0L,
    val englishMarks: Long = 0L,
    val mathMarks: Long = 0L,

    // 3. FLEXIBLE DYNAMIC VALUES (Created freely by the teacher)
    val customValues: Map<String, String> = emptyMap(),
    val subjectValues: Map<String, String> = emptyMap(),
    val examResults: Map<String, String> = emptyMap(),

    // 4. PER-FIELD COMMENTS & DETAILS (Available on every fixed, yearly, subject, and custom field)
    val sectorNotes: Map<String, String> = emptyMap(),

    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun primaryName(): String = studentNameBn.trim().ifBlank { studentNameEn.trim() }

    fun secondaryName(): String {
        val bn = studentNameBn.trim()
        val en = studentNameEn.trim()
        return if (bn.isNotBlank() && en.isNotBlank() && !en.equals(bn, ignoreCase = false)) en else ""
    }

    fun combinedDisplayName(): String {
        val primary = primaryName()
        if (primary.isBlank()) return "—"
        val secondary = secondaryName()
        return if (secondary.isNotBlank()) "$primary ($secondary)" else primary
    }

    fun effectiveAddress(): String {
        if (address.isNotBlank()) return address
        return listOf(villageWard, unionMunicipality, upazilaDistrict)
            .filter { it.isNotBlank() }
            .joinToString(", ")
    }

    fun attendancePercentage(): Int {
        if (totalSchoolDays <= 0L) return 0
        return ((daysPresent.toDouble() / totalSchoolDays.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)
    }

    fun sectorCommentsCount(): Int = sectorNotes.count { it.value.isNotBlank() }

    fun noteForKey(key: String): String = sectorNotes[key] ?: ""

    fun withNoteForKey(key: String, note: String): StudentRecord {
        val next = sectorNotes.toMutableMap()
        if (note.isBlank()) {
            next.remove(key)
        } else {
            next[key] = note.take(300)
        }
        return copy(sectorNotes = next)
    }

    fun customValue(fieldId: String): String = customValues[fieldId] ?: ""

    fun withCustomValue(fieldId: String, value: String): StudentRecord {
        val next = customValues.toMutableMap()
        if (value.isBlank()) {
            next.remove(fieldId)
        } else {
            next[fieldId] = value.take(300)
        }
        return copy(customValues = next)
    }

    fun subjectKey(subjectId: String, fieldId: String): String = "${subjectId}__${fieldId}"

    fun subjectValue(subjectId: String, fieldId: String): String =
        subjectValues[subjectKey(subjectId, fieldId)] ?: ""

    fun withSubjectValue(subjectId: String, fieldId: String, value: String): StudentRecord {
        val next = subjectValues.toMutableMap()
        val key = subjectKey(subjectId, fieldId)
        if (value.isBlank()) {
            next.remove(key)
        } else {
            next[key] = value.take(200)
        }
        return copy(subjectValues = next)
    }

    fun examMarkKey(examId: String, subjectId: String): String = "${examId}__${subjectId}"

    fun examContinuousKey(examId: String, subjectId: String): String = "${examId}__${subjectId}__cont"

    fun examSummativeKey(examId: String, subjectId: String): String = "${examId}__${subjectId}__sum"

    fun examContinuousRaw(examId: String, subjectId: String): String =
        examResults[examContinuousKey(examId, subjectId)] ?: ""

    fun examSummativeRaw(examId: String, subjectId: String): String =
        examResults[examSummativeKey(examId, subjectId)] ?: ""

    fun examContinuousDouble(examId: String, subjectId: String): Double? =
        examContinuousRaw(examId, subjectId).trim().toDoubleOrNull()

    fun examSummativeDouble(examId: String, subjectId: String): Double? =
        examSummativeRaw(examId, subjectId).trim().toDoubleOrNull()

    fun examMarkRaw(examId: String, subjectId: String): String {
        val cont = examContinuousDouble(examId, subjectId)
        val sum = examSummativeDouble(examId, subjectId)
        if (cont != null || sum != null) {
            val total = (cont ?: 0.0) + (sum ?: 0.0)
            val rounded = total.roundToInt().toDouble()
            return if (kotlin.math.abs(total - rounded) < 0.01) {
                rounded.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", total)
            }
        }
        return examResults[examMarkKey(examId, subjectId)] ?: ""
    }

    fun examMarkDouble(examId: String, subjectId: String): Double? {
        val cont = examContinuousDouble(examId, subjectId)
        val sum = examSummativeDouble(examId, subjectId)
        return if (cont != null || sum != null) {
            (cont ?: 0.0) + (sum ?: 0.0)
        } else {
            examResults[examMarkKey(examId, subjectId)]?.trim()?.toDoubleOrNull()
        }
    }

    fun withExamMark(examId: String, subjectId: String, rawValue: String): StudentRecord {
        val next = examResults.toMutableMap()
        val key = examMarkKey(examId, subjectId)
        val clean = rawValue.trim()
        if (clean.isBlank()) {
            next.remove(key)
        } else {
            next[key] = clean.take(20)
        }
        return copy(examResults = next)
    }

    fun withExamTermParts(
        examId: String,
        subjectId: String,
        contRaw: String,
        sumRaw: String
    ): StudentRecord {
        val next = examResults.toMutableMap()
        val contKey = examContinuousKey(examId, subjectId)
        val sumKey = examSummativeKey(examId, subjectId)
        val totalKey = examMarkKey(examId, subjectId)

        val cleanCont = contRaw.trim().take(20)
        val cleanSum = sumRaw.trim().take(20)

        if (cleanCont.isBlank()) next.remove(contKey) else next[contKey] = cleanCont
        if (cleanSum.isBlank()) next.remove(sumKey) else next[sumKey] = cleanSum

        val contNum = cleanCont.toDoubleOrNull()
        val sumNum = cleanSum.toDoubleOrNull()
        if (contNum != null || sumNum != null) {
            val total = (contNum ?: 0.0) + (sumNum ?: 0.0)
            val rounded = total.roundToInt().toDouble()
            next[totalKey] = if (kotlin.math.abs(total - rounded) < 0.01) {
                rounded.toInt().toString()
            } else {
                String.format(java.util.Locale.US, "%.1f", total)
            }
        } else if (cleanCont.isBlank() && cleanSum.isBlank()) {
            next.remove(totalKey)
        }
        return copy(examResults = next)
    }

    fun filledFieldProgress(
        customFields: List<CustomFieldDef> = emptyList(),
        subjects: List<SubjectDefinition> = emptyList()
    ): Pair<Int, Int> {
        val fixedValues = listOf(
            primaryName(), fatherNameEn, motherNameEn, dateOfBirth, guardianPhone, effectiveAddress()
        )
        val yearlyValues = listOf(
            rollNumber, classLevel, academicYear.toString()
        )
        val matchingSubjectFields = subjects
            .filter { it.appliesToClass(classLevel) }
            .flatMap { sub -> sub.fields.map { sub.id to it.fieldId } }

        val filledFixed = fixedValues.count { it.isNotBlank() }
        val filledYearly = yearlyValues.count { it.isNotBlank() }
        val filledCustom = customFields.count { cf -> !customValues[cf.id].isNullOrBlank() }
        val filledSubjects = matchingSubjectFields.count { (subId, fId) ->
            subjectValue(subId, fId).isNotBlank()
        }

        val total = fixedValues.size + yearlyValues.size + customFields.size + matchingSubjectFields.size
        val filled = filledFixed + filledYearly + filledCustom + filledSubjects
        return filled to total.coerceAtLeast(1)
    }

    fun subjectPercentage(subject: SubjectDefinition): Int? {
        val scores = subject.fields.mapNotNull { field ->
            val rawVal = subjectValue(subject.id, field.fieldId).trim()
            if (rawVal.isBlank()) return@mapNotNull null
            when (field.fieldType) {
                CustomFieldTypes.NUMBER -> {
                    val raw = rawVal.toDoubleOrNull() ?: return@mapNotNull null
                    val max = if (field.maxMarks > 0L) field.maxMarks.toDouble() else 100.0
                    ((raw / max) * 100.0).coerceIn(0.0, 100.0)
                }
                CustomFieldTypes.LEVEL -> when (rawVal) {
                    "Mastered" -> 100.0
                    "Good" -> 75.0
                    "Average" -> 50.0
                    "Needs Help" -> 25.0
                    else -> null
                }
                CustomFieldTypes.YES_NO -> when (rawVal) {
                    "Yes" -> 100.0
                    "No" -> 0.0
                    else -> null
                }
                else -> null
            }
        }
        return if (scores.isEmpty()) null else scores.average().roundToInt()
    }

    fun overallPerformanceScore(subjects: List<SubjectDefinition> = emptyList()): Int {
        val matchingSubjects = subjects.filter { it.appliesToClass(classLevel) }
        val subjectPcts = matchingSubjects.mapNotNull { subjectPercentage(it) }
        val parts = buildList {
            if (totalSchoolDays > 0L) add(attendancePercentage())
            addAll(subjectPcts)
            // Fallback to legacy fields if no dynamic subject scores exist yet
            if (subjectPcts.isEmpty()) {
                if (banglaMarks > 0L) add(banglaMarks.coerceAtMost(100L).toInt())
                if (englishMarks > 0L) add(englishMarks.coerceAtMost(100L).toInt())
                if (mathMarks > 0L) add(mathMarks.coerceAtMost(100L).toInt())
                if (annualTotalMarks > 0L) {
                    add(((annualTotalMarks * 100.0) / 600.0).roundToInt().coerceIn(0, 100))
                }
            }
        }
        return if (parts.isEmpty()) 0 else parts.average().roundToInt().coerceIn(0, 100)
    }

    fun rollSortKey(): Int = rollNumber.filter { it.isDigit() }.toIntOrNull() ?: Int.MAX_VALUE

    fun toMutableFieldMap(): Map<String, Any> {
        val cleanCustom = customValues.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(100)
            .associate { it.key.trim().take(60) to it.value.trim().take(300) }
        val cleanSubjects = subjectValues.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(150)
            .associate { it.key.trim().take(90) to it.value.trim().take(200) }
        val cleanNotes = sectorNotes.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(150)
            .associate { it.key.trim().take(90) to it.value.trim().take(300) }
        val cleanExams = examResults.entries
            .filter { it.key.isNotBlank() && it.value.isNotBlank() }
            .take(350)
            .associate { it.key.trim().take(90) to it.value.trim().take(20) }

        return mapOf(
            "studentNameEn" to studentNameEn.trim().take(120),
            "studentNameBn" to studentNameBn.trim().take(120),
            "fatherNameEn" to fatherNameEn.trim().take(120),
            "motherNameEn" to motherNameEn.trim().take(120),
            "dateOfBirth" to dateOfBirth.trim().take(30),
            "guardianPhone" to guardianPhone.trim().take(30),
            "address" to effectiveAddress().trim().take(250),
            "photoBase64" to photoBase64.take(140000),
            "rollNumber" to rollNumber.trim().take(20),
            "nextYearRollNumber" to nextYearRollNumber.trim().take(20),
            "classLevel" to classLevel.trim().ifBlank { "Class 1" }.take(40),
            "section" to section.trim().take(30),
            "academicYear" to academicYear.coerceIn(2020L, 2050L),
            "totalSchoolDays" to totalSchoolDays.coerceIn(0L, 366L),
            "daysPresent" to daysPresent.coerceIn(0L, 366L),
            "promotionStatus" to promotionStatus.trim().ifBlank { "Studying" }.take(40),
            "customValues" to cleanCustom,
            "subjectValues" to cleanSubjects,
            "examResults" to cleanExams,
            "sectorNotes" to cleanNotes,
            "lastUpdatedByName" to lastUpdatedByName.trim().take(100),
            "updatedAt" to FieldValue.serverTimestamp()
        )
    }

    fun toCreateMap(uid: String): Map<String, Any> {
        val map = toMutableFieldMap().toMutableMap()
        map["userId"] = uid
        map["createdAt"] = FieldValue.serverTimestamp()
        return map
    }

    fun toCreateMapForSchool(targetSchoolId: String, actorName: String): Map<String, Any> {
        val map = toMutableFieldMap().toMutableMap()
        map["schoolId"] = targetSchoolId.take(32)
        if (actorName.isNotBlank()) {
            map["lastUpdatedByName"] = actorName.trim().take(100)
        }
        map["createdAt"] = FieldValue.serverTimestamp()
        return map
    }
}
