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
import com.example.data.model.generateShortId
import com.example.data.model.normalizeSchoolId
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import com.example.util.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

class SchoolRepository(
    private val db: FirebaseFirestore,
    private val isOnlineProvider: () -> Boolean = { true }
) {
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        isOnlineProvider = { NetworkMonitor.isCurrentlyOnline(context.applicationContext) }
    )

    private suspend fun <T> Task<T>.awaitWriteOfflineSafe(): T? {
        val timeoutMs = if (isOnlineProvider()) 3500L else 200L
        return withTimeoutOrNull(timeoutMs) { await() }
    }

    private suspend fun getDocOfflineSafe(
        ref: com.google.firebase.firestore.DocumentReference
    ): DocumentSnapshot? {
        if (isOnlineProvider()) {
            val onlineSnap = withTimeoutOrNull(2500L) {
                runCatching { ref.get().await() }.getOrNull()
            }
            if (onlineSnap != null) return onlineSnap
        }
        return runCatching { ref.get(Source.CACHE).await() }.getOrNull()
    }

    private suspend fun getQueryOfflineSafe(query: Query): com.google.firebase.firestore.QuerySnapshot? {
        if (isOnlineProvider()) {
            val onlineSnap = withTimeoutOrNull(2500L) {
                runCatching { query.get().await() }.getOrNull()
            }
            if (onlineSnap != null) return onlineSnap
        }
        return runCatching { query.get(Source.CACHE).await() }.getOrNull()
    }

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

    suspend fun fetchSchoolWorkspaceOnce(rawSchoolId: String): Result<SchoolWorkspace?> {
        val cleanId = normalizeSchoolId(rawSchoolId)
        if (cleanId.length < 2) return Result.success(null)
        val path = "schools/$cleanId"
        return try {
            val snap = db.collection("schools").document(cleanId).get().await()
            if (!snap.exists()) {
                Result.success(null)
            } else {
                Result.success(
                    snap.toObject(
                        SchoolWorkspace::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun createSchoolWorkspace(
        rawSchoolId: String,
        schoolName: String,
        emisCode: String,
        upazilaDistrict: String,
        teacherName: String,
        deviceId: String,
        phoneNumber: String = "",
        passwordHash: String = ""
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
                    IllegalStateException("School ID '$cleanId' already exists. Use 'Sign Up / Login' to enter it.")
                )
            }

            val normPhone = TeacherProfile.normalizePhone(phoneNumber)
            val workspace = SchoolWorkspace(
                schoolId = cleanId,
                schoolName = schoolName.trim(),
                emisCode = emisCode.trim(),
                upazilaDistrict = upazilaDistrict.trim(),
                defaultAcademicYear = 2026L,
                lastAutoRolloverYear = 0L,
                teachersPanelVersion = 2L,
                adminDeviceId = deviceId,
                adminName = teacherName.trim(),
                teachers = listOf(
                    SchoolTeacherMember(
                        deviceId = deviceId,
                        name = teacherName.trim(),
                        phoneNumber = normPhone,
                        passwordHash = passwordHash.trim(),
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
        deviceId: String,
        phoneNumber: String = "",
        passwordHash: String = "",
        isPasswordReset: Boolean = false
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

            val cleanName = teacherName.trim().take(100)
            val normPhone = TeacherProfile.normalizePhone(phoneNumber)
            val cleanHash = passwordHash.trim()

            // v1.1 -> v1.2 Teachers Panel migration:
            // Preserve all students/subjects/marks/attendance; reset ONLY the Teachers panel so the first
            // teacher who signs into this updated version becomes School Admin automatically.
            if (existing.teachersPanelVersion < 2L) {
                val firstV2Admin = SchoolTeacherMember(
                    deviceId = deviceId,
                    name = cleanName,
                    phoneNumber = normPhone,
                    passwordHash = cleanHash,
                    role = TeacherRoles.ADMIN,
                    joinedAtMs = System.currentTimeMillis()
                )
                docRef.update(
                    mapOf(
                        "teachersPanelVersion" to 2L,
                        "adminDeviceId" to deviceId.take(64),
                        "adminName" to cleanName,
                        "teachers" to listOf(firstV2Admin.toMap()),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                return Result.success(cleanId)
            }

            // Match uniquely by normalized Phone Number first so multiple devices never duplicate a teacher,
            // and separate phones on one shared tablet never collide or overwrite each other.
            val matchedByPhone = if (normPhone.isNotBlank()) {
                existing.teachers.firstOrNull {
                    it.phoneNumber.isNotBlank() && TeacherProfile.normalizePhone(it.phoneNumber) == normPhone
                }
            } else null

            val matchedLegacyByDevice = if (matchedByPhone == null) {
                existing.teachers.firstOrNull {
                    it.deviceId == deviceId && it.phoneNumber.isBlank()
                } ?: if (normPhone.isBlank()) existing.teachers.firstOrNull { it.deviceId == deviceId } else null
            } else null

            val matchedExisting = matchedByPhone ?: matchedLegacyByDevice

            // Verify password server-state when account already has a password hash and not resetting via OTP
            if (matchedExisting != null &&
                matchedExisting.passwordHash.isNotBlank() &&
                cleanHash.isNotBlank() &&
                !isPasswordReset &&
                matchedExisting.passwordHash != cleanHash
            ) {
                return Result.failure(
                    IllegalArgumentException("Incorrect password for $normPhone. Use 'Forgot Password?' if needed.")
                )
            }

            val hasAnyAdmin = existing.teachers.any { it.role.equals(TeacherRoles.ADMIN, ignoreCase = true) } ||
                (existing.teachers.isEmpty() && existing.adminDeviceId.isNotBlank())

            val role = when {
                matchedExisting?.role.equals(TeacherRoles.ADMIN, ignoreCase = true) -> TeacherRoles.ADMIN
                matchedLegacyByDevice != null && existing.isDeviceAdmin(deviceId) -> TeacherRoles.ADMIN
                !hasAnyAdmin -> TeacherRoles.ADMIN
                else -> TeacherRoles.USER
            }

            val effectiveHash = when {
                isPasswordReset && cleanHash.isNotBlank() -> cleanHash
                !matchedExisting?.passwordHash.isNullOrBlank() -> matchedExisting!!.passwordHash
                else -> cleanHash
            }

            val updatedMember = SchoolTeacherMember(
                deviceId = deviceId,
                name = cleanName,
                phoneNumber = normPhone.ifBlank { matchedExisting?.phoneNumber.orEmpty() },
                passwordHash = effectiveHash,
                role = role,
                joinedAtMs = matchedExisting?.joinedAtMs ?: System.currentTimeMillis()
            )

            val remainingTeachers = existing.teachers.filterNot { member ->
                val samePhone = normPhone.isNotBlank() &&
                    member.phoneNumber.isNotBlank() &&
                    TeacherProfile.normalizePhone(member.phoneNumber) == normPhone
                val sameLegacySlot = matchedByPhone == null && matchedLegacyByDevice != null && member == matchedLegacyByDevice
                samePhone || sameLegacySlot
            }

            val updatedTeachers = (remainingTeachers + updatedMember).take(60).map { it.toMap() }
            val updates = mutableMapOf<String, Any>(
                "teachersPanelVersion" to 2L,
                "teachers" to updatedTeachers,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (role == TeacherRoles.ADMIN) {
                updates["adminDeviceId"] = deviceId.take(64)
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
        plainPassword: String = "",
        passwordHash: String = ""
    ): Result<Unit> {
        val cleanId = normalizeSchoolId(rawSchoolId)
        val path = "schools/$cleanId"
        return try {
            require(cleanId.length >= 2) { "A valid School ID is required." }
            require(newAdminName.isNotBlank()) { "Admin teacher name is required." }

            val cleanName = newAdminName.trim().take(100)
            val effectiveHash = when {
                passwordHash.isNotBlank() -> passwordHash.trim()
                plainPassword.isNotBlank() -> TeacherProfile.hashPassword(plainPassword)
                else -> ""
            }
            val freshAdminMember = SchoolTeacherMember(
                deviceId = adminDeviceId,
                name = cleanName,
                phoneNumber = TeacherProfile.normalizePhone(phoneNumber),
                passwordHash = effectiveHash,
                role = TeacherRoles.ADMIN,
                joinedAtMs = System.currentTimeMillis()
            )

            schoolDoc(cleanId).update(
                mapOf(
                    "teachersPanelVersion" to 2L,
                    "adminDeviceId" to adminDeviceId.take(64),
                    "adminName" to cleanName,
                    "teachers" to listOf(freshAdminMember.toMap()),
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

    fun observeHasPendingSync(schoolId: String): Flow<Boolean> = flow {
        val id = requireSchoolId(schoolId)
        val studentsPending = schoolDoc(id).collection("students")
            .snapshots(MetadataChanges.INCLUDE)
            .map { it.metadata.hasPendingWrites() }
            .catch { emit(false) }
        val attendancePending = schoolDoc(id).collection("attendanceLogs")
            .snapshots(MetadataChanges.INCLUDE)
            .map { it.metadata.hasPendingWrites() }
            .catch { emit(false) }
        val changesPending = schoolDoc(id).collection("pendingChanges")
            .snapshots(MetadataChanges.INCLUDE)
            .map { it.metadata.hasPendingWrites() }
            .catch { emit(false) }
        emitAll(
            combine(studentsPending, attendancePending, changesPending) { a, b, c ->
                a || b || c
            }
        )
    }

    suspend fun syncPendingWritesAndCheckUpdates(schoolId: String): Result<SchoolWorkspace?> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId"
        return try {
            withTimeoutOrNull(6000L) {
                db.waitForPendingWrites().await()
            }
            val snap = if (isOnlineProvider()) {
                withTimeoutOrNull(4500L) {
                    schoolDoc(cleanId).get(Source.SERVER).await()
                } ?: schoolDoc(cleanId).get(Source.CACHE).await()
            } else {
                schoolDoc(cleanId).get(Source.CACHE).await()
            }
            val ws = if (snap.exists()) {
                snap.toObject(SchoolWorkspace::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(ws)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun saveAppUpdateMetadata(
        schoolId: String,
        latestVersionCode: Long,
        latestVersionName: String,
        updateUrl: String,
        releaseNotes: String
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId"
        return try {
            schoolDoc(cleanId).update(
                mapOf(
                    "latestAppVersionCode" to latestVersionCode.coerceIn(0L, 999999L),
                    "latestAppVersionName" to latestVersionName.trim().take(20),
                    "appUpdateUrl" to updateUrl.trim().take(500),
                    "appUpdateNotes" to releaseNotes.trim().take(300),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
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
            ).awaitWriteOfflineSafe()
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
                val matchesTarget = member.deviceId == newAdminDeviceId &&
                    (newAdminName.isBlank() || member.name.equals(newAdminName, ignoreCase = true) ||
                        workspace.teachers.count { it.deviceId == newAdminDeviceId } == 1)
                val nextRole = if (matchesTarget) TeacherRoles.ADMIN else TeacherRoles.USER
                member.copy(role = nextRole).toMap()
            }
            schoolDoc(schoolId).update(
                mapOf(
                    "adminDeviceId" to newAdminDeviceId.take(64),
                    "adminName" to newAdminName.trim().ifBlank { "Admin Teacher" }.take(100),
                    "teachers" to nextTeachers,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWriteOfflineSafe()
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
            ).awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // ─────────────────────────── STUDENTS & YEAR-END PROMOTION ───────────────────────────

    fun observeStudents(schoolId: String, academicYear: Int): Flow<List<StudentRecord>> = flow {
        val id = requireSchoolId(schoolId)
        val path = "schools/$id/students"
        emitAll(
            schoolDoc(id).collection("students")
                .whereEqualTo("academicYear", academicYear.toLong())
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            StudentRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedWith(
                        compareBy<StudentRecord> { ClassLevels.sortOrder(it.classLevel) }
                            .thenBy { it.rollSortKey() }
                            .thenBy { it.rollNumber }
                            .thenBy { it.primaryName().lowercase() }
                    )
                }
                .catch { error -> throw handleFlowError(error, OperationType.LIST, path) }
        )
    }

    suspend fun createStudentDirect(
        schoolId: String,
        student: StudentRecord,
        actorName: String
    ): Result<String> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/students"
        return try {
            require(student.studentNameBn.isNotBlank() || student.studentNameEn.isNotBlank()) {
                "Student Name (Bangla) is required."
            }
            require(student.rollNumber.isNotBlank()) { "Roll number is required." }
            val colRef = schoolDoc(cleanId).collection("students")
            val docRef = if (student.id.isNotBlank()) colRef.document(student.id) else colRef.document()
            docRef.set(student.toCreateMapForSchool(cleanId, actorName)).awaitWriteOfflineSafe()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun updateStudentDirect(
        schoolId: String,
        student: StudentRecord,
        actorName: String
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        require(student.id.isNotBlank()) { "Student ID is required for update." }
        val path = "schools/$cleanId/students/${student.id}"
        return try {
            val fields = student.copy(
                lastUpdatedByName = actorName.ifBlank { student.lastUpdatedByName }
            ).toMutableFieldMap()
            schoolDoc(cleanId).collection("students").document(student.id).update(fields).awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteStudentDirect(schoolId: String, studentId: String): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/students/$studentId"
        return try {
            require(studentId.isNotBlank()) { "Student ID is required." }
            schoolDoc(cleanId).collection("students").document(studentId).delete().awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun batchUpdateStudentsDirect(
        schoolId: String,
        updatesByStudentId: Map<String, Map<String, Any>>,
        actorName: String
    ): Result<Int> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/students"
        return try {
            if (updatesByStudentId.isEmpty()) return Result.success(0)
            val col = schoolDoc(cleanId).collection("students")
            val entries = updatesByStudentId.entries.filter { it.key.isNotBlank() && it.value.isNotEmpty() }
            entries.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { (stuId, rawFields) ->
                    val fields = mutableMapOf<String, Any>()
                    rawFields.forEach { (k, v) ->
                        if ((k.startsWith("examResults.") || k.startsWith("subjectValues.")) &&
                            v is String && v.isBlank()
                        ) {
                            fields[k] = FieldValue.delete()
                        } else {
                            fields[k] = v
                        }
                    }
                    if (actorName.isNotBlank()) {
                        fields["lastUpdatedByName"] = actorName.trim().take(100)
                    }
                    fields["updatedAt"] = FieldValue.serverTimestamp()
                    batch.update(col.document(stuId), fields)
                }
                batch.commit().awaitWriteOfflineSafe()
            }
            Result.success(entries.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    /**
     * Rolls forward an academic year (after 31 Dec or triggered manually):
     * - "Promoted" in Pre-Primary..Class 4 -> moves to next class in targetYear with nextYearRollNumber (or current roll).
     * - "Promoted" in Class 5 -> source record updated to "Passed Out".
     * - "Not Promoted" (or "Studying" when includeStudyingInSameClass == true) -> remains in SAME class in targetYear
     *   with flexible roll position (nextYearRollNumber or current roll).
     * Prevents duplicate student entries in targetYear and preserves fixed identity, photo, and comments.
     */
    suspend fun promoteStudentsToNextYear(
        schoolId: String,
        studentsToPromote: List<StudentRecord>,
        targetYear: Int,
        yearlyCustomFieldIds: Set<String>,
        actorName: String,
        includeStudyingInSameClass: Boolean = false
    ): Result<Int> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/students"
        return try {
            val col = schoolDoc(cleanId).collection("students")
            val existingInTargetSnap = col
                .whereEqualTo("academicYear", targetYear.toLong())
                .get()
                .await()
            val existingKeys = existingInTargetSnap.documents.mapNotNull { doc ->
                val s = doc.toObject(StudentRecord::class.java) ?: return@mapNotNull null
                "${s.primaryName().trim().lowercase()}|${s.fatherNameEn.trim().lowercase()}|${s.dateOfBirth.trim()}"
            }.toMutableSet()

            var affectedCount = 0
            val pendingCreates = mutableListOf<Map<String, Any>>()
            val class5PassedOutIds = mutableListOf<String>()

            studentsToPromote.forEach { student ->
                val status = student.promotionStatus.trim()
                if (status == "Passed Out" || status == "Transferred") return@forEach

                val isPromoted = status == "Promoted"
                val isRepeatSameClass = status == "Not Promoted" ||
                    (includeStudyingInSameClass && (status == "Studying" || status.isBlank()))

                if (!isPromoted && !isRepeatSameClass) return@forEach

                val classIdx = ClassLevels.ALL.indexOf(student.classLevel)
                if (isPromoted && classIdx == ClassLevels.ALL.lastIndex) {
                    // Class 5 promoted -> completed primary school ("Passed Out")
                    if (student.id.isNotBlank()) {
                        class5PassedOutIds += student.id
                    }
                    return@forEach
                }

                val key = "${student.primaryName().trim().lowercase()}|${student.fatherNameEn.trim().lowercase()}|${student.dateOfBirth.trim()}"
                if (existingKeys.contains(key)) return@forEach
                existingKeys.add(key)

                val nextClass = if (isPromoted && classIdx in 0 until ClassLevels.ALL.lastIndex) {
                    ClassLevels.ALL[classIdx + 1]
                } else {
                    student.classLevel
                }

                val nextRoll = student.nextYearRollNumber.trim()
                    .ifBlank { student.rollNumber.trim() }
                    .ifBlank { "01" }

                val carriedCustomValues = student.customValues.filterKeys { it !in yearlyCustomFieldIds }
                val rolledRecord = student.copy(
                    id = "",
                    rollNumber = nextRoll,
                    nextYearRollNumber = "",
                    classLevel = nextClass,
                    academicYear = targetYear.toLong(),
                    totalSchoolDays = 0L,
                    daysPresent = 0L,
                    promotionStatus = "Studying",
                    customValues = carriedCustomValues,
                    subjectValues = emptyMap(),
                    examResults = emptyMap()
                )
                pendingCreates += rolledRecord.toCreateMapForSchool(cleanId, actorName)
            }

            pendingCreates.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { data ->
                    batch.set(col.document(), data)
                    affectedCount++
                }
                batch.commit().await()
            }

            class5PassedOutIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { stuId ->
                    batch.update(
                        col.document(stuId),
                        mapOf(
                            "promotionStatus" to "Passed Out",
                            "lastUpdatedByName" to actorName.trim().take(100),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                    affectedCount++
                }
                batch.commit().await()
            }

            schoolDoc(cleanId).update(
                mapOf(
                    "defaultAcademicYear" to targetYear.toLong().coerceIn(2020L, 2050L),
                    "lastAutoRolloverYear" to targetYear.toLong().coerceIn(2020L, 2050L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(affectedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreBackupData(
        schoolId: String,
        students: List<StudentRecord>,
        existingStudents: List<StudentRecord>,
        subjectsToUpsert: List<SubjectDefinition>,
        customFieldsToUpsert: List<CustomFieldDef>,
        actorName: String,
        replaceMatchedExisting: Boolean = true
    ): Result<Triple<Int, Int, Int>> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId"
        return try {
            val schoolRef = schoolDoc(cleanId)
            val existingByMatchKey = existingStudents.associateBy { s ->
                "${s.academicYear}|${s.classLevel.lowercase()}|${s.rollNumber.trim().lowercase()}|${s.primaryName().trim().lowercase()}"
            }

            // Upsert subjects
            var subCount = 0
            subjectsToUpsert.forEach { sub ->
                val targetId = sub.id.ifBlank { generateShortId("sub") }
                val cleanSub = sub.copy(id = targetId)
                schoolRef.collection("subjects").document(targetId)
                    .set(cleanSub.toCreateMapForSchool(cleanId))
                    .await()
                subCount++
            }

            // Upsert custom fields
            var cfCount = 0
            customFieldsToUpsert.forEach { cf ->
                val targetId = cf.id.ifBlank { generateShortId("cf") }
                val cleanCf = cf.copy(id = targetId)
                schoolRef.collection("customFields").document(targetId)
                    .set(cleanCf.toCreateMapForSchool(cleanId))
                    .await()
                cfCount++
            }

            // Upsert students in batches
            val stuCol = schoolRef.collection("students")
            var stuCount = 0
            students.chunked(350).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { incoming ->
                    if (incoming.primaryName().isBlank()) return@forEach
                    val matchKey = "${incoming.academicYear}|${incoming.classLevel.lowercase()}|${incoming.rollNumber.trim().lowercase()}|${incoming.primaryName().trim().lowercase()}"
                    val existing = if (replaceMatchedExisting) existingByMatchKey[matchKey] else null
                    val normalizedIncoming = incoming.copy(
                        rollNumber = incoming.rollNumber.ifBlank { "01" },
                        lastUpdatedByName = actorName.ifBlank { "Backup Restore" }
                    )
                    if (existing != null && existing.id.isNotBlank()) {
                        batch.update(stuCol.document(existing.id), normalizedIncoming.toMutableFieldMap())
                    } else {
                        batch.set(stuCol.document(), normalizedIncoming.toCreateMapForSchool(cleanId, actorName))
                    }
                    stuCount++
                }
                batch.commit().await()
            }

            Result.success(Triple(stuCount, subCount, cfCount))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // ─────────────────────────── SUBJECTS & FLEXIBLE FIELDS ───────────────────────────

    fun observeSubjects(schoolId: String): Flow<List<SubjectDefinition>> = flow {
        val id = requireSchoolId(schoolId)
        val path = "schools/$id/subjects"
        emitAll(
            schoolDoc(id).collection("subjects")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            SubjectDefinition::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedBy { it.name.lowercase() }
                }
                .catch { error -> throw handleFlowError(error, OperationType.LIST, path) }
        )
    }

    suspend fun saveSubjectDirect(schoolId: String, subject: SubjectDefinition): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/subjects"
        return try {
            require(subject.name.isNotBlank()) { "Subject name is required." }
            val col = schoolDoc(cleanId).collection("subjects")
            if (subject.id.isBlank()) {
                val docRef = col.document(generateShortId("sub"))
                docRef.set(subject.toCreateMapForSchool(cleanId)).awaitWriteOfflineSafe()
            } else {
                val docRef = col.document(subject.id)
                val snap = getDocOfflineSafe(docRef)
                if (snap != null && snap.exists()) {
                    docRef.update(subject.toUpdateMap()).awaitWriteOfflineSafe()
                } else {
                    docRef.set(subject.toCreateMapForSchool(cleanId)).awaitWriteOfflineSafe()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubjectDirect(schoolId: String, subjectId: String): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/subjects/$subjectId"
        return try {
            require(subjectId.isNotBlank()) { "Subject ID is required." }
            schoolDoc(cleanId).collection("subjects").document(subjectId).delete().awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeCustomFields(schoolId: String): Flow<List<CustomFieldDef>> = flow {
        val id = requireSchoolId(schoolId)
        val path = "schools/$id/customFields"
        emitAll(
            schoolDoc(id).collection("customFields")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            CustomFieldDef::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedWith(compareBy<CustomFieldDef> { it.category.lowercase() }.thenBy { it.label.lowercase() })
                }
                .catch { error -> throw handleFlowError(error, OperationType.LIST, path) }
        )
    }

    suspend fun saveCustomFieldDirect(schoolId: String, field: CustomFieldDef): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/customFields"
        return try {
            require(field.label.isNotBlank()) { "Field label is required." }
            val col = schoolDoc(cleanId).collection("customFields")
            if (field.id.isBlank()) {
                val docRef = col.document(generateShortId("cf"))
                docRef.set(field.toCreateMapForSchool(cleanId)).awaitWriteOfflineSafe()
            } else {
                val docRef = col.document(field.id)
                val snap = getDocOfflineSafe(docRef)
                if (snap != null && snap.exists()) {
                    docRef.update(field.toUpdateMap()).awaitWriteOfflineSafe()
                } else {
                    docRef.set(field.toCreateMapForSchool(cleanId)).awaitWriteOfflineSafe()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteCustomFieldDirect(schoolId: String, fieldId: String): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/customFields/$fieldId"
        return try {
            require(fieldId.isNotBlank()) { "Field ID is required." }
            schoolDoc(cleanId).collection("customFields").document(fieldId).delete().awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // ─────────────────────────── CLASS ATTENDANCE SHEETS ───────────────────────────

    fun observeAttendanceLogs(schoolId: String, academicYear: Int): Flow<List<AttendanceLog>> = flow {
        val id = requireSchoolId(schoolId)
        val path = "schools/$id/attendanceLogs"
        emitAll(
            schoolDoc(id).collection("attendanceLogs")
                .whereEqualTo("academicYear", academicYear.toLong())
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            AttendanceLog::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }.sortedByDescending { it.dateKey }
                }
                .catch { error -> throw handleFlowError(error, OperationType.LIST, path) }
        )
    }

    suspend fun saveAttendanceLogDirect(
        schoolId: String,
        log: AttendanceLog,
        classStudents: List<StudentRecord>
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val docId = log.id.ifBlank {
            AttendanceLog.buildDocId(log.academicYear.toInt(), log.classLevel, log.dateKey)
        }
        val path = "schools/$cleanId/attendanceLogs/$docId"
        return try {
            val docRef = schoolDoc(cleanId).collection("attendanceLogs").document(docId)
            val snap = getDocOfflineSafe(docRef)
            if (snap != null && snap.exists()) {
                docRef.update(log.toUpdateMap()).awaitWriteOfflineSafe()
            } else {
                docRef.set(log.toCreateMap(cleanId)).awaitWriteOfflineSafe()
            }

            // Recompute cumulative daysPresent / totalSchoolDays across this class & year (works offline via cache)
            val logsSnap = getQueryOfflineSafe(
                schoolDoc(cleanId).collection("attendanceLogs")
                    .whereEqualTo("academicYear", log.academicYear)
            )
            val classLogs = logsSnap?.documents.orEmpty().mapNotNull {
                it.toObject(AttendanceLog::class.java)
            }.filter { it.classLevel == log.classLevel }.let { list ->
                if (list.any { it.dateKey == log.dateKey }) list else list + log
            }

            if (classStudents.isNotEmpty()) {
                val stuCol = schoolDoc(cleanId).collection("students")
                classStudents.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { stu ->
                        if (stu.id.isBlank()) return@forEach
                        val stuLogs = classLogs.mapNotNull { it.statuses[stu.id] }
                        if (stuLogs.isNotEmpty()) {
                            val totalDays = stuLogs.size.toLong().coerceIn(0L, 366L)
                            val presentDays = stuLogs.count { it == AttendanceCodes.PRESENT }.toLong().coerceIn(0L, totalDays)
                            batch.update(
                                stuCol.document(stu.id),
                                mapOf(
                                    "totalSchoolDays" to totalDays,
                                    "daysPresent" to presentDays,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }
                    }
                    batch.commit().awaitWriteOfflineSafe()
                }
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    // ─────────────────────────── PENDING ADMIN CONFIRMATIONS ───────────────────────────

    fun observePendingChanges(schoolId: String): Flow<List<PendingChangeRequest>> = flow {
        val id = requireSchoolId(schoolId)
        val path = "schools/$id/pendingChanges"
        emitAll(
            schoolDoc(id).collection("pendingChanges")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            PendingChangeRequest::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error -> throw handleFlowError(error, OperationType.LIST, path) }
        )
    }

    suspend fun submitPendingChange(schoolId: String, request: PendingChangeRequest): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/pendingChanges"
        return try {
            schoolDoc(cleanId).collection("pendingChanges")
                .add(request.toCreateMap(cleanId))
                .awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun approvePendingChange(
        schoolId: String,
        change: PendingChangeRequest,
        reviewerName: String,
        allStudents: List<StudentRecord>
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/pendingChanges/${change.id}"
        return try {
            val applyResult: Result<*> = when (change.entityType) {
                ChangeEntityTypes.STUDENT_CREATE -> {
                    val stu = PendingPayloadCodec.decodeStudent(change.payloadJson)
                    createStudentDirect(cleanId, stu, change.submittedByName.ifBlank { reviewerName })
                }
                ChangeEntityTypes.STUDENT_UPDATE -> {
                    val stu = PendingPayloadCodec.decodeStudent(change.payloadJson)
                    val targetStu = if (stu.id.isBlank() && change.targetId.isNotBlank()) stu.copy(id = change.targetId) else stu
                    if (targetStu.id.isBlank()) {
                        createStudentDirect(cleanId, targetStu, change.submittedByName.ifBlank { reviewerName })
                    } else {
                        updateStudentDirect(cleanId, targetStu, change.submittedByName.ifBlank { reviewerName })
                    }
                }
                ChangeEntityTypes.STUDENT_DELETE -> {
                    val idFromJson = runCatching { JSONObject(change.payloadJson).optString("id", "") }.getOrDefault("")
                    val targetId = change.targetId.ifBlank { idFromJson }
                    deleteStudentDirect(cleanId, targetId)
                }
                ChangeEntityTypes.STUDENT_BATCH_UPDATE -> {
                    val map = PendingPayloadCodec.decodeBatchStudentUpdates(change.payloadJson)
                    batchUpdateStudentsDirect(cleanId, map, change.submittedByName.ifBlank { reviewerName })
                }
                ChangeEntityTypes.SUBJECT_SAVE -> {
                    val sub = PendingPayloadCodec.decodeSubject(change.payloadJson)
                    saveSubjectDirect(cleanId, sub)
                }
                ChangeEntityTypes.SUBJECT_DELETE -> {
                    val idFromJson = runCatching { JSONObject(change.payloadJson).optString("id", "") }.getOrDefault("")
                    deleteSubjectDirect(cleanId, change.targetId.ifBlank { idFromJson })
                }
                ChangeEntityTypes.CUSTOM_FIELD_SAVE -> {
                    val cf = PendingPayloadCodec.decodeCustomField(change.payloadJson)
                    saveCustomFieldDirect(cleanId, cf)
                }
                ChangeEntityTypes.CUSTOM_FIELD_DELETE -> {
                    val idFromJson = runCatching { JSONObject(change.payloadJson).optString("id", "") }.getOrDefault("")
                    deleteCustomFieldDirect(cleanId, change.targetId.ifBlank { idFromJson })
                }
                ChangeEntityTypes.ATTENDANCE_SAVE -> {
                    val log = PendingPayloadCodec.decodeAttendanceLog(change.payloadJson)
                    val classStudents = allStudents.filter { it.classLevel == log.classLevel }
                    saveAttendanceLogDirect(cleanId, log, classStudents)
                }
                ChangeEntityTypes.EXAM_CONFIG_SAVE -> {
                    val configs = PendingPayloadCodec.decodeExamConfigs(change.payloadJson)
                    saveExamConfigsDirect(cleanId, configs)
                }
                else -> Result.success(Unit)
            }

            if (applyResult.isFailure) {
                return Result.failure(applyResult.exceptionOrNull() ?: IllegalStateException("Could not apply pending change."))
            }

            schoolDoc(cleanId).collection("pendingChanges").document(change.id).update(
                mapOf(
                    "status" to ChangeStatuses.APPROVED,
                    "reviewedByName" to reviewerName.trim().ifBlank { "Admin" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun rejectPendingChange(
        schoolId: String,
        changeId: String,
        reviewerName: String
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/pendingChanges/$changeId"
        return try {
            schoolDoc(cleanId).collection("pendingChanges").document(changeId).update(
                mapOf(
                    "status" to ChangeStatuses.REJECTED,
                    "reviewedByName" to reviewerName.trim().ifBlank { "Admin" }.take(100),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    // ─────────────────────────── PARENT NOTICES & TEACHERS CHAT ───────────────────────────

    fun observeParentNotices(schoolId: String): Flow<List<ParentNotice>> = flow {
        val id = requireSchoolId(schoolId)
        val path = "schools/$id/parentNotices"
        emitAll(
            schoolDoc(id).collection("parentNotices")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            ParentNotice::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error -> throw handleFlowError(error, OperationType.LIST, path) }
        )
    }

    suspend fun saveParentNotice(
        schoolId: String,
        notice: ParentNotice,
        senderName: String
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/parentNotices"
        return try {
            val col = schoolDoc(cleanId).collection("parentNotices")
            if (notice.id.isBlank()) {
                col.add(notice.toCreateMapForSchool(cleanId, senderName)).awaitWriteOfflineSafe()
            } else {
                col.document(notice.id).update(notice.toUpdateMap()).awaitWriteOfflineSafe()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteParentNotice(schoolId: String, noticeId: String): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/parentNotices/$noticeId"
        return try {
            schoolDoc(cleanId).collection("parentNotices").document(noticeId).delete().awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    fun observeSchoolMessages(schoolId: String): Flow<List<SchoolChatMessage>> = flow {
        val id = requireSchoolId(schoolId)
        val path = "schools/$id/messages"
        emitAll(
            schoolDoc(id).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc ->
                        doc.toObject(
                            SchoolChatMessage::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    }
                }
                .catch { error -> throw handleFlowError(error, OperationType.LIST, path) }
        )
    }

    suspend fun sendSchoolMessage(
        schoolId: String,
        workspace: SchoolWorkspace?,
        senderDeviceId: String,
        senderName: String,
        tag: String,
        text: String
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/messages"
        return try {
            val cleanText = text.trim()
            require(cleanText.isNotEmpty()) { "Message cannot be empty." }
            val msg = SchoolChatMessage(
                roomId = cleanId,
                schoolId = cleanId,
                senderUid = senderDeviceId,
                senderName = senderName,
                tag = tag.ifBlank { "General" },
                text = cleanText
            )
            val docRef = schoolDoc(cleanId).collection("messages").document()
            docRef.set(msg.toCreateMapForSchool(cleanId)).awaitWriteOfflineSafe()

            val recent = (workspace?.recentMessages.orEmpty() + RecentChatEntry(
                id = docRef.id,
                senderUid = senderDeviceId,
                senderName = senderName,
                tag = tag.ifBlank { "General" },
                text = cleanText,
                sentAtEpochMs = System.currentTimeMillis()
            )).takeLast(30)

            schoolDoc(cleanId).update(
                mapOf(
                    "recentMessages" to recent.map { it.toMap() },
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun editSchoolMessage(
        schoolId: String,
        messageId: String,
        newText: String,
        newTag: String
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/messages/$messageId"
        return try {
            val msg = SchoolChatMessage(text = newText, tag = newTag)
            schoolDoc(cleanId).collection("messages").document(messageId)
                .update(msg.toEditMap(newText, newTag))
                .awaitWriteOfflineSafe()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSchoolMessage(
        schoolId: String,
        workspace: SchoolWorkspace?,
        messageId: String
    ): Result<Unit> {
        val cleanId = requireSchoolId(schoolId)
        val path = "schools/$cleanId/messages/$messageId"
        return try {
            schoolDoc(cleanId).collection("messages").document(messageId).delete().awaitWriteOfflineSafe()
            if (workspace != null) {
                val filteredRecent = workspace.recentMessages.filterNot { it.id == messageId }
                if (filteredRecent.size != workspace.recentMessages.size) {
                    schoolDoc(cleanId).update(
                        mapOf(
                            "recentMessages" to filteredRecent.map { it.toMap() },
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).awaitWriteOfflineSafe()
                }
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    // ─────────────────────────── LEGACY V1.0 IMPORT SUPPORT ───────────────────────────

    suspend fun migrateLegacyUserIfPresent(schoolId: String, actorName: String): Result<Int> {
        val cleanId = requireSchoolId(schoolId)
        val uid = Firebase.auth.currentUser?.uid ?: return Result.success(0)
        val path = "users/$uid/students"
        return try {
            val legacyUserRef = db.collection("users").document(uid)
            val legacyStudentsSnap = legacyUserRef.collection("students").get().await()
            if (legacyStudentsSnap.isEmpty) return Result.success(0)

            val legacySubjects = legacyUserRef.collection("subjects").get().await()
                .documents.mapNotNull { it.toObject(SubjectDefinition::class.java) }
            val legacyCustom = legacyUserRef.collection("customFields").get().await()
                .documents.mapNotNull { it.toObject(CustomFieldDef::class.java) }

            val schoolRef = schoolDoc(cleanId)
            legacySubjects.forEach { sub ->
                val targetId = sub.id.ifBlank { generateShortId("sub") }
                schoolRef.collection("subjects").document(targetId)
                    .set(sub.toCreateMapForSchool(cleanId)).await()
            }
            legacyCustom.forEach { cf ->
                val targetId = cf.id.ifBlank { generateShortId("cf") }
                schoolRef.collection("customFields").document(targetId)
                    .set(cf.toCreateMapForSchool(cleanId)).await()
            }

            val existingSnap = schoolRef.collection("students").get().await()
            val existingKeys = existingSnap.documents.mapNotNull { doc ->
                val s = doc.toObject(StudentRecord::class.java) ?: return@mapNotNull null
                "${s.academicYear}|${s.classLevel.lowercase()}|${s.rollNumber.trim().lowercase()}|${s.primaryName().trim().lowercase()}"
            }.toMutableSet()

            var imported = 0
            val stuCol = schoolRef.collection("students")
            val legacyStudents = legacyStudentsSnap.documents.mapNotNull {
                it.toObject(StudentRecord::class.java)
            }
            legacyStudents.chunked(350).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { s ->
                    if (s.primaryName().isBlank()) return@forEach
                    val k = "${s.academicYear}|${s.classLevel.lowercase()}|${s.rollNumber.trim().lowercase()}|${s.primaryName().trim().lowercase()}"
                    if (!existingKeys.contains(k)) {
                        existingKeys.add(k)
                        batch.set(stuCol.document(), s.toCreateMapForSchool(cleanId, actorName))
                        imported++
                    }
                }
                batch.commit().await()
            }
            Result.success(imported)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }
}