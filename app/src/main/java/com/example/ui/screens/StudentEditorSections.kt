package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.ClassLevels
import com.example.data.model.CustomFieldDef
import com.example.data.model.CustomFieldTypes
import com.example.data.model.SectorOptions
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.SubjectFieldDef
import com.example.ui.components.FlexibleChipSelector
import com.example.ui.components.FlexibleFieldInput

@Composable
fun FixedCoreSectionFields(
    student: StudentRecord,
    expandAllDetails: Boolean,
    onChange: (StudentRecord) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val photoBitmap = remember(student.photoBase64) {
        com.example.util.StudentPhotoHelper.decodeBase64ToImageBitmap(student.photoBase64)
    }
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val encoded = com.example.util.StudentPhotoHelper.compressUriToPassportBase64(context, uri)
            if (encoded != null) {
                onChange(student.copy(photoBase64 = encoded))
            } else {
                android.widget.Toast.makeText(context, "Could not process selected photo.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(width = 66.dp, height = 88.dp)
                ) {
                    if (photoBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = photoBitmap,
                            contentDescription = "Student passport photo",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Text("3:4\nPhoto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Passport-Size Photograph",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Auto-cropped to 3:4 portrait & synced cleanly in profile.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    androidx.activity.result.PickVisualMediaRequest(
                                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            modifier = Modifier.testTag("editor_pick_photo_button")
                        ) {
                            Text(if (photoBitmap != null) "Change Photo" else "Select Photo")
                        }
                        if (photoBitmap != null) {
                            IconButton(onClick = { onChange(student.copy(photoBase64 = "")) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove photo", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        FlexibleFieldInput(
            fieldKey = "fixed_name_en",
            badgeLabel = "Fixed",
            label = "Student Name (English)",
            value = student.studentNameEn,
            onValueChange = { onChange(student.copy(studentNameEn = it)) },
            detailNote = student.noteForKey("fixed_name_en"),
            onDetailNoteChange = { onChange(student.withNoteForKey("fixed_name_en", it)) },
            forceExpandDetail = expandAllDetails,
            placeholder = "e.g. Nusrat Jahan Mim",
            required = true,
            allowClear = false
        )
        FlexibleFieldInput(
            fieldKey = "fixed_name_bn",
            badgeLabel = "Fixed",
            label = "Student Name (Bangla / বাংলা নাম)",
            value = student.studentNameBn,
            onValueChange = { onChange(student.copy(studentNameBn = it)) },
            detailNote = student.noteForKey("fixed_name_bn"),
            onDetailNoteChange = { onChange(student.withNoteForKey("fixed_name_bn", it)) },
            forceExpandDetail = expandAllDetails,
            placeholder = "e.g. নুসরাত জাহান মিম"
        )
        FlexibleFieldInput(
            fieldKey = "fixed_father",
            badgeLabel = "Fixed",
            label = "Father's Name",
            value = student.fatherNameEn,
            onValueChange = { onChange(student.copy(fatherNameEn = it)) },
            detailNote = student.noteForKey("fixed_father"),
            onDetailNoteChange = { onChange(student.withNoteForKey("fixed_father", it)) },
            forceExpandDetail = expandAllDetails,
            placeholder = "Father's full name"
        )
        FlexibleFieldInput(
            fieldKey = "fixed_mother",
            badgeLabel = "Fixed",
            label = "Mother's Name",
            value = student.motherNameEn,
            onValueChange = { onChange(student.copy(motherNameEn = it)) },
            detailNote = student.noteForKey("fixed_mother"),
            onDetailNoteChange = { onChange(student.withNoteForKey("fixed_mother", it)) },
            forceExpandDetail = expandAllDetails,
            placeholder = "Mother's full name"
        )
        FlexibleFieldInput(
            fieldKey = "fixed_dob",
            badgeLabel = "Fixed",
            label = "Date of Birth",
            value = student.dateOfBirth,
            onValueChange = { onChange(student.copy(dateOfBirth = it)) },
            detailNote = student.noteForKey("fixed_dob"),
            onDetailNoteChange = { onChange(student.withNoteForKey("fixed_dob", it)) },
            forceExpandDetail = expandAllDetails,
            placeholder = "DD/MM/YYYY"
        )
        FlexibleFieldInput(
            fieldKey = "fixed_phone",
            badgeLabel = "Fixed",
            label = "Phone Number",
            value = student.guardianPhone,
            onValueChange = { onChange(student.copy(guardianPhone = it)) },
            detailNote = student.noteForKey("fixed_phone"),
            onDetailNoteChange = { onChange(student.withNoteForKey("fixed_phone", it)) },
            forceExpandDetail = expandAllDetails,
            placeholder = "01XXXXXXXXX",
            keyboardType = KeyboardType.Phone
        )
        FlexibleFieldInput(
            fieldKey = "fixed_address",
            badgeLabel = "Fixed",
            label = "Address",
            value = student.effectiveAddress(),
            onValueChange = { onChange(student.copy(address = it)) },
            detailNote = student.noteForKey("fixed_address"),
            onDetailNoteChange = { onChange(student.withNoteForKey("fixed_address", it)) },
            forceExpandDetail = expandAllDetails,
            placeholder = "Village/Para, Union, Upazila & District",
            singleLine = false,
            maxLines = 3
        )
    }
}

@Composable
fun YearlySectionFields(
    student: StudentRecord,
    expandAllDetails: Boolean,
    onChange: (StudentRecord) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        FlexibleChipSelector(
            fieldKey = "yearly_year",
            badgeLabel = "Yearly",
            label = "Calendar Academic Year",
            options = SectorOptions.academicYears.map { it.toString() },
            selectedValue = student.academicYear.toString(),
            onSelect = { yearStr ->
                onChange(student.copy(academicYear = yearStr.toLongOrNull() ?: 2026L))
            },
            detailNote = student.noteForKey("yearly_year"),
            onDetailNoteChange = { onChange(student.withNoteForKey("yearly_year", it)) },
            forceExpandDetail = expandAllDetails,
            required = true,
            allowDeselect = false
        )
        FlexibleChipSelector(
            fieldKey = "yearly_class",
            badgeLabel = "Yearly",
            label = "Class Name",
            options = ClassLevels.ALL,
            selectedValue = student.classLevel,
            onSelect = { onChange(student.copy(classLevel = it)) },
            detailNote = student.noteForKey("yearly_class"),
            onDetailNoteChange = { onChange(student.withNoteForKey("yearly_class", it)) },
            forceExpandDetail = expandAllDetails,
            required = true,
            allowDeselect = false
        )
        FlexibleFieldInput(
            fieldKey = "yearly_roll",
            badgeLabel = "Yearly",
            label = "Roll Number",
            value = student.rollNumber,
            onValueChange = { onChange(student.copy(rollNumber = it)) },
            detailNote = student.noteForKey("yearly_roll"),
            onDetailNoteChange = { onChange(student.withNoteForKey("yearly_roll", it)) },
            forceExpandDetail = expandAllDetails,
            placeholder = "e.g. 01",
            keyboardType = KeyboardType.Number,
            required = true,
            allowClear = false
        )
        FlexibleChipSelector(
            fieldKey = "yearly_section",
            badgeLabel = "Yearly",
            label = "Section / Branch",
            options = SectorOptions.sections,
            selectedValue = student.section,
            onSelect = { onChange(student.copy(section = it)) },
            detailNote = student.noteForKey("yearly_section"),
            onDetailNoteChange = { onChange(student.withNoteForKey("yearly_section", it)) },
            forceExpandDetail = expandAllDetails
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FlexibleFieldInput(
                fieldKey = "yearly_working_days",
                badgeLabel = "Yearly",
                label = "Working Days",
                value = if (student.totalSchoolDays == 0L) "" else student.totalSchoolDays.toString(),
                onValueChange = { raw ->
                    val v = raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    onChange(student.copy(totalSchoolDays = v.coerceIn(0L, 366L)))
                },
                detailNote = student.noteForKey("yearly_attendance"),
                onDetailNoteChange = { onChange(student.withNoteForKey("yearly_attendance", it)) },
                forceExpandDetail = expandAllDetails,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
            FlexibleFieldInput(
                fieldKey = "yearly_days_present",
                badgeLabel = "Yearly",
                label = "Days Present",
                value = if (student.daysPresent == 0L) "" else student.daysPresent.toString(),
                onValueChange = { raw ->
                    val v = raw.filter { it.isDigit() }.toLongOrNull() ?: 0L
                    onChange(student.copy(daysPresent = v.coerceIn(0L, 366L)))
                },
                detailNote = student.noteForKey("yearly_days_present"),
                onDetailNoteChange = { onChange(student.withNoteForKey("yearly_days_present", it)) },
                forceExpandDetail = expandAllDetails,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.weight(1f)
            )
        }
        FlexibleChipSelector(
            fieldKey = "yearly_promotion",
            badgeLabel = "Yearly",
            label = "Promotion Status",
            options = SectorOptions.promotionStatuses,
            selectedValue = student.promotionStatus,
            onSelect = { onChange(student.copy(promotionStatus = it)) },
            detailNote = student.noteForKey("yearly_promotion"),
            onDetailNoteChange = { onChange(student.withNoteForKey("yearly_promotion", it)) },
            forceExpandDetail = expandAllDetails,
            allowDeselect = false
        )
    }
}

@Composable
fun SubjectsSectionFields(
    student: StudentRecord,
    subjects: List<SubjectDefinition>,
    expandAllDetails: Boolean,
    onChange: (StudentRecord) -> Unit,
    onRequestAddSubject: () -> Unit,
    onRequestEditSubject: (SubjectDefinition) -> Unit,
    onRequestDeleteSubject: (SubjectDefinition) -> Unit,
    onRequestAddSubjectField: (SubjectDefinition) -> Unit,
    onRequestEditSubjectField: (SubjectDefinition, SubjectFieldDef) -> Unit,
    onRequestDeleteSubjectField: (SubjectDefinition, String) -> Unit
) {
    var showAllSubjects by remember { mutableStateOf(false) }
    val visibleSubjects = remember(subjects, student.classLevel, showAllSubjects) {
        if (showAllSubjects) subjects
        else subjects.filter { it.appliesToClass(student.classLevel) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = showAllSubjects,
                onClick = { showAllSubjects = !showAllSubjects },
                label = { Text(if (showAllSubjects) "Showing All (${subjects.size})" else "For ${student.classLevel} (${visibleSubjects.size})") }
            )
            Button(
                onClick = onRequestAddSubject,
                modifier = Modifier.testTag("editor_add_subject_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Subject")
            }
        }

        if (visibleSubjects.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "No subjects configured for ${student.classLevel} yet.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tap 'Add Subject' above (or open the Subjects & Fields menu) to create subjects for ${student.classLevel} and their assessment fields. All students in ${student.classLevel} inherit them automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            visibleSubjects.forEach { subject ->
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
                                Text(
                                    text = "${subject.gradeScope} • ${subject.fields.size} field(s)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row {
                                IconButton(onClick = { onRequestEditSubject(subject) }) {
                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit ${subject.name}", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onRequestDeleteSubject(subject) }) {
                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete ${subject.name}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                }
                            }
                        }

                        if (subject.fields.isEmpty()) {
                            Text(
                                text = "No assessment fields created inside ${subject.name} yet. Tap '+' below to add fields.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            subject.fields.forEach { field ->
                                val noteKey = "sf_${subject.id}__${field.fieldId}"
                                val curValue = student.subjectValue(subject.id, field.fieldId)
                                val suffix = if (field.fieldType == CustomFieldTypes.NUMBER && field.maxMarks > 0) " / ${field.maxMarks}" else ""

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    when (field.fieldType) {
                                        CustomFieldTypes.LEVEL -> FlexibleChipSelector(
                                            fieldKey = noteKey,
                                            badgeLabel = subject.name.take(8),
                                            label = field.label,
                                            options = CustomFieldTypes.SKILL_LEVEL_OPTIONS,
                                            selectedValue = curValue,
                                            onSelect = { onChange(student.withSubjectValue(subject.id, field.fieldId, it)) },
                                            detailNote = student.noteForKey(noteKey),
                                            onDetailNoteChange = { onChange(student.withNoteForKey(noteKey, it)) },
                                            forceExpandDetail = expandAllDetails
                                        )
                                        CustomFieldTypes.YES_NO -> FlexibleChipSelector(
                                            fieldKey = noteKey,
                                            badgeLabel = subject.name.take(8),
                                            label = field.label,
                                            options = listOf("Yes", "No"),
                                            selectedValue = curValue,
                                            onSelect = { onChange(student.withSubjectValue(subject.id, field.fieldId, it)) },
                                            detailNote = student.noteForKey(noteKey),
                                            onDetailNoteChange = { onChange(student.withNoteForKey(noteKey, it)) },
                                            forceExpandDetail = expandAllDetails
                                        )
                                        else -> FlexibleFieldInput(
                                            fieldKey = noteKey,
                                            badgeLabel = subject.name.take(8),
                                            label = "${field.label}$suffix",
                                            value = curValue,
                                            onValueChange = { raw ->
                                                val cleaned = if (field.fieldType == CustomFieldTypes.NUMBER) {
                                                    raw.filter { it.isDigit() || it == '.' }
                                                } else raw
                                                onChange(student.withSubjectValue(subject.id, field.fieldId, cleaned))
                                            },
                                            detailNote = student.noteForKey(noteKey),
                                            onDetailNoteChange = { onChange(student.withNoteForKey(noteKey, it)) },
                                            forceExpandDetail = expandAllDetails,
                                            keyboardType = if (field.fieldType == CustomFieldTypes.NUMBER) KeyboardType.Decimal else KeyboardType.Text
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        IconButton(
                                            onClick = { onRequestEditSubjectField(subject, field) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.Edit, contentDescription = "Rename ${field.label}", modifier = Modifier.size(15.dp))
                                        }
                                        IconButton(
                                            onClick = { onRequestDeleteSubjectField(subject, field.fieldId) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Rounded.Delete, contentDescription = "Remove ${field.label}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { onRequestAddSubjectField(subject) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("editor_add_field_${subject.id}")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add Field to ${subject.name}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlexibleCategorySectionFields(
    student: StudentRecord,
    customFields: List<CustomFieldDef>,
    expandAllDetails: Boolean,
    onChange: (StudentRecord) -> Unit,
    onRequestAddCustomField: () -> Unit,
    onRequestEditCustomField: (CustomFieldDef) -> Unit,
    onRequestDeleteCustomField: (CustomFieldDef) -> Unit
) {
    val grouped = remember(customFields) { customFields.groupBy { it.category } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = onRequestAddCustomField,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("editor_add_custom_field_button")
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create Flexible Field in Any Category")
        }

        if (customFields.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "No extra fields created yet. Tap the button above to create flexible fields under Birth & Health, Stipend, Family, or any custom category you want — nothing is forced!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            grouped.forEach { (category, fieldsInCategory) ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                fieldsInCategory.forEach { cf ->
                    val noteKey = "cf_${cf.id}"
                    val curValue = student.customValue(cf.id)

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (cf.fieldType == CustomFieldTypes.YES_NO) {
                            FlexibleChipSelector(
                                fieldKey = noteKey,
                                badgeLabel = cf.category.take(8),
                                label = cf.label,
                                options = listOf("Yes", "No", "Pending"),
                                selectedValue = curValue,
                                onSelect = { onChange(student.withCustomValue(cf.id, it)) },
                                detailNote = student.noteForKey(noteKey),
                                onDetailNoteChange = { onChange(student.withNoteForKey(noteKey, it)) },
                                forceExpandDetail = expandAllDetails
                            )
                        } else {
                            FlexibleFieldInput(
                                fieldKey = noteKey,
                                badgeLabel = cf.category.take(8),
                                label = cf.label,
                                value = curValue,
                                onValueChange = { raw ->
                                    val cleaned = if (cf.fieldType == CustomFieldTypes.NUMBER) {
                                        raw.filter { it.isDigit() || it == '.' }
                                    } else raw
                                    onChange(student.withCustomValue(cf.id, cleaned))
                                },
                                detailNote = student.noteForKey(noteKey),
                                onDetailNoteChange = { onChange(student.withNoteForKey(noteKey, it)) },
                                forceExpandDetail = expandAllDetails,
                                keyboardType = if (cf.fieldType == CustomFieldTypes.NUMBER) KeyboardType.Decimal else KeyboardType.Text
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { onRequestEditCustomField(cf) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${cf.label}", modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = { onRequestDeleteCustomField(cf) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete ${cf.label}", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
