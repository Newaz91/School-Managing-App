package com.example.data.firebase

import android.content.Context
import com.example.R
import com.example.data.model.AttendanceCodes
import com.example.data.model.AttendanceLog
import com.example.data.model.ChangeEntityTypes
import com.example.data.model.ChangeStatuses
import com.example.data.model.ClassLevels
import com.example.data.model.CustomFieldDef
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamTermConfig
import com.example.data.model.ParentNotice
import com.example.data.model.PendingChangeRequest
import com.example.data.model.PendingPayloadCodec
import com.example.data.model.RecentChatEntry
import com.example.data.model.SchoolChatMessage
import com.example.data.model.SchoolTeacherMember
import com.example.data.model.SchoolWorkspace
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.TeacherProfile
import com.example.data.model.TeacherRoles
import com.example.data.model.normalizeSchoolId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class SchoolRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireSchoolId(schoolId: String): String {
        val clean = normalizeSchoolId(schoolId)
        require(clean.length >= 2) { "A valid School ID (at least 2 characters) is required." }
        return clean
    }

    private fun schoolDoc(schoolId: String) =
        db.collection("schools").document(requireSchoolId(schoolId))

    private fun handleFlowError(error: Throwable, op: OperationType, path: String): Throwable {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val exceptionToLog = firestoreError ?: (error as? Exception)
        if (exceptionToLog != null) {
            handleFirestoreError(exceptionToLog, op, path)
        }
        return error
    }

    // ─────────────────────────── SCHOOL WORKSPACE ENTRY & SETTINGS ───────────────────────────

    suspend fun createSchoolWorkspace(
        rawSchoolId: String,
        schoolName: String,
        emisCode: String,
        upazilaDistrict: String,
        teacherName: String,
        deviceId: String
    ): Result<String> {
        val cleanId = normalizeSchoolId(rawSchoolId)
        val path = "schools/$cleanId"
        return try {
            require(cleanId.length >= 2) { "School ID must be at least 2 letters or numbers." }
            require(schoolName.isNotBlank()) { "Please enter your School Name." }
            require(teacherName.isNotBlank()) { "Please enter your Teacher Name." }

            val docRef = db.collection("schools").document(cleanId)
            val snap = docRef.get().await()
            if (snap.exists()) {
                return Result.failure(
                    IllegalStateException("School ID '$cleanId' already exists. Use 'Join Existing School' to enter it.")
                )
            }

            val workspace = SchoolWorkspace(
                schoolId = cleanId,
                schoolName = schoolName.trim(),
                emisCode = emisCode.trim(),
                upazilaDistrict = upazilaDistrict.trim(),
                defaultAcademicYear = 2026L,
                adminDeviceId = deviceId,
                adminName = teacherName.trim(),
                teachers = listOf(
                    SchoolTeacherMember(
                        deviceId = deviceId,
                        name = teacherName.trim(),
                        role = TeacherRoles.ADMIN,
                        joinedAtMs = System.currentTimeMillis()
                    )
                ),
                examConfigs = ExamDefaults.defaultThreeExams()
            )
            docRef.set(workspace.toCreateMap()).await()
            Result.success(cleanId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun joinSchoolWorkspace(
        rawSchoolId: String,
        teacherName: String,
        deviceId: String
    ): Result<String> {
        val cleanId = normalizeSchoolId(rawSchoolId)
        val path = "schools/$cleanId"
        return try {
            require(cleanId.length >= 2) { "Enter a valid School ID (at least 2 characters)." }
            require(teacherName.isNotBlank()) { "Please enter your Teacher Name." }

            val docRef = db.collection("schools").document(cleanId)
            val snap = docRef.get().await()
            if (!snap.exists()) {
                return Result.failure(
                    IllegalStateException("No school found with ID '$cleanId'. Ask your School Admin for the exact ID, or create a new school.")
                )
            }

            val existing = snap.toObject(
                SchoolWorkspace::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: SchoolWorkspace(schoolId = cleanId)

            // Preserve existing role; never auto-promote on join unless no admin exists yet
            val matchedExisting = existing.teachers.firstOrNull {
                it.deviceId == deviceId || it.name.equals(teacherName.trim(), ignoreCase = true)
            }
            val hasAnyAdmin = existing.adminDeviceId.isNotBlank() ||
                existing.teachers.any { it.role.equals(TeacherRoles.ADMIN, ignoreCase = true) }
            val role = when {
                matchedExisting?.role.equals(TeacherRoles.ADMIN, ignoreCase = true) || existing.isDeviceAdmin(deviceId) -> TeacherRoles.ADMIN
                !hasAnyAdmin -> TeacherRoles.ADMIN
                else -> TeacherRoles.USER
            }

            val cleanName = teacherName.trim().take(100)
            val remainingTeachers = existing.teachers.filterNot {
                it.deviceId == deviceId || it.name.equals(cleanName, ignoreCase = true)
            }
            val updatedTeachers = (remainingTeachers + SchoolTeacherMember(
                deviceId = deviceId,
                name = cleanName,
                role = role,
                joinedAtMs = matchedExisting?.joinedAtMs ?: System.currentTimeMillis()
            )).take(60).map { it.toMap() }

            val updates = mutableMapOf<String, Any>(
                "teachers" to updatedTeachers,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (role == TeacherRoles.ADMIN) {
                updates["adminDeviceId"] = deviceId
                updates["adminName"] = cleanName
            }

            docRef.update(updates).await()
            Result.success(cleanId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    /** Clears all existing teachers & resets admin so the Teachers/Admin panel starts fresh without touching student data */
    suspend fun resetTeachersAndAdminPanel(
        rawSchoolId: String,
        newAdminName: String,
        adminDeviceId: String,
        phoneNumber: String = "",
        plainPassword: String = ""
    ): Result<Unit> {
        val cleanId = normalizeSchoolId(rawSchoolId)
        val path = "schools/$cleanId"
        return try {
            require(cleanId.length >= 2) { "A valid School ID is required." }
            require(newAdminName.isNotBlank()) { "Admin teacher name is required." }

            val cleanName = newAdminName.trim().take(100)
            val freshAdminMember = SchoolTeacherMember(
                deviceId = adminDeviceId,
                name = cleanName,
                role = TeacherRoles.ADMIN,
                joinedAtMs = System.currentTimeMillis()
            ).toMap().toMutableMap().apply {
                if (phoneNumber.isNotBlank()) {
                    put("phoneNumber", TeacherProfile.normalizePhone(phoneNumber))
                }
                if (plainPassword.isNotBlank()) {
                    put("passwordHash", TeacherProfile.hashPassword(plainPassword))
                }
            }

            schoolDoc(cleanId).update(
                mapOf(
                    "adminDeviceId" to adminDeviceId.take(64),
                    "adminName" to cleanName,
                    "teachers" to listOf(freshAdminMember),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    fun observeSchoolWorkspace(schoolId: String): Flow<SchoolWorkspace?> = flow {
        val id = requireSchoolId(schoolId)
        val path = "schools/$id"
        emitAll(
            db.collection("schools").document(id)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(
                            SchoolWorkspace::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    } else null
                }
                .catch { error -> throw handleFlowError(error, OperationType.GET, path) }
        )
    }

    suspend fun updateSchoolDetails(
        schoolId: String,
        schoolName: String,
        emisCode: String,
        upazilaDistrict: String,
        defaultAcademicYear: Long
    ): Result<Unit> {
        val path = "schools/$schoolId"
        return try {
            schoolDoc(schoolId).update(
                mapOf(
                    "schoolName" to schoolName.trim().ifBlank { "My School" }.take(150),
                    "emisCode" to emisCode.trim().take(30),
                    "upazilaDistrict" to upazilaDistrict.trim().take(100),
                    "defaultAcademicYear" to defaultAcademicYear.coerceIn(2020L, 2050L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun transferAdminRole(
        schoolId: String,
        workspace: SchoolWorkspace,
        newAdminDeviceId: String,
        newAdminName: String
    ): Result<Unit> {
        val path = "schools/$schoolId"
        return try {
            val nextTeachers = workspace.teachers.map { member ->
                val nextRole = if (member.deviceId == newAdminDeviceId) TeacherRoles.ADMIN else TeacherRoles.USER
                member.copy(role = nextRole).toMap()
            }
            schoolDoc(schoolId).update(
                mapOf(
                    "adminDeviceId" to newAdminDeviceId.take(64),
                    "adminName" to newAdminName.trim().ifBlank { "Admin Teacher" }.take(100),
                    "teachers" to nextTeachers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun saveExamConfigsDirect(schoolId: String, configs: List<ExamTermConfig>): Result<Unit> {
        val path = "schools/$schoolId"
        return try {
            val normalized = ExamDefaults.ensureThreeExams(configs).map { it.toMap() }
            schoolDoc(schoolId).update(
                mapOf(
                    "examConfigs" to normalized,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }