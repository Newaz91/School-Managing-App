package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pentagon
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomFieldDef
import com.example.data.model.EditorSectionTab
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.SubjectFieldDef
import com.example.ui.components.FiveAngleRadarChart
import com.example.ui.components.RadarAxisMetric
import com.example.ui.theme.JetBrainsMonoFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentEditorScreen(
    initialStudent: StudentRecord,
    allStudents: List<StudentRecord>,
    subjects: List<SubjectDefinition>,
    customFields: List<CustomFieldDef>,
    isSaving: Boolean,
    onSelectOtherStudent: (StudentRecord) -> Unit,
    onExportStudent: (StudentRecord) -> Unit,
    onSave: (StudentRecord) -> Unit,
    onDelete: (StudentRecord) -> Unit,
    onSaveSubject: (SubjectDefinition) -> Unit,
    onDeleteSubject: (SubjectDefinition) -> Unit,
    onAddSubjectField: (SubjectDefinition, String, String, Long) -> Unit,
    onUpdateSubjectField: (SubjectDefinition, SubjectFieldDef) -> Unit,
    onDeleteSubjectField: (SubjectDefinition, String) -> Unit,
    onSaveCustomField: (CustomFieldDef) -> Unit,
    onDeleteCustomField: (CustomFieldDef) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var draft by remember(initialStudent.id) { mutableStateOf(initialStudent) }
    var activeTab by rememberSaveable { mutableStateOf(EditorSectionTab.FIXED) }
    var expandAllDetails by rememberSaveable { mutableStateOf(false) }
    var showRadarPreview by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }

    // Inline schema creation/edit dialogs
    var showSubjectDialog by remember { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<SubjectDefinition?>(null) }
    var addingFieldForSubject by remember { mutableStateOf<SubjectDefinition?>(null) }
    var editingSubjectField by remember { mutableStateOf<Pair<SubjectDefinition, SubjectFieldDef>?>(null) }
    var showCustomFieldDialog by remember { mutableStateOf(false) }
    var editingCustomField by remember { mutableStateOf<CustomFieldDef?>(null) }

    val scrollState = rememberScrollState()

    LaunchedEffect(activeTab) {
        scrollState.scrollTo(0)
    }

    val isExisting = draft.id.isNotBlank()
    val currentIndex = remember(initialStudent.id, allStudents) {
        allStudents.indexOfFirst { it.id == initialStudent.id }
    }
    val prevStudent = if (currentIndex > 0) allStudents[currentIndex - 1] else null
    val nextStudent = if (currentIndex in 0 until allStudents.lastIndex) allStudents[currentIndex + 1] else null
    val (filledCount, totalCount) = draft.filledFieldProgress(customFields, subjects)
    val commentsCount = draft.sectorCommentsCount()
    val perfScore = draft.overallPerformanceScore(subjects)

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isExisting) {
                                draft.primaryName().ifBlank { "Edit Student" }
                            } else {
                                "Enroll New Student"
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "${draft.classLevel} • Roll ${draft.rollNumber.ifBlank { "—" }} • $filledCount/$totalCount • $commentsCount notes",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("editor_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onExportStudent(draft) },
                        modifier = Modifier.testTag("editor_export_button")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Export or share student report")
                    }
                    if (isExisting) {
                        IconButton(
                            onClick = { prevStudent?.let(onSelectOtherStudent) },
                            enabled = prevStudent != null
                        ) {
                            Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous student")
                        }
                        IconButton(
                            onClick = { nextStudent?.let(onSelectOtherStudent) },
                            enabled = nextStudent != null
                        ) {
                            Icon(Icons.Rounded.ChevronRight, contentDescription = "Next student")
                        }
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.testTag("editor_delete_button")
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete student",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (validationError != null) {
                        Text(
                            text = validationError ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Button(
                        onClick = {
                            when {
                                draft.studentNameBn.isBlank() -> {
                                    validationError = "Student Name (Bangla / বাংলা নাম) is required."
                                    activeTab = EditorSectionTab.FIXED
                                }
                                draft.rollNumber.isBlank() -> {
                                    validationError = "Roll Number is required."
                                    activeTab = EditorSectionTab.YEARLY
                                }
                                draft.classLevel.isBlank() -> {
                                    validationError = "Class Name is required."
                                    activeTab = EditorSectionTab.YEARLY
                                }
                                else -> {
                                    validationError = null
                                    onSave(draft)
                                }
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("save_student_button")
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Saving Student…")
                        } else {
                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (isExisting) "Save Changes ($filledCount/$totalCount)" else "Save Student to Register")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LinearProgressIndicator(
                progress = { (filledCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )

            ScrollableTabRow(
                selectedTabIndex = activeTab.index,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                EditorSectionTab.entries.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        text = {
                            Text(
                                text = tab.shortTitle,
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        modifier = Modifier.testTag("editor_tab_${tab.index}")
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = expandAllDetails,
                        onClick = { expandAllDetails = !expandAllDetails },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Rounded.Comment, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text(if (expandAllDetails) "Hide Empty Details" else "All Field Details") },
                        modifier = Modifier.testTag("toggle_all_details_chip")
                    )
                    FilterChip(
                        selected = showRadarPreview,
                        onClick = { showRadarPreview = !showRadarPreview },
                        leadingIcon = {
                            Icon(Icons.Rounded.Pentagon, contentDescription = null, modifier = Modifier.size(16.dp))
                        },
                        label = { Text("5-Angle Graph ($perfScore%)") },
                        modifier = Modifier.testTag("toggle_student_radar_chip")
                    )
                }

                AnimatedVisibility(visible = showRadarPreview) {
                    val radarAxes = remember(draft, subjects) {
                        buildStudentRadarAxes(draft, subjects)
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Student 5-Angle Performance Pentagon",
                                style = MaterialTheme.typography.titleMedium
                            )
                            FiveAngleRadarChart(axes = radarAxes)
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = activeTab.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = activeTab.subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }

                when (activeTab) {
                    EditorSectionTab.FIXED -> FixedCoreSectionFields(
                        student = draft,
                        expandAllDetails = expandAllDetails,
                        onChange = { draft = it }
                    )

                    EditorSectionTab.YEARLY -> YearlySectionFields(
                        student = draft,
                        expandAllDetails = expandAllDetails,
                        onChange = { draft = it }
                    )

                    EditorSectionTab.SUBJECTS -> SubjectsSectionFields(
                        student = draft,
                        subjects = subjects,
                        expandAllDetails = expandAllDetails,
                        onChange = { draft = it },
                        onRequestAddSubject = { showSubjectDialog = true },
                        onRequestEditSubject = { editingSubject = it },
                        onRequestDeleteSubject = onDeleteSubject,
                        onRequestAddSubjectField = { addingFieldForSubject = it },
                        onRequestEditSubjectField = { sub, f -> editingSubjectField = sub to f },
                        onRequestDeleteSubjectField = onDeleteSubjectField
                    )

                    EditorSectionTab.CUSTOM -> FlexibleCategorySectionFields(
                        student = draft,
                        customFields = customFields,
                        expandAllDetails = expandAllDetails,
                        onChange = { draft = it },
                        onRequestAddCustomField = { showCustomFieldDialog = true },
                        onRequestEditCustomField = { editingCustomField = it },
                        onRequestDeleteCustomField = onDeleteCustomField
                    )
                }

                Spacer(Modifier.height(12.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Student?") },
            text = {
                Text("Remove ${draft.primaryName()} (Roll ${draft.rollNumber}) from your ${draft.academicYear} register? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(draft)
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSubjectDialog || editingSubject != null) {
        SubjectFormDialog(
            initial = editingSubject,
            defaultGradeScope = draft.classLevel,
            onDismiss = {
                showSubjectDialog = false
                editingSubject = null
            },
            onSave = { subject ->
                onSaveSubject(subject)
                showSubjectDialog = false
                editingSubject = null
            }
        )
    }

    val subForField = addingFieldForSubject
    if (subForField != null) {
        SubjectFieldFormDialog(
            subjectName = subForField.name,
            initial = null,
            onDismiss = { addingFieldForSubject = null },
            onSave = { label, type, maxMarks ->
                onAddSubjectField(subForField, label, type, maxMarks)
                addingFieldForSubject = null
            }
        )
    }

    val editSubField = editingSubjectField
    if (editSubField != null) {
        SubjectFieldFormDialog(
            subjectName = editSubField.first.name,
            initial = editSubField.second,
            onDismiss = { editingSubjectField = null },
            onSave = { label, type, maxMarks ->
                onUpdateSubjectField(
                    editSubField.first,
                    editSubField.second.copy(label = label, fieldType = type, maxMarks = maxMarks)
                )
                editingSubjectField = null
            }
        )
    }

    if (showCustomFieldDialog || editingCustomField != null) {
        CustomFieldFormDialog(
            initial = editingCustomField,
            existingCategories = customFields.map { it.category }.distinct(),
            onDismiss = {
                showCustomFieldDialog = false
                editingCustomField = null
            },
            onSave = { cf ->
                onSaveCustomField(cf)
                showCustomFieldDialog = false
                editingCustomField = null
            }
        )
    }
}

private fun buildStudentRadarAxes(
    student: StudentRecord,
    subjects: List<SubjectDefinition>
): List<RadarAxisMetric> {
    val axes = mutableListOf<RadarAxisMetric>()
    axes += RadarAxisMetric("Attend", student.attendancePercentage())

    val matchingSubjects = subjects.filter { it.appliesToClass(student.classLevel) }
    matchingSubjects.forEach { sub ->
        if (axes.size < 5) {
            val pct = student.subjectPercentage(sub) ?: 0
            axes += RadarAxisMetric(sub.name.take(8), pct)
        }
    }

    if (axes.size == 1 && (student.banglaMarks > 0 || student.englishMarks > 0 || student.mathMarks > 0)) {
        axes += RadarAxisMetric("Bangla", student.banglaMarks.coerceAtMost(100).toInt())
        axes += RadarAxisMetric("English", student.englishMarks.coerceAtMost(100).toInt())
        axes += RadarAxisMetric("Math", student.mathMarks.coerceAtMost(100).toInt())
    }

    while (axes.size < 5) {
        val label = when (axes.size) {
            1 -> "Subj 1"
            2 -> "Subj 2"
            3 -> "Subj 3"
            else -> "Overall"
        }
        val valPct = if (axes.size == 4) student.overallPerformanceScore(subjects) else 0
        axes += RadarAxisMetric(label, valPct)
    }
    return axes
}
