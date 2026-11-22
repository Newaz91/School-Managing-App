package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceCodes
import com.example.data.model.AttendanceLog
import com.example.data.model.ClassLevels
import com.example.data.model.StudentRecord
import com.example.ui.components.HorizontalBarMetric
import com.example.ui.components.HorizontalMetricBarChart
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private enum class AttendanceViewMode(val label: String) {
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    YEARLY("Yearly")
}

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

private val MONTH_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

private fun defaultDateKeyForYear(selectedYear: Int): String {
    val now = Calendar.getInstance()
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    return if (now.get(Calendar.YEAR) == selectedYear) {
        fmt.format(now.time)
    } else {
        String.format(Locale.ENGLISH, "%04d-01-01", selectedYear)
    }
}

private fun parseCalendarDate(dateKey: String, fallbackYear: Int): Calendar {
    val cal = Calendar.getInstance()
    val parts = dateKey.split("-")
    if (parts.size == 3) {
        val y = parts[0].toIntOrNull() ?: fallbackYear
        val m = ((parts[1].toIntOrNull() ?: 1) - 1).coerceIn(0, 11)
        val d = (parts[2].toIntOrNull() ?: 1).coerceIn(1, 31)
        cal.set(y, m, d, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
    } else {
        cal.set(fallbackYear, Calendar.JANUARY, 1, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
    }
    return cal
}

private fun sundayOfWeek(cal: Calendar): Calendar {
    val c = cal.clone() as Calendar
    val dow = c.get(Calendar.DAY_OF_WEEK) // Sun = 1 .. Sat = 7
    c.add(Calendar.DAY_OF_MONTH, -(dow - Calendar.SUNDAY))
    return c
}

private fun shiftDateKey(dateKey: String, deltaDays: Int, fallbackYear: Int): String {
    val cal = parseCalendarDate(dateKey, fallbackYear)
    cal.add(Calendar.DAY_OF_MONTH, deltaDays)
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    return fmt.format(cal.time)
}

private data class WeekDayMeta(
    val dateKey: String,
    val dayShort: String,
    val dayNum: String
)

private fun buildWeekDays(anchorDateKey: String, fallbackYear: Int): List<WeekDayMeta> {
    val sun = sundayOfWeek(parseCalendarDate(anchorDateKey, fallbackYear))
    val fmtKey = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
    val fmtDow = SimpleDateFormat("EEE", Locale.ENGLISH)
    val fmtDay = SimpleDateFormat("dd", Locale.ENGLISH)
    return (0 until 7).map { offset ->
        val c = sun.clone() as Calendar
        c.add(Calendar.DAY_OF_MONTH, offset)
        WeekDayMeta(
            dateKey = fmtKey.format(c.time),
            dayShort = fmtDow.format(c.time),
            dayNum = fmtDay.format(c.time)
        )
    }
}

@Composable
fun AttendanceSheetScreen(
    allStudents: List<StudentRecord>,
    attendanceLogsState: UiState<List<AttendanceLog>>,
    selectedYear: Int,
    isAdmin: Boolean,
    isSaving: Boolean,
    initialSelectedClass: String? = null,
    onSaveAttendanceSheet: (classLevel: String, dateKey: String, statuses: Map<String, String>, onSuccess: () -> Unit) -> Unit,
    onOpenStudentProfile: (StudentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedClass by remember(initialSelectedClass) {
        mutableStateOf(
            initialSelectedClass?.takeIf { it in ClassLevels.ALL }
                ?: ClassLevels.ALL.firstOrNull { cls -> allStudents.any { it.classLevel == cls } }
                ?: "Class 1"
        )
    }
    var viewMode by remember { mutableStateOf(AttendanceViewMode.WEEKLY) }
    var activeDateKey by remember(selectedYear) { mutableStateOf(defaultDateKeyForYear(selectedYear)) }
    var activeMonthIndex by remember(selectedYear) {
        val cal = parseCalendarDate(defaultDateKeyForYear(selectedYear), selectedYear)
        mutableIntStateOf(cal.get(Calendar.MONTH))
    }

    val allLogs = (attendanceLogsState as? UiState.Success)?.data.orEmpty()
    val classLogs = remember(allLogs, selectedClass, selectedYear) {
        allLogs.filter { it.classLevel == selectedClass && it.academicYear == selectedYear.toLong() }
    }
    val classLogsByDate = remember(classLogs) {
        classLogs.associateBy { it.dateKey }
    }
    val classStudents = remember(allStudents, selectedClass) {
        allStudents.filter { it.classLevel == selectedClass }
            .sortedWith(compareBy<StudentRecord> { it.rollSortKey() }.thenBy { it.rollNumber })
    }

    val draftStatuses = remember(selectedClass, activeDateKey) {
        mutableStateMapOf<String, String>()
    }

    // Sync draft from Firestore log whenever class, active date, or remote logs refresh
    LaunchedEffect(selectedClass, activeDateKey, classLogsByDate[activeDateKey]) {
        draftStatuses.clear()
        classLogsByDate[activeDateKey]?.statuses?.forEach { (stuId, code) ->
            draftStatuses[stuId] = code
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Class Separator Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ClassLevels.ALL.forEachIndexed { idx, cls ->
                val count = allStudents.count { it.classLevel == cls }
                FilterChip(
                    selected = selectedClass == cls,
                    onClick = { selectedClass = cls },
                    label = { Text("${ClassLevels.shortLabel(cls)} ($count)") },
                    modifier = Modifier.testTag("attendance_class_chip_$idx")
                )
            }
        }

        // Weekly / Monthly / Yearly View Segmented Bar
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            AttendanceViewMode.entries.forEachIndexed { idx, mode ->
                SegmentedButton(
                    selected = viewMode == mode,
                    onClick = { viewMode = mode },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = idx,
                        count = AttendanceViewMode.entries.size
                    ),
                    icon = {
                        Icon(
                            imageVector = when (mode) {
                                AttendanceViewMode.WEEKLY -> Icons.Rounded.DateRange
                                AttendanceViewMode.MONTHLY -> Icons.Rounded.CalendarMonth
                                AttendanceViewMode.YEARLY -> Icons.Rounded.EventAvailable
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("attendance_mode_${mode.name.lowercase()}")
                ) {
                    Text(mode.label, maxLines = 1)
                }
            }
        }

        // Role behavior pill
        Surface(
            color = if (isAdmin) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (isAdmin) {
                        "$selectedClass Attendance Sheet • Admin Mode (updates apply live)"
                    } else {
                        "$selectedClass Attendance Sheet • Submissions go to School Admin for confirmation"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (attendanceLogsState is UiState.Loading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (classStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No students enrolled in $selectedClass ($selectedYear) yet. Add students first to take class attendance.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Column
        }

        when (viewMode) {
            AttendanceViewMode.WEEKLY -> {
                WeeklyAttendanceView(
                    selectedClass = selectedClass,
                    selectedYear = selectedYear,
                    activeDateKey = activeDateKey,
                    classStudents = classStudents,
                    classLogsByDate = classLogsByDate,
                    draftStatuses = draftStatuses,
                    isAdmin = isAdmin,
                    isSaving = isSaving,
                    onSelectDate = { activeDateKey = it },
                    onShiftWeek = { deltaWeeks ->
                        activeDateKey = shiftDateKey(activeDateKey, deltaWeeks * 7, selectedYear)
                    },
                    onJumpToday = { activeDateKey = defaultDateKeyForYear(selectedYear) },
                    onSaveSheet = {
                        onSaveAttendanceSheet(selectedClass, activeDateKey, draftStatuses.toMap()) {}
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            AttendanceViewMode.MONTHLY -> {
                MonthlyAttendanceView(
                    selectedClass = selectedClass,
                    selectedYear = selectedYear,
                    activeMonthIndex = activeMonthIndex,
                    classStudents = classStudents,
                    classLogs = classLogs,
                    onSelectMonth = { activeMonthIndex = it },
                    onJumpToDateInWeekly = { dateKey ->
                        activeDateKey = dateKey
                        viewMode = AttendanceViewMode.WEEKLY
                    },
                    onOpenStudentProfile = onOpenStudentProfile,
                    modifier = Modifier.weight(1f)
                )
            }

            AttendanceViewMode.YEARLY -> {
                YearlyAttendanceView(
                    selectedClass = selectedClass,
                    selectedYear = selectedYear,
                    classStudents = classStudents,
                    classLogs = classLogs,
                    onOpenStudentProfile = onOpenStudentProfile,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun WeeklyAttendanceView(
    selectedClass: String,
    selectedYear: Int,
    activeDateKey: String,
    classStudents: List<StudentRecord>,
    classLogsByDate: Map<String, AttendanceLog>,
    draftStatuses: MutableMap<String, String>,
    isAdmin: Boolean,
    isSaving: Boolean,
    onSelectDate: (String) -> Unit,
    onShiftWeek: (Int) -> Unit,
    onJumpToday: () -> Unit,
    onSaveSheet: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weekDays = remember(activeDateKey, selectedYear) {
        buildWeekDays(activeDateKey, selectedYear)
    }

    val presentCount = classStudents.count { draftStatuses[it.id] == AttendanceCodes.PRESENT }
    val absentCount = classStudents.count { draftStatuses[it.id] == AttendanceCodes.ABSENT }
    val leaveCount = classStudents.count { draftStatuses[it.id] == AttendanceCodes.LEAVE }

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            IconButton(
                                onClick = { onShiftWeek(-1) },
                                modifier = Modifier.testTag("attendance_prev_week")
                            ) {
                                Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous week")
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${weekDays.first().dateKey} → ${weekDays.last().dateKey.takeLast(5)}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Selected Date: $activeDateKey",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = onJumpToday) {
                                    Text("Today")
                                }
                                IconButton(
                                    onClick = { onShiftWeek(1) },
                                    modifier = Modifier.testTag("attendance_next_week")
                                ) {
                                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Next week")
                                }
                            }
                        }

                        // 7-day selector strip
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            weekDays.forEach { wd ->
                                val selected = wd.dateKey == activeDateKey
                                val hasLog = classLogsByDate.containsKey(wd.dateKey)
                                Surface(
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        hasLog -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        else -> MaterialTheme.colorScheme.surface
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = if (selected) null else BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onSelectDate(wd.dateKey) }
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = wd.dayShort,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = wd.dayNum,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Summary & Batch Mark row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "P: $presentCount • A: $absentCount • L: $leaveCount",
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = {
                                        classStudents.forEach { s ->
                                            draftStatuses[s.id] = AttendanceCodes.PRESENT
                                        }
                                    },
                                    modifier = Modifier.testTag("attendance_mark_all_present")
                                ) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("All P")
                                }
                                TextButton(
                                    onClick = { draftStatuses.clear() }
                                ) {
                                    Icon(Icons.Rounded.Clear, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(2.dp))
                                    Text("Clear")
                                }
                            }
                        }
                    }
                }
            }

            items(classStudents, key = { it.id }) { student ->
                val currentStatus = draftStatuses[student.id].orEmpty()
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(16.dp)
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
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = student.rollNumber.ifBlank { "—" },
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.primaryName().ifBlank { "Unnamed Student" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                // Mini 7-day weekly status indicator row
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    weekDays.forEach { wd ->
                                        val code = if (wd.dateKey == activeDateKey) {
                                            currentStatus
                                        } else {
                                            classLogsByDate[wd.dateKey]?.statuses?.get(student.id).orEmpty()
                                        }
                                        val chipBg = when (code) {
                                            AttendanceCodes.PRESENT -> Color(0xFF006A4E).copy(alpha = 0.18f)
                                            AttendanceCodes.ABSENT -> Color(0xFFBA1A1A).copy(alpha = 0.18f)
                                            AttendanceCodes.LEAVE -> Color(0xFF916400).copy(alpha = 0.22f)
                                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        }
                                        val chipFg = when (code) {
                                            AttendanceCodes.PRESENT -> Color(0xFF006A4E)
                                            AttendanceCodes.ABSENT -> Color(0xFFBA1A1A)
                                            AttendanceCodes.LEAVE -> Color(0xFF916400)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Surface(
                                            color = chipBg,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = code.ifBlank { wd.dayShort.take(1) },
                                                fontFamily = JetBrainsMonoFontFamily,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = chipFg,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Tactile P / A / L chips for the active day
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AttendanceCodeButton(
                                    code = AttendanceCodes.PRESENT,
                                    selected = currentStatus == AttendanceCodes.PRESENT,
                                    activeColor = Color(0xFF006A4E),
                                    onClick = {
                                        if (currentStatus == AttendanceCodes.PRESENT) draftStatuses.remove(student.id)
                                        else draftStatuses[student.id] = AttendanceCodes.PRESENT
                                    }
                                )
                                AttendanceCodeButton(
                                    code = AttendanceCodes.ABSENT,
                                    selected = currentStatus == AttendanceCodes.ABSENT,
                                    activeColor = Color(0xFFBA1A1A),
                                    onClick = {
                                        if (currentStatus == AttendanceCodes.ABSENT) draftStatuses.remove(student.id)
                                        else draftStatuses[student.id] = AttendanceCodes.ABSENT
                                    }
                                )
                                AttendanceCodeButton(
                                    code = AttendanceCodes.LEAVE,
                                    selected = currentStatus == AttendanceCodes.LEAVE,
                                    activeColor = Color(0xFF916400),
                                    onClick = {
                                        if (currentStatus == AttendanceCodes.LEAVE) draftStatuses.remove(student.id)
                                        else draftStatuses[student.id] = AttendanceCodes.LEAVE
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Sticky Save Bar
        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Button(
                    onClick = onSaveSheet,
                    enabled = !isSaving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("attendance_save_button")
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
                        Text(
                            text = if (isAdmin) {
                                "Save $selectedClass Sheet ($activeDateKey)"
                            } else {
                                "Submit $selectedClass Sheet for Admin Approval"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttendanceCodeButton(
    code: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) activeColor else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) activeColor else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = code,
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun MonthlyAttendanceView(
    selectedClass: String,
    selectedYear: Int,
    activeMonthIndex: Int,
    classStudents: List<StudentRecord>,
    classLogs: List<AttendanceLog>,
    onSelectMonth: (Int) -> Unit,
    onJumpToDateInWeekly: (String) -> Unit,
    onOpenStudentProfile: (StudentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthKey = remember(selectedYear, activeMonthIndex) {
        String.format(Locale.ENGLISH, "%04d-%02d", selectedYear, activeMonthIndex + 1)
    }
    val monthLogs = remember(classLogs, monthKey) {
        classLogs.filter { it.monthKey == monthKey || it.dateKey.startsWith(monthKey) }
            .sortedBy { it.dateKey }
    }

    val monthlyRates = remember(classStudents, monthLogs) {
        classStudents.map { stu ->
            var p = 0
            var a = 0
            var l = 0
            monthLogs.forEach { log ->
                when (log.statuses[stu.id]) {
                    AttendanceCodes.PRESENT -> p++
                    AttendanceCodes.ABSENT -> a++
                    AttendanceCodes.LEAVE -> l++
                }
            }
            val total = p + a + l
            val pct = if (total > 0) ((p * 100.0) / total).roundToInt().coerceIn(0, 100) else 0
            Triple(stu, listOf(p, a, l, total), pct)
        }
    }
    val avgMonthlyPct = remember(monthlyRates) {
        val withMarks = monthlyRates.filter { it.second[3] > 0 }
        if (withMarks.isEmpty()) 0 else withMarks.map { it.third }.average().roundToInt()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MONTH_SHORT.forEachIndexed { idx, lbl ->
                            FilterChip(
                                selected = idx == activeMonthIndex,
                                onClick = { onSelectMonth(idx) },
                                label = { Text(lbl) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${MONTH_NAMES[activeMonthIndex]} $selectedYear • $selectedClass",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${monthLogs.size} school day(s) recorded this month",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$avgMonthlyPct%",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (monthLogs.isNotEmpty()) {
                        Text(
                            text = "Recorded Dates (Tap to edit in Weekly View):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            monthLogs.forEach { log ->
                                FilterChip(
                                    selected = false,
                                    onClick = { onJumpToDateInWeekly(log.dateKey) },
                                    label = {
                                        Text(
                                            log.dateKey.takeLast(5),
                                            fontFamily = JetBrainsMonoFontFamily
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        items(monthlyRates, key = { it.first.id }) { (student, counts, pct) ->
            val p = counts[0]
            val a = counts[1]
            val l = counts[2]
            val total = counts[3]
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenStudentProfile(student) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${student.rollNumber}. ${student.primaryName()}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (total > 0) "$pct%" else "—",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Text(
                        text = if (total > 0) {
                            "Present: $p • Absent: $a • Leave: $l (out of $total marked days)"
                        } else {
                            "No attendance marked in ${MONTH_NAMES[activeMonthIndex]} yet"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun YearlyAttendanceView(
    selectedClass: String,
    selectedYear: Int,
    classStudents: List<StudentRecord>,
    classLogs: List<AttendanceLog>,
    onOpenStudentProfile: (StudentRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val monthlyBars = remember(classLogs, selectedYear) {
        MONTH_SHORT.mapIndexedNotNull { idx, shortName ->
            val mKey = String.format(Locale.ENGLISH, "%04d-%02d", selectedYear, idx + 1)
            val mLogs = classLogs.filter { it.monthKey == mKey || it.dateKey.startsWith(mKey) }
            if (mLogs.isEmpty()) return@mapIndexedNotNull null
            var p = 0
            var total = 0
            mLogs.forEach { log ->
                log.statuses.values.forEach { st ->
                    if (st.isNotBlank()) {
                        total++
                        if (st == AttendanceCodes.PRESENT) p++
                    }
                }
            }
            val pct = if (total > 0) ((p * 100.0) / total).roundToInt().coerceIn(0, 100) else 0
            HorizontalBarMetric(
                label = "${MONTH_NAMES[idx]} $selectedYear",
                percentage = pct,
                subtitle = "${mLogs.size} school day(s) logged"
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$selectedClass • Full Year $selectedYear Attendance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${classLogs.size} total school day sheet(s) recorded across $selectedYear for $selectedClass.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (monthlyBars.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Month-by-Month $selectedClass Attendance %",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        HorizontalMetricBarChart(items = monthlyBars)
                    }
                }
            }
        }

        item {
            HorizontalDivider()
            Text(
                text = "Student-Wise Yearly Attendance (${classStudents.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        items(classStudents, key = { it.id }) { student ->
            var present = 0
            var absent = 0
            var leave = 0
            classLogs.forEach { log ->
                when (log.statuses[student.id]) {
                    AttendanceCodes.PRESENT -> present++
                    AttendanceCodes.ABSENT -> absent++
                    AttendanceCodes.LEAVE -> leave++
                }
            }
            val loggedTotal = present + absent + leave
            val effectivePresent = if (loggedTotal > 0) present else student.daysPresent.toInt()
            val effectiveTotal = if (loggedTotal > 0) loggedTotal else student.totalSchoolDays.toInt()
            val yearlyPct = if (effectiveTotal > 0) {
                ((effectivePresent * 100.0) / effectiveTotal).roundToInt().coerceIn(0, 100)
            } else 0

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onOpenStudentProfile(student) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = student.rollNumber.ifBlank { "—" },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = student.primaryName().ifBlank { "Unnamed" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (effectiveTotal > 0) {
                                "$effectivePresent / $effectiveTotal days • Absent: $absent • Leave: $leave"
                            } else {
                                "No attendance recorded yet"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        color = if (yearlyPct >= 75) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "$yearlyPct%",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (yearlyPct >= 75) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
