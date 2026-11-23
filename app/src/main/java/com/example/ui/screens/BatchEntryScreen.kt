package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassLevels
import com.example.data.model.CustomFieldTypes
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamResultCalculator
import com.example.data.model.ExamTermConfig
import com.example.data.model.MarksheetSubjectSpec
import com.example.data.model.SectorOptions
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.TeacherProfile
import com.example.export.ExcelBackupManager
import com.example.ui.components.ClassResultsPanel
import com.example.ui.components.EmptyRegisterState
import com.example.ui.components.MarksheetCustomizerDialog
import com.example.ui.i18n.AppStrings
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState

enum class BatchMode {
    ATTENDANCE,
    YEARLY,
    SUBJECT_FIELDS,
    EXAM_MARKS,
    CLASS_RESULTS;

    fun localizedLabel(strings: AppStrings): String = when (this) {
        ATTENDANCE -> strings.batchAttendance
        YEARLY -> strings.batchYearly
        SUBJECT_FIELDS -> strings.batchSubjectFields
        EXAM_MARKS -> strings.batchExamMarks
        CLASS_RESULTS -> strings.batchClassResults
    }
}

@Composable
fun BatchEntryScreen(
    studentsState: UiState<List<StudentRecord>>,
    subjects: List<SubjectDefinition>,
    examConfigs: List<ExamTermConfig> = ExamDefaults.defaultThreeExams(),
    teacherProfile: TeacherProfile? = null,
    selectedYear: Int,
    isAdmin: Boolean = true,
    isSaving: Boolean,
    initialClass: String? = null,
    initialSubjectId: String? = null,
    initialBatchMode: BatchMode? = null,
    onSaveBatch: (
        updatesByStudentId: Map<String, Map<String, Any>>,
        customSummary: String?,
        customDetails: String?,
        onSuccess: () -> Unit
    ) -> Unit,
    onSaveExamConfigs: (List<ExamTermConfig>, onSuccess: () -> Unit) -> Unit = { _, done -> done() },
    onOpenStudentProfile: (StudentRecord) -> Unit = {},
    onAddStudent: () -> Unit,
    onOpenFieldManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    val scope = rememberCoroutineScope()
    val batchListState = rememberLazyListState()
    val resultsListState = rememberLazyListState()

    var selectedClass by rememberSaveable(initialClass) {
        mutableStateOf(initialClass?.takeIf { it in ClassLevels.ALL } ?: ClassLevels.ALL[1])
    }
    var selectedMode by rememberSaveable(initialSubjectId, initialBatchMode) {
        mutableStateOf(
            when {
                initialBatchMode != null -> initialBatchMode
                !initialSubjectId.isNullOrBlank() -> BatchMode.SUBJECT_FIELDS
                else -> BatchMode.ATTENDANCE
            }
        )
    }
    val draftEdits = remember { mutableStateMapOf<String, StudentRecord>() }

    val allStudents = (studentsState as? UiState.Success)?.data ?: emptyList()
    val classStudents = remember(allStudents, selectedClass) {
        allStudents.filter { it.classLevel == selectedClass }
            .sortedWith(compareBy<StudentRecord> { it.rollSortKey() }.thenBy { it.rollNumber })
    }

    val matchingSubjects = remember(subjects, selectedClass) {
        subjects.filter { it.appliesToClass(selectedClass) }
    }
    var selectedSubjectId by rememberSaveable(initialSubjectId) {
        mutableStateOf(initialSubjectId.orEmpty())
    }
    val activeSubject = matchingSubjects.firstOrNull { it.id == selectedSubjectId }
        ?: matchingSubjects.firstOrNull()

    // Class-wise Single-Subject Exam Marks state
    val normalizedExams = remember(examConfigs) { ExamDefaults.ensureThreeExams(examConfigs) }
    var selectedExamIdx by rememberSaveable { mutableIntStateOf(2) } // Default Final Exam
    val activeExam = normalizedExams[selectedExamIdx.coerceIn(0, 2)]
    val applicableExamSubjects = remember(activeExam, selectedClass) {
        activeExam.subjects.filter { it.appliesToClass(selectedClass) }
    }
    var selectedExamSubjectId by rememberSaveable { mutableStateOf("") }
    val activeExamSpec = applicableExamSubjects.firstOrNull { it.subjectId == selectedExamSubjectId }
        ?: applicableExamSubjects.firstOrNull()
    var showExamCustomizeDialog by rememberSaveable { mutableStateOf(false) }

    val subjectMarksImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val spec = activeExamSpec
        if (uri != null && spec != null) {
            try {
                val imported = ExcelBackupManager.parseSubjectTermPartsFromUri(
                    context = context,
                    uri = uri,
                    classStudents = classStudents,
                    subjectName = spec.subjectName
                )
                if (imported.isEmpty()) {
                    Toast.makeText(
                        context,
                        "No matching student roll/name rows found for ${spec.subjectName}.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    imported.forEach { (stuId, rowImport) ->
                        val base = draftEdits[stuId] ?: classStudents.firstOrNull { it.id == stuId }
                        if (base != null) {
                            draftEdits[stuId] = if (rowImport.continuous != null || rowImport.summative != null) {
                                base.withExamTermParts(
                                    examId = activeExam.examId,
                                    subjectId = spec.subjectId,
                                    contRaw = rowImport.continuous ?: base.examContinuousRaw(activeExam.examId, spec.subjectId),
                                    sumRaw = rowImport.summative ?: base.examSummativeRaw(activeExam.examId, spec.subjectId)
                                )
                            } else {
                                base.withExamMark(activeExam.examId, spec.subjectId, rowImport.totalOnly.orEmpty())
                            }
                        }
                    }
                    Toast.makeText(
                        context,
                        "Loaded ${imported.size} ${spec.subjectName} mark(s) — tap Save to commit.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.localizedMessage ?: "Failed to read marks file.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    LaunchedEffect(selectedYear) {
        draftEdits.clear()
    }

    val showJumpToTopControls by remember(selectedMode) {
        derivedStateOf {
            val activeListState = if (selectedMode == BatchMode.CLASS_RESULTS) resultsListState else batchListState
            activeListState.firstVisibleItemIndex > 0
        }
    }

    val batchControlsHeader: @Composable (horizontalPaddingDp: Int) -> Unit = { horizontalPad ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPad.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ClassLevels.ALL.forEach { cls ->
                        val count = allStudents.count { it.classLevel == cls }
                        FilterChip(
                            selected = selectedClass == cls,
                            onClick = { selectedClass = cls },
                            label = { Text("${strings.localizeClass(cls)} ($count)") },
                            modifier = Modifier.testTag("batch_class_${cls.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BatchMode.entries.forEach { mode ->
                        FilterChip(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode },
                            label = { Text(mode.localizedLabel(strings)) },
                            modifier = Modifier.testTag("batch_mode_${mode.name.lowercase()}")
                        )
                    }
                }

                if (selectedMode == BatchMode.SUBJECT_FIELDS) {
                    if (matchingSubjects.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "No subjects created for ${strings.localizeClass(selectedClass)} yet.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(onClick = onOpenFieldManager) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Create Subject")
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            matchingSubjects.forEach { sub ->
                                FilterChip(
                                    selected = activeSubject?.id == sub.id,
                                    onClick = { selectedSubjectId = sub.id },
                                    label = { Text("${sub.name} (${sub.fields.size})") }
                                )
                            }
                        }
                        if (activeSubject != null && activeSubject.fields.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "'${activeSubject.name}' has no assessment fields yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilledTonalButton(onClick = onOpenFieldManager) {
                                        Text("Add Fields")
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedMode == BatchMode.EXAM_MARKS) {
                    // Exam selector
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        normalizedExams.forEachIndexed { idx, ex ->
                            FilterChip(
                                selected = selectedExamIdx == idx,
                                onClick = { selectedExamIdx = idx },
                                label = { Text(strings.localizeExamTitle(ex)) },
                                modifier = Modifier.testTag("batch_exam_select_$idx")
                            )
                        }
                    }

                    if (applicableExamSubjects.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "No exam subjects configured for ${strings.localizeClass(selectedClass)} in ${strings.localizeExamTitle(activeExam)}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                FilledTonalButton(onClick = { showExamCustomizeDialog = true }) {
                                    Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(strings.subjectsAndExamsBtn)
                                }
                            }
                        }
                    } else {
                        // Subject selector + Customize + Excel Import
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            applicableExamSubjects.forEach { spec ->
                                FilterChip(
                                    selected = activeExamSpec?.subjectId == spec.subjectId,
                                    onClick = { selectedExamSubjectId = spec.subjectId },
                                    label = {
                                        Text("${spec.subjectName} (${spec.fullMarks})")
                                    },
                                    modifier = Modifier.testTag("batch_exam_subject_${spec.subjectId}")
                                )
                            }
                            OutlinedButton(
                                onClick = { showExamCustomizeDialog = true },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(strings.subjectsAndExamsBtn, style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        if (activeExamSpec != null) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${strings.localizeClass(selectedClass)} • ${strings.localizeExamTitle(activeExam)} • ${activeExamSpec.subjectName} (${strings.fullMarksLabel}: ${activeExamSpec.fullMarks})",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = strings.examMarksSheetHint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            subjectMarksImportLauncher.launch(
                                                arrayOf(
                                                    ExcelBackupManager.MIME_XLSX,
                                                    "text/csv",
                                                    "text/comma-separated-values",
                                                    "application/vnd.ms-excel",
                                                    "*/*"
                                                )
                                            )
                                        },
                                        enabled = classStudents.isNotEmpty(),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp)
                                            .testTag("batch_import_subject_marks_button")
                                    ) {
                                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("${strings.importSubjectExcelCsv} (${activeExamSpec.subjectName})")
                                    }
                                }
                            }
                        }
                    }
                }

                if (classStudents.isNotEmpty()) {
                    when (selectedMode) {
                        BatchMode.ATTENDANCE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        classStudents.forEach { base ->
                                            val cur = draftEdits[base.id] ?: base
                                            val nextTotal = (cur.totalSchoolDays + 1L).coerceAtMost(366L)
                                            draftEdits[base.id] = cur.copy(totalSchoolDays = nextTotal)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("+1 Working Day")
                                }
                                FilledTonalButton(
                                    onClick = {
                                        classStudents.forEach { base ->
                                            val cur = draftEdits[base.id] ?: base
                                            val nextTotal = (cur.totalSchoolDays + 1L).coerceAtMost(366L)
                                            val nextPresent = (cur.daysPresent + 1L).coerceAtMost(nextTotal)
                                            draftEdits[base.id] = cur.copy(
                                                totalSchoolDays = nextTotal,
                                                daysPresent = nextPresent
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("All Present Today")
                                }
                            }
                        }

                        BatchMode.YEARLY -> {
                            OutlinedButton(
                                onClick = {
                                    classStudents.forEach { base ->
                                        val cur = draftEdits[base.id] ?: base
                                        draftEdits[base.id] = cur.copy(promotionStatus = "Promoted")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Mark All ${strings.localizeClass(selectedClass)} Students as Promoted")
                            }
                        }

                        BatchMode.SUBJECT_FIELDS, BatchMode.EXAM_MARKS, BatchMode.CLASS_RESULTS -> Unit
                    }
                }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (selectedMode == BatchMode.CLASS_RESULTS && studentsState is UiState.Success && classStudents.isNotEmpty()) {
            ClassResultsPanel(
                selectedClass = selectedClass,
                allStudents = allStudents,
                examConfigs = normalizedExams,
                teacherProfile = teacherProfile,
                selectedYear = selectedYear,
                onSelectStudent = onOpenStudentProfile,
                onOpenExamSetup = { showExamCustomizeDialog = true },
                listState = resultsListState,
                headerContent = { batchControlsHeader(0) },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            LazyColumn(
                state = batchListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = if (draftEdits.isNotEmpty()) 104.dp else 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "batch_top_controls") {
                    batchControlsHeader(0)
                }

                when (studentsState) {
                    is UiState.Loading -> {
                        item(key = "batch_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(key = "batch_error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(studentsState.message, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    is UiState.Success -> {
                        if (classStudents.isEmpty()) {
                            item(key = "batch_empty") {
                                EmptyRegisterState(
                                    academicYear = selectedYear,
                                    hasActiveFilter = false,
                                    onAddStudent = onAddStudent,
                                    onClearFilters = {}
                                )
                            }
                        } else {
                            items(
                                items = classStudents,
                                key = { it.id }
                            ) { original ->
                                val current = draftEdits[original.id] ?: original
                                val isDirty = draftEdits.containsKey(original.id)

                                BatchStudentRowCard(
                                    student = current,
                                    mode = selectedMode,
                                    activeSubject = activeSubject,
                                    activeExamId = activeExam.examId,
                                    activeExamSpec = activeExamSpec,
                                    normalizedExams = normalizedExams,
                                    isDirty = isDirty,
                                    onUpdate = { updated ->
                                        draftEdits[original.id] = updated
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showJumpToTopControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 8.dp, end = 16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = {
                    scope.launch {
                        val activeListState = if (selectedMode == BatchMode.CLASS_RESULTS) resultsListState else batchListState
                        activeListState.animateScrollToItem(0)
                    }
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.testTag("batch_scroll_to_controls_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = "Scroll up to class & mode controls"
                )
            }
        }

        if (draftEdits.isNotEmpty() && selectedMode != BatchMode.CLASS_RESULTS) {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { draftEdits.clear() },
                        enabled = !isSaving
                    ) {
                        Text(strings.discardButton)
                    }
                    Button(
                        onClick = {
                            if (selectedMode == BatchMode.EXAM_MARKS && activeExamSpec != null) {
                                val markKey = "${activeExam.examId}__${activeExamSpec.subjectId}"
                                val contKey = "${markKey}__cont"
                                val sumKey = "${markKey}__sum"
                                val payload = draftEdits.mapValues { (_, s) ->
                                    val cleanCont = s.examContinuousRaw(activeExam.examId, activeExamSpec.subjectId)
                                        .trim().take(20)
                                    val cleanSum = s.examSummativeRaw(activeExam.examId, activeExamSpec.subjectId)
                                        .trim().take(20)
                                    val cleanMark = s.examMarkRaw(activeExam.examId, activeExamSpec.subjectId)
                                        .trim().take(20)
                                    mapOf<String, Any>(
                                        "examResults.$contKey" to cleanCont,
                                        "examResults.$sumKey" to cleanSum,
                                        "examResults.$markKey" to cleanMark
                                    )
                                }
                                val summaryText = "$selectedClass • ${activeExam.title} • ${activeExamSpec.subjectName} (${draftEdits.size} students)"
                                val detailsText = "Class-wise single-subject Continuous & Summative marks submission (other subjects preserved)"
                                onSaveBatch(payload, summaryText, detailsText) {
                                    draftEdits.clear()
                                }
                            } else {
                                val payload = draftEdits.mapValues { (_, s) ->
                                    val cleanSubjectValues = s.subjectValues.entries
                                        .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                                        .take(150)
                                        .associate { it.key.trim().take(90) to it.value.trim().take(200) }
                                    val cleanNotes = s.sectorNotes.entries
                                        .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                                        .take(150)
                                        .associate { it.key.trim().take(90) to it.value.trim().take(300) }
                                    mapOf(
                                        "rollNumber" to s.rollNumber.trim().ifBlank { "1" }.take(20),
                                        "classLevel" to s.classLevel.trim().ifBlank { "Class 1" }.take(40),
                                        "section" to s.section.trim().take(30),
                                        "totalSchoolDays" to s.totalSchoolDays.coerceIn(0L, 366L),
                                        "daysPresent" to s.daysPresent.coerceIn(0L, 366L),
                                        "promotionStatus" to s.promotionStatus.trim().ifBlank { "Studying" }.take(40),
                                        "subjectValues" to cleanSubjectValues,
                                        "sectorNotes" to cleanNotes
                                    )
                                }
                                onSaveBatch(payload, null, null) {
                                    draftEdits.clear()
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("batch_save_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            val saveTxt = if (selectedMode == BatchMode.EXAM_MARKS && activeExamSpec != null) {
                                if (isAdmin) "${strings.saveClassSubjectMarks} (${draftEdits.size})"
                                else "${strings.submitClassSubjectMarks} (${draftEdits.size})"
                            } else {
                                "${strings.saveButton} ${draftEdits.size} ${strings.studentsCountWord}"
                            }
                            Text(saveTxt, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }

    if (showExamCustomizeDialog) {
        MarksheetCustomizerDialog(
            examConfigs = normalizedExams,
            initialExamIndex = selectedExamIdx,
            defaultClassScope = selectedClass,
            schoolSubjects = subjects,
            isAdmin = isAdmin,
            isSaving = isSaving,
            onDismiss = { showExamCustomizeDialog = false },
            onSave = { updated ->
                onSaveExamConfigs(updated) {
                    showExamCustomizeDialog = false
                }
            }
        )
    }
}

@Composable
private fun BatchStudentRowCard(
    student: StudentRecord,
    mode: BatchMode,
    activeSubject: SubjectDefinition?,
    activeExamId: String,
    activeExamSpec: MarksheetSubjectSpec?,
    normalizedExams: List<ExamTermConfig>,
    isDirty: Boolean,
    onUpdate: (StudentRecord) -> Unit
) {
    val strings = LocalAppStrings.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isDirty) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = student.rollNumber,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.primaryName(),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val secondaryName = student.secondaryName()
                    if (secondaryName.isNotBlank()) {
                        Text(
                            text = secondaryName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
                if (isDirty) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            when (mode) {
                BatchMode.ATTENDANCE -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = if (student.daysPresent == 0L) "" else student.daysPresent.toString(),
                            onValueChange = { raw ->
                                val v = raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                onUpdate(student.copy(daysPresent = v.coerceIn(0L, 366L)))
                            },
                            label = { Text("Present") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = if (student.totalSchoolDays == 0L) "" else student.totalSchoolDays.toString(),
                            onValueChange = { raw ->
                                val v = raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                                onUpdate(student.copy(totalSchoolDays = v.coerceIn(0L, 366L)))
                            },
                            label = { Text("Working") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val nextDays = maxOf(student.totalSchoolDays, student.daysPresent + 1L).coerceAtMost(366L)
                                val nextPresent = (student.daysPresent + 1L).coerceAtMost(366L)
                                onUpdate(student.copy(daysPresent = nextPresent, totalSchoolDays = nextDays))
                            },
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("+1")
                        }
                    }
                }

                BatchMode.YEARLY -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = student.rollNumber,
                                onValueChange = { onUpdate(student.copy(rollNumber = it.take(20))) },
                                label = { Text("Roll Number") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = student.section,
                                onValueChange = { onUpdate(student.copy(section = it.take(20))) },
                                label = { Text("Section") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            ClassLevels.ALL.forEach { cls ->
                                FilterChip(
                                    selected = student.classLevel == cls,
                                    onClick = { onUpdate(student.copy(classLevel = cls)) },
                                    label = { Text(strings.localizeClass(cls)) }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SectorOptions.promotionStatuses.forEach { status ->
                                FilterChip(
                                    selected = student.promotionStatus == status,
                                    onClick = { onUpdate(student.copy(promotionStatus = status)) },
                                    label = { Text(strings.localizePromotionStatus(status)) }
                                )
                            }
                        }
                    }
                }

                BatchMode.SUBJECT_FIELDS -> {
                    if (activeSubject == null || activeSubject.fields.isEmpty()) {
                        Text(
                            text = "Create a subject & assessment fields to enter values here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            activeSubject.fields.forEach { field ->
                                val curVal = student.subjectValue(activeSubject.id, field.fieldId)
                                when (field.fieldType) {
                                    CustomFieldTypes.LEVEL -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(field.label, style = MaterialTheme.typography.labelMedium)
                                            Row(
                                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                CustomFieldTypes.SKILL_LEVEL_OPTIONS.forEach { opt ->
                                                    FilterChip(
                                                        selected = curVal == opt,
                                                        onClick = {
                                                            val next = if (curVal == opt) "" else opt
                                                            onUpdate(student.withSubjectValue(activeSubject.id, field.fieldId, next))
                                                        },
                                                        label = { Text(opt) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    CustomFieldTypes.YES_NO -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(field.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                listOf("Yes", "No").forEach { opt ->
                                                    FilterChip(
                                                        selected = curVal == opt,
                                                        onClick = {
                                                            val next = if (curVal == opt) "" else opt
                                                            onUpdate(student.withSubjectValue(activeSubject.id, field.fieldId, next))
                                                        },
                                                        label = { Text(opt) }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    else -> {
                                        val labelSuffix = if (field.fieldType == CustomFieldTypes.NUMBER && field.maxMarks > 0) {
                                            " (/${field.maxMarks})"
                                        } else ""
                                        OutlinedTextField(
                                            value = curVal,
                                            onValueChange = { raw ->
                                                val cleaned = if (field.fieldType == CustomFieldTypes.NUMBER) {
                                                    raw.filter { it.isDigit() || it == '.' }
                                                } else raw
                                                onUpdate(student.withSubjectValue(activeSubject.id, field.fieldId, cleaned))
                                            },
                                            label = { Text("${field.label}$labelSuffix", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                            keyboardOptions = if (field.fieldType == CustomFieldTypes.NUMBER) {
                                                KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                            } else KeyboardOptions.Default,
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                BatchMode.EXAM_MARKS -> {
                    if (activeExamSpec == null) {
                        Text(
                            text = "Configure an Exam Subject above to enter marks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val contRaw = student.examContinuousRaw(activeExamId, activeExamSpec.subjectId)
                        val sumRaw = student.examSummativeRaw(activeExamId, activeExamSpec.subjectId)
                        val totalRaw = student.examMarkRaw(activeExamId, activeExamSpec.subjectId)
                        val numeric = student.examMarkDouble(activeExamId, activeExamSpec.subjectId)
                        val passReq = ExamResultCalculator.effectivePassMarks(activeExamSpec)
                        val (passed, grade, _) = ExamResultCalculator.evaluateGrade(
                            obtained = numeric,
                            fullMarks = activeExamSpec.fullMarks,
                            passMarks = passReq
                        )
                        val combinedSubjectRow = remember(student, normalizedExams, activeExamSpec.subjectName, activeExamId) {
                            ExamResultCalculator.computeCombinedThreeTermTotals(
                                student = student,
                                examConfigs = normalizedExams,
                                activeExamId = activeExamId
                            ).subjectRows.firstOrNull {
                                it.specByExamId[activeExamId]?.subjectId == activeExamSpec.subjectId ||
                                    it.subjectName.equals(activeExamSpec.subjectName, ignoreCase = true)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = contRaw,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() || it == '.' }.take(10)
                                        onUpdate(student.withExamTermParts(activeExamId, activeExamSpec.subjectId, filtered, sumRaw))
                                    },
                                    label = { Text(strings.continuousMarkLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("batch_exam_cont_input_${student.id}")
                                )
                                OutlinedTextField(
                                    value = sumRaw,
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() || it == '.' }.take(10)
                                        onUpdate(student.withExamTermParts(activeExamId, activeExamSpec.subjectId, contRaw, filtered))
                                    },
                                    label = { Text(strings.summativeMarkLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("batch_exam_sum_input_${student.id}")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = "${strings.totalShortCol}: ${totalRaw.ifBlank { "—" }}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val avgTxt = combinedSubjectRow?.threeTermAverage?.let { ExamResultCalculator.formatMark(it) } ?: "—"
                                    Text(
                                        text = "${strings.threeTermAverageCol}: $avgTxt",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                                Surface(
                                    color = when {
                                        numeric == null -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        passed -> MaterialTheme.colorScheme.primaryContainer
                                        else -> MaterialTheme.colorScheme.errorContainer
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = when {
                                            numeric == null -> "—"
                                            passed -> "$grade • ${strings.resultPass}"
                                            else -> "$grade • ${strings.resultFail}"
                                        },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when {
                                            numeric == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                            passed -> MaterialTheme.colorScheme.onPrimaryContainer
                                            else -> MaterialTheme.colorScheme.onErrorContainer
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                BatchMode.CLASS_RESULTS -> Unit
            }
        }
    }
}
