package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamTermConfig
import com.example.data.model.SchoolWorkspace
import com.example.data.model.SubjectDefinition
import com.example.data.model.TeacherRoles
import com.example.ui.components.MarksheetCustomizerDialog
import com.example.ui.components.YearSwitcherRow
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun SchoolSettingsScreen(
    schoolId: String,
    deviceId: String,
    teacherName: String,
    workspace: SchoolWorkspace?,
    isAdmin: Boolean,
    selectedYear: Int,
    studentCount: Int,
    subjects: List<SubjectDefinition>,
    customFieldsCount: Int,
    isSaving: Boolean,
    isOnline: Boolean = true,
    hasPendingSync: Boolean = false,
    onSelectLanguage: (AppLanguage) -> Unit = {},
    onSyncNow: () -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
    onSaveAppUpdateMetadata: (
        latestVersionCode: Long,
        latestVersionName: String,
        updateUrl: String,
        releaseNotes: String,
        onSuccess: () -> Unit
    ) -> Unit = { _, _, _, _, done -> done() },
    onUpdateSchoolDetails: (schoolName: String, emisCode: String, upazilaDistrict: String, defaultAcademicYear: Long) -> Unit,
    onSaveExamConfigs: (List<ExamTermConfig>, () -> Unit) -> Unit,
    onTransferAdmin: (newAdminDeviceId: String, newAdminName: String) -> Unit,
    onVerifyAdminPassword: ((String) -> Boolean)? = null,
    onResetTeachersPanel: (() -> Unit)? = null,
    onSwitchSchool: () -> Unit,
    onMigrateLegacyData: () -> Unit,
    onOpenFieldManager: () -> Unit,
    onExportReport: () -> Unit,
    onBackupExcelToDeviceOrDrive: () -> Unit,
    onBackupExcelToCloudShare: () -> Unit,
    onRestoreFromDeviceOrDrive: () -> Unit,
    onShareApp: () -> Unit,
    onOpenPromotionManager: () -> Unit = {},
    onPromoteToNextYear: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val currentLanguage = LocalAppLanguage.current
    val clipboard = LocalClipboardManager.current
    var schoolName by remember { mutableStateOf(workspace?.schoolName ?: "") }
    var emisCode by remember { mutableStateOf(workspace?.emisCode ?: "") }
    var upazilaDistrict by remember { mutableStateOf(workspace?.upazilaDistrict ?: "") }
    var defaultYear by remember { mutableIntStateOf((workspace?.defaultAcademicYear ?: 2026L).toInt()) }
    var showPromoteDialog by remember { mutableStateOf(false) }
    var showExamSetupDialog by remember { mutableStateOf(false) }
    var showPublishUpdateEditor by remember { mutableStateOf(false) }
    var draftUpdateVerCode by remember {
        mutableStateOf((workspace?.latestAppVersionCode?.takeIf { it > 0L } ?: BuildConfig.VERSION_CODE.toLong()).toString())
    }
    var draftUpdateVerName by remember {
        mutableStateOf(workspace?.latestAppVersionName?.ifBlank { BuildConfig.VERSION_NAME } ?: BuildConfig.VERSION_NAME)
    }
    var draftUpdateUrl by remember { mutableStateOf(workspace?.appUpdateUrl ?: "") }
    var draftUpdateNotes by remember { mutableStateOf(workspace?.appUpdateNotes ?: "") }

    // Safe Admin Transfer confirmation state
    var pendingAdminTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var adminConfirmPassword by remember { mutableStateOf("") }
    var adminTransferError by remember { mutableStateOf<String?>(null) }
    var showResetTeachersDialog by remember { mutableStateOf(false) }

    LaunchedEffect(workspace) {
        if (workspace != null) {
            schoolName = workspace.schoolName
            emisCode = workspace.emisCode
            upazilaDistrict = workspace.upazilaDistrict
            defaultYear = workspace.defaultAcademicYear.toInt()
            if (workspace.latestAppVersionCode > 0L) {
                draftUpdateVerCode = workspace.latestAppVersionCode.toString()
            }
            if (workspace.latestAppVersionName.isNotBlank()) {
                draftUpdateVerName = workspace.latestAppVersionName
            }
            if (workspace.appUpdateUrl.isNotBlank()) {
                draftUpdateUrl = workspace.appUpdateUrl
            }
            if (workspace.appUpdateNotes.isNotBlank()) {
                draftUpdateNotes = workspace.appUpdateNotes
            }
        }
    }

    val examConfigs = remember(workspace?.examConfigs) {
        ExamDefaults.ensureThreeExams(workspace?.examConfigs.orEmpty())
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Language Selector (English / বাংলা)
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = strings.languageSettingTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = strings.languageSettingDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            AppLanguage.entries.forEachIndexed { idx, lang ->
                                SegmentedButton(
                                    selected = currentLanguage == lang,
                                    onClick = { onSelectLanguage(lang) },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = idx,
                                        count = AppLanguage.entries.size
                                    ),
                                    modifier = Modifier.testTag("settings_lang_${lang.code}")
                                ) {
                                    Text(lang.displayLabel)
                                }
                            }
                        }
                    }
                }
            }

            // 0b. App Updates & Offline/Cloud Sync Card
            item {
                val remoteCode = workspace?.latestAppVersionCode ?: 0L
                val remoteName = workspace?.latestAppVersionName.orEmpty()
                val hasUpdateAvailable = remoteCode > BuildConfig.VERSION_CODE.toLong() &&
                    !workspace?.appUpdateUrl.isNullOrBlank()
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasUpdateAvailable) {
                            MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
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
                                    imageVector = Icons.Rounded.SystemUpdate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = strings.updateCheckTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Surface(
                            color = when {
                                !isOnline -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                                hasPendingSync -> MaterialTheme.colorScheme.secondaryContainer
                                else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = when {
                                        !isOnline -> Icons.Rounded.CloudOff
                                        hasPendingSync -> Icons.Rounded.CloudSync
                                        else -> Icons.Rounded.CloudDone
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = when {
                                        !isOnline -> strings.offlineBannerText
                                        hasPendingSync -> strings.pendingSyncBannerText
                                        else -> strings.syncedStatusText
                                    },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        if (hasUpdateAvailable) {
                            Text(
                                text = "${strings.updateAvailableBadge}: v${remoteName.ifBlank { remoteCode.toString() }}",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = onSyncNow,
                                enabled = !isSaving,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("settings_sync_now_button")
                            ) {
                                Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.syncNowButton, maxLines = 1)
                            }
                            Button(
                                onClick = onOpenUpdateDialog,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("settings_update_app_button")
                            ) {
                                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(strings.updateAppButton, maxLines = 1)
                            }
                        }

                        if (isAdmin) {
                            TextButton(
                                onClick = { showPublishUpdateEditor = !showPublishUpdateEditor },
                                modifier = Modifier.testTag("settings_toggle_publish_update")
                            ) {
                                Text(
                                    if (showPublishUpdateEditor) "Hide Admin App Release Link Settings"
                                    else "Configure School App Update Link (Admin)"
                                )
                            }
                            if (showPublishUpdateEditor) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedTextField(
                                            value = draftUpdateVerCode,
                                            onValueChange = { draftUpdateVerCode = it.filter { c -> c.isDigit() } },
                                            label = { Text("Version Code") },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = draftUpdateVerName,
                                            onValueChange = { draftUpdateVerName = it },
                                            label = { Text("Version Name") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    OutlinedTextField(
                                        value = draftUpdateUrl,
                                        onValueChange = { draftUpdateUrl = it },
                                        label = { Text("App Update Link (https://...)") },
                                        placeholder = { Text("https://drive.google.com/... or Play Store URL") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = draftUpdateNotes,
                                        onValueChange = { draftUpdateNotes = it },
                                        label = { Text("Release Notes (Optional)") },
                                        maxLines = 2,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    FilledTonalButton(
                                        onClick = {
                                            val code = (draftUpdateVerCode.toLongOrNull() ?: BuildConfig.VERSION_CODE.toLong())
                                                .coerceIn(1L, 999999L)
                                            onSaveAppUpdateMetadata(
                                                code,
                                                draftUpdateVerName,
                                                draftUpdateUrl,
                                                draftUpdateNotes
                                            ) {
                                                showPublishUpdateEditor = false
                                            }
                                        },
                                        enabled = !isSaving && draftUpdateUrl.trim().startsWith("https://", ignoreCase = true),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("settings_publish_update_button")
                                    ) {
                                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Save Release Link for Teachers")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 1. Unique School ID & Shared Teacher Roster Card
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Unique School ID",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = schoolId,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(onClick = { clipboard.setText(AnnotatedString(schoolId)) }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy School ID")
                                }
                                Surface(
                                    color = if (isAdmin) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.tertiary,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isAdmin) "ADMIN" else "TEACHER",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isAdmin) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onTertiary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "Signed in as $teacherName. Share School ID '$schoolId' with fellow teachers so everyone works on the same register (teacher edits await Admin confirmation).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        val teachers = workspace?.teachers.orEmpty()
                        if (teachers.isNotEmpty()) {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Connected Teachers (${teachers.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isAdmin && onResetTeachersPanel != null && teachers.size > 1) {
                                    TextButton(onClick = { showResetTeachersDialog = true }) {
                                        Icon(
                                            Icons.Rounded.PersonRemove,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Reset List", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }

                            teachers.forEach { member ->
                                val memberIsAdmin = member.role == TeacherRoles.ADMIN || member.deviceId == workspace?.adminDeviceId
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
                                            imageVector = Icons.Rounded.AdminPanelSettings,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = if (memberIsAdmin) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (member.deviceId == deviceId) "${member.name} (You)" else member.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (memberIsAdmin) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    if (memberIsAdmin) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "School Admin",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    } else if (isAdmin) {
                                        TextButton(
                                            onClick = {
                                                adminConfirmPassword = ""
                                                adminTransferError = null
                                                pendingAdminTarget = member.deviceId to member.name
                                            }
                                        ) {
                                            Icon(
                                                Icons.Rounded.Lock,
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Transfer Admin")
                                        }
                                    } else {
                                        Text(
                                            text = "Teacher",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSwitchSchool,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("settings_logout_button")
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Logout / Switch", maxLines = 1)
                            }
                            FilledTonalButton(
                                onClick = onShareApp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("settings_share_app_button")
                            ) {
                                Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Share School ID", maxLines = 1)
                            }
                        }
                    }
                }
            }

            // 2. Backup & Restore (Excel & Google Drive)
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Backup & Restore (Excel & Google Drive)",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Save a complete Excel (.xlsx) backup containing all students, 3-exam marksheets, subjects, fields, comments, and photos directly to Device Storage or Google Drive — or restore anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (isWide) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = onBackupExcelToDeviceOrDrive,
                                    enabled = studentCount > 0 && !isSaving,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("settings_backup_device_excel_button")
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save Excel to Device / Drive", maxLines = 1)
                                }
                                FilledTonalButton(
                                    onClick = onBackupExcelToCloudShare,
                                    enabled = studentCount > 0 && !isSaving,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag("settings_backup_cloud_drive_button")
                                ) {
                                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Send to Google Drive", maxLines = 1)
                                }
                            }
                        } else {
                            Button(
                                onClick = onBackupExcelToDeviceOrDrive,
                                enabled = studentCount > 0 && !isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_backup_device_excel_button")
                            ) {
                                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Save Excel (.xlsx) to Device / Drive")
                            }
                            FilledTonalButton(
                                onClick = onBackupExcelToCloudShare,
                                enabled = studentCount > 0 && !isSaving,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_backup_cloud_drive_button")
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Upload Backup to Google Drive / Cloud")
                            }
                        }

                        OutlinedButton(
                            onClick = onRestoreFromDeviceOrDrive,
                            enabled = !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_restore_excel_button")
                        ) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(strings.menuRestoreExcel)
                        }

                        TextButton(
                            onClick = onMigrateLegacyData,
                            enabled = !isSaving,
                            modifier = Modifier.testTag("settings_migrate_legacy_button")
                        ) {
                            Text("Import Earlier App Version (v1.0) Data into School $schoolId")
                        }
                    }
                }
            }

            // 3. Subjects, Fields & 3-Exam Marksheet Setup
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Subjects, Fields & 3-Exam Marksheet",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${subjects.size} subject(s) & $customFieldsCount flexible category field(s) active. Customize subjects, Full Marks, Pass Marks, and exam names for the 3-Exam Marksheet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onOpenFieldManager,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_open_schema_button")
                        ) {
                            Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage Subjects & Flexible Fields")
                        }
                        FilledTonalButton(
                            onClick = { showExamSetupDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_open_marksheet_setup_button")
                        ) {
                            Icon(Icons.Rounded.Grade, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Customize 3-Exam Marksheet & Subjects")
                        }
                    }
                }
            }

            // 4. School Profile Details
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "School Details",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = schoolName,
                            onValueChange = { schoolName = it },
                            label = { Text("Government Primary School Name") },
                            placeholder = { Text("e.g. Purbo Para Govt. Primary School") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("settings_school_name")
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedTextField(
                                value = emisCode,
                                onValueChange = { emisCode = it },
                                label = { Text("EMIS Code") },
                                placeholder = { Text("91020401") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = upazilaDistrict,
                                onValueChange = { upazilaDistrict = it },
                                label = { Text("Upazila & District") },
                                placeholder = { Text("Sadar, Cumilla") },
                                singleLine = true,
                                modifier = Modifier.weight(1.4f)
                            )
                        }
                        Text(
                            text = "Default Calendar Year",
                            style = MaterialTheme.typography.labelLarge
                        )
                        YearSwitcherRow(
                            selectedYear = defaultYear,
                            onYearSelected = { defaultYear = it }
                        )
                        Button(
                            onClick = {
                                onUpdateSchoolDetails(
                                    schoolName,
                                    emisCode,
                                    upazilaDistrict,
                                    defaultYear.toLong()
                                )
                            },
                            enabled = !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_save_profile_button")
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
                                Text("Save School Details")
                            }
                        }
                    }
                }
            }

            // 5. Annual Register Tools
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Annual Register Tools ($selectedYear)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Export or share all $studentCount students as PDF, Google Sheets (CSV), Microsoft Word (.docx), or WhatsApp summary — or promote eligible students into ${selectedYear + 1}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = onExportReport,
                            enabled = studentCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_export_report_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Export / Share $selectedYear ($studentCount Students)")
                        }
                        FilledTonalButton(
                            onClick = onOpenPromotionManager,
                            enabled = studentCount > 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("settings_promotion_rolls_button")
                        ) {
                            Icon(Icons.Rounded.SwapVert, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Manage Promotion & Roll Positions")
                        }
                        if (isAdmin) {
                            OutlinedButton(
                                onClick = { showPromoteDialog = true },
                                enabled = studentCount > 0 && selectedYear < 2030,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("settings_promote_year_button")
                            ) {
                                Icon(Icons.Rounded.Upgrade, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Year-End Promotion (31 Dec -> ${selectedYear + 1})")
                            }
                        }
                    }
                }
            }
        }
    }

    // Password-Protected Admin Transfer Confirmation Dialog
    pendingAdminTarget?.let { (targetDeviceId, targetName) ->
        AlertDialog(
            onDismissRequest = { pendingAdminTarget = null },
            title = { Text("Transfer Admin Role?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "You are transferring School Admin control to $targetName. You will become a regular Teacher user and will no longer be able to approve pending changes."
                    )
                    OutlinedTextField(
                        value = adminConfirmPassword,
                        onValueChange = {
                            adminConfirmPassword = it
                            adminTransferError = null
                        },
                        label = { Text("Enter Your Admin Password to Confirm") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = adminTransferError != null,
                        modifier = Modifier.fillMaxWidth()
                    )
                    adminTransferError?.let { err ->
                        Text(
                            text = err,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            adminConfirmPassword.length < 4 -> {
                                adminTransferError = "Enter your password (at least 4 characters) to confirm."
                            }
                            onVerifyAdminPassword != null && !onVerifyAdminPassword(adminConfirmPassword) -> {
                                adminTransferError = "Incorrect Admin password. Transfer cancelled."
                            }
                            else -> {
                                pendingAdminTarget = null
                                onTransferAdmin(targetDeviceId, targetName)
                            }
                        }
                    }
                ) {
                    Text("Confirm Transfer")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAdminTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reset Teachers Roster Confirmation Dialog
    if (showResetTeachersDialog) {
        AlertDialog(
            onDismissRequest = { showResetTeachersDialog = false },
            title = { Text("Reset Teachers List?") },
            text = {
                Text("This removes all duplicate/old teacher accounts from the Teachers Panel while keeping you as Admin and preserving 100% of student, subject, and marksheet data.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetTeachersDialog = false
                        onResetTeachersPanel?.invoke()
                    }
                ) {
                    Text("Reset Teachers")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetTeachersDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showExamSetupDialog) {
        MarksheetCustomizerDialog(
            examConfigs = examConfigs,
            schoolSubjects = subjects,
            isAdmin = isAdmin,
            isSaving = isSaving,
            onDismiss = { showExamSetupDialog = false },
            onSave = { nextConfigs ->
                onSaveExamConfigs(nextConfigs) {
                    showExamSetupDialog = false
                }
            }
        )
    }

    if (showPromoteDialog) {
        AlertDialog(
            onDismissRequest = { showPromoteDialog = false },
            title = { Text("End-of-Year Promotion ($selectedYear -> ${selectedYear + 1})?") },
            text = {
                Text(
                    "Applies the English calendar year-end (31 Dec) promotion: students marked 'Promoted' (Pre-Primary through Class 4) move to the next class in ${selectedYear + 1} with their updated roll numbers, Class 5 promoted students are marked 'Passed Out', and 'Not Promoted' students remain in their current class. Fixed identity & photos stay intact."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPromoteDialog = false
                        onPromoteToNextYear(selectedYear + 1)
                    }
                ) {
                    Text("Run Promotion")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPromoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}