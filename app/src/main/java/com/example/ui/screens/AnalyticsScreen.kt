package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Class
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassLevels
import com.example.data.model.CustomFieldTypes
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.ui.components.ClassBarMetric
import com.example.ui.components.ClassComparisonBarChart
import com.example.ui.components.EmptyRegisterState
import com.example.ui.components.FiveAngleRadarChart
import com.example.ui.components.HorizontalBarMetric
import com.example.ui.components.HorizontalMetricBarChart
import com.example.ui.components.PerformancePieChart
import com.example.ui.components.PieTierSlice
import com.example.ui.components.RadarAxisMetric
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState
import kotlin.math.roundToInt

private enum class AnalyticsViewMode(val label: String) {
    STUDENT_WISE("Student"),
    CLASS_SUBJECT("Class Subject"),
    SCHOOL_SUBJECT("School Subject")
}

@Composable
fun AnalyticsScreen(
    studentsState: UiState<List<StudentRecord>>,
    subjects: List<SubjectDefinition>,
    selectedYear: Int,
    onOpenExportSheet: (List<StudentRecord>) -> Unit,
    onOpenStudentProfile: (StudentRecord) -> Unit,
    onAddStudent: () -> Unit,
    modifier: Modifier = Modifier
) {
    var mode by remember { mutableStateOf(AnalyticsViewMode.SCHOOL_SUBJECT) }
    var selectedClass by remember { mutableStateOf("Class 5") }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var studentSearch by remember { mutableStateOf("") }
    var selectedStudentId by remember { mutableStateOf<String?>(null) }

    when (studentsState) {
        is UiState.Loading -> {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is UiState.Error -> {
            Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(studentsState.message, color = MaterialTheme.colorScheme.error)
            }
        }

        is UiState.Success -> {
            val allStudents = studentsState.data
            if (allStudents.isEmpty()) {
                EmptyRegisterState(
                    academicYear = selectedYear,
                    hasActiveFilter = false,
                    onAddStudent = onAddStudent,
                    onClearFilters = {}
                )
                return
            }

            BoxWithConstraints(modifier = modifier.fillMaxSize()) {
                val isWide = maxWidth >= 600.dp

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Mode Switcher: Student-wise | Class & Subject-wise | Whole School & Subject-wise
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Graphical Performance Analytics",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Student-wise, Class-wise Subject & Whole School Subject Graphs ($selectedYear)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                AnalyticsViewMode.entries.forEachIndexed { idx, option ->
                                    SegmentedButton(
                                        selected = mode == option,
                                        onClick = { mode = option },
                                        shape = SegmentedButtonDefaults.itemShape(idx, AnalyticsViewMode.entries.size),
                                        icon = {
                                            Icon(
                                                imageVector = when (option) {
                                                    AnalyticsViewMode.STUDENT_WISE -> Icons.Rounded.Person
                                                    AnalyticsViewMode.CLASS_SUBJECT -> Icons.Rounded.Class
                                                    AnalyticsViewMode.SCHOOL_SUBJECT -> Icons.Rounded.AccountBalance
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("analytics_mode_${option.name.lowercase()}")
                                    ) {
                                        Text(option.label, maxLines = 1)
                                    }
                                }
                            }
                        }
                    }

                    when (mode) {
                        // ─────────────────────────── 1. STUDENT-WISE GRAPHICAL PERFORMANCE ───────────────────────────
                        AnalyticsViewMode.STUDENT_WISE -> {
                            val matching = allStudents.filter {
                                val q = studentSearch.trim().lowercase()
                                q.isBlank() || it.studentNameEn.lowercase().contains(q) ||
                                    it.rollNumber.lowercase().contains(q) ||
                                    it.classLevel.lowercase().contains(q)
                            }
                            val activeStudent = allStudents.firstOrNull { it.id == selectedStudentId }
                                ?: matching.firstOrNull()
                                ?: allStudents.first()

                            val stuSubjects = subjects.filter {
                                it.appliesToClass(activeStudent.classLevel) ||
                                    it.fields.any { f -> activeStudent.subjectValue(it.id, f.fieldId).isNotBlank() }
                            }
                            val attPct = activeStudent.attendancePercentage()
                            val overallPct = activeStudent.overallPerformanceScore(subjects)
                            val radarAxes = buildStudentAxes(activeStudent, stuSubjects, attPct, overallPct)
                            val subjectBars = stuSubjects.map { sub ->
                                HorizontalBarMetric(
                                    label = sub.name,
                                    percentage = activeStudent.subjectPercentage(sub) ?: 0,
                                    subtitle = "${sub.fields.count { activeStudent.subjectValue(sub.id, it.fieldId).isNotBlank() }}/${sub.fields.size} fields scored"
                                )
                            }
                            val stuPie = listOf(
                                PieTierSlice("Present", activeStudent.daysPresent.toInt().coerceAtLeast(0), Color(0xFF006A4E)),
                                PieTierSlice("Absent", (activeStudent.totalSchoolDays - activeStudent.daysPresent).toInt().coerceAtLeast(0), Color(0xFFB4432C))
                            )

                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    shape = RoundedCornerShape(18.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = studentSearch,
                                            onValueChange = { studentSearch = it },
                                            label = { Text("Select Student (search name, roll, or class)") },
                                            singleLine = true,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("analytics_student_search")
                                        )
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            matching.take(30).forEach { s ->
                                                FilterChip(
                                                    selected = activeStudent.id == s.id,
                                                    onClick = { selectedStudentId = s.id },
                                                    label = { Text("${s.studentNameEn} (${ClassLevels.shortLabel(s.classLevel)}-${s.rollNumber})") }
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = { onOpenStudentProfile(activeStudent) },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(46.dp)
                                            ) {
                                                Text("Open Full Single-Page Profile")
                                            }
                                            Button(
                                                onClick = { onOpenExportSheet(listOf(activeStudent)) },
                                                modifier = Modifier.height(46.dp)
                                            ) {
                                                Icon(Icons.Rounded.Share, contentDescription = "Share Student Report", modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                ChartGridSection(
                                    isWide = isWide,
                                    firstCard = { mod ->
                                        ChartCard("Student 5-Angle Pentagon (${activeStudent.studentNameEn})", mod) {
                                            FiveAngleRadarChart(axes = radarAxes)
                                        }
                                    },
                                    secondCard = { mod ->
                                        ChartCard("Student Attendance & Score Pie", mod) {
                                            PerformancePieChart(
                                                slices = stuPie,
                                                centerValue = "$overallPct%",
                                                centerLabel = "Overall"
                                            )
                                        }
                                    }
                                )
                            }

                            item {
                                ChartCard("Student Subject-Wise Score Graph", Modifier.fillMaxWidth()) {
                                    if (subjectBars.isEmpty()) {
                                        Text(
                                            text = "No subjects configured for ${activeStudent.classLevel} yet. Open the Subjects menu to define subjects & fields.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    } else {
                                        HorizontalMetricBarChart(items = subjectBars)
                                    }
                                }
                            }
                        }

                        // ─────────────────────────── 2. INDIVIDUAL CLASS-WISE & SUBJECT-WISE ───────────────────────────
                        AnalyticsViewMode.CLASS_SUBJECT -> {
                            val classStudents = allStudents.filter { it.classLevel == selectedClass }
                            val classSubjects = subjects.filter { it.appliesToClass(selectedClass) }
                            val activeSubject = classSubjects.firstOrNull { it.id == selectedSubjectId }
                                ?: classSubjects.firstOrNull()

                            val subjectBars = classSubjects.map { sub ->
                                val scores = classStudents.mapNotNull { it.subjectPercentage(sub) }
                                val avg = if (scores.isEmpty()) 0 else scores.average().roundToInt()
                                HorizontalBarMetric(
                                    label = sub.name,
                                    percentage = avg,
                                    subtitle = "${scores.size}/${classStudents.size} students assessed • ${sub.fields.size} field(s)"
                                )
                            }

                            val fieldBars = activeSubject?.fields?.map { field ->
                                val pcts = classStudents.mapNotNull { s ->
                                    subjectFieldPct(s.subjectValue(activeSubject.id, field.fieldId), field.fieldType, field.maxMarks)
                                }
                                val avg = if (pcts.isEmpty()) 0 else pcts.average().roundToInt()
                                HorizontalBarMetric(
                                    label = field.label,
                                    percentage = avg,
                                    subtitle = "${pcts.size}/${classStudents.size} assessed"
                                )
                            }.orEmpty()

                            val classAtt = classStudents.filter { it.totalSchoolDays > 0 }
                                .let { if (it.isEmpty()) 0 else it.map { s -> s.attendancePercentage() }.average().roundToInt() }

                            val classRadar = buildGroupRadarAxes(classStudents, classSubjects, classAtt)
                            val classPie = buildTierSlices(classStudents, classSubjects, activeSubject)
                            val classAvgCenter = if (activeSubject != null) {
                                val list = classStudents.mapNotNull { it.subjectPercentage(activeSubject) }
                                if (list.isEmpty()) 0 else list.average().roundToInt()
                            } else {
                                val list = classStudents.map { it.overallPerformanceScore(subjects) }.filter { it > 0 }
                                if (list.isEmpty()) 0 else list.average().roundToInt()
                            }

                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Select Class:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
                                                modifier = Modifier.testTag("analytics_class_chip_${cls.lowercase().replace(" ", "_")}")
                                            )
                                        }
                                    }

                                    if (classSubjects.isNotEmpty()) {
                                        Text("Inspect Subject Assessment Breakdown:", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            classSubjects.forEach { sub ->
                                                FilterChip(
                                                    selected = activeSubject?.id == sub.id,
                                                    onClick = { selectedSubjectId = sub.id },
                                                    leadingIcon = { Icon(Icons.Rounded.MenuBook, contentDescription = null, modifier = Modifier.size(15.dp)) },
                                                    label = { Text(sub.name) }
                                                )
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = { onOpenExportSheet(classStudents.ifEmpty { allStudents }) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("analytics_export_share_button")
                                    ) {
                                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Export / Share $selectedClass (${classStudents.size} Students)")
                                    }
                                }
                            }

                            item {
                                ChartGridSection(
                                    isWide = isWide,
                                    firstCard = { mod ->
                                        ChartCard("$selectedClass 5-Angle Subject Pentagon", mod) {
                                            FiveAngleRadarChart(axes = classRadar)
                                        }
                                    },
                                    secondCard = { mod ->
                                        ChartCard(
                                            title = if (activeSubject != null) "$selectedClass — ${activeSubject.name} Pie" else "$selectedClass Performance Pie",
                                            modifier = mod
                                        ) {
                                            PerformancePieChart(
                                                slices = classPie,
                                                centerValue = "$classAvgCenter%",
                                                centerLabel = activeSubject?.name?.take(10) ?: "Class Avg"
                                            )
                                        }
                                    }
                                )
                            }

                            item {
                                ChartGridSection(
                                    isWide = isWide,
                                    firstCard = { mod ->
                                        ChartCard("All Subjects in $selectedClass (Bar Graph)", mod) {
                                            if (subjectBars.isEmpty()) {
                                                Text(
                                                    text = "No subjects configured for $selectedClass yet.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                HorizontalMetricBarChart(items = subjectBars)
                                            }
                                        }
                                    },
                                    secondCard = { mod ->
                                        ChartCard(
                                            title = if (activeSubject != null) "${activeSubject.name} Field-Wise Graph ($selectedClass)" else "Field-Wise Breakdown",
                                            modifier = mod
                                        ) {
                                            if (fieldBars.isEmpty()) {
                                                Text(
                                                    text = "Add assessment fields inside a subject to view per-skill bar graphs.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                HorizontalMetricBarChart(
                                                    items = fieldBars,
                                                    barColor = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        // ─────────────────────────── 3. WHOLE SCHOOL-WISE & SUBJECT-WISE ───────────────────────────
                        AnalyticsViewMode.SCHOOL_SUBJECT -> {
                            val schoolAtt = allStudents.filter { it.totalSchoolDays > 0 }
                                .let { if (it.isEmpty()) 0 else it.map { s -> s.attendancePercentage() }.average().roundToInt() }
                            val schoolAvgPerf = allStudents.map { it.overallPerformanceScore(subjects) }.filter { it > 0 }
                                .let { if (it.isEmpty()) 0 else it.average().roundToInt() }

                            val schoolRadar = buildGroupRadarAxes(allStudents, subjects, schoolAtt)
                            val schoolPie = buildTierSlices(allStudents, subjects, null)

                            val classBars = ClassLevels.ALL.map { cls ->
                                val clsList = allStudents.filter { it.classLevel == cls }
                                val att = clsList.filter { it.totalSchoolDays > 0 }
                                    .let { if (it.isEmpty()) 0 else it.map { s -> s.attendancePercentage() }.average().roundToInt() }
                                val perf = clsList.map { it.overallPerformanceScore(subjects) }.filter { it > 0 }
                                    .let { if (it.isEmpty()) 0 else it.average().roundToInt() }
                                ClassBarMetric(ClassLevels.shortLabel(cls), att, perf)
                            }

                            // Group all school subjects by subject name so e.g. "Bangla" across classes combines cleanly!
                            val schoolSubjectBars = subjects
                                .groupBy { it.name.trim() }
                                .map { (subName, subDefs) ->
                                    val matchingScores = allStudents.mapNotNull { stu ->
                                        val matchingDef = subDefs.firstOrNull { it.appliesToClass(stu.classLevel) }
                                            ?: subDefs.firstOrNull()
                                        matchingDef?.let { stu.subjectPercentage(it) }
                                    }
                                    val avg = if (matchingScores.isEmpty()) 0 else matchingScores.average().roundToInt()
                                    HorizontalBarMetric(
                                        label = subName,
                                        percentage = avg,
                                        subtitle = "Across ${matchingScores.size} assessed student(s) school-wide"
                                    )
                                }

                            item {
                                Button(
                                    onClick = { onOpenExportSheet(allStudents) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("analytics_export_share_button")
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Export / Share Whole School (${allStudents.size} Students)")
                                }
                            }

                            item {
                                ChartGridSection(
                                    isWide = isWide,
                                    firstCard = { mod ->
                                        ChartCard("Whole School 5-Angle Pentagon", mod) {
                                            FiveAngleRadarChart(axes = schoolRadar)
                                        }
                                    },
                                    secondCard = { mod ->
                                        ChartCard("Whole School Performance Tier Pie", mod) {
                                            PerformancePieChart(
                                                slices = schoolPie,
                                                centerValue = "$schoolAvgPerf%",
                                                centerLabel = "School Avg"
                                            )
                                        }
                                    }
                                )
                            }

                            item {
                                ChartGridSection(
                                    isWide = isWide,
                                    firstCard = { mod ->
                                        ChartCard("Whole School Subject-Wise Graph", mod) {
                                            if (schoolSubjectBars.isEmpty()) {
                                                Text(
                                                    text = "No school subjects configured yet. Add subjects in the Subjects menu to compare every subject school-wide.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            } else {
                                                HorizontalMetricBarChart(items = schoolSubjectBars)
                                            }
                                        }
                                    },
                                    secondCard = { mod ->
                                        ChartCard("Class-by-Class Attendance vs Score", mod) {
                                            ClassComparisonBarChart(bars = classBars)
                                        }
                                    }
                                )
                            }

                            item {
                                val top5 = allStudents.sortedByDescending { it.overallPerformanceScore(subjects) }.take(5)
                                ChartCard("Top Performers (Whole School)", Modifier.fillMaxWidth()) {
                                    top5.forEachIndexed { idx, s ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${idx + 1}. ${s.studentNameEn} (${s.classLevel}, Roll ${s.rollNumber})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "${s.overallPerformanceScore(subjects)}%",
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
        }
    }
}

@Composable
private fun ChartGridSection(
    isWide: Boolean,
    firstCard: @Composable (Modifier) -> Unit,
    secondCard: @Composable (Modifier) -> Unit
) {
    if (isWide) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            firstCard(Modifier.weight(1f))
            secondCard(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            firstCard(Modifier.fillMaxWidth())
            secondCard(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun subjectFieldPct(rawVal: String, fieldType: String, maxMarks: Long): Double? {
    val clean = rawVal.trim()
    if (clean.isBlank()) return null
    return when (fieldType) {
        CustomFieldTypes.NUMBER -> {
            val raw = clean.toDoubleOrNull() ?: return null
            val max = if (maxMarks > 0L) maxMarks.toDouble() else 100.0
            ((raw / max) * 100.0).coerceIn(0.0, 100.0)
        }
        CustomFieldTypes.LEVEL -> when (clean) {
            "Mastered" -> 100.0
            "Good" -> 75.0
            "Average" -> 50.0
            "Needs Help" -> 25.0
            else -> null
        }
        CustomFieldTypes.YES_NO -> when (clean) {
            "Yes" -> 100.0
            "No" -> 0.0
            else -> null
        }
        else -> null
    }
}

private fun buildStudentAxes(
    student: StudentRecord,
    subjects: List<SubjectDefinition>,
    attPct: Int,
    overallPct: Int
): List<RadarAxisMetric> {
    val axes = mutableListOf<RadarAxisMetric>()
    axes += RadarAxisMetric("Attend", attPct)
    subjects.forEach { sub ->
        if (axes.size < 5) {
            axes += RadarAxisMetric(sub.name.take(8), student.subjectPercentage(sub) ?: 0)
        }
    }
    while (axes.size < 5) {
        axes += RadarAxisMetric(if (axes.size == 4) "Overall" else "Subj ${axes.size}", if (axes.size == 4) overallPct else 0)
    }
    return axes
}

private fun buildGroupRadarAxes(
    students: List<StudentRecord>,
    subjects: List<SubjectDefinition>,
    avgAtt: Int
): List<RadarAxisMetric> {
    val subjectMetrics = subjects.distinctBy { it.name.trim() }.take(4).map { sub ->
        val pcts = students.mapNotNull { it.subjectPercentage(sub) }
        val avg = if (pcts.isEmpty()) 0 else pcts.average().roundToInt()
        RadarAxisMetric(sub.name.take(9), avg)
    }
    val overall = students.map { it.overallPerformanceScore(subjects) }.filter { it > 0 }
        .let { if (it.isEmpty()) 0 else it.average().roundToInt() }

    return buildList {
        add(RadarAxisMetric("Attend", avgAtt))
        addAll(subjectMetrics)
        while (size < 5) {
            add(RadarAxisMetric(if (size == 4) "Overall" else "Axis $size", overall))
        }
    }.take(5)
}

private fun buildTierSlices(
    students: List<StudentRecord>,
    subjects: List<SubjectDefinition>,
    specificSubject: SubjectDefinition?
): List<PieTierSlice> {
    val scores = students.map { s ->
        if (specificSubject != null) s.subjectPercentage(specificSubject) ?: 0
        else s.overallPerformanceScore(subjects)
    }
    return listOf(
        PieTierSlice("Excellent (80%+)", scores.count { it >= 80 }, Color(0xFF006A4E)),
        PieTierSlice("Good (60–79%)", scores.count { it in 60..79 }, Color(0xFF2E8B6E)),
        PieTierSlice("Average (40–59%)", scores.count { it in 40..59 }, Color(0xFFD99B00)),
        PieTierSlice("Needs Help (<40%)", scores.count { it in 1..39 }, Color(0xFFB4432C)),
        PieTierSlice("Not Assessed", scores.count { it == 0 }, Color(0xFF9BA49E))
    )
}
