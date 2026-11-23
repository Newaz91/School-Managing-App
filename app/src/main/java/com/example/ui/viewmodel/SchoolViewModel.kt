package com.example.ui.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.firebase.SchoolRepository
import com.example.data.local.AppPreferences
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
import com.example.data.model.SchoolChatMessage
import com.example.data.model.SchoolWorkspace
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.SubjectFieldDef
import com.example.data.model.TeacherProfile
import com.example.data.model.generateShortId
import com.example.data.model.normalizeSchoolId
import com.example.util.NetworkMonitor
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RegisterQuickFilter(val label: String) {
    ALL("All Students"),
    MISSING_PHONE("Missing Phone"),
    PROMOTED("Promoted"),
    WITH_NOTES("Has Notes")
}

data class DashboardSummary(
    val totalStudents: Int = 0,
    val averageAttendancePct: Int = 0,
    val averageCompletenessPct: Int = 0,
    val averagePerformancePct: Int = 0,
    val subjectsCount: Int = 0,
    val customFieldsCount: Int = 0,
    val pendingChangesCount: Int = 0,
    val classCounts: Map<String, Int> = emptyMap(),
    val classAvgCompleteness: Map<String, Int> = emptyMap()
)

class SchoolViewModel(
    private val repository: SchoolRepository,
    val schoolId: String,
    val deviceId: String,
    val teacherName: String,
    val teacherPhone: String = "",
    initialYear: Int = 2026,
    appContext: Context? = null
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(initialYear)
    val selectedYear: StateFlow<Int> = _selectedYear.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedClassFilter = MutableStateFlow<String?>(null)
    val selectedClassFilter: StateFlow<String?> = _selectedClassFilter.asStateFlow()

    private val _quickFilter = MutableStateFlow(RegisterQuickFilter.ALL)
    val quickFilter: StateFlow<RegisterQuickFilter> = _quickFilter.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    val isOnlineState: StateFlow<Boolean> = if (appContext != null) {
        NetworkMonitor.observeIsOnline(appContext.applicationContext)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = NetworkMonitor.isCurrentlyOnline(appContext.applicationContext)
            )
    } else {
        flowOf(true).stateIn(viewModelScope, SharingStarted.Eagerly, true)
    }

    val hasPendingSyncState: StateFlow<Boolean> = repository.observeHasPendingSync(schoolId)
        .catch { emit(false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    private fun <T> Flow<T>.toUiStateFlow(
        tagMsg: String,
        initial: UiState<T> = UiState.Loading
    ): StateFlow<UiState<T>> =
        map<T, UiState<T>> { UiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, tagMsg, rootError)
                emit(UiState.Error(rootError.message ?: tagMsg))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = initial
            )

    val schoolWorkspaceState: StateFlow<UiState<SchoolWorkspace?>> =
        repository.observeSchoolWorkspace(schoolId)
            .toUiStateFlow("Failed to load school workspace")

    val isAdminState: StateFlow<Boolean> = schoolWorkspaceState
        .map { state ->
            val ws = (state as? UiState.Success)?.data
            ws?.isTeacherAdmin(deviceId, teacherPhone) == true
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val studentsState: StateFlow<UiState<List<StudentRecord>>> =
        _selectedYear.flatMapLatest { year ->
            repository.observeStudents(schoolId, year)
        }.toUiStateFlow("Failed to load student register")

    val subjectsState: StateFlow<UiState<List<SubjectDefinition>>> =
        repository.observeSubjects(schoolId)
            .toUiStateFlow("Failed to load subjects")

    val customFieldsState: StateFlow<UiState<List<CustomFieldDef>>> =
        repository.observeCustomFields(schoolId)
            .toUiStateFlow("Failed to load flexible category fields")

    @OptIn(ExperimentalCoroutinesApi::class)
    val attendanceLogsState: StateFlow<UiState<List<AttendanceLog>>> =
        _selectedYear.flatMapLatest { year ->
            repository.observeAttendanceLogs(schoolId, year)
        }.toUiStateFlow("Failed to load class attendance sheets")

    val pendingChangesState: StateFlow<UiState<List<PendingChangeRequest>>> =
        repository.observePendingChanges(schoolId)
            .toUiStateFlow("Failed to load pending approvals")

    val parentNoticesState: StateFlow<UiState<List<ParentNotice>>> =
        repository.observeParentNotices(schoolId)
            .toUiStateFlow("Failed to load parent notices")

    val messagesState: StateFlow<UiState<List<SchoolChatMessage>>> =
        repository.observeSchoolMessages(schoolId)
            .toUiStateFlow("Failed to load teachers chat")

    val filteredStudents: StateFlow<List<StudentRecord>> = combine(
        studentsState,
        _searchQuery,
        _selectedClassFilter,
        _quickFilter
    ) { state, query, classFilter, quick ->
        val list = (state as? UiState.Success)?.data ?: emptyList()
        val q = query.trim().lowercase()
        list.filter { student ->
            val matchesClass = classFilter == null || student.classLevel == classFilter
            val matchesQuick = when (quick) {
                RegisterQuickFilter.ALL -> true
                RegisterQuickFilter.MISSING_PHONE -> student.guardianPhone.isBlank()
                RegisterQuickFilter.PROMOTED -> student.promotionStatus == "Promoted"
                RegisterQuickFilter.WITH_NOTES -> student.sectorCommentsCount() > 0
            }
            val matchesQuery = if (q.isBlank()) {
                true
            } else {
                student.studentNameEn.lowercase().contains(q) ||
                    student.studentNameBn.lowercase().contains(q) ||
                    student.rollNumber.lowercase().contains(q) ||
                    student.fatherNameEn.lowercase().contains(q) ||
                    student.motherNameEn.lowercase().contains(q) ||
                    student.guardianPhone.lowercase().contains(q) ||
                    student.effectiveAddress().lowercase().contains(q)
            }
            matchesClass && matchesQuick && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = emptyList()
    )

    val dashboardSummary: StateFlow<DashboardSummary> = combine(
        studentsState,
        subjectsState,
        customFieldsState,
        pendingChangesState
    ) { stuState, subState, cfState, pendingState ->
        val students = (stuState as? UiState.Success)?.data ?: emptyList()
        val subjects = (subState as? UiState.Success)?.data ?: emptyList()
        val customFields = (cfState as? UiState.Success)?.data ?: emptyList()
        val pendingCount = ((pendingState as? UiState.Success)?.data ?: emptyList())
            .count { it.status == ChangeStatuses.PENDING }

        if (students.isEmpty()) {
            DashboardSummary(
                subjectsCount = subjects.size,
                customFieldsCount = customFields.size,
                pendingChangesCount = pendingCount
            )
        } else {
            val total = students.size
            val withDays = students.filter { it.totalSchoolDays > 0L }
            val avgAttendance = if (withDays.isNotEmpty()) {
                withDays.map { it.attendancePercentage() }.average().toInt()
            } else 0
            val avgCompleteness = students.map { s ->
                val (filled, max) = s.filledFieldProgress(customFields, subjects)
                (filled * 100) / max
            }.average().toInt()
            val assessed = students.map { it.overallPerformanceScore(subjects) }.filter { it > 0 }
            val avgPerf = if (assessed.isNotEmpty()) assessed.average().toInt() else 0

            val counts = ClassLevels.ALL.associateWith { cls ->
                students.count { it.classLevel == cls }
            }
            val classComplete = ClassLevels.ALL.associateWith { cls ->
                val clsList = students.filter { it.classLevel == cls }
                if (clsList.isEmpty()) 0
                else clsList.map { s ->
                    val (filled, max) = s.filledFieldProgress(customFields, subjects)
                    (filled * 100) / max
                }.average().toInt()
            }

            DashboardSummary(
                totalStudents = total,
                averageAttendancePct = avgAttendance,
                averageCompletenessPct = avgCompleteness,
                averagePerformancePct = avgPerf,
                subjectsCount = subjects.size,
                customFieldsCount = customFields.size,
                pendingChangesCount = pendingCount,
                classCounts = counts,
                classAvgCompleteness = classComplete
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = DashboardSummary()
    )

    fun setSelectedYear(year: Int) {
        _selectedYear.value = year
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setClassFilter(classLevel: String?) {
        _selectedClassFilter.value = classLevel
    }

    fun setQuickFilter(filter: RegisterQuickFilter) {
        _quickFilter.value = filter
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    fun showMessage(msg: String) {
        _feedbackMessage.value = msg
    }

    private fun activeActorName(): String {
        val ws = (schoolWorkspaceState.value as? UiState.Success)?.data
        val fromWs = ws?.findTeacher(deviceId, teacherPhone)?.name
        return fromWs?.takeIf { it.isNotBlank() } ?: teacherName.ifBlank { "Teacher" }
    }

    // ─────────────────────────── STUDENTS (ADMIN DIRECT / TEACHER QUEUE) ───────────────────────────

    fun saveStudent(student: StudentRecord, onSuccess: () -> Unit = {}) {
        val isAdmin = isAdminState.value
        val actor = activeActorName()
        viewModelScope.launch {
            _isSaving.value = true
            if (isAdmin) {
                val result = if (student.id.isBlank()) {
                    repository.createStudentDirect(schoolId, student, actor).map { }
                } else {
                    repository.updateStudentDirect(schoolId, student, actor)
                }
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = if (student.id.isBlank()) {
                        "Added ${student.primaryName()} (Roll ${student.rollNumber})"
                    } else {
                        "Updated ${student.primaryName()}'s record"
                    }
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not save student record."
                }
            } else {
                val isNew = student.id.isBlank()
                val req = PendingChangeRequest(
                    entityType = if (isNew) ChangeEntityTypes.STUDENT_CREATE else ChangeEntityTypes.STUDENT_UPDATE,
                    targetId = student.id,
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = if (isNew) {
                        "New Student: ${student.primaryName()} (${student.classLevel}, Roll ${student.rollNumber})"
                    } else {
                        "Edit Student: ${student.primaryName()} (${student.classLevel}, Roll ${student.rollNumber})"
                    },
                    detailsText = "Year ${student.academicYear} • Attendance ${student.daysPresent}/${student.totalSchoolDays} • ${student.examResults.size} exam marks • ${student.subjectValues.size} subject scores",
                    payloadJson = PendingPayloadCodec.encodeStudent(student)
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Submitted ${student.primaryName()}'s changes for Admin confirmation"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit change for approval."
                }
            }
        }
    }

    fun deleteStudent(student: StudentRecord, onSuccess: () -> Unit = {}) {
        val isAdmin = isAdminState.value
        val actor = activeActorName()
        viewModelScope.launch {
            _isSaving.value = true
            if (isAdmin) {
                val result = repository.deleteStudentDirect(schoolId, student.id)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Removed ${student.primaryName()} from register"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not remove student."
                }
            } else {
                val req = PendingChangeRequest(
                    entityType = ChangeEntityTypes.STUDENT_DELETE,
                    targetId = student.id,
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = "Remove Student: ${student.primaryName()} (${student.classLevel}, Roll ${student.rollNumber})",
                    detailsText = "Requested removal of student ID ${student.id}",
                    payloadJson = "{\"id\":\"${student.id}\"}"
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Removal request submitted for Admin confirmation"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit removal request."
                }
            }
        }
    }

    fun saveBatchUpdates(
        updatesByStudentId: Map<String, Map<String, Any>>,
        customSummary: String? = null,
        customDetails: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        val isAdmin = isAdminState.value
        val actor = activeActorName()
        viewModelScope.launch {
            _isSaving.value = true
            if (isAdmin) {
                val result = repository.batchUpdateStudentsDirect(schoolId, updatesByStudentId, actor)
                _isSaving.value = false
                result.onSuccess { count ->
                    _feedbackMessage.value = "Saved updates for $count student(s)"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Batch save failed."
                }
            } else {
                val req = PendingChangeRequest(
                    entityType = ChangeEntityTypes.STUDENT_BATCH_UPDATE,
                    targetId = "batch_${updatesByStudentId.size}",
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = customSummary?.take(160) ?: "Batch Edit: ${updatesByStudentId.size} student(s)",
                    detailsText = customDetails?.take(600) ?: "Class register batch update for ${updatesByStudentId.size} student records",
                    payloadJson = PendingPayloadCodec.encodeBatchStudentUpdates(updatesByStudentId)
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Batch edit for ${updatesByStudentId.size} student(s) sent for Admin confirmation"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit batch update."
                }
            }
        }
    }

    fun promoteEligibleStudentsToNextYear(targetYear: Int, isAutoRollover: Boolean = false) {
        if (!isAdminState.value) {
            _feedbackMessage.value = "Only the School Admin can promote classes into a new year."
            return
        }
        val currentList = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        val customFields = (customFieldsState.value as? UiState.Success)?.data ?: emptyList()
        val yearlyFieldIds = customFields.filter { it.resetsYearly }.map { it.id }.toSet()
        val eligible = currentList.filter { s ->
            s.promotionStatus == "Promoted" ||
                s.promotionStatus == "Not Promoted" ||
                (isAutoRollover && (s.promotionStatus == "Studying" || s.promotionStatus.isBlank()))
        }
        if (eligible.isEmpty()) {
            if (!isAutoRollover) {
                _feedbackMessage.value = "Mark students as 'Promoted' or 'Not Promoted' before rolling forward."
            }
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.promoteStudentsToNextYear(
                schoolId = schoolId,
                studentsToPromote = currentList,
                targetYear = targetYear,
                yearlyCustomFieldIds = yearlyFieldIds,
                actorName = activeActorName(),
                includeStudyingInSameClass = isAutoRollover
            )
            _isSaving.value = false
            result.onSuccess { count ->
                _selectedYear.value = targetYear
                _feedbackMessage.value = if (isAutoRollover) {
                    "Year-end promotion applied after Dec 31: updated $count student(s) into $targetYear"
                } else {
                    "Applied promotion & roll positions for $count student(s) into $targetYear"
                }
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Year promotion failed."
            }
        }
    }

    fun checkAndRunAutoCalendarYearRollover(context: Context, currentCalendarYear: Int) {
        if (!isAdminState.value) return
        val ws = (schoolWorkspaceState.value as? UiState.Success)?.data ?: return
        val currentList = (studentsState.value as? UiState.Success)?.data ?: return
        if (currentList.isEmpty()) return
        if (currentCalendarYear <= ws.defaultAcademicYear || currentCalendarYear <= ws.lastAutoRolloverYear) return
        if (AppPreferences.getLastAutoRolloverYear(context, schoolId) >= currentCalendarYear) return

        val eligibleToRoll = currentList.any {
            it.academicYear < currentCalendarYear &&
                (it.promotionStatus == "Promoted" || it.promotionStatus == "Not Promoted")
        }
        AppPreferences.markAutoRolloverDone(context, schoolId, currentCalendarYear)
        if (eligibleToRoll) {
            promoteEligibleStudentsToNextYear(targetYear = currentCalendarYear, isAutoRollover = true)
        }
    }

    fun verifyAdminPassword(context: Context, plainPassword: String): Boolean {
        if (plainPassword.length < 4) return false
        val ws = (schoolWorkspaceState.value as? UiState.Success)?.data
        val firestoreHash = ws?.findTeacher(deviceId, teacherPhone)?.passwordHash.orEmpty()
        if (firestoreHash.isNotBlank() && TeacherProfile.verifyPassword(plainPassword, firestoreHash)) {
            return true
        }
        val normPhone = TeacherProfile.normalizePhone(teacherPhone)
        if (normPhone.isNotBlank()) {
            val localKey = "cred_${normalizeSchoolId(schoolId)}_${normPhone}_hash"
            val localHash = AppPreferences.authPrefs(context).getString(localKey, "").orEmpty()
            if (localHash.isNotBlank() && TeacherProfile.verifyPassword(plainPassword, localHash)) {
                return true
            }
        }
        return false
    }

    fun resetTeachersPanel(context: Context) {
        if (!isAdminState.value) return
        val ws = (schoolWorkspaceState.value as? UiState.Success)?.data
        val firestoreHash = ws?.findTeacher(deviceId, teacherPhone)?.passwordHash.orEmpty()
        val normPhone = TeacherProfile.normalizePhone(teacherPhone)
        val effectiveHash = firestoreHash.ifBlank {
            if (normPhone.isNotBlank()) {
                AppPreferences.authPrefs(context).getString("cred_${normalizeSchoolId(schoolId)}_${normPhone}_hash", "").orEmpty()
            } else ""
        }
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.resetTeachersAndAdminPanel(
                rawSchoolId = schoolId,
                newAdminName = activeActorName(),
                adminDeviceId = deviceId,
                phoneNumber = teacherPhone,
                passwordHash = effectiveHash
            )
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "Teachers panel reset — student & subject data preserved."
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not reset teachers panel."
            }
        }
    }

    fun restoreFromBackup(
        parsed: com.example.export.BackupParseResult,
        onSuccess: () -> Unit = {}
    ) {
        val currentStudents = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.restoreBackupData(
                schoolId = schoolId,
                students = parsed.students,
                existingStudents = currentStudents,
                subjectsToUpsert = parsed.subjectsToUpsert,
                customFieldsToUpsert = parsed.customFieldsToUpsert,
                actorName = activeActorName(),
                replaceMatchedExisting = parsed.replaceMatchedExisting
            )
            _isSaving.value = false
            result.onSuccess { (stuCount, subCount, cfCount) ->
                val targetYear = parsed.students.firstOrNull()?.academicYear?.toInt()
                if (targetYear != null) _selectedYear.value = targetYear
                _feedbackMessage.value = "Restored $stuCount student(s), $subCount subject(s), $cfCount field(s) from ${parsed.formatLabel}"
                onSuccess()
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Restore failed."
            }
        }
    }

    // ─────────────────────────── CLASS ATTENDANCE SHEET ───────────────────────────

    fun saveAttendanceSheet(
        classLevel: String,
        dateKey: String,
        statuses: Map<String, String>,
        onSuccess: () -> Unit = {}
    ) {
        val year = _selectedYear.value
        val monthKey = dateKey.take(7)
        val actor = activeActorName()
        val log = AttendanceLog(
            id = AttendanceLog.buildDocId(year, classLevel, dateKey),
            schoolId = schoolId,
            academicYear = year.toLong(),
            classLevel = classLevel,
            dateKey = dateKey,
            monthKey = monthKey,
            statuses = statuses,
            markedByName = actor
        )
        val allStudents = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        val classStudents = allStudents.filter { it.classLevel == classLevel }

        viewModelScope.launch {
            _isSaving.value = true
            if (isAdminState.value) {
                val result = repository.saveAttendanceLogDirect(schoolId, log, classStudents)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Saved $classLevel attendance ($dateKey)"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not save attendance sheet."
                }
            } else {
                val presentCount = statuses.values.count { it == "P" }
                val absentCount = statuses.values.count { it == "A" }
                val req = PendingChangeRequest(
                    entityType = ChangeEntityTypes.ATTENDANCE_SAVE,
                    targetId = log.id,
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = "Attendance Sheet: $classLevel ($dateKey)",
                    detailsText = "Present: $presentCount • Absent: $absentCount • Total marked: ${statuses.size}",
                    payloadJson = PendingPayloadCodec.encodeAttendanceLog(log)
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "$classLevel attendance ($dateKey) submitted for Admin confirmation"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit attendance."
                }
            }
        }
    }

    // ─────────────────────────── 3-EXAM MARKSHEET CUSTOMIZATION ───────────────────────────

    fun saveExamConfigs(configs: List<ExamTermConfig>, onSuccess: () -> Unit = {}) {
        val clean = ExamDefaults.ensureThreeExams(configs)
        val actor = activeActorName()
        viewModelScope.launch {
            _isSaving.value = true
            if (isAdminState.value) {
                val result = repository.saveExamConfigsDirect(schoolId, clean)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Exam & Marksheet subjects updated"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not save exam subjects."
                }
            } else {
                val totalSubjects = clean.sumOf { it.subjects.size }
                val req = PendingChangeRequest(
                    entityType = ChangeEntityTypes.EXAM_CONFIG_SAVE,
                    targetId = "exam_configs",
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = "Marksheet Setup: ${clean.joinToString(" / ") { it.title }}",
                    detailsText = "$totalSubjects exam subject rule(s) configured across 3 exams",
                    payloadJson = PendingPayloadCodec.encodeExamConfigs(clean)
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Marksheet setup submitted for Admin confirmation"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit marksheet setup."
                }
            }
        }
    }

    // ─────────────────────────── SUBJECTS & FLEXIBLE FIELDS ───────────────────────────

    fun saveSubject(subject: SubjectDefinition, onSuccess: () -> Unit = {}) {
        val actor = activeActorName()
        viewModelScope.launch {
            _isSaving.value = true
            if (isAdminState.value) {
                val result = repository.saveSubjectDirect(schoolId, subject)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Saved subject '${subject.name}'"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not save subject."
                }
            } else {
                val withId = if (subject.id.isBlank()) subject.copy(id = generateShortId("sub")) else subject
                val req = PendingChangeRequest(
                    entityType = ChangeEntityTypes.SUBJECT_SAVE,
                    targetId = withId.id,
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = "Subject: ${withId.name} (${withId.gradeScope})",
                    detailsText = "${withId.fields.size} assessment field(s): ${withId.fields.joinToString { it.label }}",
                    payloadJson = PendingPayloadCodec.encodeSubject(withId)
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Subject '${subject.name}' sent for Admin confirmation"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit subject."
                }
            }
        }
    }

    fun addFieldToSubject(
        subject: SubjectDefinition,
        fieldLabel: String,
        fieldType: String,
        maxMarks: Long,
        onSuccess: () -> Unit = {}
    ) {
        if (fieldLabel.isBlank()) return
        val newField = SubjectFieldDef(
            fieldId = generateShortId("sf"),
            label = fieldLabel.trim(),
            fieldType = fieldType,
            maxMarks = maxMarks
        )
        saveSubject(subject.copy(fields = subject.fields + newField), onSuccess)
    }

    fun updateFieldInSubject(
        subject: SubjectDefinition,
        updatedField: SubjectFieldDef,
        onSuccess: () -> Unit = {}
    ) {
        val nextFields = subject.fields.map {
            if (it.fieldId == updatedField.fieldId) updatedField else it
        }
        saveSubject(subject.copy(fields = nextFields), onSuccess)
    }

    fun removeFieldFromSubject(subject: SubjectDefinition, fieldId: String) {
        val nextFields = subject.fields.filterNot { it.fieldId == fieldId }
        saveSubject(subject.copy(fields = nextFields))
    }

    fun deleteSubject(subject: SubjectDefinition) {
        val actor = activeActorName()
        viewModelScope.launch {
            _isSaving.value = true
            if (isAdminState.value) {
                val result = repository.deleteSubjectDirect(schoolId, subject.id)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Removed subject '${subject.name}'"
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not remove subject."
                }
            } else {
                val req = PendingChangeRequest(
                    entityType = ChangeEntityTypes.SUBJECT_DELETE,
                    targetId = subject.id,
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = "Remove Subject: ${subject.name} (${subject.gradeScope})",
                    payloadJson = "{\"id\":\"${subject.id}\"}"
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Subject removal sent for Admin confirmation"
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit subject removal."
                }
            }
        }
    }

    fun saveCustomField(field: CustomFieldDef, onSuccess: () -> Unit = {}) {
        val actor = activeActorName()
        viewModelScope.launch {
            _isSaving.value = true
            if (isAdminState.value) {
                val result = repository.saveCustomFieldDirect(schoolId, field)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Saved field '${field.label}' in ${field.category}"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not save flexible field."
                }
            } else {
                val withId = if (field.id.isBlank()) field.copy(id = generateShortId("cf")) else field
                val req = PendingChangeRequest(
                    entityType = ChangeEntityTypes.CUSTOM_FIELD_SAVE,
                    targetId = withId.id,
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = "Category Field: ${withId.label} (${withId.category})",
                    payloadJson = PendingPayloadCodec.encodeCustomField(withId)
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Field '${field.label}' sent for Admin confirmation"
                    onSuccess()
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit field."
                }
            }
        }
    }

    fun deleteCustomField(field: CustomFieldDef) {
        val actor = activeActorName()
        viewModelScope.launch {
            _isSaving.value = true
            if (isAdminState.value) {
                val result = repository.deleteCustomFieldDirect(schoolId, field.id)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Removed field '${field.label}'"
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not remove field."
                }
            } else {
                val req = PendingChangeRequest(
                    entityType = ChangeEntityTypes.CUSTOM_FIELD_DELETE,
                    targetId = field.id,
                    submittedByDeviceId = deviceId,
                    submittedByName = actor,
                    summary = "Remove Field: ${field.label} (${field.category})",
                    payloadJson = "{\"id\":\"${field.id}\"}"
                )
                val result = repository.submitPendingChange(schoolId, req)
                _isSaving.value = false
                result.onSuccess {
                    _feedbackMessage.value = "Field removal sent for Admin confirmation"
                }.onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not submit removal."
                }
            }
        }
    }

    // ─────────────────────────── ADMIN CONFIRMATION QUEUE ───────────────────────────

    fun approvePendingChange(change: PendingChangeRequest) {
        if (!isAdminState.value) {
            _feedbackMessage.value = "Only the School Admin can confirm pending changes."
            return
        }
        val allStudents = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.approvePendingChange(schoolId, change, activeActorName(), allStudents)
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "Confirmed: ${change.summary}"
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Approval failed."
            }
        }
    }

    fun approveAllPendingChanges() {
        if (!isAdminState.value) return
        val pending = ((pendingChangesState.value as? UiState.Success)?.data ?: emptyList())
            .filter { it.status == ChangeStatuses.PENDING }
        if (pending.isEmpty()) return
        val allStudents = (studentsState.value as? UiState.Success)?.data ?: emptyList()
        val adminName = activeActorName()

        viewModelScope.launch {
            _isSaving.value = true
            var approvedCount = 0
            pending.forEach { ch ->
                val res = repository.approvePendingChange(schoolId, ch, adminName, allStudents)
                if (res.isSuccess) approvedCount++
            }
            _isSaving.value = false
            _feedbackMessage.value = "Confirmed & applied $approvedCount pending change(s)"
        }
    }

    fun rejectPendingChange(change: PendingChangeRequest) {
        if (!isAdminState.value) {
            _feedbackMessage.value = "Only the School Admin can reject pending changes."
            return
        }
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.rejectPendingChange(schoolId, change.id, activeActorName())
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "Rejected: ${change.summary}"
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Rejection failed."
            }
        }
    }

    // ─────────────────────────── SCHOOL DETAILS, CHAT & PARENT NOTICES ───────────────────────────

    fun updateSchoolDetails(
        schoolName: String,
        emisCode: String,
        upazilaDistrict: String,
        defaultAcademicYear: Long
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateSchoolDetails(
                schoolId = schoolId,
                schoolName = schoolName,
                emisCode = emisCode,
                upazilaDistrict = upazilaDistrict,
                defaultAcademicYear = defaultAcademicYear
            )
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "School profile updated"
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not save school profile."
            }
        }
    }

    fun transferAdminRole(newAdminDeviceId: String, newAdminName: String) {
        val ws = (schoolWorkspaceState.value as? UiState.Success)?.data ?: return
        if (!isAdminState.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.transferAdminRole(schoolId, ws, newAdminDeviceId, newAdminName)
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "$newAdminName is now the School Admin"
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not transfer Admin role."
            }
        }
    }

    fun sendChatMessage(tag: String, text: String, onSuccess: () -> Unit = {}) {
        val ws = (schoolWorkspaceState.value as? UiState.Success)?.data
        viewModelScope.launch {
            val result = repository.sendSchoolMessage(
                schoolId = schoolId,
                workspace = ws,
                senderDeviceId = deviceId,
                senderName = activeActorName(),
                tag = tag,
                text = text
            )
            result.onSuccess { onSuccess() }
                .onFailure { err ->
                    _feedbackMessage.value = err.message ?: "Could not send message."
                }
        }
    }

    fun editChatMessage(messageId: String, newText: String, newTag: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.editSchoolMessage(schoolId, messageId, newText, newTag)
            result.onSuccess {
                _feedbackMessage.value = "Message updated"
                onSuccess()
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not edit message."
            }
        }
    }

    fun deleteChatMessage(messageId: String) {
        val ws = (schoolWorkspaceState.value as? UiState.Success)?.data
        viewModelScope.launch {
            val result = repository.deleteSchoolMessage(schoolId, ws, messageId)
            result.onSuccess {
                _feedbackMessage.value = "Message deleted"
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not delete message."
            }
        }
    }

    fun saveParentNotice(notice: ParentNotice, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            val result = repository.saveParentNotice(schoolId, notice, activeActorName())
            result.onSuccess {
                _feedbackMessage.value = "Parent notice saved to log"
                onSuccess()
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not save notice."
            }
        }
    }

    fun deleteParentNotice(notice: ParentNotice) {
        viewModelScope.launch {
            val result = repository.deleteParentNotice(schoolId, notice.id)
            result.onSuccess {
                _feedbackMessage.value = "Parent notice deleted"
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not delete notice."
            }
        }
    }

    fun syncPendingWritesAndCheckUpdates(
        onComplete: (SchoolWorkspace?) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.syncPendingWritesAndCheckUpdates(schoolId)
            _isSaving.value = false
            result.onSuccess { ws ->
                onComplete(ws)
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not sync right now — edits remain saved locally."
                onComplete(null)
            }
        }
    }

    fun saveAppUpdateMetadata(
        latestVersionCode: Long,
        latestVersionName: String,
        updateUrl: String,
        releaseNotes: String,
        onSuccess: () -> Unit = {}
    ) {
        if (!isAdminState.value) return
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.saveAppUpdateMetadata(
                schoolId = schoolId,
                latestVersionCode = latestVersionCode,
                latestVersionName = latestVersionName,
                updateUrl = updateUrl,
                releaseNotes = releaseNotes
            )
            _isSaving.value = false
            result.onSuccess {
                _feedbackMessage.value = "App update link & version saved for all teachers"
                onSuccess()
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Could not save app update info."
            }
        }
    }

    fun migrateLegacyRecordsIfAny() {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.migrateLegacyUserIfPresent(schoolId, activeActorName())
            _isSaving.value = false
            result.onSuccess { count ->
                if (count > 0) {
                    _feedbackMessage.value = "Imported $count student(s) from your previous app version into School $schoolId"
                } else {
                    _feedbackMessage.value = "No earlier v1.0 student records found to import."
                }
            }.onFailure { err ->
                _feedbackMessage.value = err.message ?: "Legacy import failed."
            }
        }
    }

    private companion object {
        const val TAG = "SchoolViewModel"
        const val STOP_TIMEOUT_MILLIS = 5000L
    }
}
