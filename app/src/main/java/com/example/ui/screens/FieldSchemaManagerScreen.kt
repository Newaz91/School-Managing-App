package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AssignmentTurnedIn
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomFieldCategories
import com.example.data.model.CustomFieldDef
import com.example.data.model.CustomFieldTypes
import com.example.data.model.GradeScopes
import com.example.data.model.SubjectDefinition
import com.example.data.model.SubjectFieldDef

private enum class SchemaMenuTab(val label: String) {
    SUBJECTS("Subjects & Fields"),
    CATEGORIES("Student Categories")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldSchemaManagerScreen(
    subjects: List<SubjectDefinition>,
    customFields: List<CustomFieldDef>,
    initialSelectedClass: String = "Class 5",
    onSaveSubject: (SubjectDefinition) -> Unit,
    onDeleteSubject: (SubjectDefinition) -> Unit,
    onAddSubjectField: (SubjectDefinition, String, String, Long) -> Unit,
    onUpdateSubjectField: (SubjectDefinition, SubjectFieldDef) -> Unit,
    onDeleteSubjectField: (SubjectDefinition, String) -> Unit,
    onSaveCustomField: (CustomFieldDef) -> Unit,
    onDeleteCustomField: (CustomFieldDef) -> Unit,
    onAssessWholeClass: (classLevel: String, subjectId: String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var activeMenuTab by remember { mutableStateOf(SchemaMenuTab.SUBJECTS) }
    var selectedClass by remember { mutableStateOf(initialSelectedClass) }
    var newSubjectName by remember { mutableStateOf("") }

    var editingSubject by remember { mutableStateOf<SubjectDefinition?>(null) }
    var editingSubjectFieldPair by remember { mutableStateOf<Pair<SubjectDefinition, SubjectFieldDef>?>(null) }

    var editingCustomField by remember { mutableStateOf<CustomFieldDef?>(null) }
    var showCreateCustomField by remember { mutableStateOf(false) }

    val classSubjects = remember(subjects, selectedClass) {
        if (selectedClass == GradeScopes.ALL_CLASSES) subjects
        else subjects.filter { it.appliesToClass(selectedClass) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subjects & Assessment Setup") },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("schema_back_button")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SchemaMenuTab.entries.forEachIndexed { index, tab ->
                    SegmentedButton(
                        selected = activeMenuTab == tab,
                        onClick = { activeMenuTab = tab },
                        shape = SegmentedButtonDefaults.itemShape(index, SchemaMenuTab.entries.size),
                        icon = {
                            Icon(
                                imageVector = if (tab == SchemaMenuTab.SUBJECTS) Icons.Rounded.MenuBook else Icons.Rounded.Category,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    ) {
                        Text(tab.label, maxLines = 1)
                    }
                }
            }

            if (activeMenuTab == SchemaMenuTab.SUBJECTS) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Define Class Subjects & Assessment Fields",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "1. Pick a class (e.g. Class 5) and add your own subjects (e.g. English, Bangla).\n2. Add dedicated assessment fields with '+' (e.g. Reading text, Grammar, Paragraph writing).\n3. Every student in that class gets those fields automatically — no need to add fields student by student.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    // Step 1: Class Filter
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Step 1: Select Class",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val scopeChips = GradeScopes.SINGLE_CLASSES + GradeScopes.ALL_CLASSES
                                scopeChips.forEach { cls ->
                                    val count = if (cls == GradeScopes.ALL_CLASSES) subjects.size
                                    else subjects.count { it.appliesToClass(cls) }
                                    FilterChip(
                                        selected = selectedClass == cls,
                                        onClick = { selectedClass = cls },
                                        label = { Text("$cls ($count)") },
                                        modifier = Modifier.testTag("subject_class_chip_${cls.lowercase().replace(" ", "_")}")
                                    )
                                }
                            }
                        }
                    }

                    // Step 2: Inline Create Subject for Selected Class
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Step 2: Add Subject for $selectedClass",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = newSubjectName,
                                        onValueChange = { newSubjectName = it },
                                        placeholder = { Text("Type subject name (e.g. English, Bangla)") },
                                        singleLine = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("new_subject_name_input")
                                    )
                                    FilledIconButton(
                                        onClick = {
                                            val clean = newSubjectName.trim()
                                            if (clean.isNotEmpty()) {
                                                onSaveSubject(
                                                    SubjectDefinition(
                                                        name = clean,
                                                        gradeScope = selectedClass,
                                                        fields = emptyList()
                                                    )
                                                )
                                                newSubjectName = ""
                                            }
                                        },
                                        enabled = newSubjectName.isNotBlank(),
                                        modifier = Modifier
                                            .size(52.dp)
                                            .testTag("add_subject_button")
                                    ) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Create subject")
                                    }
                                }
                            }
                        }
                    }

                    // Subject List & Inline '+' Assessment Fields
                    if (classSubjects.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "No subjects added for $selectedClass yet. Enter a subject name above and tap '+' to create it, then add assessment fields inside it.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(classSubjects, key = { it.id }) { subject ->
                            SubjectAssessmentSetupCard(
                                subject = subject,
                                activeClassScope = if (selectedClass == GradeScopes.ALL_CLASSES) {
                                    GradeScopes.SINGLE_CLASSES.firstOrNull { subject.appliesToClass(it) } ?: "Class 5"
                                } else selectedClass,
                                onEditSubject = { editingSubject = subject },
                                onDeleteSubject = { onDeleteSubject(subject) },
                                onAddField = { label, type, maxMarks ->
                                    onAddSubjectField(subject, label, type, maxMarks)
                                },
                                onEditField = { field ->
                                    editingSubjectFieldPair = subject to field
                                },
                                onDeleteField = { fieldId ->
                                    onDeleteSubjectField(subject, fieldId)
                                },
                                onAssessWholeClass = { targetClass ->
                                    onAssessWholeClass(targetClass, subject.id)
                                }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Category Fields (${customFields.size})", style = MaterialTheme.typography.titleLarge)
                            Button(
                                onClick = { showCreateCustomField = true },
                                modifier = Modifier.testTag("add_custom_field_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Add Field")
                            }
                        }
                    }

                    if (customFields.isEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "No extra student profile fields created yet. Tap 'Add Field' to create flexible fields under Birth & Health, Stipend, Family, or any custom category.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        items(customFields, key = { it.id }) { cf ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cf.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${cf.category} • ${CustomFieldTypes.label(cf.fieldType)}${if (cf.resetsYearly) " • Yearly Reset" else ""}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = { editingCustomField = cf }) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Edit ${cf.label}")
                                        }
                                        IconButton(onClick = { onDeleteCustomField(cf) }) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Delete ${cf.label}", tint = MaterialTheme.colorScheme.error)
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

    val editSub = editingSubject
    if (editSub != null) {
        SubjectFormDialog(
            initial = editSub,
            onDismiss = { editingSubject = null },
            onSave = { updated ->
                onSaveSubject(updated)
                editingSubject = null
            }
        )
    }

    val editPair = editingSubjectFieldPair
    if (editPair != null) {
        SubjectFieldFormDialog(
            subjectName = editPair.first.name,
            initial = editPair.second,
            onDismiss = { editingSubjectFieldPair = null },
            onSave = { label, type, maxMarks ->
                onUpdateSubjectField(
                    editPair.first,
                    editPair.second.copy(label = label, fieldType = type, maxMarks = maxMarks)
                )
                editingSubjectFieldPair = null
            }
        )
    }

    if (showCreateCustomField || editingCustomField != null) {
        CustomFieldFormDialog(
            initial = editingCustomField,
            existingCategories = customFields.map { it.category }.distinct(),
            onDismiss = {
                showCreateCustomField = false
                editingCustomField = null
            },
            onSave = { cf ->
                onSaveCustomField(cf)
                showCreateCustomField = false
                editingCustomField = null
            }
        )
    }
}

