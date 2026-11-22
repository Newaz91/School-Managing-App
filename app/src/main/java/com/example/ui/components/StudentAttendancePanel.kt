package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AttendanceCodes
import com.example.data.model.AttendanceLog
import com.example.data.model.StudentRecord
import com.example.ui.theme.JetBrainsMonoFontFamily
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

@Composable
fun StudentAttendancePercentagePanel(
    student: StudentRecord,
    attendanceLogs: List<AttendanceLog>,
    isAdmin: Boolean,
    isSaving: Boolean,
    onUpdateStudentDays: (StudentRecord, onSuccess: () -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdjustDialog by remember { mutableStateOf(false) }

    val studentLogs = remember(attendanceLogs, student.id, student.classLevel, student.academicYear) {
        attendanceLogs.filter {
            it.classLevel == student.classLevel &&
                it.academicYear == student.academicYear &&
                !it.statuses[student.id].isNullOrBlank()
        }
    }

    val yearlyPresent = remember(studentLogs, student.daysPresent) {
        if (studentLogs.isNotEmpty()) {
            studentLogs.count { it.statuses[student.id] == AttendanceCodes.PRESENT }
        } else {
            student.daysPresent.toInt()
        }
    }
    val yearlyAbsent = remember(studentLogs, student.totalSchoolDays, student.daysPresent) {
        if (studentLogs.isNotEmpty()) {
            studentLogs.count { it.statuses[student.id] == AttendanceCodes.ABSENT }
        } else {
            (student.totalSchoolDays - student.daysPresent).toInt().coerceAtLeast(0)
        }
    }
    val yearlyLeave = remember(studentLogs) {
        studentLogs.count { it.statuses[student.id] == AttendanceCodes.LEAVE }
    }
    val yearlyTotal = remember(studentLogs, student.totalSchoolDays) {
        if (studentLogs.isNotEmpty()) studentLogs.size else student.totalSchoolDays.toInt()
    }
    val yearlyPct = if (yearlyTotal > 0) ((yearlyPresent * 100.0) / yearlyTotal).roundToInt().coerceIn(0, 100) else 0

    // Compute current month & week keys
    val cal = remember { Calendar.getInstance() }
    val currentMonthKey = remember(student.academicYear) {
        val m = cal.get(Calendar.MONTH) + 1
        String.format(Locale.ENGLISH, "%04d-%02d", student.academicYear, m)
    }
    val currentWeekDateKeys = remember {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_MONTH, -(c.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY))
        (0 until 7).map { offset ->
            val d = c.clone() as Calendar
            d.add(Calendar.DAY_OF_MONTH, offset)
            fmt.format(d.time)
        }.toSet()
    }

    val monthLogs = studentLogs.filter { it.monthKey == currentMonthKey || it.dateKey.startsWith(currentMonthKey) }
    val monthPct = if (monthLogs.isNotEmpty()) {
        val mp = monthLogs.count { it.statuses[student.id] == AttendanceCodes.PRESENT }
        ((mp * 100.0) / monthLogs.size).roundToInt().coerceIn(0, 100)
    } else null

    val weekLogs = studentLogs.filter { it.dateKey in currentWeekDateKeys }
    val weekPct = if (weekLogs.isNotEmpty()) {
        val wp = weekLogs.count { it.statuses[student.id] == AttendanceCodes.PRESENT }
        ((wp * 100.0) / weekLogs.size).roundToInt().coerceIn(0, 100)
    } else null

    val monthlyBars = remember(studentLogs, student.academicYear) {
        MONTHS_SHORT.mapIndexedNotNull { idx, shortLbl ->
            val mKey = String.format(Locale.ENGLISH, "%04d-%02d", student.academicYear, idx + 1)
            val mList = studentLogs.filter { it.monthKey == mKey || it.dateKey.startsWith(mKey) }
            if (mList.isEmpty()) return@mapIndexedNotNull null
            val p = mList.count { it.statuses[student.id] == AttendanceCodes.PRESENT }
            val pct = ((p * 100.0) / mList.size).roundToInt().coerceIn(0, 100)
            HorizontalBarMetric(
                label = "$shortLbl ${student.academicYear}",
                percentage = pct,
                subtitle = "$p / ${mList.size} day(s) present"
            )
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                            imageVector = Icons.Rounded.EventAvailable,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Attendance Percentage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    OutlinedButton(
                        onClick = { showAdjustDialog = true },
                        modifier = Modifier.testTag("profile_adjust_attendance_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Adjust")
                    }
                }

                // Weekly / Monthly / Yearly Summary Tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Yearly %", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "$yearlyPct%",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$yearlyPresent/$yearlyTotal days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("This Month", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = monthPct?.let { "$it%" } ?: "—",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = "${monthLogs.size} day(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("This Week", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = weekPct?.let { "$it%" } ?: "—",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = "${weekLogs.size} day(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                PerformancePieChart(
                    slices = listOf(
                        PieTierSlice("Present", yearlyPresent, Color(0xFF006A4E)),
                        PieTierSlice("Absent", yearlyAbsent, Color(0xFFB4432C)),
                        PieTierSlice("Leave", yearlyLeave, Color(0xFF916400))
                    ),
                    centerValue = "$yearlyPct%",
                    centerLabel = "Attendance"
                )
            }
        }

        if (monthlyBars.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Monthly Attendance Breakdown (${student.academicYear})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalMetricBarChart(items = monthlyBars)
                }
            }
        }
    }

    if (showAdjustDialog) {
        var presentInput by remember(student.id) { mutableStateOf(student.daysPresent.toString()) }
        var totalInput by remember(student.id) { mutableStateOf(student.totalSchoolDays.toString()) }

        AlertDialog(
            onDismissRequest = { showAdjustDialog = false },
            title = { Text("Adjust Attendance Totals") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Update ${student.primaryName()}'s cumulative present & working school days for ${student.academicYear}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = presentInput,
                        onValueChange = { presentInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Days Present") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = totalInput,
                        onValueChange = { totalInput = it.filter { c -> c.isDigit() } },
                        label = { Text("Total Working School Days") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val total = (totalInput.toLongOrNull() ?: 0L).coerceIn(0L, 366L)
                        val present = (presentInput.toLongOrNull() ?: 0L).coerceIn(0L, total.coerceAtLeast(366L))
                        onUpdateStudentDays(
                            student.copy(
                                totalSchoolDays = total,
                                daysPresent = if (total > 0L) present.coerceAtMost(total) else present
                            )
                        ) {
                            showAdjustDialog = false
                        }
                    },
                    enabled = !isSaving
                ) {
                    Text(if (isAdmin) "Save" else "Submit for Approval")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdjustDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
