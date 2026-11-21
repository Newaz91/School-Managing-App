package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamTermConfig
import com.example.data.model.GradeScopes
import com.example.data.model.MarksheetSubjectSpec
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.generateShortId
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.util.Locale
import kotlin.math.roundToInt

private fun formatScore(value: Double): String {
    val rounded = value.roundToInt()
    return if (kotlin.math.abs(value - rounded) < 0.001) {
        rounded.toString()
    } else {
        String.format(Locale.ENGLISH, "%.1f", value)
    }
}

@Composable
fun StudentMarksheetPanel(
    student: StudentRecord,
    allStudents: List<StudentRecord>,
    examConfigs: List<ExamTermConfig>,
    schoolSubjects: List<SubjectDefinition>,
    isAdmin: Boolean,
    isSaving: Boolean,
    onSaveStudentMarks: (StudentRecord, onSuccess: () -> Unit) -> Unit,
    onSaveExamConfigs: (List<ExamTermConfig>, onSuccess: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedExams = remember(examConfigs) { ExamDefaults.ensureThreeExams(examConfigs) }
    var activeExamIdx by remember { mutableIntStateOf(0) }
    val activeExam = normalizedExams[activeExamIdx.coerceIn(0, 2)]

    var isEditingMarks by remember { mutableStateOf(false) }
    var showCustomizeDialog by remember { mutableStateOf(false) }

    val draftMarks = remember(student.id, student.examResults) {
        mutableStateMapOf<String, String>().apply {
            putAll(student.examResults)
        }
    }

    LaunchedEffect(student.id, student.examResults) {
        if (!isEditingMarks) {
            draftMarks.clear()
            draftMarks.putAll(student.examResults)
        }
    }

    val classPeers = remember(allStudents, student.classLevel, student.academicYear) {
        allStudents.filter {
            it.classLevel == student.classLevel && it.academicYear == student.academicYear
        }
    }

    val applicableSpecs = remember(activeExam, student.classLevel) {
        activeExam.subjects.filter { it.appliesToClass(student.classLevel) }
    }

    fun obtainedDoubleFor(spec: MarksheetSubjectSpec): Double? {
        val raw = if (isEditingMarks) {
            draftMarks[student.examMarkKey(activeExam.examId, spec.subjectId)]
        } else {
            student.examMarkRaw(activeExam.examId, spec.subjectId)
        }
        return raw?.trim()?.toDoubleOrNull()
    }

    val totalFullMarks = applicableSpecs.sumOf { it.fullMarks }
    val scoredSpecs = applicableSpecs.filter { obtainedDoubleFor(it) != null }
    val totalObtained = scoredSpecs.sumOf { obtainedDoubleFor(it) ?: 0.0 }
    val scoredFullMarks = scoredSpecs.sumOf { it.fullMarks }
    val examPercentage = if (scoredFullMarks > 0L) {
        ((totalObtained * 100.0) / scoredFullMarks.toDouble()).roundToInt().coerceIn(0, 100)
    } else 0
    val passedSubjectsCount = scoredSpecs.count { (obtainedDoubleFor(it) ?: 0.0) >= it.passMarks.toDouble() }
    val failedAny = scoredSpecs.any { (obtainedDoubleFor(it) ?: 0.0) < it.passMarks.toDouble() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Exam Term Switcher
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Grade,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "3-Exam Marksheet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { showCustomizeDialog = true },
                        modifier = Modifier.testTag("marksheet_customize_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Subjects / Exams")
                    }
                }

                // 3 Exams Selector
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    normalizedExams.forEachIndexed { idx, exam ->
                        SegmentedButton(
                            selected = activeExamIdx == idx,
                            onClick = { activeExamIdx = idx },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = idx,
                                count = normalizedExams.size
                            ),
                            modifier = Modifier.testTag("marksheet_exam_tab_$idx")
                        ) {
                            Text(
                                text = exam.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Exam Summary Strip
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${activeExam.title} • ${student.classLevel}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (scoredSpecs.isEmpty()) {
                                    "${applicableSpecs.size} subject(s) configured • Total Full Marks: $totalFullMarks"
                                } else {
                                    "Obtained ${formatScore(totalObtained)} / $scoredFullMarks ($examPercentage%) • Passed $passedSubjectsCount/${scoredSpecs.size}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (scoredSpecs.isNotEmpty()) {
                            Surface(
                                color = if (!failedAny) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (!failedAny) "PASS" else "FAIL",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (!failedAny) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Action row: Edit Marks / Save Marks
        if (applicableSpecs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isEditingMarks) {
                    Button(
                        onClick = { isEditingMarks = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("marksheet_edit_marks_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enter / Edit ${activeExam.title} Marks")
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            draftMarks.clear()
                            draftMarks.putAll(student.examResults)
                            isEditingMarks = false
                        },
                        modifier = Modifier
                            .weight(0.8f)
                            .height(48.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            var updated = student
                            draftMarks.forEach { (k, v) ->
                                val parts = k.split("__", limit = 2)
                                if (parts.size == 2) {
                                    updated = updated.withExamMark(parts[0], parts[1], v)
                                }
                            }
                            // Remove any cleared keys
                            student.examResults.keys.forEach { oldKey ->
                                if (!draftMarks.containsKey(oldKey) || draftMarks[oldKey].isNullOrBlank()) {
                                    val parts = oldKey.split("__", limit = 2)
                                    if (parts.size == 2) {
                                        updated = updated.withExamMark(parts[0], parts[1], "")
                                    }
                                }
                            }
                            onSaveStudentMarks(updated) {
                                isEditingMarks = false
                            }
                        },
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("marksheet_save_marks_button")
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isAdmin) "Save Marks" else "Submit for Admin Approval")
                    }
                }
            }
        }

        // Subject Marksheet Table / Cards
        if (applicableSpecs.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "No Marksheet Subjects for ${activeExam.title}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Add custom subjects, Full Marks, and Pass Marks for ${activeExam.title} — or import subjects already created in your school register.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { showCustomizeDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Configure ${activeExam.title} Subjects")
                    }
                }
            }
        } else {
            applicableSpecs.forEach { spec ->
                val markKey = student.examMarkKey(activeExam.examId, spec.subjectId)
                val obtained = obtainedDoubleFor(spec)
                val autoHighest = classPeers.mapNotNull { it.examMarkDouble(activeExam.examId, spec.subjectId) }
                    .maxOrNull()
                val highestVal = if (spec.highestMarkOverride > 0L) {
                    spec.highestMarkOverride.toDouble()
                } else {
                    maxOf(autoHighest ?: 0.0, obtained ?: 0.0)
                }

                val isPass = obtained != null && obtained >= spec.passMarks.toDouble()
                val badgeBg = when {
                    obtained == null -> MaterialTheme.colorScheme.surfaceContainerHigh
                    isPass -> MaterialTheme.colorScheme.primaryContainer
                    else -> MaterialTheme.colorScheme.errorContainer
                }
                val badgeFg = when {
                    obtained == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    isPass -> MaterialTheme.colorScheme.onPrimaryContainer
                    else -> MaterialTheme.colorScheme.onErrorContainer
                }
                val badgeText = when {
                    obtained == null -> "—"
                    isPass -> "PASS"
                    else -> "FAIL"
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = spec.subjectName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Full Marks: ${spec.fullMarks} • Pass Marks: ${spec.passMarks}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = badgeBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = badgeText,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = badgeFg,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    Text(
                                        text = "Highest in Class",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (highestVal > 0.0) formatScore(highestVal) else "—",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }

                            if (isEditingMarks) {
                                OutlinedTextField(
                                    value = draftMarks[markKey].orEmpty(),
                                    onValueChange = { input ->
                                        val filtered = input.filter { it.isDigit() || it == '.' }
                                        if (filtered.isBlank()) draftMarks.remove(markKey)
                                        else draftMarks[markKey] = filtered
                                    },
                                    label = { Text("Obtained") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .testTag("mark_input_${spec.subjectId}")
                                )
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            text = "Obtained Mark",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (obtained != null) "${formatScore(obtained)} / ${spec.fullMarks}" else "—",
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
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

    if (showCustomizeDialog) {
        MarksheetCustomizerDialog(
            examConfigs = normalizedExams,
            initialExamIndex = activeExamIdx,
            defaultClassScope = student.classLevel,
            schoolSubjects = schoolSubjects,
            isAdmin = isAdmin,
            isSaving = isSaving,
            onDismiss = { showCustomizeDialog = false },
            onSave = { updatedConfigs ->
                onSaveExamConfigs(updatedConfigs) {
                    showCustomizeDialog = false
                }
            }
        )
    }
}

@Composable
fun MarksheetCustomizerDialog(
    examConfigs: List<ExamTermConfig>,
    initialExamIndex: Int = 0,
    defaultClassScope: String = GradeScopes.ALL_CLASSES,
    schoolSubjects: List<SubjectDefinition> = emptyList(),
    isAdmin: Boolean = true,
    isSaving: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (List<ExamTermConfig>) -> Unit
) {
    var draftConfigs by remember(examConfigs) {
        mutableStateOf(ExamDefaults.ensureThreeExams(examConfigs))
    }
    var selectedExamIdx by remember(initialExamIndex) {
        mutableIntStateOf(initialExamIndex.coerceIn(0, 2))
    }

    val currentExam = draftConfigs[selectedExamIdx]
    var examTitleInput by remember(selectedExamIdx, currentExam.title) { mutableStateOf(currentExam.title) }

    var newSubjectName by remember { mutableStateOf("") }
    var newSubjectScope by remember { mutableStateOf(defaultClassScope) }
    var newFullMarks by remember { mutableStateOf("100") }
    var newPassMarks by remember { mutableStateOf("33") }
    var newHighestOverride by remember { mutableStateOf("") }
    var editingSubject by remember { mutableStateOf<MarksheetSubjectSpec?>(null) }

    fun updateCurrentExam(transform: (ExamTermConfig) -> ExamTermConfig) {
        draftConfigs = draftConfigs.mapIndexed { idx, exam ->
            if (idx == selectedExamIdx) transform(exam) else exam
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize 3 Exams & Subjects") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        draftConfigs.forEachIndexed { idx, exam ->
                            SegmentedButton(
                                selected = selectedExamIdx == idx,
                                onClick = { selectedExamIdx = idx },
                                shape = SegmentedButtonDefaults.itemShape(idx, draftConfigs.size)
                            ) {
                                Text("Exam ${idx + 1}", maxLines = 1)
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = examTitleInput,
                        onValueChange = { nextTitle ->
                            examTitleInput = nextTitle
                            updateCurrentExam { it.copy(title = nextTitle) }
                        },
                        label = { Text("Exam ${selectedExamIdx + 1} Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (schoolSubjects.isNotEmpty()) {
                    item {
                        FilledTonalButton(
                            onClick = {
                                val existingNames = currentExam.subjects.map { it.subjectName.lowercase() }.toSet()
                                val imported = schoolSubjects
                                    .filter { it.name.lowercase() !in existingNames }
                                    .map { s ->
                                        MarksheetSubjectSpec(
                                            subjectId = s.id.ifBlank { generateShortId("es") },
                                            subjectName = s.name,
                                            classScope = s.gradeScope,
                                            fullMarks = 100L,
                                            passMarks = 33L,
                                            highestMarkOverride = 0L
                                        )
                                    }
                                if (imported.isNotEmpty()) {
                                    updateCurrentExam { it.copy(subjects = it.subjects + imported) }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Import from School Subjects (${schoolSubjects.size})")
                        }
                    }
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Add Marksheet Subject",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            OutlinedTextField(
                                value = newSubjectName,
                                onValueChange = { newSubjectName = it },
                                label = { Text("Subject Name (e.g. Bangla)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val scopes = listOf(GradeScopes.ALL_CLASSES) + ClassLevels.ALL
                                scopes.forEach { sc ->
                                    FilterChip(
                                        selected = newSubjectScope == sc,
                                        onClick = { newSubjectScope = sc },
                                        label = { Text(sc, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newFullMarks,
                                    onValueChange = { newFullMarks = it.filter { c -> c.isDigit() } },
                                    label = { Text("Full Marks") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newPassMarks,
                                    onValueChange = { newPassMarks = it.filter { c -> c.isDigit() } },
                                    label = { Text("Pass Marks") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newHighestOverride,
                                    onValueChange = { newHighestOverride = it.filter { c -> c.isDigit() } },
                                    label = { Text("Highest") },
                                    placeholder = { Text("Auto") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Button(
                                onClick = {
                                    val cleanName = newSubjectName.trim()
                                    if (cleanName.isNotEmpty()) {
                                        val full = (newFullMarks.toLongOrNull() ?: 100L).coerceIn(1L, 1000L)
                                        val pass = (newPassMarks.toLongOrNull() ?: 33L).coerceIn(0L, full)
                                        val highest = (newHighestOverride.toLongOrNull() ?: 0L).coerceIn(0L, full)
                                        val spec = MarksheetSubjectSpec(
                                            subjectId = generateShortId("es"),
                                            subjectName = cleanName,
                                            classScope = newSubjectScope,
                                            fullMarks = full,
                                            passMarks = pass,
                                            highestMarkOverride = highest
                                        )
                                        updateCurrentExam { it.copy(subjects = it.subjects + spec) }
                                        newSubjectName = ""
                                        newHighestOverride = ""
                                    }
                                },
                                enabled = newSubjectName.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Subject to Exam ${selectedExamIdx + 1}")
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        text = "${currentExam.title} Subjects (${currentExam.subjects.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(currentExam.subjects, key = { it.subjectId }) { spec ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${spec.subjectName} (${spec.classScope})",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Full: ${spec.fullMarks} • Pass: ${spec.passMarks} • Highest: ${if (spec.highestMarkOverride > 0) spec.highestMarkOverride else "Auto"}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingSubject = spec }) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit subject", modifier = Modifier.size(18.dp))
                            }
                            IconButton(
                                onClick = {
                                    updateCurrentExam {
                                        it.copy(subjects = it.subjects.filterNot { s -> s.subjectId == spec.subjectId })
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = "Delete subject",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(draftConfigs) },
                enabled = !isSaving
            ) {
                Text(if (isAdmin) "Save Setup" else "Submit Setup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )

    val toEdit = editingSubject
    if (toEdit != null) {
        var eName by remember(toEdit.subjectId) { mutableStateOf(toEdit.subjectName) }
        var eFull by remember(toEdit.subjectId) { mutableStateOf(toEdit.fullMarks.toString()) }
        var ePass by remember(toEdit.subjectId) { mutableStateOf(toEdit.passMarks.toString()) }
        var eHigh by remember(toEdit.subjectId) {
            mutableStateOf(if (toEdit.highestMarkOverride > 0) toEdit.highestMarkOverride.toString() else "")
        }

        AlertDialog(
            onDismissRequest = { editingSubject = null },
            title = { Text("Edit ${toEdit.subjectName}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = eName,
                        onValueChange = { eName = it },
                        label = { Text("Subject Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = eFull,
                        onValueChange = { eFull = it.filter { c -> c.isDigit() } },
                        label = { Text("Full Marks") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ePass,
                        onValueChange = { ePass = it.filter { c -> c.isDigit() } },
                        label = { Text("Pass Marks") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = eHigh,
                        onValueChange = { eHigh = it.filter { c -> c.isDigit() } },
                        label = { Text("Highest Mark Override (blank = Auto)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val full = (eFull.toLongOrNull() ?: 100L).coerceIn(1L, 1000L)
                        val pass = (ePass.toLongOrNull() ?: 33L).coerceIn(0L, full)
                        val high = (eHigh.toLongOrNull() ?: 0L).coerceIn(0L, full)
                        val updatedSpec = toEdit.copy(
                            subjectName = eName.trim().ifBlank { toEdit.subjectName },
                            fullMarks = full,
                            passMarks = pass,
                            highestMarkOverride = high
                        )
                        updateCurrentExam { exam ->
                            exam.copy(
                                subjects = exam.subjects.map {
                                    if (it.subjectId == toEdit.subjectId) updatedSpec else it
                                }
                            )
                        }
                        editingSubject = null
                    }
                ) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSubject = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