@Composable
private fun SubjectAssessmentSetupCard(
    subject: SubjectDefinition,
    activeClassScope: String,
    onEditSubject: () -> Unit,
    onDeleteSubject: () -> Unit,
    onAddField: (label: String, fieldType: String, maxMarks: Long) -> Unit,
    onEditField: (SubjectFieldDef) -> Unit,
    onDeleteField: (String) -> Unit,
    onAssessWholeClass: (String) -> Unit
) {
    var inlineFieldLabel by remember(subject.id) { mutableStateOf("") }
    var inlineFieldType by remember(subject.id) { mutableStateOf(CustomFieldTypes.NUMBER) }
    var inlineMaxMarks by remember(subject.id) { mutableStateOf("20") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(subject.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "${subject.gradeScope} • ${subject.fields.size} assessment field(s) • Applies to whole class",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Row {
                    IconButton(onClick = onEditSubject) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit ${subject.name}")
                    }
                    IconButton(onClick = onDeleteSubject) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Delete ${subject.name}", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (subject.fields.isEmpty()) {
                Text(
                    text = "Add dedicated assessment fields below (e.g. Identifying the alphabet, Reading text, Answering question, Grammar, Paragraph writing).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                subject.fields.forEachIndexed { idx, field ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${idx + 1}. ${field.label}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val typeDesc = when (field.fieldType) {
                                    CustomFieldTypes.NUMBER -> "Marks / Number (Max ${field.maxMarks})"
                                    else -> CustomFieldTypes.label(field.fieldType)
                                }
                                Text(
                                    text = typeDesc,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { onEditField(field) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${field.label}", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteField(field.fieldId) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete ${field.label}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Automatic inline '+' assessment field creator
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Add Assessment Field to ${subject.name} (tap '+' to add more):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CustomFieldTypes.SUBJECT_TYPES.forEach { type ->
                        FilterChip(
                            selected = inlineFieldType == type,
                            onClick = { inlineFieldType = type },
                            label = { Text(CustomFieldTypes.label(type)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inlineFieldLabel,
                        onValueChange = { inlineFieldLabel = it },
                        placeholder = { Text("e.g. Reading text, Creating sentence") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inline_field_input_${subject.id}")
                    )
                    if (inlineFieldType == CustomFieldTypes.NUMBER) {
                        OutlinedTextField(
                            value = inlineMaxMarks,
                            onValueChange = { inlineMaxMarks = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Max") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.width(74.dp)
                        )
                    }
                    FilledIconButton(
                        onClick = {
                            val trimmed = inlineFieldLabel.trim()
                            if (trimmed.isNotEmpty()) {
                                val max = if (inlineFieldType == CustomFieldTypes.NUMBER) {
                                    inlineMaxMarks.toLongOrNull()?.coerceIn(1L, 1000L) ?: 20L
                                } else 0L
                                onAddField(trimmed, inlineFieldType, max)
                                inlineFieldLabel = ""
                            }
                        },
                        enabled = inlineFieldLabel.isNotBlank(),
                        modifier = Modifier
                            .size(52.dp)
                            .testTag("add_field_subject_${subject.id}")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "Add assessment field to ${subject.name}")
                    }
                }
            }

            if (subject.fields.isNotEmpty()) {
                FilledTonalButton(
                    onClick = { onAssessWholeClass(activeClassScope) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("assess_whole_class_${subject.id}")
                ) {
                    Icon(Icons.Rounded.AssignmentTurnedIn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Assess All $activeClassScope Students in ${subject.name}")
                }
            }
        }
    }
}

@Composable
fun SubjectFormDialog(
    initial: SubjectDefinition?,
    defaultGradeScope: String = "Class 5",
    onDismiss: () -> Unit,
    onSave: (SubjectDefinition) -> Unit
) {
    var name by remember(initial) { mutableStateOf(initial?.name ?: "") }
    var gradeScope by remember(initial, defaultGradeScope) {
        mutableStateOf(initial?.gradeScope ?: defaultGradeScope)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Subject" else "Edit Subject") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name *") },
                    placeholder = { Text("e.g. English, Bangla, Mathematics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Applies To Class / Grades", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GradeScopes.ALL.forEach { scope ->
                        FilterChip(
                            selected = gradeScope == scope,
                            onClick = { gradeScope = scope },
                            label = { Text(scope) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(
                            (initial ?: SubjectDefinition()).copy(
                                name = name.trim(),
                                gradeScope = gradeScope
                            )
                        )
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SubjectFieldFormDialog(
    subjectName: String,
    initial: SubjectFieldDef?,
    onDismiss: () -> Unit,
    onSave: (label: String, fieldType: String, maxMarks: Long) -> Unit
) {
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var fieldType by remember(initial) { mutableStateOf(initial?.fieldType ?: CustomFieldTypes.NUMBER) }
    var maxMarksText by remember(initial) { mutableStateOf((initial?.maxMarks ?: 20L).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Assessment Field to $subjectName" else "Edit $subjectName Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Field Name *") },
                    placeholder = { Text("e.g. Reading text, Paragraph writing, Grammar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Field Input Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CustomFieldTypes.SUBJECT_TYPES.forEach { type ->
                        FilterChip(
                            selected = fieldType == type,
                            onClick = { fieldType = type },
                            label = { Text(CustomFieldTypes.label(type)) }
                        )
                    }
                }
                if (fieldType == CustomFieldTypes.NUMBER) {
                    OutlinedTextField(
                        value = maxMarksText,
                        onValueChange = { maxMarksText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Full / Max Marks (for % & charts)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val max = maxMarksText.toLongOrNull()?.coerceIn(1L, 1000L) ?: 20L
                    onSave(label.trim(), fieldType, if (fieldType == CustomFieldTypes.NUMBER) max else 0L)
                },
                enabled = label.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CustomFieldFormDialog(
    initial: CustomFieldDef?,
    existingCategories: List<String>,
    onDismiss: () -> Unit,
    onSave: (CustomFieldDef) -> Unit
) {
    var category by remember(initial) { mutableStateOf(initial?.category ?: "General") }
    var label by remember(initial) { mutableStateOf(initial?.label ?: "") }
    var fieldType by remember(initial) { mutableStateOf(initial?.fieldType ?: CustomFieldTypes.TEXT) }
    var resetsYearly by remember(initial) { mutableStateOf(initial?.resetsYearly ?: false) }

    val allCategories = remember(existingCategories) {
        (CustomFieldCategories.SUGGESTED + existingCategories).distinct()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Create Flexible Field" else "Edit Flexible Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Pick or Type Category", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat) }
                        )
                    }
                }
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category Name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Field Label *") },
                    placeholder = { Text("e.g. Birth Reg No, Blood Group, Stipend MFS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Field Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CustomFieldTypes.ALL.forEach { type ->
                        FilterChip(
                            selected = fieldType == type,
                            onClick = { fieldType = type },
                            label = { Text(CustomFieldTypes.label(type)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Reset Every Year?", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Clear value when promoted to next year",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = resetsYearly, onCheckedChange = { resetsYearly = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        (initial ?: CustomFieldDef()).copy(
                            category = category.trim().ifBlank { "General" },
                            label = label.trim(),
                            fieldType = fieldType,
                            resetsYearly = resetsYearly
                        )
                    )
                },
                enabled = label.isNotBlank() && category.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
