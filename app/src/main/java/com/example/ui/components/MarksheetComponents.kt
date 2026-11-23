package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.VerticalDivider
import com.example.data.model.ClassLevels
import com.example.data.model.CombinedThreeTermTotals
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamPassOutcome
import com.example.data.model.ExamResultCalculator
import com.example.data.model.ExamTermConfig
import com.example.data.model.GradeScopes
import com.example.data.model.MarksheetSubjectSpec
import com.example.data.model.StudentExamResultSummary
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.TeacherProfile
import com.example.data.model.generateShortId
import com.example.export.ReportExporter
import com.example.messaging.ParentMessagingHelper
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun StudentMarksheetPanel(
    student: StudentRecord,
    allStudents: List<StudentRecord>,
    examConfigs: List<ExamTermConfig>,
    schoolSubjects: List<SubjectDefinition>,
    teacherProfile: TeacherProfile? = null,
    isAdmin: Boolean,
    isSaving: Boolean,
    onSaveStudentMarks: (StudentRecord, onSuccess: () -> Unit) -> Unit,
    onSaveExamConfigs: (List<ExamTermConfig>, onSuccess: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current

    val normalizedExams = remember(examConfigs) { ExamDefaults.ensureThreeExams(examConfigs) }
    var activeExamIdx by remember { mutableIntStateOf(0) }
    val activeExam = normalizedExams[activeExamIdx.coerceIn(0, 2)]

    var isEditingMarks by remember { mutableStateOf(false) }
    var showCustomizeDialog by remember { mutableStateOf(false) }

    val draftMarks = remember(student.id) {
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

    val draftStudent = remember(student, isEditingMarks, draftMarks.toMap()) {
        if (!isEditingMarks) student
        else student.copy(examResults = draftMarks.filterValues { it.isNotBlank() })
    }

    val classSheet = remember(activeExam, student.classLevel, allStudents, draftStudent, isEditingMarks) {
        ExamResultCalculator.computeClassResultSheet(
            exam = activeExam,
            classLevel = student.classLevel,
            allStudentsInYear = allStudents,
            draftOverridesByStudentId = if (isEditingMarks) mapOf(student.id to draftStudent) else emptyMap()
        )
    }

    val summary = remember(classSheet, draftStudent, activeExam) {
        classSheet.studentSummaries.firstOrNull { it.student.id == draftStudent.id }
            ?: ExamResultCalculator.computeClassResultSheet(
                exam = activeExam,
                classLevel = draftStudent.classLevel,
                allStudentsInYear = listOf(draftStudent)
            ).studentSummaries.first()
    }

    val applicableSpecs = summary.subjectEvaluations.map { it.spec }

    val combinedTotals = remember(normalizedExams, draftStudent, activeExam.examId) {
        ExamResultCalculator.computeCombinedThreeTermTotals(
            student = draftStudent,
            examConfigs = normalizedExams,
            activeExamId = activeExam.examId
        )
    }

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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Grade,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = strings.threeExamMarksheetTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { showCustomizeDialog = true },
                        modifier = Modifier.testTag("marksheet_customize_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(strings.subjectsAndExamsBtn, maxLines = 1)
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
                                text = strings.localizeExamTitle(exam),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }

                // Exam Summary Strip + Merit Badge (Final Exam ONLY)
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.localizeExamTitle(activeExam)} • ${strings.localizeClass(student.classLevel)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (summary.scoredCount == 0) {
                                    "${applicableSpecs.size} subject(s) • ${strings.fullMarksLabel}: ${summary.configuredFullMarks}"
                                } else {
                                    "${strings.obtainedMark}: ${ExamResultCalculator.formatMark(summary.totalObtained)} / ${summary.scoredFullMarks} (${summary.percentage}%) • ${strings.resultSheetGpaLabel} ${ExamResultCalculator.formatGpa(summary.gpa)}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (summary.isFinalExam && summary.meritPosition != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.EmojiEvents,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = "${strings.meritPositionLabel}: ${ExamResultCalculator.ordinal(summary.meritPosition)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        if (summary.scoredCount > 0) {
                            val isPass = summary.outcome == ExamPassOutcome.PASS
                            Surface(
                                color = if (isPass) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.error,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = strings.localizeOutcome(summary.outcome.name),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isPass) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onError,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Send Result to Guardian Card (WhatsApp, PDF, Image PNG, Standard Share)
        if (applicableSpecs.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${strings.sendResultToGuardian} (${strings.localizeExamTitle(activeExam)})",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val text = ReportExporter.buildStudentExamResultWhatsAppText(
                                    student = draftStudent,
                                    exam = activeExam,
                                    summary = summary,
                                    examConfigs = normalizedExams,
                                    teacherProfile = teacherProfile,
                                    strings = strings
                                )
                                ParentMessagingHelper.sendWhatsAppToParentOrBroadcast(
                                    context = context,
                                    singlePhone = draftStudent.guardianPhone.ifBlank { null },
                                    message = text
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("marksheet_share_whatsapp_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.sendViaWhatsApp)
                        }

                        FilledTonalButton(
                            onClick = {
                                val pdfBytes = ReportExporter.buildStudentExamResultPdfBytes(
                                    student = draftStudent,
                                    exam = activeExam,
                                    summary = summary,
                                    examConfigs = normalizedExams,
                                    teacherProfile = teacherProfile,
                                    strings = strings
                                )
                                val safeName = draftStudent.primaryName().ifBlank { "student" }
                                    .lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                                    .ifBlank { "student_${draftStudent.rollNumber.ifBlank { "id" }}" }
                                ReportExporter.shareFile(
                                    context = context,
                                    fileName = "result_${safeName}_${activeExam.examId}.pdf",
                                    mimeType = "application/pdf",
                                    bytes = pdfBytes,
                                    chooserTitle = strings.shareResultPdf
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("marksheet_share_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.shareResultPdf)
                        }

                        OutlinedButton(
                            onClick = {
                                val pngBytes = ReportExporter.buildStudentExamResultPngBytes(
                                    student = draftStudent,
                                    exam = activeExam,
                                    summary = summary,
                                    examConfigs = normalizedExams,
                                    teacherProfile = teacherProfile,
                                    strings = strings
                                )
                                val safeName = draftStudent.primaryName().ifBlank { "student" }
                                    .lowercase().replace(Regex("[^a-z0-9]+"), "_").trim('_')
                                    .ifBlank { "student_${draftStudent.rollNumber.ifBlank { "id" }}" }
                                ReportExporter.shareFile(
                                    context = context,
                                    fileName = "result_${safeName}_${activeExam.examId}.png",
                                    mimeType = "image/png",
                                    bytes = pngBytes,
                                    chooserTitle = strings.shareResultImage
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("marksheet_share_png_button")
                        ) {
                            Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.shareResultImage)
                        }

                        OutlinedButton(
                            onClick = {
                                val text = ReportExporter.buildStudentExamResultWhatsAppText(
                                    student = draftStudent,
                                    exam = activeExam,
                                    summary = summary,
                                    examConfigs = normalizedExams,
                                    teacherProfile = teacherProfile,
                                    strings = strings
                                )
                                ReportExporter.sharePlainText(
                                    context = context,
                                    text = text,
                                    chooserTitle = strings.shareStandard
                                )
                            },
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("marksheet_share_standard_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.shareStandard)
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
                        Text("${strings.enterEditMarksPrefix} (${strings.localizeExamTitle(activeExam)})")
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
                        Text(strings.cancelButton)
                    }
                    Button(
                        onClick = {
                            val cleanExamResults = draftMarks.entries
                                .filter { it.key.isNotBlank() && it.value.isNotBlank() }
                                .associate { it.key.trim().take(90) to it.value.trim().take(20) }
                            val updated = student.copy(examResults = cleanExamResults)
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
                        Text(if (isAdmin) strings.saveButton else strings.submitClassSubjectMarks)
                    }
                }
            }
        }

        // Marksheet Preview Table Chart OR Subject Continuous+Summative Input Cards when editing
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
                        text = "No Marksheet Subjects for ${strings.localizeExamTitle(activeExam)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = strings.bangladeshPassRuleHint,
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
                        Text(strings.subjectsAndExamsBtn)
                    }
                }
            }
        } else if (!isEditingMarks) {
            MarksheetFormTableCard(
                student = draftStudent,
                exam = activeExam,
                examConfigs = normalizedExams,
                summary = summary,
                combinedTotals = combinedTotals,
                teacherProfile = teacherProfile
            )
        } else {
            summary.subjectEvaluations.forEach { ev ->
                val spec = ev.spec
                val markKey = student.examMarkKey(activeExam.examId, spec.subjectId)
                val contKey = student.examContinuousKey(activeExam.examId, spec.subjectId)
                val sumKey = student.examSummativeKey(activeExam.examId, spec.subjectId)

                val contVal = draftMarks[contKey].orEmpty()
                val sumVal = draftMarks[sumKey].orEmpty()
                val totalVal = draftMarks[markKey].orEmpty()
                val combinedRow = combinedTotals.subjectRows.firstOrNull {
                    it.specByExamId[activeExam.examId]?.subjectId == spec.subjectId ||
                        it.subjectName.equals(spec.subjectName, ignoreCase = true)
                }

                fun updateParts(nextCont: String, nextSum: String) {
                    if (nextCont.isBlank()) draftMarks.remove(contKey) else draftMarks[contKey] = nextCont
                    if (nextSum.isBlank()) draftMarks.remove(sumKey) else draftMarks[sumKey] = nextSum
                    val c = nextCont.trim().toDoubleOrNull()
                    val s = nextSum.trim().toDoubleOrNull()
                    if (c != null || s != null) {
                        draftMarks[markKey] = ExamResultCalculator.formatMark((c ?: 0.0) + (s ?: 0.0))
                    } else {
                        draftMarks.remove(markKey)
                    }
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
                                val highStr = if (ev.highestInClass > 0.0) ExamResultCalculator.formatMark(ev.highestInClass) else "—"
                                Text(
                                    text = "${strings.fullMarksLabel}: ${spec.fullMarks} • ${strings.highestInClass}: $highStr",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                color = when {
                                    !ev.hasScore -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    ev.isPassed -> MaterialTheme.colorScheme.primaryContainer
                                    else -> MaterialTheme.colorScheme.errorContainer
                                },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = when {
                                        !ev.hasScore -> "—"
                                        ev.isPassed -> "${ev.letterGrade} • ${strings.resultPass}"
                                        else -> "${ev.letterGrade} • ${strings.resultFail}"
                                    },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        !ev.hasScore -> MaterialTheme.colorScheme.onSurfaceVariant
                                        ev.isPassed -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onErrorContainer
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = contVal,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() || it == '.' }.take(10)
                                    updateParts(filtered, sumVal)
                                },
                                label = { Text(strings.continuousMarkLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mark_cont_input_${spec.subjectId}")
                            )

                            OutlinedTextField(
                                value = sumVal,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() || it == '.' }.take(10)
                                    updateParts(contVal, filtered)
                                },
                                label = { Text(strings.summativeMarkLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mark_sum_input_${spec.subjectId}")
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.totalObtainedMarkLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = totalVal.ifBlank { "—" },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.threeTermAverageCol,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = combinedRow?.threeTermAverage?.let { ExamResultCalculator.formatMark(it) } ?: "—",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
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

/**
 * 3-Term Combined Marksheet Chart:
 * - No Pass column
 * - 3 individual columns per Term Examination: Continuous | Summative | Total Obtained (auto-summed)
 * - 3-Term Average column at the end + Grade/Status
 */
@Composable
private fun MarksheetFormTableCard(
    student: StudentRecord,
    exam: ExamTermConfig,
    examConfigs: List<ExamTermConfig>,
    summary: StudentExamResultSummary,
    combinedTotals: CombinedThreeTermTotals,
    teacherProfile: TeacherProfile?
) {
    val strings = LocalAppStrings.current
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val normalizedExams = remember(examConfigs) { ExamDefaults.ensureThreeExams(examConfigs) }

    val subjectColW = 116.dp
    val fullColW = 42.dp
    val contColW = 44.dp
    val sumColW = 44.dp
    val termTotColW = 50.dp
    val termGroupW = contColW + sumColW + termTotColW // 138.dp
    val avgColW = 68.dp
    val gradeColW = 72.dp

    fun fmt(v: Double?): String = v?.let { ExamResultCalculator.formatMark(it) } ?: "—"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Center: School + Exam Name
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val schoolTitle = teacherProfile?.schoolName?.ifBlank { strings.appName } ?: strings.appName
                Text(
                    text = "$schoolTitle • ${student.academicYear}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${strings.threeExamMarksheetTitle} (${strings.localizeExamTitle(exam)})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }

            HorizontalDivider()

            // Student Name, Class, Roll
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val displayName = student.combinedDisplayName()
                    Text(
                        text = "${strings.resultSheetStudentName}: $displayName",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val clsStr = strings.localizeClass(student.classLevel) +
                            if (student.section.isNotBlank()) " (${student.section})" else ""
                        Text(
                            text = "${strings.resultSheetClassName}: $clsStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${strings.resultSheetRollNo}: ${student.rollNumber}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Text(
                text = strings.swipeTableHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )

            // Horizontally Scrollable 2-Tier 3-Term Marksheet Table (No Pass Column)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                    .horizontalScroll(rememberScrollState())
            ) {
                // Tier 1 Group Header
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.resultSheetSubjectCol,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width(subjectColW)
                            .padding(horizontal = 8.dp)
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                    Text(
                        strings.resultSheetFullCol,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(fullColW)
                    )
                    normalizedExams.forEach { termExam ->
                        VerticalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f))
                        Text(
                            text = strings.localizeExamTitle(termExam),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(termGroupW)
                                .padding(horizontal = 4.dp)
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f))
                    Text(
                        strings.threeTermAverageCol,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(avgColW)
                            .padding(horizontal = 4.dp)
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f))
                    Text(
                        strings.resultSheetGradeCol,
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(gradeColW)
                            .padding(horizontal = 4.dp)
                    )
                }

                // Tier 2 Sub-Header (Cont. | Sum. | Total per Term)
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.width(subjectColW))
                    VerticalDivider(color = borderColor)
                    Spacer(Modifier.width(fullColW))
                    repeat(3) {
                        VerticalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                        Text(
                            strings.continuousShortCol,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(contColW)
                        )
                        VerticalDivider(color = borderColor.copy(alpha = 0.6f))
                        Text(
                            strings.summativeShortCol,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(sumColW)
                        )
                        VerticalDivider(color = borderColor.copy(alpha = 0.6f))
                        Text(
                            strings.totalShortCol,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(termTotColW)
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    Text(
                        strings.threeTermAverageCol,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(avgColW)
                    )
                    VerticalDivider(color = borderColor)
                    Text(
                        strings.resultSheetOverallResult,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.width(gradeColW)
                    )
                }

                HorizontalDivider(color = borderColor)

                // Subject Rows across all 3 Terms
                combinedTotals.subjectRows.forEachIndexed { idx, row ->
                    val bg = if (idx % 2 == 1) MaterialTheme.colorScheme.surfaceContainerLow
                    else MaterialTheme.colorScheme.surface
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .background(bg)
                            .padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            row.subjectName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .width(subjectColW)
                                .padding(horizontal = 8.dp)
                        )
                        VerticalDivider(color = borderColor.copy(alpha = 0.6f))
                        Text(
                            row.fullMarks.toString(),
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(fullColW)
                        )
                        listOf(row.term1, row.term2, row.term3).forEach { term ->
                            VerticalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            Text(
                                fmt(term.continuous),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(contColW)
                            )
                            VerticalDivider(color = borderColor.copy(alpha = 0.45f))
                            Text(
                                fmt(term.summative),
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(sumColW)
                            )
                            VerticalDivider(color = borderColor.copy(alpha = 0.45f))
                            Text(
                                fmt(term.total),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = if (term.total != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(termTotColW)
                            )
                        }
                        VerticalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        Text(
                            fmt(row.threeTermAverage),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = if (row.threeTermAverage != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(avgColW)
                        )
                        VerticalDivider(color = borderColor.copy(alpha = 0.6f))
                        val stColor = when {
                            !row.activeTermHasScore -> MaterialTheme.colorScheme.onSurfaceVariant
                            row.activeTermPassed -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.error
                        }
                        val stText = when {
                            !row.activeTermHasScore -> "—"
                            row.activeTermPassed -> "${row.activeTermGrade} (${strings.resultPass})"
                            else -> "${row.activeTermGrade} (${strings.resultFail})"
                        }
                        Text(
                            stText,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall,
                            color = stColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .width(gradeColW)
                                .padding(horizontal = 4.dp)
                        )
                    }
                    HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
                }

                // Bottom Auto-Calculated Total Summary Row
                Row(
                    modifier = Modifier
                        .height(IntrinsicSize.Min)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        strings.resultSheetTotalRow,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width(subjectColW)
                            .padding(horizontal = 8.dp)
                    )
                    VerticalDivider(color = borderColor)
                    Text(
                        combinedTotals.configuredFullMarks.toString(),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(fullColW)
                    )
                    val termTotalsList = listOf(
                        Triple(combinedTotals.t1ContTotal, combinedTotals.t1SumTotal, combinedTotals.t1Total to combinedTotals.t1HasScore),
                        Triple(combinedTotals.t2ContTotal, combinedTotals.t2SumTotal, combinedTotals.t2Total to combinedTotals.t2HasScore),
                        Triple(combinedTotals.t3ContTotal, combinedTotals.t3SumTotal, combinedTotals.t3Total to combinedTotals.t3HasScore)
                    )
                    termTotalsList.forEach { (cSum, sSum, totPair) ->
                        val (tVal, tHas) = totPair
                        VerticalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                        Text(
                            if (tHas && cSum > 0.0) ExamResultCalculator.formatMark(cSum) else "—",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(contColW)
                        )
                        VerticalDivider(color = borderColor.copy(alpha = 0.5f))
                        Text(
                            if (tHas && sSum > 0.0) ExamResultCalculator.formatMark(sSum) else "—",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(sumColW)
                        )
                        VerticalDivider(color = borderColor.copy(alpha = 0.5f))
                        Text(
                            if (tHas) ExamResultCalculator.formatMark(tVal) else "—",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(termTotColW)
                        )
                    }
                    VerticalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                    Text(
                        fmt(combinedTotals.threeTermAvgTotal),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(avgColW)
                    )
                    VerticalDivider(color = borderColor)
                    val totalResultText = when (summary.outcome) {
                        ExamPassOutcome.PASS -> "${strings.resultPass} (${ExamResultCalculator.formatGpa(summary.gpa)})"
                        ExamPassOutcome.FAIL -> strings.resultFail
                        ExamPassOutcome.PENDING -> strings.resultPending
                    }
                    Text(
                        totalResultText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (summary.outcome == ExamPassOutcome.FAIL) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .width(gradeColW)
                            .padding(horizontal = 4.dp)
                    )
                }
            }

            if (summary.isFinalExam && summary.meritPosition != null) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.EmojiEvents, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${strings.meritPositionLabel}: ${ExamResultCalculator.ordinal(summary.meritPosition)}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
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
    val strings = LocalAppStrings.current
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
    var passManuallyEdited by remember { mutableStateOf(false) }
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
                        draftConfigs.forEachIndexed { idx, _ ->
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
                                        val firstNumericMax = s.fields.firstOrNull { it.maxMarks > 0L }?.maxMarks ?: 100L
                                        val passAuto = ExamResultCalculator.bangladeshPassMarks(firstNumericMax)
                                        MarksheetSubjectSpec(
                                            subjectId = s.id.ifBlank { generateShortId("es") },
                                            subjectName = s.name,
                                            classScope = s.gradeScope,
                                            fullMarks = firstNumericMax,
                                            passMarks = passAuto,
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
                            Text(
                                text = strings.bangladeshPassRuleHint,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
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
                                        label = { Text(strings.localizeClass(sc), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newFullMarks,
                                    onValueChange = { raw ->
                                        val cleaned = raw.filter { c -> c.isDigit() }
                                        newFullMarks = cleaned
                                        if (!passManuallyEdited) {
                                            cleaned.toLongOrNull()?.let { f ->
                                                newPassMarks = ExamResultCalculator.bangladeshPassMarks(f).toString()
                                            }
                                        }
                                    },
                                    label = { Text(strings.fullMarksLabel) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newPassMarks,
                                    onValueChange = {
                                        passManuallyEdited = true
                                        newPassMarks = it.filter { c -> c.isDigit() }
                                    },
                                    label = { Text(strings.passMarksLabel) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = newHighestOverride,
                                    onValueChange = { newHighestOverride = it.filter { c -> c.isDigit() } },
                                    label = { Text(strings.highestInClass) },
                                    placeholder = { Text("Auto") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                FilterChip(
                                    selected = newFullMarks == "100",
                                    onClick = {
                                        newFullMarks = "100"
                                        newPassMarks = "33"
                                        passManuallyEdited = false
                                    },
                                    label = { Text("100 (Pass 33)") }
                                )
                                FilterChip(
                                    selected = newFullMarks == "50",
                                    onClick = {
                                        newFullMarks = "50"
                                        newPassMarks = "17"
                                        passManuallyEdited = false
                                    },
                                    label = { Text("50 (Pass 17)") }
                                )
                            }
                            Button(
                                onClick = {
                                    val cleanName = newSubjectName.trim()
                                    if (cleanName.isNotEmpty()) {
                                        val full = (newFullMarks.toLongOrNull() ?: 100L).coerceIn(1L, 1000L)
                                        val pass = (newPassMarks.toLongOrNull()
                                            ?: ExamResultCalculator.bangladeshPassMarks(full)).coerceIn(0L, full)
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
                                        passManuallyEdited = false
                                    }
                                },
                                enabled = newSubjectName.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
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
                                    text = "Full: ${spec.fullMarks} • Pass: ${ExamResultCalculator.effectivePassMarks(spec)} • Highest: ${if (spec.highestMarkOverride > 0) spec.highestMarkOverride else "Auto"}",
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
                Text(if (isAdmin) strings.saveButton else "Submit Setup")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        }
    )

    val toEdit = editingSubject
    if (toEdit != null) {
        var eName by remember(toEdit.subjectId) { mutableStateOf(toEdit.subjectName) }
        var eFull by remember(toEdit.subjectId) { mutableStateOf(toEdit.fullMarks.toString()) }
        var ePass by remember(toEdit.subjectId) {
            mutableStateOf(ExamResultCalculator.effectivePassMarks(toEdit).toString())
        }
        var editPassManual by remember(toEdit.subjectId) { mutableStateOf(false) }
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
                        onValueChange = { raw ->
                            val clean = raw.filter { c -> c.isDigit() }
                            eFull = clean
                            if (!editPassManual) {
                                clean.toLongOrNull()?.let { f ->
                                    ePass = ExamResultCalculator.bangladeshPassMarks(f).toString()
                                }
                            }
                        },
                        label = { Text(strings.fullMarksLabel) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = ePass,
                        onValueChange = {
                            editPassManual = true
                            ePass = it.filter { c -> c.isDigit() }
                        },
                        label = { Text(strings.passMarksLabel) },
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
                        val pass = (ePass.toLongOrNull()
                            ?: ExamResultCalculator.bangladeshPassMarks(full)).coerceIn(0L, full)
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
                    Text(strings.cancelButton)
                }
            }
        )
    }
}
