package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

fun normalizeSchoolId(raw: String): String = raw.trim().uppercase()
    .replace(Regex("[^A-Z0-9_\\-]+"), "-")
    .trim('-', '_')
    .take(32)

fun generateUniqueSchoolNumber(): String =
    Random.nextInt(100000, 999999).toString()

object TeacherRoles {
    const val ADMIN = "ADMIN"
    const val USER = "USER"
}

object AttendanceCodes {
    const val PRESENT = "P"
    const val ABSENT = "A"
    const val LEAVE = "L"

    fun nextCycle(current: String): String = when (current) {
        PRESENT -> ABSENT
        ABSENT -> LEAVE
        LEAVE -> ""
        else -> PRESENT
    }

    fun label(code: String): String = when (code) {
        PRESENT -> "Present"
        ABSENT -> "Absent"
        LEAVE -> "Leave"
        else -> "Not Marked"
    }
}

object ChangeStatuses {
    const val PENDING = "PENDING"
    const val APPROVED = "APPROVED"
    const val REJECTED = "REJECTED"
}

object ChangeEntityTypes {
    const val STUDENT_CREATE = "STUDENT_CREATE"
    const val STUDENT_UPDATE = "STUDENT_UPDATE"
    const val STUDENT_DELETE = "STUDENT_DELETE"
    const val STUDENT_BATCH_UPDATE = "STUDENT_BATCH_UPDATE"
    const val SUBJECT_SAVE = "SUBJECT_SAVE"
    const val SUBJECT_DELETE = "SUBJECT_DELETE"
    const val CUSTOM_FIELD_SAVE = "CUSTOM_FIELD_SAVE"
    const val CUSTOM_FIELD_DELETE = "CUSTOM_FIELD_DELETE"
    const val ATTENDANCE_SAVE = "ATTENDANCE_SAVE"
    const val EXAM_CONFIG_SAVE = "EXAM_CONFIG_SAVE"
}

@Keep
@IgnoreExtraProperties
data class SchoolTeacherMember(
    val deviceId: String = "",
    val name: String = "",
    val phoneNumber: String = "",
    val passwordHash: String = "",
    val role: String = TeacherRoles.USER,
    val joinedAtMs: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "deviceId" to deviceId.take(64),
        "name" to name.trim().ifBlank { "Teacher" }.take(100),
        "phoneNumber" to TeacherProfile.normalizePhone(phoneNumber).take(25),
        "passwordHash" to passwordHash.trim().take(128),
        "role" to if (role == TeacherRoles.ADMIN) TeacherRoles.ADMIN else TeacherRoles.USER,
        "joinedAtMs" to joinedAtMs
    )
}

@Keep
@IgnoreExtraProperties
data class MarksheetSubjectSpec(
    val subjectId: String = "",
    val subjectName: String = "",
    val classScope: String = GradeScopes.ALL_CLASSES,
    val fullMarks: Long = 100L,
    val passMarks: Long = 33L,
    val highestMarkOverride: Long = 0L
) {
    fun appliesToClass(classLevel: String): Boolean = when (classScope) {
        GradeScopes.ALL_CLASSES, "" -> true
        GradeScopes.GRADES_PRE_TO_2 -> classLevel in listOf("Pre-Primary", "Class 1", "Class 2")
        GradeScopes.GRADES_3_TO_5 -> classLevel in listOf("Class 3", "Class 4", "Class 5")
        else -> classScope.equals(classLevel, ignoreCase = true)
    }

    fun toMap(): Map<String, Any> = mapOf(
        "subjectId" to subjectId.ifBlank { generateShortId("es") }.take(40),
        "subjectName" to subjectName.trim().take(80),
        "classScope" to classScope.trim().ifBlank { GradeScopes.ALL_CLASSES }.take(50),
        "fullMarks" to fullMarks.coerceIn(1L, 1000L),
        "passMarks" to passMarks.coerceIn(0L, 1000L),
        "highestMarkOverride" to highestMarkOverride.coerceIn(0L, 1000L)
    )
}

