package com.example

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Approval
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.firebase.SchoolRepository
import com.example.data.local.AppPreferences
import com.example.data.model.ExamDefaults
import com.example.data.model.StudentRecord
import com.example.export.AppShareHelper
import com.example.export.BackupParseResult
import com.example.export.ExcelBackupManager
import com.example.export.ExportTargetFormat
import com.example.export.ReportExporter
import com.example.export.ShareAppDialog
import com.example.ui.components.ExportShareBottomSheet
import com.example.ui.screens.AnalyticsScreen
import com.example.ui.screens.ApprovalsScreen
import com.example.ui.screens.AttendanceSheetScreen
import com.example.ui.screens.ConnectScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FieldSchemaManagerScreen
import com.example.ui.screens.PromotionRollManagerScreen
import com.example.ui.screens.RegisterScreen
import com.example.ui.screens.SchoolEntryScreen
import com.example.ui.screens.SchoolSettingsScreen
import com.example.ui.screens.StudentEditorScreen
import com.example.ui.screens.StudentProfileScreen
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.ShikkhaKhataTheme
import com.example.ui.viewmodel.SchoolViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.launch
import java.util.Calendar

enum class MainTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    DASHBOARD("Overview", Icons.Rounded.Dashboard, Icons.Outlined.Dashboard),
    REGISTER("Students", Icons.Rounded.Groups, Icons.Outlined.Groups),
    ATTENDANCE("Attend", Icons.Rounded.EventAvailable, Icons.Outlined.EventAvailable),
    CHARTS("Charts", Icons.Rounded.PieChart, Icons.Outlined.PieChart),
    SCHOOL("School", Icons.Rounded.School, Icons.Outlined.School)
}

private enum class SchoolSubTab(val label: String) {
    APPROVALS("Approvals"),
    CONNECT("Chat & Parents"),
    SETTINGS("Settings & Backup")
}

private sealed interface ExportRequest {
    data class SingleStudent(val student: StudentRecord) : ExportRequest
    data class StudentList(val students: List<StudentRecord>, val label: String) : ExportRequest
}

