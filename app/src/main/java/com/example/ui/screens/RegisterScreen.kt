package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
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
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.data.model.CustomFieldDef
import com.example.data.model.ExamDefaults
import com.example.data.model.ExamTermConfig
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.TeacherProfile
import com.example.ui.components.ClassFilterRow
import com.example.ui.components.EmptyRegisterState
import com.example.ui.components.StudentListCard
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.viewmodel.RegisterQuickFilter
import com.example.ui.viewmodel.UiState

private enum class RegisterSubMode {
    STUDENT_LIST,
    BATCH_UPDATE
}

@Composable
fun RegisterScreen(
    studentsState: UiState<List<StudentRecord>>,
    filteredStudents: List<StudentRecord>,
    subjects: List<SubjectDefinition>,
    customFields: List<CustomFieldDef>,
    examConfigs: List<ExamTermConfig> = ExamDefaults.defaultThreeExams(),
    teacherProfile: TeacherProfile? = null,
    selectedYear: Int,
    searchQuery: String,
    selectedClassFilter: String?,
    quickFilter: RegisterQuickFilter,
    isAdmin: Boolean = true,
    isSaving: Boolean,
    initialBatchMode: Boolean = false,
    initialBatchClass: String? = null,
    initialBatchSubjectId: String? = null,
    initialBatchModeEnum: BatchMode? = null,
    onSearchQueryChange: (String) -> Unit,
    onClassFilterChange: (String?) -> Unit,
    onQuickFilterChange: (RegisterQuickFilter) -> Unit,
    onStudentClick: (StudentRecord) -> Unit,
    onAddStudent: () -> Unit,
    onSaveBatch: (
        updatesByStudentId: Map<String, Map<String, Any>>,
        customSummary: String?,
        customDetails: String?,
        onSuccess: () -> Unit
    ) -> Unit,
    onSaveExamConfigs: (List<ExamTermConfig>, onSuccess: () -> Unit) -> Unit = { _, done -> done() },
    onOpenFieldManager: () -> Unit,
    onOpenPromotionManager: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var subMode by rememberSaveable(initialBatchMode, initialBatchSubjectId, initialBatchModeEnum) {
        mutableStateOf(
            if (initialBatchMode || !initialBatchSubjectId.isNullOrBlank() || initialBatchModeEnum != null) {
                RegisterSubMode.BATCH_UPDATE
            } else RegisterSubMode.STUDENT_LIST
        )
    }

    LaunchedEffect(initialBatchMode, initialBatchModeEnum, initialBatchSubjectId) {
        if (initialBatchMode || initialBatchModeEnum != null || !initialBatchSubjectId.isNullOrBlank()) {
            subMode = RegisterSubMode.BATCH_UPDATE
        }
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
                        Text(
                            text = if (mode == RegisterSubMode.STUDENT_LIST) strings.modeStudentList else strings.modeBatchEntry,
                            maxLines = 1
                        )
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
                Text(strings.subjectsAndExamsBtn.substringBefore(" "))
            }
            FilledTonalButton(
                onClick = onOpenPromotionManager,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier.testTag("register_open_promotion_button")
            ) {
                Icon(Icons.Rounded.SwapVert, contentDescription = strings.menuPromotionRolls, modifier = Modifier.size(16.dp))
            }
        }

        if (subMode == RegisterSubMode.BATCH_UPDATE) {
            BatchEntryScreen(
                studentsState = studentsState,
                subjects = subjects,
                examConfigs = examConfigs,
                teacherProfile = teacherProfile,
                selectedYear = selectedYear,
                isAdmin = isAdmin,
                isSaving = isSaving,
                initialClass = initialBatchClass,
                initialSubjectId = initialBatchSubjectId,
                initialBatchMode = initialBatchModeEnum,
                onSaveBatch = onSaveBatch,
                onSaveExamConfigs = onSaveExamConfigs,
                onOpenStudentProfile = onStudentClick,
                onAddStudent = onAddStudent,
                onOpenFieldManager = onOpenFieldManager,
                modifier = Modifier.weight(1f)
            )
            return
        }

        val listControlsHeader: @Composable () -> Unit = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
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

                if (studentsState is UiState.Success && filteredStudents.isNotEmpty()) {
                    Text(
                        text = "Showing ${filteredStudents.size} of ${studentsState.data.size} ${strings.studentsCountWord} ($selectedYear)",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val showScrollTop by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 }
        }

        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWide = maxWidth >= 640.dp
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "student_list_filters_header") {
                    listControlsHeader()
                }

                when (studentsState) {
                    is UiState.Loading -> {
                        item(key = "student_list_loading") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }

                    is UiState.Error -> {
                        item(key = "student_list_error") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
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
                    }

                    is UiState.Success -> {
                        val hasFilter = searchQuery.isNotBlank() ||
                            selectedClassFilter != null ||
                            quickFilter != RegisterQuickFilter.ALL

                        if (filteredStudents.isEmpty()) {
                            item(key = "student_list_empty") {
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
                            }
                        } else if (isWide) {
                            val pairs = filteredStudents.chunked(2)
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
                        } else {
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

            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollTop,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 16.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.testTag("register_scroll_to_filters_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowUpward,
                        contentDescription = "Scroll up to search and filters"
                    )
                }
            }
        }
    }
}
