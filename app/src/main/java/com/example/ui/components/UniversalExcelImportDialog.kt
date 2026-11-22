package com.example.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassLevels
import com.example.data.model.CustomFieldDef
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamTermConfig
import com.example.export.BackupParseResult
import com.example.export.ExcelBackupManager
import com.example.export.ExcelColumnRole
import com.example.export.RawWorkbookPreview
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun UniversalExcelImportDialog(
    preview: RawWorkbookPreview,
    examConfigs: List<ExamTermConfig>,
    existingCustomFields: List<CustomFieldDef>,
    selectedYear: Int,
    onDismiss: () -> Unit,
    onConfirmImport: (BackupParseResult) -> Unit
) {
    val strings = LocalAppStrings.current
    val normalizedExams = remember(examConfigs) { ExamDefaults.ensureThreeExams(examConfigs) }

    var selectedSheetIdx by remember(preview) { mutableIntStateOf(0) }
    val activeSheet = preview.sheets[selectedSheetIdx.coerceIn(0, preview.sheets.lastIndex)]

    var headerRowIdx by remember(selectedSheetIdx, preview) {
        mutableIntStateOf(activeSheet.suggestedHeaderRowIndex)
    }
    var defaultClass by remember { mutableStateOf(ClassLevels.ALL[1]) }
    var targetExamId by remember(normalizedExams) { mutableStateOf(normalizedExams.last().examId) }
    var updateMatchedExisting by remember { mutableStateOf(true) }
    var importError by remember { mutableStateOf<String?>(null) }

    val headers = remember(activeSheet, headerRowIdx) {
        val rawHeaders = activeSheet.rows.getOrNull(headerRowIdx).orEmpty()
        val maxCols = maxOf(
            rawHeaders.size,
            activeSheet.rows.drop(headerRowIdx + 1).take(5).maxOfOrNull { it.size } ?: 0
        )
        (0 until maxCols).map { cIdx ->
            rawHeaders.getOrNull(cIdx)?.trim().orEmpty().ifBlank { "Column ${cIdx + 1}" }
        }
    }

    val sampleRow = remember(activeSheet, headerRowIdx) {
        activeSheet.rows.getOrNull(headerRowIdx + 1).orEmpty()
    }

    val columnRoles = remember { mutableStateMapOf<Int, ExcelColumnRole>() }
    LaunchedEffect(activeSheet.sheetName, headerRowIdx) {
        columnRoles.clear()
        headers.forEachIndexed { idx, title ->
            columnRoles[idx] = ExcelBackupManager.guessColumnRole(title)
        }
        importError = null
    }

    val dataRowsCount = (activeSheet.rows.size - (headerRowIdx + 1)).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = strings.universalRestoreTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = strings.universalRestoreSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (preview.sheets.size > 1) {
                    item {
                        Text(
                            text = strings.selectWorksheetTab,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            preview.sheets.forEachIndexed { idx, s ->
                                FilterChip(
                                    selected = idx == selectedSheetIdx,
                                    onClick = { selectedSheetIdx = idx },
                                    label = { Text("${s.sheetName} (${s.rows.size})") }
                                )
                            }
                        }
                    }
                }

                item {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${strings.headerRowLabel}: Row ${headerRowIdx + 1} • $dataRowsCount rows",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = { if (headerRowIdx > 0) headerRowIdx-- },
                                        enabled = headerRowIdx > 0
                                    ) { Text("-1") }
                                    OutlinedButton(
                                        onClick = {
                                            if (headerRowIdx + 1 < activeSheet.rows.lastIndex) headerRowIdx++
                                        },
                                        enabled = headerRowIdx + 1 < activeSheet.rows.lastIndex
                                    ) { Text("+1") }
                                }
                            }

                            Text(
                                text = strings.defaultClassLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                ClassLevels.ALL.forEach { cls ->
                                    FilterChip(
                                        selected = defaultClass == cls,
                                        onClick = { defaultClass = cls },
                                        label = { Text(strings.localizeClass(cls), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            if (columnRoles.values.any { it == ExcelColumnRole.EXAM_SUBJECT_MARK }) {
                                Text(
                                    text = strings.targetExamForMarksLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    normalizedExams.forEach { ex ->
                                        FilterChip(
                                            selected = targetExamId == ex.examId,
                                            onClick = { targetExamId = ex.examId },
                                            label = { Text(strings.localizeExamTitle(ex), style = MaterialTheme.typography.labelSmall) }
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = updateMatchedExisting,
                                    onClick = { updateMatchedExisting = true },
                                    label = { Text(strings.importModeUpdateExisting, style = MaterialTheme.typography.labelSmall) }
                                )
                                FilterChip(
                                    selected = !updateMatchedExisting,
                                    onClick = { updateMatchedExisting = false },
                                    label = { Text(strings.importModeAddAllNew, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                }

                itemsIndexed(headers) { colIdx, colTitle ->
                    val currentRole = columnRoles[colIdx] ?: ExcelColumnRole.IGNORE
                    val sampleVal = sampleRow.getOrNull(colIdx)?.trim().orEmpty().ifBlank { "—" }
                    var menuExpanded by remember { mutableStateOf(false) }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = colTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Sample: $sampleVal",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Box {
                                OutlinedButton(
                                    onClick = { menuExpanded = true },
                                    modifier = Modifier.height(38.dp)
                                ) {
                                    Text(
                                        text = currentRole.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    ExcelColumnRole.entries.forEach { role ->
                                        DropdownMenuItem(
                                            text = { Text(role.label) },
                                            onClick = {
                                                columnRoles[colIdx] = role
                                                menuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!importError.isNullOrBlank()) {
                    item {
                        Text(
                            text = importError.orEmpty(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = runCatching {
                        ExcelBackupManager.buildBackupResultFromMapping(
                            sheet = activeSheet,
                            headerRowIndex = headerRowIdx,
                            columnMappings = columnRoles.toMap(),
                            columnHeaders = headers,
                            defaultClassLevel = defaultClass,
                            defaultAcademicYear = selectedYear,
                            targetExamId = targetExamId,
                            existingCustomFields = existingCustomFields,
                            updateMatchedExisting = updateMatchedExisting,
                            formatLabel = preview.formatLabel
                        )
                    }
                    result.onSuccess { parsed ->
                        onConfirmImport(parsed)
                    }.onFailure { err ->
                        importError = err.message ?: "Could not map columns from Excel sheet."
                    }
                },
                modifier = Modifier.testTag("universal_excel_import_confirm")
            ) {
                Text(strings.restoreNowBtn)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.cancelButton)
            }
        }
    )
}
