package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassLevels
import com.example.data.model.StudentRecord
import com.example.ui.theme.JetBrainsMonoFontFamily

private data class PromotionRowDraft(
    val currentRoll: String,
    val nextYearRoll: String,
    val status: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromotionRollManagerScreen(
    allStudents: List<StudentRecord>,
    selectedYear: Int,
    initialSelectedClass: String?,
    isAdmin: Boolean,
    isSaving: Boolean,
    onSaveBatch: (Map<String, Map<String, Any>>, onSuccess: () -> Unit) -> Unit,
    onRunPromotion: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedClass by remember { mutableStateOf(initialSelectedClass ?: "Class 1") }
    val orderedIds = remember { mutableStateListOf<String>() }
    val drafts = remember { mutableStateMapOf<String, PromotionRowDraft>() }
    var showConfirmRunDialog by remember { mutableStateOf(false) }

    val classStudents = remember(allStudents, selectedClass) {
        allStudents
            .filter { it.classLevel == selectedClass && it.id.isNotBlank() }
            .sortedWith(
                compareBy<StudentRecord> { it.rollSortKey() }
                    .thenBy { it.rollNumber }
                    .thenBy { it.studentNameEn.lowercase() }
            )
    }

    LaunchedEffect(classStudents) {
        orderedIds.clear()
        drafts.clear()
        classStudents.forEach { stu ->
            orderedIds.add(stu.id)
            drafts[stu.id] = PromotionRowDraft(
                currentRoll = stu.rollNumber,
                nextYearRoll = stu.nextYearRollNumber.ifBlank { stu.rollNumber },
                status = stu.promotionStatus.ifBlank { "Studying" }
            )
        }
    }

    val studentById = remember(classStudents) { classStudents.associateBy { it.id } }

    fun autoNumberRolls() {
        orderedIds.forEachIndexed { idx, id ->
            val numbered = (idx + 1).toString().padStart(2, '0')
            val old = drafts[id] ?: return@forEachIndexed
            drafts[id] = old.copy(currentRoll = numbered, nextYearRoll = numbered)
        }
    }

    fun movePosition(index: Int, delta: Int) {
        val targetIndex = index + delta
        if (index !in orderedIds.indices || targetIndex !in orderedIds.indices) return
        val item = orderedIds.removeAt(index)
        orderedIds.add(targetIndex, item)
    }

    fun saveAllChanges(afterSave: () -> Unit = {}) {
        val batchMap = mutableMapOf<String, Map<String, Any>>()
        classStudents.forEach { original ->
            val draft = drafts[original.id] ?: return@forEach
            val nextCurrRoll = draft.currentRoll.trim().ifBlank { original.rollNumber }
            val nextNextRoll = draft.nextYearRoll.trim()
            val nextStatus = draft.status.trim().ifBlank { "Studying" }
            if (nextCurrRoll != original.rollNumber ||
                nextNextRoll != original.nextYearRollNumber ||
                nextStatus != original.promotionStatus
            ) {
                batchMap[original.id] = mapOf(
                    "rollNumber" to nextCurrRoll,
                    "nextYearRollNumber" to nextNextRoll,
                    "promotionStatus" to nextStatus
                )
            }
        }
        if (batchMap.isEmpty()) {
            afterSave()
            return
        }
        onSaveBatch(batchMap, afterSave)
    }

    val classIdx = ClassLevels.ALL.indexOf(selectedClass)
    val nextClassLabel = if (classIdx in 0 until ClassLevels.ALL.lastIndex) {
        ClassLevels.ALL[classIdx + 1]
    } else {
        "Passed Out"
    }

    val promotedCount = drafts.values.count { it.status == "Promoted" }
    val notPromotedCount = drafts.values.count { it.status == "Not Promoted" }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Promotion & Roll Positions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$selectedClass • $selectedYear -> ${selectedYear + 1}",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("promo_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Auto Calendar-Year End Promotion (31 Dec)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "• Promoted -> automatically moves to $nextClassLabel after 31 Dec.\n• Not Promoted -> stays in $selectedClass.\n• Use arrows or edit Roll / Next-Year Position fields freely.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ClassLevels.ALL.forEach { cls ->
                            FilterChip(
                                selected = selectedClass == cls,
                                onClick = { selectedClass = cls },
                                label = { Text(cls) },
                                modifier = Modifier.testTag("promo_class_${cls.replace(" ", "_").lowercase()}")
                            )
                        }
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${classStudents.size} Student(s) • $promotedCount Promoted • $notPromotedCount Stay",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { autoNumberRolls() },
                            enabled = orderedIds.isNotEmpty(),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("promo_autonumber_button")
                        ) {
                            Icon(Icons.Rounded.FormatListNumbered, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Auto-Number 01..N", maxLines = 1)
                        }
                    }
                }

                if (orderedIds.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No students found in $selectedClass for $selectedYear.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    itemsIndexed(orderedIds, key = { _, id -> id }) { idx, stuId ->
                        val student = studentById[stuId] ?: return@itemsIndexed
                        val draft = drafts[stuId] ?: PromotionRowDraft(
                            currentRoll = student.rollNumber,
                            nextYearRoll = student.nextYearRollNumber,
                            status = student.promotionStatus
                        )

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
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
                                            text = "${idx + 1}. ${student.studentNameEn}",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (draft.status == "Promoted") {
                                                "Moves -> $nextClassLabel (Roll ${draft.nextYearRoll.ifBlank { draft.currentRoll }})"
                                            } else {
                                                "Remains in ${student.classLevel} (Roll ${draft.nextYearRoll.ifBlank { draft.currentRoll }})"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (draft.status == "Promoted") MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = { movePosition(idx, -1) },
                                            enabled = idx > 0,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.ArrowUpward, contentDescription = "Move up")
                                        }
                                        IconButton(
                                            onClick = { movePosition(idx, +1) },
                                            enabled = idx < orderedIds.lastIndex,
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Rounded.ArrowDownward, contentDescription = "Move down")
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = draft.currentRoll,
                                        onValueChange = { v -> drafts[stuId] = draft.copy(currentRoll = v) },
                                        label = { Text("$selectedYear Roll") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = draft.nextYearRoll,
                                        onValueChange = { v -> drafts[stuId] = draft.copy(nextYearRoll = v) },
                                        label = { Text("${selectedYear + 1} Position/Roll") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("Studying", "Promoted", "Not Promoted").forEach { option ->
                                        FilterChip(
                                            selected = draft.status == option,
                                            onClick = { drafts[stuId] = draft.copy(status = option) },
                                            label = { Text(option) },
                                            modifier = Modifier.testTag("promo_status_${stuId}_${option.replace(" ", "_").lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { saveAllChanges() },
                            enabled = !isSaving && classStudents.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("promo_save_batch_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isAdmin) "Save Positions & Status" else "Submit to Admin", maxLines = 1)
                            }
                        }
                        if (isAdmin) {
                            OutlinedButton(
                                onClick = {
                                    saveAllChanges {
                                        showConfirmRunDialog = true
                                    }
                                },
                                enabled = !isSaving && allStudents.isNotEmpty() && selectedYear < 2030,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("promo_run_year_rollover_button")
                            ) {
                                Icon(Icons.Rounded.Upgrade, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Apply to ${selectedYear + 1}", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showConfirmRunDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmRunDialog = false },
            title = { Text("Apply Promotion & Roll Positions to ${selectedYear + 1}?") },
            text = {
                Text(
                    "Promoted students move to the next class (Class 5 becomes 'Passed Out'), 'Not Promoted' students remain in their current class, and every student takes their assigned ${selectedYear + 1} Roll / Serial position. All identities, photos, and notes are preserved."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirmRunDialog = false
                        onRunPromotion(selectedYear + 1)
                    }
                ) {
                    Text("Apply Promotion")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRunDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
