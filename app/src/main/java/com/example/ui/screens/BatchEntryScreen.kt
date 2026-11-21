package com.example.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassLevels
import com.example.data.model.CustomFieldTypes
import com.example.data.model.SectorOptions
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.ui.components.EmptyRegisterState
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState

enum class BatchMode(val label: String) {
    ATTENDANCE("Attendance"),
    YEARLY("Roll, Class & Promotion"),
    SUBJECT_FIELDS("Subject Fields")
}

@Composable
fun BatchEntryScreen(
    studentsState: UiState<List<StudentRecord>>,
    subjects: List<SubjectDefinition>,
    selectedYear: Int,
    isSaving: Boolean,
    initialClass: String? = null,
    initialSubjectId: String? = null,
    onSaveBatch: (Map<String, Map<String, Any>>, onSuccess: () -> Unit) -> Unit,
    onAddStudent: () -> Unit,
    onOpenFieldManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedClass by remember(initialClass) {
        mutableStateOf(initialClass?.takeIf { it in ClassLevels.ALL } ?: ClassLevels.ALL[1])
    }
    var selectedMode by remember(initialSubjectId) {
        mutableStateOf(if (!initialSubjectId.isNullOrBlank()) BatchMode.SUBJECT_FIELDS else BatchMode.ATTENDANCE)
    }
    val draftEdits = remember { mutableStateMapOf<String, StudentRecord>() }

    val allStudents = (studentsState as? UiState.Success)?.data ?: emptyList()
    val classStudents = remember(allStudents, selectedClass) {
        allStudents.filter { it.classLevel == selectedClass }
    }

    val matchingSubjects = remember(subjects, selectedClass) {
        subjects.filter { it.appliesToClass(selectedClass) }
    }
    var selectedSubjectId by remember(matchingSubjects, initialSubjectId) {
        mutableStateOf(
            initialSubjectId?.takeIf { id -> matchingSubjects.any { it.id == id } }
                ?: matchingSubjects.firstOrNull()?.id
                ?: ""
        )
    }
    val activeSubject = matchingSubjects.firstOrNull { it.id == selectedSubjectId }
        ?: matchingSubjects.firstOrNull()

    LaunchedEffect(selectedYear) {
        draftEdits.clear()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            label = { Text("$cls ($count)") },
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
                            label = { Text(mode.label) },
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
                                    text = "No subjects created for $selectedClass yet.",
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
                                Text("Mark All $selectedClass Students as Promoted")
                            }
                        }

                        BatchMode.SUBJECT_FIELDS -> Unit
                    }
                }
            }

            when (studentsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                is UiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(studentsState.message, color = MaterialTheme.colorScheme.error)
                    }
                }

                is UiState.Success -> {
                    if (classStudents.isEmpty()) {
                        EmptyRegisterState(
                            academicYear = selectedYear,
                            hasActiveFilter = false,
                            onAddStudent = onAddStudent,
                            onClearFilters = {}
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
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

        if (draftEdits.isNotEmpty()) {
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
                        Text("Discard")
                    }
                    Button(
                        onClick = {
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
                            onSaveBatch(payload) {
                                draftEdits.clear()
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
                            Text("Save ${draftEdits.size} Student(s)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BatchStudentRowCard(
    student: StudentRecord,
    mode: BatchMode,
    activeSubject: SubjectDefinition?,
    isDirty: Boolean,
    onUpdate: (StudentRecord) -> Unit
) {
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
                        text = student.studentNameEn,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (student.studentNameBn.isNotBlank()) {
                        Text(
                            text = student.studentNameBn,
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
                                    label = { Text(cls) }
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
                                    label = { Text(status) }
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
            }
        }
    }
}
