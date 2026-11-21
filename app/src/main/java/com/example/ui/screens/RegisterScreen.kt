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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import com.example.data.model.CustomFieldDef
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.ui.components.ClassFilterRow
import com.example.ui.components.EmptyRegisterState
import com.example.ui.components.StudentListCard
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.RegisterQuickFilter
import com.example.ui.viewmodel.UiState

private enum class RegisterSubMode(val label: String) {
    STUDENT_LIST("Student List"),
    BATCH_UPDATE("Batch Entry")
}

@Composable
fun RegisterScreen(
    studentsState: UiState<List<StudentRecord>>,
    filteredStudents: List<StudentRecord>,
    subjects: List<SubjectDefinition>,
    customFields: List<CustomFieldDef>,
    selectedYear: Int,
    searchQuery: String,
    selectedClassFilter: String?,
    quickFilter: RegisterQuickFilter,
    isSaving: Boolean,
    initialBatchMode: Boolean = false,
    initialBatchClass: String? = null,
    initialBatchSubjectId: String? = null,
    onSearchQueryChange: (String) -> Unit,
    onClassFilterChange: (String?) -> Unit,
    onQuickFilterChange: (RegisterQuickFilter) -> Unit,
    onStudentClick: (StudentRecord) -> Unit,
    onAddStudent: () -> Unit,
    onSaveBatch: (Map<String, Map<String, Any>>, onSuccess: () -> Unit) -> Unit,
    onOpenFieldManager: () -> Unit,
    modifier: Modifier = Modifier
) {
    var subMode by remember(initialBatchMode, initialBatchSubjectId) {
        mutableStateOf(
            if (initialBatchMode || !initialBatchSubjectId.isNullOrBlank()) {
                RegisterSubMode.BATCH_UPDATE
            } else RegisterSubMode.STUDENT_LIST
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                RegisterSubMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = subMode == mode,
                        onClick = { subMode = mode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = RegisterSubMode.entries.size
                        ),
                        icon = {
                            Icon(
                                imageVector = if (mode == RegisterSubMode.STUDENT_LIST) Icons.Rounded.Groups else Icons.Rounded.EditNote,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("register_mode_${mode.name.lowercase()}")
                    ) {
                        Text(mode.label, maxLines = 1)
                    }
                }
            }

            FilledTonalButton(
                onClick = onOpenFieldManager,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                modifier = Modifier.testTag("register_open_schema_button")
            ) {
                Icon(Icons.Rounded.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Subjects")
            }
        }

        if (subMode == RegisterSubMode.BATCH_UPDATE) {
            BatchEntryScreen(
                studentsState = studentsState,
                subjects = subjects,
                selectedYear = selectedYear,
                isSaving = isSaving,
                initialClass = initialBatchClass,
                initialSubjectId = initialBatchSubjectId,
                onSaveBatch = onSaveBatch,
                onAddStudent = onAddStudent,
                onOpenFieldManager = onOpenFieldManager,
                modifier = Modifier.weight(1f)
            )
            return
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search name, Roll, Parent, Phone, Address…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("register_search_input")
            )

            ClassFilterRow(
                selectedClass = selectedClassFilter,
                onClassSelected = onClassFilterChange
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RegisterQuickFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = quickFilter == filter,
                        onClick = { onQuickFilterChange(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("quick_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        when (studentsState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = studentsState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }

            is UiState.Success -> {
                val totalCount = studentsState.data.size
                val hasFilter = searchQuery.isNotBlank() ||
                    selectedClassFilter != null ||
                    quickFilter != RegisterQuickFilter.ALL

                if (filteredStudents.isEmpty()) {
                    EmptyRegisterState(
                        academicYear = selectedYear,
                        hasActiveFilter = hasFilter,
                        onAddStudent = onAddStudent,
                        onClearFilters = {
                            onSearchQueryChange("")
                            onClassFilterChange(null)
                            onQuickFilterChange(RegisterQuickFilter.ALL)
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Showing ${filteredStudents.size} of $totalCount students ($selectedYear)",
                            fontFamily = JetBrainsMonoFontFamily,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    androidx.compose.foundation.layout.BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val isWide = maxWidth >= 640.dp
                        if (isWide) {
                            val pairs = remember(filteredStudents) { filteredStudents.chunked(2) }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 88.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = pairs,
                                    key = { row -> row.first().id }
                                ) { rowStudents ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowStudents.forEach { student ->
                                            StudentListCard(
                                                student = student,
                                                subjects = subjects,
                                                customFields = customFields,
                                                onClick = { onStudentClick(student) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        if (rowStudents.size == 1) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 4.dp,
                                    bottom = 88.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(
                                    items = filteredStudents,
                                    key = { it.id },
                                    contentType = { "student_card" }
                                ) { student ->
                                    StudentListCard(
                                        student = student,
                                        subjects = subjects,
                                        customFields = customFields,
                                        onClick = { onStudentClick(student) }
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