private fun provideFirestore(context: Context): FirebaseFirestore {
    val databaseId = context.applicationContext.getString(R.string.firestore_database_id)
    val db = FirebaseFirestore.getInstance(databaseId)
    try {
        db.firestoreSettings = firestoreSettings {
            setLocalCacheSettings(persistentCacheSettings {})
        }
    } catch (_: Exception) {
        // Firestore settings already initialized in this process
    }
    return db
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShikkhaKhataTheme {
                AppNavigation()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val db = remember(context) { provideFirestore(context) }
    val repository = remember(db) { SchoolRepository(db) }
    var session by remember {
        mutableStateOf(AppPreferences.loadSession(context, BuildConfig.VERSION_CODE))
    }

    if (session.activeSchoolId.isBlank()) {
        SchoolEntryScreen(
            deviceId = session.deviceId,
            initialTeacherName = session.teacherName,
            initialTeacherPhone = session.teacherPhone,
            recentSchools = session.recentSchools,
            repository = repository,
            onEnteredSchool = { schoolId, teacherName, teacherPhone ->
                AppPreferences.saveActiveSchool(context, schoolId, teacherName, teacherPhone)
                session = AppPreferences.loadSession(context, BuildConfig.VERSION_CODE)
            }
        )
    } else {
        key(session.activeSchoolId, session.deviceId, session.teacherPhone) {
            SharedSchoolApp(
                schoolId = session.activeSchoolId,
                deviceId = session.deviceId,
                teacherName = session.teacherName,
                teacherPhone = session.teacherPhone,
                initialYear = session.selectedYear,
                upgradedNotice = session.upgradedNoticeMessage,
                db = db,
                onSwitchSchool = {
                    AppPreferences.clearActiveSchool(context)
                    session = AppPreferences.loadSession(context, BuildConfig.VERSION_CODE)
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedSchoolApp(
    schoolId: String,
    deviceId: String,
    teacherName: String,
    teacherPhone: String,
    initialYear: Int,
    upgradedNotice: String?,
    db: FirebaseFirestore,
    onSwitchSchool: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val viewModel: SchoolViewModel = viewModel(
        key = "${schoolId}_${deviceId}_${teacherPhone}",
        factory = viewModelFactory {
            initializer {
                SchoolViewModel(
                    repository = SchoolRepository(db),
                    schoolId = schoolId,
                    deviceId = deviceId,
                    teacherName = teacherName,
                    teacherPhone = teacherPhone,
                    initialYear = initialYear
                )
            }
        }
    )

    val selectedYear by viewModel.selectedYear.collectAsStateWithLifecycle()
    val workspaceState by viewModel.schoolWorkspaceState.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdminState.collectAsStateWithLifecycle()
    val studentsState by viewModel.studentsState.collectAsStateWithLifecycle()
    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val customFieldsState by viewModel.customFieldsState.collectAsStateWithLifecycle()
    val attendanceLogsState by viewModel.attendanceLogsState.collectAsStateWithLifecycle()
    val pendingChangesState by viewModel.pendingChangesState.collectAsStateWithLifecycle()
    val parentNoticesState by viewModel.parentNoticesState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val filteredStudents by viewModel.filteredStudents.collectAsStateWithLifecycle()
    val summary by viewModel.dashboardSummary.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedClassFilter by viewModel.selectedClassFilter.collectAsStateWithLifecycle()
    val quickFilter by viewModel.quickFilter.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val feedbackMessage by viewModel.feedbackMessage.collectAsStateWithLifecycle()

    var activeTab by remember { mutableStateOf(MainTab.DASHBOARD) }
    var activeSchoolSubTab by remember { mutableStateOf(SchoolSubTab.APPROVALS) }
    var openRegisterInBatch by remember { mutableStateOf(false) }
    var batchTargetClass by remember { mutableStateOf<String?>(null) }
    var batchTargetSubjectId by remember { mutableStateOf<String?>(null) }
    var showFieldManager by remember { mutableStateOf(false) }
    var showPromotionManager by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showShareAppDialog by remember { mutableStateOf(false) }
    var viewingProfileStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var editingStudent by remember { mutableStateOf<StudentRecord?>(null) }
    var activeExportRequest by remember { mutableStateOf<ExportRequest?>(null) }
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingSaveLabel by remember { mutableStateOf("File") }
    var pendingRestorePreview by remember { mutableStateOf<BackupParseResult?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val workspace = (workspaceState as? UiState.Success)?.data
    val teacherProfileView = remember(workspace, teacherName) {
        workspace?.toTeacherProfileView(teacherName)
    }
    val allStudents = (studentsState as? UiState.Success)?.data ?: emptyList()
    val subjects = (subjectsState as? UiState.Success)?.data ?: emptyList()
    val customFields = (customFieldsState as? UiState.Success)?.data ?: emptyList()
    val attendanceLogs = (attendanceLogsState as? UiState.Success)?.data ?: emptyList()
    val examConfigs = remember(workspace?.examConfigs) {
        ExamDefaults.ensureThreeExams(workspace?.examConfigs.orEmpty())
    }

    LaunchedEffect(upgradedNotice) {
        if (!upgradedNotice.isNullOrBlank()) {
            snackbarHostState.showSnackbar(upgradedNotice)
        }
    }

    LaunchedEffect(workspace, isAdmin, allStudents.size) {
        if (workspace != null && isAdmin && allStudents.isNotEmpty()) {
            val calendarYear = Calendar.getInstance().get(Calendar.YEAR)
            viewModel.checkAndRunAutoCalendarYearRollover(context, calendarYear)
        }
    }

    val handleSavedUri: (Uri?) -> Unit = { uri ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (uri != null && bytes != null) {
            try {
                ReportExporter.writeBytesToUri(context, uri, bytes)
                val msg = "$pendingSaveLabel saved successfully."
                if (editingStudent != null || viewingProfileStudent != null) {
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                }
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Failed to save $pendingSaveLabel"
                if (editingStudent != null || viewingProfileStudent != null) {
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                } else {
                    scope.launch { snackbarHostState.showSnackbar(err) }
                }
            }
        }
    }

    val pdfSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportTargetFormat.PDF.mimeType),
        onResult = handleSavedUri
    )
    val csvSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportTargetFormat.GOOGLE_SHEETS.mimeType),
        onResult = handleSavedUri
    )
    val docxSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExportTargetFormat.MICROSOFT_WORD.mimeType),
        onResult = handleSavedUri
    )
    val xlsxSaveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(ExcelBackupManager.MIME_XLSX),
        onResult = handleSavedUri
    )
    val restoreDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val parsed = ExcelBackupManager.parseBackupFromUri(
                    context = context,
                    uri = uri,
                    defaultAcademicYear = selectedYear,
                    existingSubjects = subjects,
                    existingCustomFields = customFields
                )
                pendingRestorePreview = parsed
            } catch (e: Exception) {
                scope.launch {
                    snackbarHostState.showSnackbar(e.localizedMessage ?: "Could not read backup spreadsheet.")
                }
            }
        }
    }

    val launchExcelBackupToDeviceOrDrive: () -> Unit = {
        pendingSaveBytes = ExcelBackupManager.buildWorkbookXlsxBytes(allStudents, subjects, customFields)
        pendingSaveLabel = "Excel Backup (.xlsx)"
        xlsxSaveLauncher.launch("my_school_${schoolId}_${selectedYear}_backup.xlsx")
    }

    val launchExcelBackupToCloudShare: () -> Unit = {
        val bytes = ExcelBackupManager.buildWorkbookXlsxBytes(allStudents, subjects, customFields)
        ReportExporter.shareFile(
            context = context,
            fileName = "my_school_${schoolId}_${selectedYear}_backup.xlsx",
            mimeType = ExcelBackupManager.MIME_XLSX,
            bytes = bytes,
            chooserTitle = "Save / Upload Backup to Google Drive or Cloud"
        )
    }

    val launchRestorePicker: () -> Unit = {
        restoreDocumentLauncher.launch(
            arrayOf(
                ExcelBackupManager.MIME_XLSX,
                "text/csv",
                "text/comma-separated-values",
                "application/vnd.ms-excel",
                "text/plain",
                "*/*"
            )
        )
    }

    fun buildExportBytes(request: ExportRequest, format: ExportTargetFormat): ByteArray =
        when (request) {
            is ExportRequest.SingleStudent -> when (format) {
                ExportTargetFormat.PDF -> ReportExporter.buildStudentPdfBytes(request.student, teacherProfileView, subjects, customFields)
                ExportTargetFormat.GOOGLE_SHEETS -> ReportExporter.writeCsvBytes(listOf(request.student), subjects, customFields)
                ExportTargetFormat.MICROSOFT_WORD -> ReportExporter.buildStudentDocxBytes(request.student, teacherProfileView, subjects, customFields)
                ExportTargetFormat.WHATSAPP -> ReportExporter.buildStudentWhatsAppText(request.student, teacherProfileView, subjects, customFields).toByteArray()
            }
            is ExportRequest.StudentList -> when (format) {
                ExportTargetFormat.PDF -> ReportExporter.buildRegisterPdfBytes(request.students, teacherProfileView, selectedYear, subjects, customFields)
                ExportTargetFormat.GOOGLE_SHEETS -> ReportExporter.writeCsvBytes(request.students, subjects, customFields)
                ExportTargetFormat.MICROSOFT_WORD -> ReportExporter.buildRegisterDocxBytes(request.students, teacherProfileView, selectedYear, subjects, customFields)
                ExportTargetFormat.WHATSAPP -> ReportExporter.buildRegisterWhatsAppText(request.students, teacherProfileView, selectedYear, subjects).toByteArray()
            }
        }

    fun buildExportFileName(request: ExportRequest, format: ExportTargetFormat): String =
        when (request) {
            is ExportRequest.SingleStudent -> {
                val cleanName = request.student.studentNameEn
                    .ifBlank { "student_${request.student.rollNumber}" }
                    .lowercase()
                    .replace(Regex("[^a-z0-9]+"), "_")
                    .trim('_')
                "my_school_${cleanName}_${request.student.academicYear}.${format.extension}"
            }
            is ExportRequest.StudentList -> {
                "my_school_${schoolId}_${selectedYear}_students.${format.extension}"
            }
        }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    val openNewStudent: () -> Unit = {
        val nextClass = selectedClassFilter ?: "Class 1"
        val classCount = allStudents.count { it.classLevel == nextClass }
        val suggestedRoll = (classCount + 1).toString().padStart(2, '0')
        editingStudent = StudentRecord(
            classLevel = nextClass,
            rollNumber = suggestedRoll,
            academicYear = selectedYear.toLong(),
            upazilaDistrict = workspace?.upazilaDistrict ?: ""
        )
    }

    val activeEditor = editingStudent
    val activeProfileRaw = viewingProfileStudent
    val activeProfile = if (activeProfileRaw != null) {
        allStudents.firstOrNull { it.id == activeProfileRaw.id } ?: activeProfileRaw
    } else null

    if (showPromotionManager) {
        PromotionRollManagerScreen(
            allStudents = allStudents,
            selectedYear = selectedYear,
            initialSelectedClass = selectedClassFilter,
            isAdmin = isAdmin,
            isSaving = isSaving,
            onSaveBatch = viewModel::saveBatchUpdates,
            onRunPromotion = { targetYear ->
                viewModel.promoteEligibleStudentsToNextYear(targetYear)
            },
            onBack = { showPromotionManager = false }
        )
    } else if (showFieldManager) {
        FieldSchemaManagerScreen(
            subjects = subjects,
            customFields = customFields,
            initialSelectedClass = selectedClassFilter ?: "Class 5",
            onSaveSubject = { viewModel.saveSubject(it) },
            onDeleteSubject = viewModel::deleteSubject,
            onAddSubjectField = { sub, label, type, max ->
                viewModel.addFieldToSubject(sub, label, type, max)
            },
            onUpdateSubjectField = { sub, updatedField ->
                viewModel.updateFieldInSubject(sub, updatedField)
            },
            onDeleteSubjectField = viewModel::removeFieldFromSubject,
            onSaveCustomField = { viewModel.saveCustomField(it) },
            onDeleteCustomField = viewModel::deleteCustomField,
            onAssessWholeClass = { targetClass, subjectId ->
                showFieldManager = false
                batchTargetClass = targetClass
                batchTargetSubjectId = subjectId
                openRegisterInBatch = true
                activeTab = MainTab.REGISTER
            },
            onBack = { showFieldManager = false }
        )
    } else if (activeEditor != null) {
        StudentEditorScreen(
            initialStudent = activeEditor,
            allStudents = allStudents,
            subjects = subjects,
            customFields = customFields,
            isSaving = isSaving,
            onSelectOtherStudent = { next ->
                editingStudent = next
                if (viewingProfileStudent != null) viewingProfileStudent = next
            },
            onExportStudent = { draftStudent ->
                activeExportRequest = ExportRequest.SingleStudent(draftStudent)
            },
            onSave = { updated ->
                viewModel.saveStudent(updated) {
                    if (viewingProfileStudent != null && isAdmin) {
                        viewingProfileStudent = updated
                    }
                    editingStudent = null
                }
            },
            onDelete = { toDelete ->
                viewModel.deleteStudent(toDelete) {
                    if (isAdmin) {
                        viewingProfileStudent = null
                    }
                    editingStudent = null
                }
            },
            onSaveSubject = { viewModel.saveSubject(it) },
            onDeleteSubject = viewModel::deleteSubject,
            onAddSubjectField = { sub, label, type, max ->
                viewModel.addFieldToSubject(sub, label, type, max)
            },
            onUpdateSubjectField = { sub, updatedField ->
                viewModel.updateFieldInSubject(sub, updatedField)
            },
            onDeleteSubjectField = viewModel::removeFieldFromSubject,
            onSaveCustomField = { viewModel.saveCustomField(it) },
            onDeleteCustomField = viewModel::deleteCustomField,
            onBack = { editingStudent = null }
        )
    } else if (activeProfile != null) {
        StudentProfileScreen(
            student = activeProfile,
            allStudents = allStudents,
            subjects = subjects,
            customFields = customFields,
            attendanceLogs = attendanceLogs,
            examConfigs = examConfigs,
            isAdmin = isAdmin,
            isSaving = isSaving,
            onSelectOtherStudent = { next -> viewingProfileStudent = next },
            onEditStudent = { editingStudent = activeProfile },
            onUpdateStudentRecord = { updated, onDone ->
                viewModel.saveStudent(updated) {
                    if (isAdmin) viewingProfileStudent = updated
                    onDone()
                }
            },
            onSaveExamConfigs = { configs, onDone ->
                viewModel.saveExamConfigs(configs, onDone)
            },
            onExportStudent = {
                activeExportRequest = ExportRequest.SingleStudent(activeProfile)
            },
            onBack = { viewingProfileStudent = null }
        )
    } else {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = when (activeTab) {
                                        MainTab.DASHBOARD -> workspace?.schoolName?.ifBlank { "My School" } ?: "My School"
                                        MainTab.REGISTER -> "Students & Batch"
                                        MainTab.ATTENDANCE -> "Class Attendance Sheet"
                                        MainTab.CHARTS -> "Performance Charts"
                                        MainTab.SCHOOL -> "School Hub & Settings"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "ID: $schoolId • ${if (isAdmin) "ADMIN" else "TEACHER"} • $selectedYear (${allStudents.size} Students)",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = {
                                    activeSchoolSubTab = SchoolSubTab.APPROVALS
                                    activeTab = MainTab.SCHOOL
                                },
                                modifier = Modifier.testTag("topbar_approvals_button")
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (summary.pendingChangesCount > 0) {
                                            Badge { Text("${summary.pendingChangesCount}") }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AdminPanelSettings,
                                        contentDescription = "Approvals Queue",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showFieldManager = true },
                                modifier = Modifier.testTag("topbar_subjects_menu_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MenuBook,
                                    contentDescription = "Subjects & Class Fields Menu",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            IconButton(
                                onClick = { showShareAppDialog = true },
                                modifier = Modifier.testTag("topbar_share_app_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "Share App & School ID",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { showTopMenu = true },
                                    modifier = Modifier.testTag("topbar_more_menu_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MoreVert,
                                        contentDescription = "Open Menu"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showTopMenu,
                                    onDismissRequest = { showTopMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Class Attendance Sheet") },
                                        leadingIcon = { Icon(Icons.Rounded.EventAvailable, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            activeTab = MainTab.ATTENDANCE
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Admin Approvals (${summary.pendingChangesCount})") },
                                        leadingIcon = { Icon(Icons.Rounded.Approval, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            activeSchoolSubTab = SchoolSubTab.APPROVALS
                                            activeTab = MainTab.SCHOOL
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Subjects & Assessment Fields") },
                                        leadingIcon = { Icon(Icons.Rounded.MenuBook, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            showFieldManager = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Backup Excel to Device / Drive") },
                                        leadingIcon = { Icon(Icons.Rounded.Backup, contentDescription = null) },
                                        enabled = allStudents.isNotEmpty(),
                                        onClick = {
                                            showTopMenu = false
                                            launchExcelBackupToDeviceOrDrive()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Restore from Excel / Google Drive") },
                                        leadingIcon = { Icon(Icons.Rounded.Restore, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            launchRestorePicker()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share My School & ID") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            showShareAppDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Export Student Data") },
                                        enabled = allStudents.isNotEmpty(),
                                        onClick = {
                                            showTopMenu = false
                                            activeExportRequest = ExportRequest.StudentList(
                                                students = allStudents,
                                                label = "$selectedYear Full School Register (${allStudents.size} Students)"
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Promotion & Roll Positions") },
                                        leadingIcon = { Icon(Icons.Rounded.SwapVert, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            showPromotionManager = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Logout / Switch School") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null) },
                                        onClick = {
                                            showTopMenu = false
                                            onSwitchSchool()
                                        }
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
                    if (!useRail) {
                        NavigationBar {
                            MainTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        openRegisterInBatch = false
                                        batchTargetClass = null
                                        batchTargetSubjectId = null
                                        activeTab = tab
                                    },
                                    icon = {
                                        if (tab == MainTab.SCHOOL && summary.pendingChangesCount > 0) {
                                            BadgedBox(badge = { Badge { Text("${summary.pendingChangesCount}") } }) {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (activeTab == MainTab.REGISTER || activeTab == MainTab.DASHBOARD) {
                        ExtendedFloatingActionButton(
                            onClick = openNewStudent,
                            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            text = { Text("New Student") },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_new_student")
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            windowInsets = WindowInsets(0, 0, 0, 0)
                        ) {
                            MainTab.entries.forEach { tab ->
                                val selected = activeTab == tab
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        openRegisterInBatch = false
                                        batchTargetClass = null
                                        batchTargetSubjectId = null
                                        activeTab = tab
                                    },
                                    icon = {
                                        if (tab == MainTab.SCHOOL && summary.pendingChangesCount > 0) {
                                            BadgedBox(badge = { Badge { Text("${summary.pendingChangesCount}") } }) {
                                                Icon(
                                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                    contentDescription = tab.label
                                                )
                                            }
                                        } else {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        }
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag("rail_tab_${tab.name.lowercase()}")
                                )
                            }
                        }
                    }

                    when (activeTab) {
                        MainTab.DASHBOARD -> DashboardScreen(
                            schoolId = schoolId,
                            teacherName = teacherName,
                            isAdmin = isAdmin,
                            summary = summary,
                            schoolWorkspace = workspace,
                            selectedYear = selectedYear,
                            onYearSelected = { year ->
                                viewModel.setSelectedYear(year)
                                AppPreferences.saveSelectedYear(context, year)
                            },
                            onOpenClassRegister = { cls ->
                                viewModel.setClassFilter(cls)
                                openRegisterInBatch = false
                                batchTargetClass = null
                                batchTargetSubjectId = null
                                activeTab = MainTab.REGISTER
                            },
                            onAddStudent = openNewStudent,
                            onOpenAttendanceSheet = { activeTab = MainTab.ATTENDANCE },
                            onOpenApprovals = {
                                activeSchoolSubTab = SchoolSubTab.APPROVALS
                                activeTab = MainTab.SCHOOL
                            },
                            onOpenBatchEntry = {
                                openRegisterInBatch = true
                                batchTargetClass = null
                                batchTargetSubjectId = null
                                activeTab = MainTab.REGISTER
                            },
                            onOpenFieldManager = { showFieldManager = true },
                            onExportReport = {
                                activeExportRequest = ExportRequest.StudentList(
                                    students = allStudents,
                                    label = "$selectedYear Full School Register (${allStudents.size} Students)"
                                )
                            },
                            onOpenBackupRestore = {
                                activeSchoolSubTab = SchoolSubTab.SETTINGS
                                activeTab = MainTab.SCHOOL
                            },
                            onShareApp = { showShareAppDialog = true },
                            onOpenPromotionManager = { showPromotionManager = true },
                            onOpenSchoolSettings = {
                                activeSchoolSubTab = SchoolSubTab.SETTINGS
                                activeTab = MainTab.SCHOOL
                            }
                        )

                        MainTab.REGISTER -> RegisterScreen(
                            studentsState = studentsState,
                            filteredStudents = filteredStudents,
                            subjects = subjects,
                            customFields = customFields,
                            selectedYear = selectedYear,
                            searchQuery = searchQuery,
                            selectedClassFilter = selectedClassFilter,
                            quickFilter = quickFilter,
                            isSaving = isSaving,
                            initialBatchMode = openRegisterInBatch,
                            initialBatchClass = batchTargetClass,
                            initialBatchSubjectId = batchTargetSubjectId,
                            onSearchQueryChange = viewModel::setSearchQuery,
                            onClassFilterChange = viewModel::setClassFilter,
                            onQuickFilterChange = viewModel::setQuickFilter,
                            onStudentClick = { student -> viewingProfileStudent = student },
                            onAddStudent = openNewStudent,
                            onSaveBatch = viewModel::saveBatchUpdates,
                            onOpenFieldManager = { showFieldManager = true },
                            onOpenPromotionManager = { showPromotionManager = true }
                        )

                        MainTab.ATTENDANCE -> AttendanceSheetScreen(
                            allStudents = allStudents,
                            attendanceLogsState = attendanceLogsState,
                            selectedYear = selectedYear,
                            isAdmin = isAdmin,
                            isSaving = isSaving,
                            initialSelectedClass = selectedClassFilter,
                            onSaveAttendanceSheet = viewModel::saveAttendanceSheet,
                            onOpenStudentProfile = { student -> viewingProfileStudent = student }
                        )

                        MainTab.CHARTS -> AnalyticsScreen(
                            studentsState = studentsState,
                            subjects = subjects,
                            selectedYear = selectedYear,
                            onOpenExportSheet = { scopedStudents ->
                                activeExportRequest = ExportRequest.StudentList(
                                    students = scopedStudents,
                                    label = "$selectedYear Performance Report (${scopedStudents.size} Students)"
                                )
                            },
                            onOpenStudentProfile = { student ->
                                viewingProfileStudent = student
                            },
                            onAddStudent = openNewStudent
                        )

                        MainTab.SCHOOL -> Column(modifier = Modifier.fillMaxSize()) {
                            SingleChoiceSegmentedButtonRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                SchoolSubTab.entries.forEachIndexed { idx, sub ->
                                    SegmentedButton(
                                        selected = activeSchoolSubTab == sub,
                                        onClick = { activeSchoolSubTab = sub },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = idx,
                                            count = SchoolSubTab.entries.size
                                        ),
                                        icon = {
                                            Icon(
                                                imageVector = when (sub) {
                                                    SchoolSubTab.APPROVALS -> Icons.Rounded.AdminPanelSettings
                                                    SchoolSubTab.CONNECT -> Icons.Rounded.Forum
                                                    SchoolSubTab.SETTINGS -> Icons.Rounded.Settings
                                                },
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        },
                                        modifier = Modifier.testTag("school_subtab_${sub.name.lowercase()}")
                                    ) {
                                        val txt = if (sub == SchoolSubTab.APPROVALS && summary.pendingChangesCount > 0) {
                                            "${sub.label} (${summary.pendingChangesCount})"
                                        } else sub.label
                                        Text(txt, maxLines = 1)
                                    }
                                }
                            }

                            when (activeSchoolSubTab) {
                                SchoolSubTab.APPROVALS -> ApprovalsScreen(
                                    pendingChangesState = pendingChangesState,
                                    isAdmin = isAdmin,
                                    adminName = workspace?.adminName ?: "Admin",
                                    isSaving = isSaving,
                                    onApproveChange = viewModel::approvePendingChange,
                                    onApproveAll = viewModel::approveAllPendingChanges,
                                    onRejectChange = viewModel::rejectPendingChange,
                                    modifier = Modifier.weight(1f)
                                )

                                SchoolSubTab.CONNECT -> ConnectScreen(
                                    schoolId = schoolId,
                                    deviceId = deviceId,
                                    teacherName = teacherName,
                                    schoolWorkspace = workspace,
                                    allStudents = allStudents,
                                    messagesState = messagesState,
                                    parentNoticesState = parentNoticesState,
                                    isSaving = isSaving,
                                    onSendChatMessage = viewModel::sendChatMessage,
                                    onEditChatMessage = viewModel::editChatMessage,
                                    onDeleteChatMessage = viewModel::deleteChatMessage,
                                    onSaveParentNotice = viewModel::saveParentNotice,
                                    onDeleteParentNotice = viewModel::deleteParentNotice,
                                    modifier = Modifier.weight(1f)
                                )

                                SchoolSubTab.SETTINGS -> SchoolSettingsScreen(
                                    schoolId = schoolId,
                                    deviceId = deviceId,
                                    teacherName = teacherName,
                                    workspace = workspace,
                                    isAdmin = isAdmin,
                                    selectedYear = selectedYear,
                                    studentCount = allStudents.size,
                                    subjects = subjects,
                                    customFieldsCount = customFields.size,
                                    isSaving = isSaving,
                                    onUpdateSchoolDetails = viewModel::updateSchoolDetails,
                                    onSaveExamConfigs = viewModel::saveExamConfigs,
                                    onTransferAdmin = viewModel::transferAdminRole,
                                    onVerifyAdminPassword = { pw -> viewModel.verifyAdminPassword(context, pw) },
                                    onResetTeachersPanel = { viewModel.resetTeachersPanel(context) },
                                    onSwitchSchool = onSwitchSchool,
                                    onMigrateLegacyData = viewModel::migrateLegacyRecordsIfAny,
                                    onOpenFieldManager = { showFieldManager = true },
                                    onExportReport = {
                                        activeExportRequest = ExportRequest.StudentList(
                                            students = allStudents,
                                            label = "$selectedYear Full School Register (${allStudents.size} Students)"
                                        )
                                    },
                                    onBackupExcelToDeviceOrDrive = launchExcelBackupToDeviceOrDrive,
                                    onBackupExcelToCloudShare = launchExcelBackupToCloudShare,
                                    onRestoreFromDeviceOrDrive = launchRestorePicker,
                                    onShareApp = { showShareAppDialog = true },
                                    onOpenPromotionManager = { showPromotionManager = true },
                                    onPromoteToNextYear = viewModel::promoteEligibleStudentsToNextYear,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val restoreCandidate = pendingRestorePreview
    if (restoreCandidate != null) {
        AlertDialog(
            onDismissRequest = { pendingRestorePreview = null },
            title = { Text("Restore Backup Data?") },
            text = {
                Text(
                    "Found ${restoreCandidate.students.size} student(s), " +
                        "${restoreCandidate.subjectsToUpsert.size} new/updated subject(s), and " +
                        "${restoreCandidate.customFieldsToUpsert.size} new category field(s) in the selected ${restoreCandidate.formatLabel} file.\n\n" +
                        "Existing matching students (same year, class, roll, and name) will be updated; new students will be added."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestorePreview = null
                        viewModel.restoreFromBackup(restoreCandidate)
                    }
                ) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestorePreview = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showShareAppDialog) {
        ShareAppDialog(
            schoolName = workspace?.schoolName,
            schoolRoomId = schoolId,
            onDismiss = { showShareAppDialog = false },
            onShareWhatsApp = {
                AppShareHelper.shareInviteViaWhatsApp(
                    context = context,
                    schoolName = workspace?.schoolName,
                    schoolRoomId = schoolId
                )
            },
            onShareSystem = {
                AppShareHelper.shareInviteText(
                    context = context,
                    schoolName = workspace?.schoolName,
                    schoolRoomId = schoolId
                )
            },
            onShareApk = {
                AppShareHelper.shareAppApk(context)
            }
        )
    }

    val currentExport = activeExportRequest
    if (currentExport != null) {
        val sheetTitle = when (currentExport) {
            is ExportRequest.SingleStudent -> "Export ${currentExport.student.studentNameEn.ifBlank { "Student" }}"
            is ExportRequest.StudentList -> "Export & Share Register"
        }
        val sheetSubtitle = when (currentExport) {
            is ExportRequest.SingleStudent -> "${currentExport.student.classLevel} • Roll ${currentExport.student.rollNumber} • Fixed, yearly, subject & flexible fields + comments"
            is ExportRequest.StudentList -> currentExport.label
        }

        ExportShareBottomSheet(
            title = sheetTitle,
            subtitle = sheetSubtitle,
            onDismiss = { activeExportRequest = null },
            onSaveToDevice = { format ->
                val bytes = buildExportBytes(currentExport, format)
                val fileName = buildExportFileName(currentExport, format)
                pendingSaveBytes = bytes
                pendingSaveLabel = format.title
                when (format) {
                    ExportTargetFormat.PDF -> pdfSaveLauncher.launch(fileName)
                    ExportTargetFormat.GOOGLE_SHEETS -> csvSaveLauncher.launch(fileName)
                    ExportTargetFormat.MICROSOFT_WORD -> docxSaveLauncher.launch(fileName)
                    ExportTargetFormat.WHATSAPP -> Unit
                }
            },
            onShareDirect = { format ->
                if (format == ExportTargetFormat.WHATSAPP) {
                    val text = when (currentExport) {
                        is ExportRequest.SingleStudent ->
                            ReportExporter.buildStudentWhatsAppText(currentExport.student, teacherProfileView, subjects, customFields)
                        is ExportRequest.StudentList ->
                            ReportExporter.buildRegisterWhatsAppText(currentExport.students, teacherProfileView, selectedYear, subjects)
                    }
                    ReportExporter.shareWhatsAppText(context, text)
                } else {
                    val bytes = buildExportBytes(currentExport, format)
                    val fileName = buildExportFileName(currentExport, format)
                    ReportExporter.shareFile(
                        context = context,
                        fileName = fileName,
                        mimeType = format.mimeType,
                        bytes = bytes,
                        chooserTitle = "Share ${format.title}"
                    )
                }
            }
        )
    }
}