@Keep
@IgnoreExtraProperties
data class ExamTermConfig(
    val examId: String = "",
    val title: String = "",
    val subjects: List<MarksheetSubjectSpec> = emptyList()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "examId" to examId.ifBlank { generateShortId("ex") }.take(30),
        "title" to title.trim().ifBlank { "Exam" }.take(60),
        "subjects" to subjects.take(30).map { it.toMap() }
    )
}

object ExamDefaults {
    fun defaultThreeExams(): List<ExamTermConfig> = listOf(
        ExamTermConfig(examId = "exam_1", title = "1st Term Exam", subjects = emptyList()),
        ExamTermConfig(examId = "exam_2", title = "2nd Term Exam", subjects = emptyList()),
        ExamTermConfig(examId = "exam_3", title = "Final / Annual Exam", subjects = emptyList())
    )

    fun ensureThreeExams(existing: List<ExamTermConfig>): List<ExamTermConfig> {
        val base = defaultThreeExams()
        return base.mapIndexed { idx, fallback ->
            existing.firstOrNull { it.examId == fallback.examId }
                ?: existing.getOrNull(idx)
                ?: fallback
        }
    }
}

@Keep
@IgnoreExtraProperties
data class SchoolWorkspace(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val schoolName: String = "",
    val emisCode: String = "",
    val upazilaDistrict: String = "",
    val defaultAcademicYear: Long = 2026L,
    val lastAutoRolloverYear: Long = 0L,
    val teachersPanelVersion: Long = 1L,
    val adminDeviceId: String = "",
    val adminName: String = "",
    val teachers: List<SchoolTeacherMember> = emptyList(),
    val examConfigs: List<ExamTermConfig> = ExamDefaults.defaultThreeExams(),
    val recentMessages: List<RecentChatEntry> = emptyList(),
    val latestAppVersionCode: Long = 0L,
    val latestAppVersionName: String = "",
    val appUpdateUrl: String = "",
    val appUpdateNotes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun isDeviceAdmin(deviceId: String): Boolean =
        adminDeviceId.isNotBlank() && adminDeviceId == deviceId

    fun findTeacher(deviceId: String, phoneNumber: String = ""): SchoolTeacherMember? {
        val normPhone = TeacherProfile.normalizePhone(phoneNumber)
        if (normPhone.isNotBlank()) {
            val byPhone = teachers.firstOrNull {
                it.phoneNumber.isNotBlank() && TeacherProfile.normalizePhone(it.phoneNumber) == normPhone
            }
            if (byPhone != null) return byPhone
        }
        return teachers.firstOrNull { it.deviceId == deviceId }
    }

    fun isTeacherAdmin(deviceId: String, phoneNumber: String = ""): Boolean {
        val normPhone = TeacherProfile.normalizePhone(phoneNumber)
        if (normPhone.isNotBlank()) {
            val byPhone = teachers.firstOrNull {
                it.phoneNumber.isNotBlank() && TeacherProfile.normalizePhone(it.phoneNumber) == normPhone
            }
            if (byPhone != null) {
                return byPhone.role.equals(TeacherRoles.ADMIN, ignoreCase = true)
            }
        }
        return isDeviceAdmin(deviceId)
    }

    fun toTeacherProfileView(activeTeacherName: String): TeacherProfile =
        TeacherProfile(
            id = schoolId,
            userId = schoolId,
            teacherName = activeTeacherName.ifBlank { adminName },
            schoolName = schoolName,
            emisCode = emisCode,
            upazilaDistrict = upazilaDistrict,
            schoolRoomId = schoolId,
            defaultAcademicYear = defaultAcademicYear
        )

    fun toCreateMap(): Map<String, Any> = mapOf(
        "schoolId" to schoolId.take(32),
        "schoolName" to schoolName.trim().ifBlank { "My Primary School" }.take(150),
        "emisCode" to emisCode.trim().take(30),
        "upazilaDistrict" to upazilaDistrict.trim().take(100),
        "defaultAcademicYear" to defaultAcademicYear.coerceIn(2020L, 2050L),
        "lastAutoRolloverYear" to lastAutoRolloverYear.coerceIn(0L, 2050L),
        "teachersPanelVersion" to teachersPanelVersion.coerceAtLeast(2L),
        "adminDeviceId" to adminDeviceId.take(64),
        "adminName" to adminName.trim().ifBlank { "Head Teacher" }.take(100),
        "teachers" to teachers.take(60).map { it.toMap() },
        "examConfigs" to ExamDefaults.ensureThreeExams(examConfigs).map { it.toMap() },
        "recentMessages" to recentMessages.takeLast(30).map { it.toMap() },
        "latestAppVersionCode" to latestAppVersionCode.coerceIn(0L, 999999L),
        "latestAppVersionName" to latestAppVersionName.trim().take(20),
        "appUpdateUrl" to appUpdateUrl.trim().take(500),
        "appUpdateNotes" to appUpdateNotes.trim().take(300),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AttendanceLog(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val academicYear: Long = 2026L,
    val classLevel: String = "Class 1",
    val dateKey: String = "",   // YYYY-MM-DD
    val monthKey: String = "",  // YYYY-MM
    val statuses: Map<String, String> = emptyMap(), // studentId -> P / A / L
    val markedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(targetSchoolId: String): Map<String, Any> = mapOf(
        "schoolId" to targetSchoolId.take(32),
        "academicYear" to academicYear.coerceIn(2020L, 2050L),
        "classLevel" to classLevel.trim().ifBlank { "Class 1" }.take(40),
        "dateKey" to dateKey.trim().take(16),
        "monthKey" to monthKey.trim().take(10),
        "statuses" to cleanStatusesMap(),
        "markedByName" to markedByName.trim().ifBlank { "Teacher" }.take(100),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    fun toUpdateMap(): Map<String, Any> = mapOf(
        "statuses" to cleanStatusesMap(),
        "markedByName" to markedByName.trim().ifBlank { "Teacher" }.take(100),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    private fun cleanStatusesMap(): Map<String, String> = statuses.entries
        .filter { it.key.isNotBlank() && it.value in listOf(AttendanceCodes.PRESENT, AttendanceCodes.ABSENT, AttendanceCodes.LEAVE) }
        .take(300)
        .associate { it.key.trim().take(64) to it.value }

    companion object {
        fun buildDocId(academicYear: Int, classLevel: String, dateKey: String): String {
            val safeClass = classLevel.trim().replace(Regex("[^a-zA-Z0-9]+"), "_")
            return "${academicYear}_${safeClass}_${dateKey}"
        }
    }
}

@Keep
@IgnoreExtraProperties
data class PendingChangeRequest(
    @DocumentId val id: String = "",
    val schoolId: String = "",
    val entityType: String = ChangeEntityTypes.STUDENT_UPDATE,
    val targetId: String = "",
    val submittedByDeviceId: String = "",
    val submittedByName: String = "",
    val summary: String = "",
    val detailsText: String = "",
    val payloadJson: String = "{}",
    val status: String = ChangeStatuses.PENDING,
    val reviewedByName: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(targetSchoolId: String): Map<String, Any> = mapOf(
        "schoolId" to targetSchoolId.take(32),
        "entityType" to entityType.take(40),
        "targetId" to targetId.take(80),
        "submittedByDeviceId" to submittedByDeviceId.take(64),
        "submittedByName" to submittedByName.trim().ifBlank { "Teacher" }.take(100),
        "summary" to summary.trim().ifBlank { "Pending change" }.take(180),
        "detailsText" to detailsText.trim().take(800),
        "payloadJson" to payloadJson.take(180000),
        "status" to ChangeStatuses.PENDING,
        "reviewedByName" to "",
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

object PendingPayloadCodec {
    private fun mapToJson(map: Map<String, String>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v) }
        return obj
    }

    private fun jsonToMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        val map = mutableMapOf<String, String>()
        obj.keys().forEach { k ->
            val v = obj.optString(k, "")
            if (v.isNotBlank()) map[k] = v
        }
        return map
    }

    fun encodeStudent(student: StudentRecord): String = JSONObject().apply {
        put("id", student.id)
        put("studentNameEn", student.studentNameEn)
        put("studentNameBn", student.studentNameBn)
        put("fatherNameEn", student.fatherNameEn)
        put("motherNameEn", student.motherNameEn)
        put("dateOfBirth", student.dateOfBirth)
        put("guardianPhone", student.guardianPhone)
        put("address", student.effectiveAddress())
        put("photoBase64", student.photoBase64)
        put("rollNumber", student.rollNumber)
        put("nextYearRollNumber", student.nextYearRollNumber)
        put("classLevel", student.classLevel)
        put("section", student.section)
        put("academicYear", student.academicYear)
        put("totalSchoolDays", student.totalSchoolDays)
        put("daysPresent", student.daysPresent)
        put("promotionStatus", student.promotionStatus)
        put("customValues", mapToJson(student.customValues))
        put("subjectValues", mapToJson(student.subjectValues))
        put("examResults", mapToJson(student.examResults))
        put("sectorNotes", mapToJson(student.sectorNotes))
    }.toString()

    fun decodeStudent(jsonStr: String): StudentRecord {
        val obj = JSONObject(jsonStr)
        return StudentRecord(
            id = obj.optString("id", ""),
            studentNameEn = obj.optString("studentNameEn", ""),
            studentNameBn = obj.optString("studentNameBn", ""),
            fatherNameEn = obj.optString("fatherNameEn", ""),
            motherNameEn = obj.optString("motherNameEn", ""),
            dateOfBirth = obj.optString("dateOfBirth", ""),
            guardianPhone = obj.optString("guardianPhone", ""),
            address = obj.optString("address", ""),
            photoBase64 = obj.optString("photoBase64", ""),
            rollNumber = obj.optString("rollNumber", ""),
            nextYearRollNumber = obj.optString("nextYearRollNumber", ""),
            classLevel = obj.optString("classLevel", "Class 1"),
            section = obj.optString("section", "A"),
            academicYear = obj.optLong("academicYear", 2026L),
            totalSchoolDays = obj.optLong("totalSchoolDays", 0L),
            daysPresent = obj.optLong("daysPresent", 0L),
            promotionStatus = obj.optString("promotionStatus", "Studying"),
            customValues = jsonToMap(obj.optJSONObject("customValues")),
            subjectValues = jsonToMap(obj.optJSONObject("subjectValues")),
            examResults = jsonToMap(obj.optJSONObject("examResults")),
            sectorNotes = jsonToMap(obj.optJSONObject("sectorNotes"))
        )
    }

    fun encodeSubject(subject: SubjectDefinition): String = JSONObject().apply {
        put("id", subject.id)
        put("name", subject.name)
        put("gradeScope", subject.gradeScope)
        val arr = JSONArray()
        subject.fields.forEach { f ->
            arr.put(JSONObject().apply {
                put("fieldId", f.fieldId)
                put("label", f.label)
                put("fieldType", f.fieldType)
                put("maxMarks", f.maxMarks)
            })
        }
        put("fields", arr)
    }.toString()

    fun decodeSubject(jsonStr: String): SubjectDefinition {
        val obj = JSONObject(jsonStr)
        val arr = obj.optJSONArray("fields") ?: JSONArray()
        val fields = buildList {
            for (i in 0 until arr.length()) {
                val f = arr.getJSONObject(i)
                add(
                    SubjectFieldDef(
                        fieldId = f.optString("fieldId", ""),
                        label = f.optString("label", ""),
                        fieldType = f.optString("fieldType", CustomFieldTypes.NUMBER),
                        maxMarks = f.optLong("maxMarks", 100L)
                    )
                )
            }
        }
        return SubjectDefinition(
            id = obj.optString("id", ""),
            name = obj.optString("name", ""),
            gradeScope = obj.optString("gradeScope", GradeScopes.ALL_CLASSES),
            fields = fields
        )
    }

    fun encodeCustomField(field: CustomFieldDef): String = JSONObject().apply {
        put("id", field.id)
        put("category", field.category)
        put("label", field.label)
        put("fieldType", field.fieldType)
        put("resetsYearly", field.resetsYearly)
    }.toString()

    fun decodeCustomField(jsonStr: String): CustomFieldDef {
        val obj = JSONObject(jsonStr)
        return CustomFieldDef(
            id = obj.optString("id", ""),
            category = obj.optString("category", "General"),
            label = obj.optString("label", ""),
            fieldType = obj.optString("fieldType", CustomFieldTypes.TEXT),
            resetsYearly = obj.optBoolean("resetsYearly", false)
        )
    }

    fun encodeAttendanceLog(log: AttendanceLog): String = JSONObject().apply {
        put("id", log.id)
        put("academicYear", log.academicYear)
        put("classLevel", log.classLevel)
        put("dateKey", log.dateKey)
        put("monthKey", log.monthKey)
        put("markedByName", log.markedByName)
        put("statuses", mapToJson(log.statuses))
    }.toString()

    fun decodeAttendanceLog(jsonStr: String): AttendanceLog {
        val obj = JSONObject(jsonStr)
        return AttendanceLog(
            id = obj.optString("id", ""),
            academicYear = obj.optLong("academicYear", 2026L),
            classLevel = obj.optString("classLevel", "Class 1"),
            dateKey = obj.optString("dateKey", ""),
            monthKey = obj.optString("monthKey", ""),
            markedByName = obj.optString("markedByName", "Teacher"),
            statuses = jsonToMap(obj.optJSONObject("statuses"))
        )
    }

    fun encodeExamConfigs(configs: List<ExamTermConfig>): String {
        val arr = JSONArray()
        configs.forEach { exam ->
            val subArr = JSONArray()
            exam.subjects.forEach { sub ->
                subArr.put(JSONObject().apply {
                    put("subjectId", sub.subjectId)
                    put("subjectName", sub.subjectName)
                    put("classScope", sub.classScope)
                    put("fullMarks", sub.fullMarks)
                    put("passMarks", sub.passMarks)
                    put("highestMarkOverride", sub.highestMarkOverride)
                })
            }
            arr.put(JSONObject().apply {
                put("examId", exam.examId)
                put("title", exam.title)
                put("subjects", subArr)
            })
        }
        return arr.toString()
    }

    fun decodeExamConfigs(jsonStr: String): List<ExamTermConfig> {
        val arr = JSONArray(jsonStr)
        return buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val subArr = obj.optJSONArray("subjects") ?: JSONArray()
                val subs = buildList {
                    for (j in 0 until subArr.length()) {
                        val s = subArr.getJSONObject(j)
                        add(
                            MarksheetSubjectSpec(
                                subjectId = s.optString("subjectId", ""),
                                subjectName = s.optString("subjectName", ""),
                                classScope = s.optString("classScope", GradeScopes.ALL_CLASSES),
                                fullMarks = s.optLong("fullMarks", 100L),
                                passMarks = s.optLong("passMarks", 33L),
                                highestMarkOverride = s.optLong("highestMarkOverride", 0L)
                            )
                        )
                    }
                }
                add(
                    ExamTermConfig(
                        examId = obj.optString("examId", "exam_${i + 1}"),
                        title = obj.optString("title", "Exam ${i + 1}"),
                        subjects = subs
                    )
                )
            }
        }
    }

    fun encodeBatchStudentUpdates(updatesByStudentId: Map<String, Map<String, Any>>): String {
        val root = JSONObject()
        updatesByStudentId.forEach { (stuId, fields) ->
            val fObj = JSONObject()
            fields.forEach { (k, v) ->
                when (v) {
                    is Map<*, *> -> {
                        val sub = JSONObject()
                        v.forEach { (mk, mv) ->
                            if (mk != null && mv != null) sub.put(mk.toString(), mv.toString())
                        }
                        fObj.put(k, sub)
                    }
                    is Number -> fObj.put(k, v.toLong())
                    else -> fObj.put(k, v.toString())
                }
            }
            root.put(stuId, fObj)
        }
        return root.toString()
    }

    fun decodeBatchStudentUpdates(jsonStr: String): Map<String, Map<String, Any>> {
        val root = JSONObject(jsonStr)
        val out = mutableMapOf<String, Map<String, Any>>()
        root.keys().forEach { stuId ->
            val fObj = root.getJSONObject(stuId)
            val fields = mutableMapOf<String, Any>()
            fObj.keys().forEach { k ->
                val raw = fObj.get(k)
                when (raw) {
                    is JSONObject -> {
                        fields[k] = jsonToMap(raw)
                    }
                    is Number -> fields[k] = raw.toLong()
                    else -> fields[k] = raw.toString()
                }
            }
            out[stuId] = fields
        }
        return out
    }
}
