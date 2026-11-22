package com.example.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamPassOutcome
import com.example.data.model.ExamResultCalculator
import com.example.data.model.ExamTermConfig
import com.example.data.model.StudentExamResultSummary
import com.example.data.model.StudentRecord
import com.example.data.model.TeacherProfile
import com.example.export.ReportExporter
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun ClassResultsPanel(
    selectedClass: String,
    allStudents: List<StudentRecord>,
    examConfigs: List<ExamTermConfig>,
    teacherProfile: TeacherProfile?,
    selectedYear: Int,
    onSelectStudent: (StudentRecord) -> Unit,
    onOpenExamSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val normalizedExams = remember(examConfigs) { ExamDefaults.ensureThreeExams(examConfigs) }
    var selectedExamIdx by remember { mutableIntStateOf(2) } // Default to Final Exam
    val activeExam = normalizedExams[selectedExamIdx.coerceIn(0, 2)]

    val sheet = remember(activeExam, selectedClass, allStudents) {
        ExamResultCalculator.computeClassResultSheet(
            exam = activeExam,
            classLevel = selectedClass,
            allStudentsInYear = allStudents
        )
    }

    val sortedSummaries = remember(sheet) {
        if (sheet.isFinalExam) {
            sheet.studentSummaries.sortedWith(
                compareBy<StudentExamResultSummary> { it.meritPosition ?: Int.MAX_VALUE }
                    .thenBy { it.outcome.ordinal }
                    .thenBy { it.student.rollSortKey() }
            )
        } else {
            sheet.studentSummaries
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Exam Selector Row
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                normalizedExams.forEachIndexed { idx, ex ->
                    FilterChip(
                        selected = selectedExamIdx == idx,
                        onClick = { selectedExamIdx = idx },
                        label = { Text(strings.localizeExamTitle(ex)) },
                        modifier = Modifier.testTag("class_results_exam_$idx")
                    )
                }
            }
        }

        // Class KPI Summary Card + Merit Rule Note
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                ),
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${strings.localizeClass(selectedClass)} • ${strings.localizeExamTitle(activeExam)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (sheet.isFinalExam) strings.finalMeritNote else strings.termNoMeritNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.EmojiEvents,
                            contentDescription = null,
                            tint = if (sheet.isFinalExam) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ResultStatChip(strings.appearedLabel, sheet.appearedCount.toString(), Modifier.weight(1f))
                        ResultStatChip(strings.passedLabel, sheet.passedCount.toString(), Modifier.weight(1f))
                        ResultStatChip(strings.failedLabel, sheet.failedCount.toString(), Modifier.weight(1f))
                        ResultStatChip(strings.passRateLabel, "${sheet.passPercentage}%", Modifier.weight(1f))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                val bytes = ReportExporter.buildClassExamResultPdfBytes(
                                    sheet = sheet,
                                    teacherProfile = teacherProfile,
                                    year = selectedYear,
                                    strings = strings
                                )
                                val safeCls = selectedClass.lowercase().replace(" ", "_")
                                ReportExporter.shareFile(
                                    context = context,
                                    fileName = "result_${safeCls}_${activeExam.examId}_$selectedYear.pdf",
                                    mimeType = "application/pdf",
                                    bytes = bytes,
                                    chooserTitle = strings.shareClassResultPdf
                                )
                            },
                            enabled = sheet.studentSummaries.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("class_result_pdf_button")
                        ) {
                            Icon(Icons.Rounded.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.shareClassResultPdf, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = {
                                val bytes = ReportExporter.buildClassExamResultCsvBytes(sheet)
                                val safeCls = selectedClass.lowercase().replace(" ", "_")
                                ReportExporter.shareFile(
                                    context = context,
                                    fileName = "result_${safeCls}_${activeExam.examId}_$selectedYear.csv",
                                    mimeType = "text/csv",
                                    bytes = bytes,
                                    chooserTitle = strings.exportClassResultCsv
                                )
                            },
                            enabled = sheet.studentSummaries.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("class_result_csv_button")
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.exportClassResultCsv, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        if (sheet.applicableSpecs.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "No Marksheet Subjects configured for ${strings.localizeClass(selectedClass)} in ${strings.localizeExamTitle(activeExam)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        FilledTonalButton(
                            onClick = onOpenExamSetup,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.subjectsAndExamsBtn)
                        }
                    }
                }
            }
        }

        items(sortedSummaries, key = { it.student.id }) { summary ->
            val stu = summary.student
            val outcomeColor = when (summary.outcome) {
                ExamPassOutcome.PASS -> MaterialTheme.colorScheme.primary
                ExamPassOutcome.FAIL -> MaterialTheme.colorScheme.error
                ExamPassOutcome.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val outcomeContainer = when (summary.outcome) {
                ExamPassOutcome.PASS -> MaterialTheme.colorScheme.primaryContainer
                ExamPassOutcome.FAIL -> MaterialTheme.colorScheme.errorContainer
                ExamPassOutcome.PENDING -> MaterialTheme.colorScheme.surfaceContainerHigh
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelectStudent(stu) }
                    .testTag("class_result_row_${stu.id}")
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = stu.rollNumber.ifBlank { "—" },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stu.primaryName(),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (summary.scoredCount > 0) {
                                    "${strings.resultSheetTotalRow}: ${ExamResultCalculator.formatMark(summary.totalObtained)}/${summary.scoredFullMarks} (${summary.percentage}%) • ${strings.resultSheetGpaLabel} ${ExamResultCalculator.formatGpa(summary.gpa)}"
                                } else {
                                    strings.resultPending
                                },
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                color = outcomeContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = strings.localizeOutcome(summary.outcome.name),
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = outcomeColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                            if (summary.isFinalExam && summary.meritPosition != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Rank ${ExamResultCalculator.ordinal(summary.meritPosition)}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (summary.subjectEvaluations.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            summary.subjectEvaluations.forEach { ev ->
                                val chipBg = when {
                                    !ev.hasScore -> MaterialTheme.colorScheme.surface
                                    ev.isPassed -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    else -> MaterialTheme.colorScheme.errorContainer
                                }
                                val chipFg = when {
                                    !ev.hasScore -> MaterialTheme.colorScheme.onSurfaceVariant
                                    ev.isPassed -> MaterialTheme.colorScheme.onSecondaryContainer
                                    else -> MaterialTheme.colorScheme.onErrorContainer
                                }
                                Surface(
                                    color = chipBg,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    val markStr = ev.obtained?.let { ExamResultCalculator.formatMark(it) } ?: "—"
                                    Text(
                                        text = "${ev.spec.subjectName}: $markStr/${ev.spec.fullMarks}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = chipFg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

@Composable
private fun ResultStatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
