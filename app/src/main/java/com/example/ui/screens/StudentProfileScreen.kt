package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.AttendanceLog
import com.example.data.model.CustomFieldDef
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamTermConfig
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.ui.components.FiveAngleRadarChart
import com.example.ui.components.HorizontalBarMetric
import com.example.ui.components.HorizontalMetricBarChart
import com.example.ui.components.PerformancePieChart
import com.example.ui.components.PieTierSlice
import com.example.ui.components.RadarAxisMetric
import com.example.ui.components.StudentAttendancePercentagePanel
import com.example.ui.components.StudentMarksheetPanel
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.util.StudentPhotoHelper

private enum class ProfileSection(val label: String) {
    DOSSIER("Full Profile"),
    ATTENDANCE("Attendance %"),
    MARKSHEET("Marksheet (3)")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentProfileScreen(
    student: StudentRecord,
    allStudents: List<StudentRecord>,
    subjects: List<SubjectDefinition>,
    customFields: List<CustomFieldDef>,
    attendanceLogs: List<AttendanceLog> = emptyList(),
    examConfigs: List<ExamTermConfig> = ExamDefaults.defaultThreeExams(),
    isAdmin: Boolean = true,
    isSaving: Boolean = false,
    onSelectOtherStudent: (StudentRecord) -> Unit,
    onEditStudent: () -> Unit,
    onUpdateStudentRecord: (StudentRecord, onSuccess: () -> Unit) -> Unit,
    onSaveExamConfigs: (List<ExamTermConfig>, onSuccess: () -> Unit) -> Unit,
    onExportStudent: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var activeSection by remember { mutableStateOf(ProfileSection.DOSSIER) }
    val context = LocalContext.current
    val photoBitmap = remember(student.photoBase64) {
        StudentPhotoHelper.decodeBase64ToImageBitmap(student.photoBase64)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = StudentPhotoHelper.compressUriToPassportBase64(context, uri)
            if (encoded != null) {
                onUpdateStudentRecord(student.copy(photoBase64 = encoded)) {}
            } else {
                Toast.makeText(context, "Could not process selected photo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentIndex = remember(student.id, allStudents) {
        allStudents.indexOfFirst { it.id == student.id }
    }
    val prevStudent = if (currentIndex > 0) allStudents[currentIndex - 1] else null
    val nextStudent = if (currentIndex in 0 until allStudents.lastIndex) allStudents[currentIndex + 1] else null

    val (filledCount, totalCount) = student.filledFieldProgress(customFields, subjects)
    val commentsCount = student.sectorCommentsCount()
    val attendancePct = student.attendancePercentage()
    val perfScore = student.overallPerformanceScore(subjects)

    val matchingSubjects = remember(subjects, student) {
        subjects.filter { sub ->
            sub.appliesToClass(student.classLevel) ||
                sub.fields.any { student.subjectValue(sub.id, it.fieldId).isNotBlank() }
        }
    }

    val radarAxes = remember(student, matchingSubjects) {
        val axes = mutableListOf<RadarAxisMetric>()
        axes += RadarAxisMetric("Attend", attendancePct)
        matchingSubjects.forEach { sub ->
            if (axes.size < 5) {
                axes += RadarAxisMetric(sub.name.take(8), student.subjectPercentage(sub) ?: 0)
            }
        }
        while (axes.size < 5) {
            val lbl = if (axes.size == 4) "Overall" else "Axis ${axes.size}"
            axes += RadarAxisMetric(lbl, if (axes.size == 4) perfScore else 0)
        }
        axes
    }

    val subjectBars = remember(student, matchingSubjects) {
        matchingSubjects.map { sub ->
            val pct = student.subjectPercentage(sub) ?: 0
            val filledInSub = sub.fields.count { student.subjectValue(sub.id, it.fieldId).isNotBlank() }
            HorizontalBarMetric(
                label = sub.name,
                percentage = pct,
                subtitle = "$filledInSub/${sub.fields.size} assessment field(s) recorded"
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = student.studentNameEn.ifBlank { "Student Profile" },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${student.classLevel} • Roll ${student.rollNumber} • Year ${student.academicYear}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("profile_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to Register")
                    }
                },
                actions = {
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
                        onClick = onExportStudent,
                        modifier = Modifier.testTag("profile_export_button")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Export or share student profile")
                    }
                    IconButton(
                        onClick = onEditStudent,
                        modifier = Modifier.testTag("profile_edit_button")
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit student details", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWide = maxWidth >= 600.dp

            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    painter = painterResource(id = R.drawable.img_simple_bg),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(0.32f),
                    contentScale = ContentScale.Crop
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Passport Photo + Student Identity Header Card
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                            ),
                            shape = RoundedCornerShape(22.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // 3:4 Passport-Size Photograph Box
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 96.dp, height = 128.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (photoBitmap != null) {
                                            Image(
                                                bitmap = photoBitmap,
                                                contentDescription = "${student.studentNameEn} passport photograph",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Person,
                                                    contentDescription = "No passport photo",
                                                    modifier = Modifier.size(36.dp),
                                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                                )
                                                Text(
                                                    text = "Passport\nPhoto",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                photoPickerLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier
                                                .height(34.dp)
                                                .testTag("profile_upload_photo_button")
                                        ) {
                                            Icon(Icons.Rounded.AddAPhoto, contentDescription = "Upload photo", modifier = Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (photoBitmap != null) "Change" else "Photo", style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (photoBitmap != null) {
                                            IconButton(
                                                onClick = { onUpdateStudentRecord(student.copy(photoBase64 = "")) {} },
                                                modifier = Modifier.size(34.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = "Remove photo",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // Name, Class, Key Metrics & Action Buttons
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = student.studentNameEn.ifBlank { "Unnamed Student" },
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    if (student.studentNameBn.isNotBlank()) {
                                        Text(
                                            text = student.studentNameBn,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                    Text(
                                        text = "${student.classLevel}${if (student.section.isNotBlank()) " (${student.section})" else ""} • Roll ${student.rollNumber} • ${student.academicYear}",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                Text("Overall", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    "$perfScore%",
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                Text("Attend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    "$attendancePct%",
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                        Surface(
                                            color = MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                Text("Fields", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text(
                                                    "$filledCount/$totalCount",
                                                    fontFamily = JetBrainsMonoFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.padding(top = 6.dp)
                                    ) {
                                        Button(
                                            onClick = onEditStudent,
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(44.dp)
                                                .testTag("profile_edit_full_button")
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Edit Details", maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = onExportStudent,
                                            modifier = Modifier
                                                .height(44.dp)
                                                .testTag("profile_share_button")
                                        ) {
                                            Icon(Icons.Rounded.Share, contentDescription = "Share/Export", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 2. Profile Mode Selector: Full Profile | Attendance % | Marksheet (3 Exams)
                    item {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            ProfileSection.entries.forEachIndexed { idx, sec ->
                                SegmentedButton(
                                    selected = activeSection == sec,
                                    onClick = { activeSection = sec },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = ProfileSection.entries.size
                                    ),
                                    icon = {
                                        when (sec) {
                                            ProfileSection.DOSSIER -> Unit
                                            ProfileSection.ATTENDANCE -> Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.size(16.dp))
                                            ProfileSection.MARKSHEET -> Icon(Icons.Rounded.Grade, contentDescription = null, modifier = Modifier.size(16.dp))
                                        }
                                    },
                                    modifier = Modifier.testTag("profile_section_${sec.name.lowercase()}")
                                ) {
                                    Text(sec.label, maxLines = 1)
                                }
                            }
                        }
                    }

                    when (activeSection) {
                        ProfileSection.ATTENDANCE -> {
                            item {
                                StudentAttendancePercentagePanel(
                                    student = student,
                                    attendanceLogs = attendanceLogs,
                                    isAdmin = isAdmin,
                                    isSaving = isSaving,
                                    onUpdateStudentDays = onUpdateStudentRecord
                                )
                            }
                        }

                        ProfileSection.MARKSHEET -> {
                            item {
                                StudentMarksheetPanel(
                                    student = student,
                                    allStudents = allStudents,
                                    examConfigs = examConfigs,
                                    schoolSubjects = subjects,
                                    isAdmin = isAdmin,
                                    isSaving = isSaving,
                                    onSaveStudentMarks = onUpdateStudentRecord,
                                    onSaveExamConfigs = onSaveExamConfigs
                                )
                            }
                        }

                        ProfileSection.DOSSIER -> {
                            // Quick jumps to Attendance % & 3-Exam Marksheet
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    FilledTonalButton(
                                        onClick = { activeSection = ProfileSection.ATTENDANCE },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("profile_quick_attendance_pct")
                                    ) {
                                        Icon(Icons.Rounded.EventAvailable, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Attendance: $attendancePct%", maxLines = 1)
                                    }
                                    FilledTonalButton(
                                        onClick = { activeSection = ProfileSection.MARKSHEET },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                            .testTag("profile_quick_marksheet")
                                    ) {
                                        Icon(Icons.Rounded.Grade, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("3-Exam Marksheet", maxLines = 1)
                                    }
                                }
                            }

                            // Graphical Performance Summary
                            item {
                                if (isWide) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        StudentRadarSectionCard(radarAxes, Modifier.weight(1f))
                                        StudentBreakdownPieBarCard(student, attendancePct, perfScore, subjectBars, Modifier.weight(1f))
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        StudentRadarSectionCard(radarAxes, Modifier.fillMaxWidth())
                                        StudentBreakdownPieBarCard(student, attendancePct, perfScore, subjectBars, Modifier.fillMaxWidth())
                                    }
                                }
                            }

                            // Fixed & Yearly Details
                            item {
                                if (isWide) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        FixedCoreDossierCard(student, Modifier.weight(1f))
                                        YearlyDossierCard(student, attendancePct, filledCount, totalCount, commentsCount, Modifier.weight(1f))
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        FixedCoreDossierCard(student, Modifier.fillMaxWidth())
                                        YearlyDossierCard(student, attendancePct, filledCount, totalCount, commentsCount, Modifier.fillMaxWidth())
                                    }
                                }
                            }

                            // Subjects & Assessment Fields
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Subjects & Assessment Details (${matchingSubjects.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (matchingSubjects.isEmpty()) {
                                            Text(
                                                text = "No subjects configured for ${student.classLevel} yet. Tap 'Edit Details' or open the Subjects menu to add subjects & assessment fields.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            matchingSubjects.forEach { subject ->
                                                val subPct = student.subjectPercentage(subject)
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surface,
                                                    shape = RoundedCornerShape(14.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = subject.name,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Surface(
                                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                                shape = RoundedCornerShape(8.dp)
                                                            ) {
                                                                Text(
                                                                    text = if (subPct != null) "$subPct%" else "Not Scored",
                                                                    fontFamily = JetBrainsMonoFontFamily,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }
                                                        if (subPct != null) {
                                                            LinearProgressIndicator(
                                                                progress = { (subPct / 100f).coerceIn(0f, 1f) },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(5.dp)
                                                                    .clip(RoundedCornerShape(3.dp))
                                                            )
                                                        }
                                                        if (subject.fields.isEmpty()) {
                                                            Text(
                                                                text = "No assessment fields created in ${subject.name}.",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        } else {
                                                            subject.fields.forEach { f ->
                                                                val raw = student.subjectValue(subject.id, f.fieldId).ifBlank { "—" }
                                                                val note = student.noteForKey("sf_${subject.id}__${f.fieldId}")
                                                                val displayVal = if (f.maxMarks > 0 && raw != "—") "$raw / ${f.maxMarks}" else raw
                                                                DossierDetailRow(
                                                                    label = f.label,
                                                                    value = displayVal,
                                                                    note = note
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

                            // Flexible Custom Category Fields
                            item {
                                val groupedCustom = remember(customFields) { customFields.groupBy { it.category } }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                    shape = RoundedCornerShape(18.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(
                                            text = "Flexible Category Fields (${customFields.size})",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (customFields.isEmpty()) {
                                            Text(
                                                text = "No extra flexible category fields added yet.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        } else {
                                            groupedCustom.forEach { (category, fields) ->
                                                Text(
                                                    text = category,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                fields.forEach { cf ->
                                                    DossierDetailRow(
                                                        label = cf.label,
                                                        value = student.customValue(cf.id).ifBlank { "—" },
                                                        note = student.noteForKey("cf_${cf.id}")
                                                    )
                                                }
                                                HorizontalDivider()
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
private fun StudentRadarSectionCard(
    radarAxes: List<RadarAxisMetric>,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Student-Wise 5-Angle Pentagon Graph",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            FiveAngleRadarChart(axes = radarAxes)
        }
    }
}

@Composable
private fun StudentBreakdownPieBarCard(
    student: StudentRecord,
    attendancePct: Int,
    perfScore: Int,
    subjectBars: List<HorizontalBarMetric>,
    modifier: Modifier = Modifier
) {
    val presentDays = student.daysPresent.toInt().coerceAtLeast(0)
    val absentDays = (student.totalSchoolDays - student.daysPresent).toInt().coerceAtLeast(0)
    val pieSlices = listOf(
        PieTierSlice("Present Days", presentDays, Color(0xFF006A4E)),
        PieTierSlice("Absent Days", absentDays, Color(0xFFB4432C))
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Attendance Pie & Subject Graph",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            PerformancePieChart(
                slices = pieSlices,
                centerValue = "$attendancePct%",
                centerLabel = "Attendance"
            )
            if (subjectBars.isNotEmpty()) {
                HorizontalDivider()
                HorizontalMetricBarChart(items = subjectBars)
            } else {
                Text(
                    text = "Overall Score: $perfScore% • Add subjects to view subject-by-subject bars.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FixedCoreDossierCard(
    student: StudentRecord,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Fixed Core Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            DossierDetailRow("Student Name (EN)", student.studentNameEn.ifBlank { "—" }, student.noteForKey("fixed_name_en"))
            DossierDetailRow("Student Name (BN)", student.studentNameBn.ifBlank { "—" }, student.noteForKey("fixed_name_bn"))
            DossierDetailRow("Father's Name", student.fatherNameEn.ifBlank { "—" }, student.noteForKey("fixed_father"))
            DossierDetailRow("Mother's Name", student.motherNameEn.ifBlank { "—" }, student.noteForKey("fixed_mother"))
            DossierDetailRow("Date of Birth", student.dateOfBirth.ifBlank { "—" }, student.noteForKey("fixed_dob"))
            DossierDetailRow("Phone Number", student.guardianPhone.ifBlank { "—" }, student.noteForKey("fixed_phone"))
            DossierDetailRow("Address", student.effectiveAddress().ifBlank { "—" }, student.noteForKey("fixed_address"))
        }
    }
}

@Composable
private fun YearlyDossierCard(
    student: StudentRecord,
    attendancePct: Int,
    filledCount: Int,
    totalCount: Int,
    commentsCount: Int,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Yearly Academic Details (${student.academicYear})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            DossierDetailRow("Academic Year", student.academicYear.toString(), student.noteForKey("yearly_year"))
            DossierDetailRow("Class Name", student.classLevel, student.noteForKey("yearly_class"))
            DossierDetailRow("Roll Number", student.rollNumber.ifBlank { "—" }, student.noteForKey("yearly_roll"))
            DossierDetailRow("Section", student.section.ifBlank { "—" }, student.noteForKey("yearly_section"))
            DossierDetailRow(
                "Attendance",
                if (student.totalSchoolDays > 0) "${student.daysPresent} / ${student.totalSchoolDays} days ($attendancePct%)" else "—",
                student.noteForKey("yearly_attendance")
            )
            DossierDetailRow("Promotion Status", student.promotionStatus.ifBlank { "—" }, student.noteForKey("yearly_promotion"))
            DossierDetailRow("Completeness", "$filledCount / $totalCount sectors • $commentsCount notes", "")
        }
    }
}

@Composable
private fun DossierDetailRow(
    label: String,
    value: String,
    note: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.45f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(0.55f)
            )
        }
        if (note.isNotBlank()) {
            Text(
                text = "Note: $note",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
