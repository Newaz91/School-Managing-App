package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ClassLevels
import com.example.data.model.CustomFieldDef
import com.example.data.model.SectorOptions
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun YearSwitcherRow(
    selectedYear: Int,
    onYearSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectorOptions.academicYears.forEach { year ->
            val selected = year == selectedYear
            FilterChip(
                selected = selected,
                onClick = { onYearSelected(year) },
                label = {
                    Text(
                        text = year.toString(),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.testTag("year_chip_$year")
            )
        }
    }
}

@Composable
fun ClassFilterRow(
    selectedClass: String?,
    onClassSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilterChip(
            selected = selectedClass == null,
            onClick = { onClassSelected(null) },
            label = { Text("All Classes") },
            modifier = Modifier.testTag("class_filter_all")
        )
        ClassLevels.ALL.forEach { cls ->
            FilterChip(
                selected = selectedClass == cls,
                onClick = { onClassSelected(if (selectedClass == cls) null else cls) },
                label = { Text(cls) },
                modifier = Modifier.testTag("class_filter_${cls.lowercase().replace(" ", "_")}")
            )
        }
    }
}

@Composable
fun StudentListCard(
    student: StudentRecord,
    subjects: List<SubjectDefinition> = emptyList(),
    customFields: List<CustomFieldDef> = emptyList(),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (filledCount, totalCount) = student.filledFieldProgress(customFields, subjects)
    val commentsCount = student.sectorCommentsCount()
    val progress = (filledCount.toFloat() / totalCount.toFloat()).coerceIn(0f, 1f)
    val attendancePct = student.attendancePercentage()
    val perfScore = student.overallPerformanceScore(subjects)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("student_card_${student.rollNumber}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val decodedThumb = remember(student.photoBase64) {
                    com.example.util.StudentPhotoHelper.decodeBase64ToImageBitmap(student.photoBase64)
                }
                if (decodedThumb != null) {
                    Image(
                        bitmap = decodedThumb,
                        contentDescription = "${student.primaryName()} photo",
                        modifier = Modifier
                            .size(width = 42.dp, height = 54.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = student.rollNumber.ifBlank { "—" },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 1
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = student.primaryName(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val secondaryName = student.secondaryName()
                    if (secondaryName.isNotBlank()) {
                        Text(
                            text = secondaryName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${student.classLevel}${if (student.section.isNotBlank()) " (${student.section})" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (student.guardianPhone.isNotBlank()) {
                            Icon(
                                imageVector = Icons.Rounded.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = student.guardianPhone,
                                fontFamily = JetBrainsMonoFontFamily,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = if (perfScore >= 60) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (perfScore > 0) "Perf: $perfScore%" else student.promotionStatus.ifBlank { "Studying" },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (perfScore >= 60) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (student.totalSchoolDays > 0) "Att: $attendancePct%" else "Att: —",
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (commentsCount > 0) "Recorded fields ($commentsCount notes)" else "Recorded fields",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$filledCount / $totalCount",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
        }
    }
}

@Composable
fun EmptyRegisterState(
    academicYear: Int,
    hasActiveFilter: Boolean,
    onAddStudent: () -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_empty_classroom),
            contentDescription = "Empty school register illustration",
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(170.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop
        )
        Text(
            text = if (hasActiveFilter) {
                "No Matching Students"
            } else {
                "Your $academicYear Register Is Empty"
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (hasActiveFilter) {
                "Try clearing your search query or class filter to view all enrolled students."
            } else {
                "Enroll your students with fixed core details, yearly class & roll number, plus any custom subjects and flexible category fields you create."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (hasActiveFilter) {
            Button(
                onClick = onClearFilters,
                modifier = Modifier.testTag("clear_filters_button")
            ) {
                Text("Clear Filters")
            }
        } else {
            Button(
                onClick = onAddStudent,
                modifier = Modifier.testTag("empty_add_student_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enroll First Student")
            }
        }
    }
}

@Composable
fun FlexibleFieldInput(
    fieldKey: String,
    badgeLabel: String,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    detailNote: String = "",
    onDetailNoteChange: (String) -> Unit = {},
    forceExpandDetail: Boolean = false,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else 4,
    required: Boolean = false,
    allowClear: Boolean = true,
    modifier: Modifier = Modifier
) {
    var detailExpanded by remember(fieldKey) { mutableStateOf(detailNote.isNotBlank()) }
    val isDetailVisible = forceExpandDetail || detailExpanded || detailNote.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
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
                Surface(
                    color = if (value.isNotBlank()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (value.isNotBlank()) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = if (required) "$label *" else label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (value.isNotBlank()) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Filled",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            TextButton(
                onClick = { detailExpanded = !detailExpanded },
                modifier = Modifier.testTag("field_detail_toggle_$fieldKey")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Comment,
                    contentDescription = "Comment or detail for $label",
                    tint = if (detailNote.isNotBlank()) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (detailNote.isNotBlank()) "Note" else "Detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (detailNote.isNotBlank()) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = if (placeholder.isNotBlank()) {
                { Text(placeholder, style = MaterialTheme.typography.bodyMedium) }
            } else null,
            trailingIcon = if (allowClear && value.isNotBlank() && !required) {
                {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear $label", modifier = Modifier.size(18.dp))
                    }
                }
            } else null,
            singleLine = singleLine,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("field_input_$fieldKey")
        )
        AnimatedVisibility(visible = isDetailVisible) {
            OutlinedTextField(
                value = detailNote,
                onValueChange = onDetailNoteChange,
                label = { Text("Comment / Detail for $label") },
                placeholder = { Text("Add extra detail, remark, or verification note…") },
                trailingIcon = if (detailNote.isNotBlank()) {
                    {
                        IconButton(onClick = { onDetailNoteChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear note", modifier = Modifier.size(16.dp))
                        }
                    }
                } else null,
                maxLines = 3,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
                    .testTag("field_detail_input_$fieldKey")
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlexibleChipSelector(
    fieldKey: String,
    badgeLabel: String,
    label: String,
    options: List<String>,
    selectedValue: String,
    onSelect: (String) -> Unit,
    detailNote: String = "",
    onDetailNoteChange: (String) -> Unit = {},
    forceExpandDetail: Boolean = false,
    required: Boolean = false,
    allowDeselect: Boolean = true,
    modifier: Modifier = Modifier
) {
    var detailExpanded by remember(fieldKey) { mutableStateOf(detailNote.isNotBlank()) }
    val isDetailVisible = forceExpandDetail || detailExpanded || detailNote.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
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
                Surface(
                    color = if (selectedValue.isNotBlank()) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    },
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badgeLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedValue.isNotBlank()) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = if (required) "$label *" else label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            TextButton(
                onClick = { detailExpanded = !detailExpanded },
                modifier = Modifier.testTag("field_detail_toggle_$fieldKey")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Comment,
                    contentDescription = "Comment or detail for $label",
                    tint = if (detailNote.isNotBlank()) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (detailNote.isNotBlank()) "Note" else "Detail",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (detailNote.isNotBlank()) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { option ->
                val isSelected = selectedValue == option
                val bg by animateColorAsState(
                    targetValue = if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    label = "chip_bg"
                )
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected && allowDeselect && !required) {
                            onSelect("")
                        } else {
                            onSelect(option)
                        }
                    },
                    label = { Text(option) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = bg,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }
        AnimatedVisibility(visible = isDetailVisible) {
            OutlinedTextField(
                value = detailNote,
                onValueChange = onDetailNoteChange,
                label = { Text("Comment / Detail for $label") },
                placeholder = { Text("Add extra detail, remark, or verification note…") },
                maxLines = 3,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_detail_input_$fieldKey")
            )
        }
    }
}
