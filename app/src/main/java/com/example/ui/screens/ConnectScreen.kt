package com.example.ui.screens

import android.content.Intent
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassLevels
import com.example.data.model.ParentNotice
import com.example.data.model.SchoolChatMessage
import com.example.data.model.SchoolWorkspace
import com.example.data.model.StudentRecord
import com.example.data.model.TeacherProfile
import com.example.messaging.ParentMessagingHelper
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.UiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ConnectSubTab(val label: String) {
    TEACHERS_CHAT("Teachers Chat"),
    MESSAGE_PARENTS("Message Parents")
}

private enum class ParentAudience(val code: String, val label: String) {
    INDIVIDUAL("INDIVIDUAL", "Individual Parent"),
    ALL_SCHOOL("ALL_SCHOOL", "Whole School"),
    CLASS("CLASS", "Specific Class")
}

private val CHAT_TAGS = listOf(
    "General",
    "Class Routine",
    "Exam / Marks",
    "Stipend / Govt",
    "Urgent"
)

@Composable
fun ConnectScreen(
    schoolId: String,
    deviceId: String,
    teacherName: String,
    schoolWorkspace: SchoolWorkspace?,
    allStudents: List<StudentRecord>,
    messagesState: UiState<List<SchoolChatMessage>>,
    parentNoticesState: UiState<List<ParentNotice>>,
    isSaving: Boolean,
    onSendChatMessage: (tag: String, text: String, onSuccess: () -> Unit) -> Unit,
    onEditChatMessage: (messageId: String, newText: String, newTag: String, onSuccess: () -> Unit) -> Unit,
    onDeleteChatMessage: (messageId: String) -> Unit,
    onSaveParentNotice: (ParentNotice, onSuccess: () -> Unit) -> Unit,
    onDeleteParentNotice: (ParentNotice) -> Unit,
    modifier: Modifier = Modifier
) {
    var subTab by remember { mutableStateOf(ConnectSubTab.TEACHERS_CHAT) }
    val teacherProfile = remember(schoolWorkspace, teacherName) {
        schoolWorkspace?.toTeacherProfileView(teacherName)
    }

    Column(modifier = modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ConnectSubTab.entries.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = subTab == tab,
                    onClick = { subTab = tab },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ConnectSubTab.entries.size
                    ),
                    icon = {
                        Icon(
                            imageVector = if (tab == ConnectSubTab.TEACHERS_CHAT) Icons.Rounded.Forum else Icons.Rounded.Campaign,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    modifier = Modifier.testTag("connect_tab_${tab.name.lowercase()}")
                ) {
                    Text(tab.label, maxLines = 1)
                }
            }
        }

        when (subTab) {
            ConnectSubTab.TEACHERS_CHAT -> TeachersChatRoomContent(
                schoolId = schoolId,
                deviceId = deviceId,
                schoolWorkspace = schoolWorkspace,
                roomMessagesState = messagesState,
                onSendChatMessage = onSendChatMessage,
                onEditChatMessage = onEditChatMessage,
                onDeleteChatMessage = onDeleteChatMessage,
                modifier = Modifier.weight(1f)
            )

            ConnectSubTab.MESSAGE_PARENTS -> MessageParentsContent(
                teacherProfile = teacherProfile,
                allStudents = allStudents,
                parentNoticesState = parentNoticesState,
                isSaving = isSaving,
                onSaveParentNotice = onSaveParentNotice,
                onDeleteParentNotice = onDeleteParentNotice,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TeachersChatRoomContent(
    schoolId: String,
    deviceId: String,
    schoolWorkspace: SchoolWorkspace?,
    roomMessagesState: UiState<List<SchoolChatMessage>>,
    onSendChatMessage: (tag: String, text: String, onSuccess: () -> Unit) -> Unit,
    onEditChatMessage: (messageId: String, newText: String, newTag: String, onSuccess: () -> Unit) -> Unit,
    onDeleteChatMessage: (messageId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var messageText by remember { mutableStateOf("") }
    var selectedTag by remember { mutableStateOf(CHAT_TAGS.first()) }
    var editingMessage by remember { mutableStateOf<SchoolChatMessage?>(null) }

    val schoolName = schoolWorkspace?.schoolName?.ifBlank { "My School" } ?: "My School"
    val teacherCount = schoolWorkspace?.teachers?.size ?: 1
    val liveMessages = (roomMessagesState as? UiState.Success)?.data.orEmpty()
    val mergedMessages = remember(liveMessages, schoolWorkspace?.recentMessages, schoolId) {
        val liveIds = liveMessages.map { it.id }.toSet()
        val fallbackFromRecent = schoolWorkspace?.recentMessages.orEmpty()
            .filter { it.id !in liveIds }
            .map { entry ->
                SchoolChatMessage(
                    id = entry.id,
                    roomId = schoolId,
                    schoolId = schoolId,
                    senderUid = entry.senderUid,
                    senderName = entry.senderName,
                    tag = entry.tag,
                    text = entry.text,
                    editedFlag = false
                )
            }
        (fallbackFromRecent + liveMessages)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = schoolName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "School ID: $schoolId • $teacherCount teacher(s) connected",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(schoolId))
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join $schoolName in My School app using Unique School ID: $schoolId"
                            )
                        }
                        context.startActivity(Intent.createChooser(share, "Share Unique School ID"))
                    }
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share Unique School ID")
                }
            }
        }

        when (roomMessagesState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(roomMessagesState.message, color = MaterialTheme.colorScheme.error)
                }
            }

            is UiState.Success -> {
                if (mergedMessages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                Icons.Rounded.Forum,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "No messages in School $schoolId yet",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "All teachers who enter Unique School ID '$schoolId' can message here together in real time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(mergedMessages, key = { it.id }) { msg ->
                            val isMine = msg.senderUid == deviceId
                            ChatMessageBubble(
                                message = msg,
                                isMine = isMine,
                                onEdit = { editingMessage = msg },
                                onDelete = { onDeleteChatMessage(msg.id) }
                            )
                        }
                    }
                }
            }
        }

        Surface(
            tonalElevation = 4.dp,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CHAT_TAGS.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(tag) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Message all teachers in school…") },
                        maxLines = 3,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("chat_message_input")
                    )
                    Button(
                        onClick = {
                            val trimmed = messageText.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendChatMessage(selectedTag, trimmed) {
                                    messageText = ""
                                }
                            }
                        },
                        enabled = messageText.isNotBlank(),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("send_chat_message_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
                    }
                }
            }
        }
    }

    val toEdit = editingMessage
    if (toEdit != null) {
        var editBody by remember(toEdit.id) { mutableStateOf(toEdit.text) }
        var editTag by remember(toEdit.id) { mutableStateOf(toEdit.tag.ifBlank { "General" }) }
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CHAT_TAGS.forEach { tag ->
                            FilterChip(
                                selected = editTag == tag,
                                onClick = { editTag = tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = editBody,
                        onValueChange = { editBody = it },
                        label = { Text("Message") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (editBody.isNotBlank()) {
                            onEditChatMessage(toEdit.id, editBody, editTag) {
                                editingMessage = null
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatMessageBubble(
    message: SchoolChatMessage,
    isMine: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val timeText = remember(message.createdAt) {
        val date = message.createdAt?.toDate()
        if (date != null) SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(date) else "Just now"
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isMine) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (isMine) "${message.senderName} (You)" else message.senderName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (message.tag.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = message.tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                if (isMine) {
                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                            Icon(Icons.Rounded.Edit, contentDescription = "Edit message", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete message",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (message.editedFlag) "$timeText • edited" else timeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MessageParentsContent(
    teacherProfile: TeacherProfile?,
    allStudents: List<StudentRecord>,
    parentNoticesState: UiState<List<ParentNotice>>,
    isSaving: Boolean,
    onSaveParentNotice: (ParentNotice, onSuccess: () -> Unit) -> Unit,
    onDeleteParentNotice: (ParentNotice) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var audience by remember { mutableStateOf(ParentAudience.INDIVIDUAL) }
    var targetClass by remember { mutableStateOf(ClassLevels.ALL[1]) }
    var studentSearch by remember { mutableStateOf("") }
    var selectedStudentId by remember { mutableStateOf(allStudents.firstOrNull()?.id ?: "") }
    var manualPhone by remember { mutableStateOf(allStudents.firstOrNull()?.guardianPhone ?: "") }
    var noticeTitle by remember { mutableStateOf("") }
    var noticeBody by remember { mutableStateOf("") }
    var editingNotice by remember { mutableStateOf<ParentNotice?>(null) }

    val matchingStudents = remember(allStudents, studentSearch) {
        val q = studentSearch.trim().lowercase()
        if (q.isBlank()) allStudents.take(25)
        else allStudents.filter {
            it.studentNameEn.lowercase().contains(q) ||
                it.rollNumber.lowercase().contains(q) ||
                it.guardianPhone.contains(q)
        }.take(25)
    }

    val selectedStudent = allStudents.firstOrNull { it.id == selectedStudentId } ?: allStudents.firstOrNull()

    val recipientPhones: List<String> = remember(audience, targetClass, allStudents, selectedStudent, manualPhone) {
        when (audience) {
            ParentAudience.INDIVIDUAL -> listOfNotNull(
                manualPhone.ifBlank { selectedStudent?.guardianPhone }?.takeIf { it.isNotBlank() }
            )
            ParentAudience.ALL_SCHOOL -> allStudents.mapNotNull {
                it.guardianPhone.trim().takeIf { p -> p.isNotBlank() }
            }.distinct()
            ParentAudience.CLASS -> allStudents
                .filter { it.classLevel == targetClass }
                .mapNotNull { it.guardianPhone.trim().takeIf { p -> p.isNotBlank() } }
                .distinct()
        }
    }

    val targetLabel = when (audience) {
        ParentAudience.INDIVIDUAL -> selectedStudent?.let { "${it.studentNameEn} (${it.classLevel}, Roll ${it.rollNumber})" }
            ?: "Individual Parent"
        ParentAudience.ALL_SCHOOL -> "Whole School Parents (${allStudents.size} students)"
        ParentAudience.CLASS -> "$targetClass Parents (${allStudents.count { it.classLevel == targetClass }} students)"
    }

    val schoolHeader = teacherProfile?.schoolName?.ifBlank { "Govt. Primary School" } ?: "Govt. Primary School"
    val formattedMessage = buildString {
        appendLine("[$schoolHeader]")
        if (noticeTitle.isNotBlank()) appendLine("*${noticeTitle.trim()}*")
        append(noticeBody.trim())
    }

    fun logNotice(channel: String) {
        if (noticeBody.isBlank()) return
        onSaveParentNotice(
            ParentNotice(
                audienceType = audience.code,
                targetLabel = targetLabel,
                recipientCount = recipientPhones.size.toLong().coerceAtLeast(if (audience == ParentAudience.INDIVIDUAL) 1L else 0L),
                channel = channel,
                title = noticeTitle.trim(),
                messageBody = noticeBody.trim()
            )
        ) { }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                        text = "Send Message to Parents",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ParentAudience.entries.forEach { option ->
                            FilterChip(
                                selected = audience == option,
                                onClick = { audience = option },
                                label = { Text(option.label) },
                                modifier = Modifier.testTag("parent_audience_${option.code.lowercase()}")
                            )
                        }
                    }

                    when (audience) {
                        ParentAudience.INDIVIDUAL -> {
                            OutlinedTextField(
                                value = studentSearch,
                                onValueChange = { studentSearch = it },
                                label = { Text("Find Student (Name, Roll, Phone)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (matchingStudents.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    matchingStudents.forEach { s ->
                                        val isSelected = selectedStudent?.id == s.id
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                selectedStudentId = s.id
                                                manualPhone = s.guardianPhone
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (isSelected) Icons.Rounded.Check else Icons.Rounded.Person,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            label = { Text("${s.studentNameEn} (${ClassLevels.shortLabel(s.classLevel)}-${s.rollNumber})") }
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = manualPhone,
                                onValueChange = { manualPhone = it },
                                label = { Text("Guardian Phone Number") },
                                placeholder = { Text("017xxxxxxxx") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("parent_phone_input")
                            )
                        }

                        ParentAudience.CLASS -> {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ClassLevels.ALL.forEach { cls ->
                                    FilterChip(
                                        selected = targetClass == cls,
                                        onClick = { targetClass = cls },
                                        label = { Text(cls) }
                                    )
                                }
                            }
                        }

                        ParentAudience.ALL_SCHOOL -> Unit
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "Target: $targetLabel • ${recipientPhones.size} guardian phone number(s) found",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }

                    Text("Quick Templates", style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val templates = listOf(
                            "Parent Meeting" to "Dear Guardian, a parent-teacher meeting will be held at school on Thursday at 10:00 AM. Your presence is kindly requested.",
                            "Attendance Alert" to "Dear Guardian, please ensure regular school attendance for your child this week.",
                            "Exam Notice" to "Dear Guardian, upcoming class assessments begin next Sunday. Please help your child prepare at home.",
                            "Stipend Update" to "Dear Guardian, please verify your student's stipend & guardian mobile number with the class teacher."
                        )
                        templates.forEach { (title, body) ->
                            FilterChip(
                                selected = noticeTitle == title,
                                onClick = {
                                    noticeTitle = title
                                    noticeBody = body
                                },
                                label = { Text(title) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noticeTitle,
                        onValueChange = { noticeTitle = it },
                        label = { Text("Subject / Title (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = noticeBody,
                        onValueChange = { noticeBody = it },
                        label = { Text("Message to Parents") },
                        placeholder = { Text("Write announcement, homework, or student feedback…") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("parent_message_body_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                logNotice("WHATSAPP")
                                ParentMessagingHelper.sendWhatsAppToParentOrBroadcast(
                                    context = context,
                                    singlePhone = if (audience == ParentAudience.INDIVIDUAL) recipientPhones.firstOrNull() else null,
                                    message = formattedMessage
                                )
                            },
                            enabled = noticeBody.isNotBlank() && !isSaving,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("parent_send_whatsapp_button")
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("WhatsApp", maxLines = 1)
                        }

                        FilledTonalButton(
                            onClick = {
                                logNotice("SMS")
                                ParentMessagingHelper.sendSmsToRecipients(
                                    context = context,
                                    phones = recipientPhones,
                                    message = formattedMessage
                                )
                            },
                            enabled = noticeBody.isNotBlank() && !isSaving,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("parent_send_sms_button")
                        ) {
                            Icon(Icons.Rounded.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("SMS App", maxLines = 1)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            logNotice("SHARE")
                            ParentMessagingHelper.shareGeneric(context, formattedMessage)
                        },
                        enabled = noticeBody.isNotBlank() && !isSaving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text("Save & Share via Any App")
                    }
                }
            }
        }

        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Text(
                text = "Sent Parent Notices Log (Editable & Removable)",
                style = MaterialTheme.typography.titleMedium
            )
        }

        when (parentNoticesState) {
            is UiState.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> item {
                Text(parentNoticesState.message, color = MaterialTheme.colorScheme.error)
            }

            is UiState.Success -> {
                val notices = parentNoticesState.data
                if (notices.isEmpty()) {
                    item {
                        Text(
                            text = "No notices logged yet. Any message you send above will be saved here so you can edit or resend anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    items(notices, key = { it.id }) { notice ->
                        ParentNoticeHistoryCard(
                            notice = notice,
                            onEdit = { editingNotice = notice },
                            onDelete = { onDeleteParentNotice(notice) },
                            onResend = {
                                ParentMessagingHelper.shareGeneric(context, notice.messageBody)
                            }
                        )
                    }
                }
            }
        }
    }

    val toEditNotice = editingNotice
    if (toEditNotice != null) {
        var titleDraft by remember(toEditNotice.id) { mutableStateOf(toEditNotice.title) }
        var bodyDraft by remember(toEditNotice.id) { mutableStateOf(toEditNotice.messageBody) }
        AlertDialog(
            onDismissRequest = { editingNotice = null },
            title = { Text("Edit Parent Notice") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = titleDraft,
                        onValueChange = { titleDraft = it },
                        label = { Text("Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bodyDraft,
                        onValueChange = { bodyDraft = it },
                        label = { Text("Message Body") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (bodyDraft.isNotBlank()) {
                            onSaveParentNotice(
                                toEditNotice.copy(
                                    title = titleDraft.trim(),
                                    messageBody = bodyDraft.trim()
                                )
                            ) {
                                editingNotice = null
                            }
                        }
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingNotice = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ParentNoticeHistoryCard(
    notice: ParentNotice,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResend: () -> Unit
) {
    val dateLabel = remember(notice.createdAt) {
        val d = notice.createdAt?.toDate() ?: Date()
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(d)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = notice.channel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = notice.targetLabel,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onResend, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Share, contentDescription = "Resend notice", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit notice", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete notice",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            if (notice.title.isNotBlank()) {
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = notice.messageBody,
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = "$dateLabel • ${notice.recipientCount} recipient(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
