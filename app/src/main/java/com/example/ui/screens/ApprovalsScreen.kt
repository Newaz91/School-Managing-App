package com.example.ui.screens

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
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ChangeEntityTypes
import com.example.data.model.ChangeStatuses
import com.example.data.model.PendingChangeRequest
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Locale

private enum class ApprovalFilter(val label: String) {
    PENDING("Pending"),
    HISTORY("Reviewed"),
    ALL("All")
}

@Composable
fun ApprovalsScreen(
    pendingChangesState: UiState<List<PendingChangeRequest>>,
    isAdmin: Boolean,
    adminName: String,
    isSaving: Boolean,
    onApproveChange: (PendingChangeRequest) -> Unit,
    onApproveAll: () -> Unit,
    onRejectChange: (PendingChangeRequest) -> Unit,
    modifier: Modifier = Modifier
) {
    var filter by remember { mutableStateOf(ApprovalFilter.PENDING) }
    val allItems = (pendingChangesState as? UiState.Success)?.data.orEmpty()
    val pendingItems = remember(allItems) { allItems.filter { it.status == ChangeStatuses.PENDING } }
    val historyItems = remember(allItems) { allItems.filter { it.status != ChangeStatuses.PENDING } }

    val displayed = when (filter) {
        ApprovalFilter.PENDING -> pendingItems
        ApprovalFilter.HISTORY -> historyItems
        ApprovalFilter.ALL -> allItems
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdmin) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    }
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
                            imageVector = if (isAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.HourglassTop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (isAdmin) "Admin Confirmation Queue" else "Pending Admin Confirmation",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = if (isAdmin) {
                            "Review additions and edits made by fellow teachers. Only Admin confirmation writes their changes to the live school register."
                        } else {
                            "Your changes and other teachers' updates wait here until confirmed by the School Admin ($adminName)."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isAdmin && pendingItems.isNotEmpty()) {
                        Button(
                            onClick = onApproveAll,
                            enabled = !isSaving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("approvals_confirm_all_button")
                        ) {
                            Icon(Icons.Rounded.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Confirm & Apply All (${pendingItems.size})")
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ApprovalFilter.entries.forEach { option ->
                    val count = when (option) {
                        ApprovalFilter.PENDING -> pendingItems.size
                        ApprovalFilter.HISTORY -> historyItems.size
                        ApprovalFilter.ALL -> allItems.size
                    }
                    FilterChip(
                        selected = filter == option,
                        onClick = { filter = option },
                        label = { Text("${option.label} ($count)") }
                    )
                }
            }
        }

        when (pendingChangesState) {
            is UiState.Loading -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> item {
                Text(
                    text = pendingChangesState.message,
                    color = MaterialTheme.colorScheme.error
                )
            }

            is UiState.Success -> {
                if (displayed.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (filter == ApprovalFilter.PENDING) {
                                        "No pending teacher edits"
                                    } else {
                                        "No review records yet"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "When colleague teachers add or modify students, attendance, subjects, or exam marks, they appear here for Admin confirmation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(displayed, key = { it.id }) { item ->
                        ApprovalRequestCard(
                            change = item,
                            isAdmin = isAdmin,
                            isSaving = isSaving,
                            onApprove = { onApproveChange(item) },
                            onReject = { onRejectChange(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ApprovalRequestCard(
    change: PendingChangeRequest,
    isAdmin: Boolean,
    isSaving: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val entityLabel = when (change.entityType) {
        ChangeEntityTypes.STUDENT_CREATE -> "New Student"
        ChangeEntityTypes.STUDENT_UPDATE -> "Edit Student"
        ChangeEntityTypes.STUDENT_DELETE -> "Remove Student"
        ChangeEntityTypes.STUDENT_BATCH_UPDATE -> "Batch Score Edit"
        ChangeEntityTypes.SUBJECT_SAVE -> "Subject & Fields"
        ChangeEntityTypes.SUBJECT_DELETE -> "Remove Subject"
        ChangeEntityTypes.CUSTOM_FIELD_SAVE -> "Category Field"
        ChangeEntityTypes.CUSTOM_FIELD_DELETE -> "Remove Field"
        ChangeEntityTypes.ATTENDANCE_SAVE -> "Class Attendance"
        ChangeEntityTypes.EXAM_CONFIG_SAVE -> "Marksheet Setup"
        else -> "Change"
    }

    val statusBg = when (change.status) {
        ChangeStatuses.APPROVED -> MaterialTheme.colorScheme.primaryContainer
        ChangeStatuses.REJECTED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val statusFg = when (change.status) {
        ChangeStatuses.APPROVED -> MaterialTheme.colorScheme.onPrimaryContainer
        ChangeStatuses.REJECTED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    val statusText = when (change.status) {
        ChangeStatuses.APPROVED -> "CONFIRMED"
        ChangeStatuses.REJECTED -> "REJECTED"
        else -> "PENDING"
    }

    val dateStr = remember(change.createdAt) {
        val d = change.createdAt?.toDate()
        if (d != null) SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(d) else "Just now"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
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
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = entityLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = statusText,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = change.summary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            if (change.detailsText.isNotBlank()) {
                Text(
                    text = change.detailsText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = buildString {
                    append("Submitted by ${change.submittedByName} • $dateStr")
                    if (change.reviewedByName.isNotBlank()) {
                        append(" • Reviewed by ${change.reviewedByName}")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isAdmin && change.status == ChangeStatuses.PENDING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onReject,
                        enabled = !isSaving,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier
                            .weight(0.9f)
                            .height(48.dp)
                            .testTag("reject_change_${change.id}")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reject")
                    }
                    Button(
                        onClick = onApprove,
                        enabled = !isSaving,
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("approve_change_${change.id}")
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Confirm & Apply")
                    }
                }
            }
        }
    }
}
