package com.example.data.model

enum class EditorSectionTab(
    val index: Int,
    val title: String,
    val shortTitle: String,
    val subtitle: String
) {
    FIXED(0, "Fixed Core Details", "Fixed Info", "Permanent student & parent info"),
    YEARLY(1, "Yearly Class & Roll", "Yearly", "Changes every academic year"),
    SUBJECTS(2, "Subjects & Subject Fields", "Subjects", "Flexible fields per subject"),
    CUSTOM(3, "Flexible Category Fields", "Categories", "Teacher-defined fields by category")
}

object SectorOptions {
    val sections = listOf("A", "B", "C", "Common")
    val promotionStatuses = listOf("Studying", "Promoted", "Not Promoted", "Passed Out", "Transferred")
    val academicYears = listOf(2025, 2026, 2027, 2028)
}

object StudentsCsvExporter {
    fun exportToCsv(
        students: List<StudentRecord>,
        subjects: List<SubjectDefinition> = emptyList(),
        customFields: List<CustomFieldDef> = emptyList()
    ): String {
        val subjectFieldCols = subjects.flatMap { sub ->
            sub.fields.map { field -> Triple(sub.id, field.fieldId, "${sub.name}: ${field.label}") }
        }

        val headers = buildList {
            add("Academic Year")
            add("Class Name")
            add("Roll No")
            add("Section")
            add("Student Name (EN)")
            add("Student Name (BN)")
            add("Father's Name")
            add("Mother's Name")
            add("Date of Birth")
            add("Phone Number")
            add("Address")
            add("Working Days")
            add("Days Present")
            add("Attendance %")
            add("Promotion Status")
            subjectFieldCols.forEach { add(it.third) }
            customFields.forEach { add("${it.category}: ${it.label}") }
            add("Overall Performance %")
            add("Field Comments & Details")
        }

        val sb = StringBuilder()
        sb.append('\uFEFF')
        sb.appendLine(headers.joinToString(",") { escape(it) })

        students.forEach { s ->
            val notesSummary = s.sectorNotes.entries
                .filter { it.value.isNotBlank() }
                .joinToString(" | ") { "${it.key}: ${it.value}" }

            val row = buildList {
                add(s.academicYear.toString())
                add(s.classLevel)
                add(s.rollNumber)
                add(s.section)
                add(s.studentNameEn)
                add(s.studentNameBn)
                add(s.fatherNameEn)
                add(s.motherNameEn)
                add(s.dateOfBirth)
                add(s.guardianPhone)
                add(s.effectiveAddress())
                add(s.totalSchoolDays.toString())
                add(s.daysPresent.toString())
                add("${s.attendancePercentage()}%")
                add(s.promotionStatus)
                subjectFieldCols.forEach { (subId, fId, _) ->
                    add(s.subjectValue(subId, fId))
                }
                customFields.forEach { cf ->
                    add(s.customValue(cf.id))
                }
                add("${s.overallPerformanceScore(subjects)}%")
                add(notesSummary)
            }
            sb.appendLine(row.joinToString(",") { escape(it) })
        }
        return sb.toString()
    }

    private fun escape(value: String): String {
        val cleaned = value.replace("\r\n", " ").replace("\n", " ")
        return if (cleaned.contains(",") || cleaned.contains("\"")) {
            "\"${cleaned.replace("\"", "\"\"")}\""
        } else {
            cleaned
        }
    }
}
