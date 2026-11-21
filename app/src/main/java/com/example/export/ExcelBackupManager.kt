package com.example.export

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.model.ClassLevels
import com.example.data.model.CustomFieldDef
import com.example.data.model.CustomFieldTypes
import com.example.data.model.GradeScopes
import com.example.data.model.StudentRecord
import com.example.data.model.SubjectDefinition
import com.example.data.model.SubjectFieldDef
import com.example.data.model.generateShortId
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupParseResult(
    val students: List<StudentRecord>,
    val subjectsToUpsert: List<SubjectDefinition>,
    val customFieldsToUpsert: List<CustomFieldDef>,
    val formatLabel: String
)

object ExcelBackupManager {

    const val MIME_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val MIME_CSV = "text/csv"

    private sealed interface ExtraColumnMapping {
        data class SubjectCol(val subjectIdOrKey: String, val fieldId: String) : ExtraColumnMapping
        data class CustomCol(val fieldIdOrKey: String) : ExtraColumnMapping
    }

    // ─────────────────────────── EXPORT TO EXCEL (.XLSX) ───────────────────────────

    fun buildWorkbookXlsxBytes(
        students: List<StudentRecord>,
        subjects: List<SubjectDefinition>,
        customFields: List<CustomFieldDef>
    ): ByteArray {
        val subjectCols = subjects.flatMap { sub ->
            sub.fields.map { f -> Triple(sub.id, f.fieldId, "[Sub: ${sub.name} | ${f.label}]") }
        }
        val customCols = customFields.map { cf ->
            cf.id to "[Cat: ${cf.category} | ${cf.label}]"
        }

        val studentHeaders = buildList {
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
            subjectCols.forEach { add(it.third) }
            customCols.forEach { add(it.second) }
            add("Overall Performance %")
            add("Exam Marks JSON")
            add("Notes JSON")
            add("Photo Base64")
        }

        val studentRows = buildList<List<String>> {
            add(studentHeaders)
            students.forEach { s ->
                val notesJson = JSONObject().apply {
                    s.sectorNotes.forEach { (k, v) -> if (v.isNotBlank()) put(k, v) }
                }.toString()
                val examsJson = JSONObject().apply {
                    s.examResults.forEach { (k, v) -> if (v.isNotBlank()) put(k, v) }
                }.toString()
                add(
                    buildList {
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
                        subjectCols.forEach { (subId, fId, _) -> add(s.subjectValue(subId, fId)) }
                        customCols.forEach { (cfId, _) -> add(s.customValue(cfId)) }
                        add("${s.overallPerformanceScore(subjects)}%")
                        add(if (s.examResults.isEmpty()) "" else examsJson)
                        add(if (s.sectorNotes.isEmpty()) "" else notesJson)
                        add(s.photoBase64)
                    }
                )
            }
        }

        val subjectRows = buildList<List<String>> {
            add(listOf("Subject Name", "Grade Scope", "Field ID", "Field Label", "Field Type", "Max Marks"))
            subjects.forEach { sub ->
                if (sub.fields.isEmpty()) {
                    add(listOf(sub.name, sub.gradeScope, "", "", "", "0"))
                } else {
                    sub.fields.forEach { f ->
                        add(listOf(sub.name, sub.gradeScope, f.fieldId, f.label, f.fieldType, f.maxMarks.toString()))
                    }
                }
            }
        }

        val categoryFieldRows = buildList<List<String>> {
            add(listOf("Field ID", "Category", "Label", "Field Type", "Resets Yearly"))
            customFields.forEach { cf ->
                add(listOf(cf.id, cf.category, cf.label, cf.fieldType, cf.resetsYearly.toString()))
            }
        }

        return packageXlsx(
            sheets = listOf(
                "Students" to studentRows,
                "Subjects" to subjectRows,
                "CategoryFields" to categoryFieldRows
            )
        )
    }

    private fun packageXlsx(sheets: List<Pair<String, List<List<String>>>>): ByteArray {
        val contentTypes = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
            append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
            append("""<Default Extension="xml" ContentType="application/xml"/>""")
            append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
            sheets.indices.forEach { idx ->
                append("""<Override PartName="/xl/worksheets/sheet${idx + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
            }
            append("""</Types>""")
        }

        val rootRels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

        val workbookXml = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
            append("""<sheets>""")
            sheets.forEachIndexed { idx, (name, _) ->
                val safeName = xmlEscape(name.take(31))
                append("""<sheet name="$safeName" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
            }
            append("""</sheets></workbook>""")
        }

        val workbookRels = buildString {
            append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
            sheets.indices.forEach { idx ->
                append("""<Relationship Id="rId${idx + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet${idx + 1}.xml"/>""")
            }
            append("""</Relationships>""")
        }

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(contentTypes.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(rootRels.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(workbookXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(workbookRels.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            sheets.forEachIndexed { idx, (_, rows) ->
                zip.putNextEntry(ZipEntry("xl/worksheets/sheet${idx + 1}.xml"))
                zip.write(buildWorksheetXml(rows).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun buildWorksheetXml(rows: List<List<String>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rIdx, row ->
            val rowNum = rIdx + 1
            append("""<row r="$rowNum">""")
            row.forEachIndexed { cIdx, cellVal ->
                if (cellVal.isNotEmpty()) {
                    val ref = "${colIndexToLetters(cIdx)}$rowNum"
                    append("""<c r="$ref" t="inlineStr"><is><t xml:space="preserve">${xmlEscape(cellVal)}</t></is></c>""")
                }
            }
            append("""</row>""")
        }
        append("""</sheetData></worksheet>""")
    }

    private fun colIndexToLetters(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }

    private fun colRefToIndex(ref: String): Int {
        var idx = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') {
                idx = idx * 26 + (ch - 'A' + 1)
            } else if (ch in 'a'..'z') {
                idx = idx * 26 + (ch - 'a' + 1)
            } else break
        }
        return (idx - 1).coerceAtLeast(0)
    }

    // ─────────────────────────── RESTORE FROM EXCEL (.XLSX) OR CSV ───────────────────────────

    fun parseBackupFromUri(
        context: Context,
        uri: Uri,
        defaultAcademicYear: Int,
        existingSubjects: List<SubjectDefinition>,
        existingCustomFields: List<CustomFieldDef>
    ): BackupParseResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Could not open selected backup file.")
        if (bytes.isEmpty()) throw IllegalStateException("The selected file is empty.")

        val isZipXlsx = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        return if (isZipXlsx) {
            parseXlsxBytes(bytes, defaultAcademicYear, existingSubjects, existingCustomFields)
        } else {
            val csvText = bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
            val rows = parseCsvRows(csvText)
            buildResultFromTables(
                studentRows = rows,
                subjectRows = emptyList(),
                categoryRows = emptyList(),
                defaultAcademicYear = defaultAcademicYear,
                existingSubjects = existingSubjects,
                existingCustomFields = existingCustomFields,
                formatLabel = "CSV / Google Sheets"
            )
        }
    }

    private fun parseXlsxBytes(
        bytes: ByteArray,
        defaultAcademicYear: Int,
        existingSubjects: List<SubjectDefinition>,
        existingCustomFields: List<CustomFieldDef>
    ): BackupParseResult {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".xml", ignoreCase = true)) {
                    entries[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
        val sheetNamesByIndex = parseWorkbookSheetNames(entries["xl/workbook.xml"])

        val worksheetKeys = entries.keys
            .filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
            .sortedBy { name ->
                name.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: 99
            }

        if (worksheetKeys.isEmpty()) {
            throw IllegalStateException("No worksheets found inside Excel (.xlsx) file.")
        }

        var studentRows: List<List<String>> = emptyList()
        var subjectRows: List<List<String>> = emptyList()
        var categoryRows: List<List<String>> = emptyList()

        worksheetKeys.forEachIndexed { idx, key ->
            val sheetNum = key.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: (idx + 1)
            val sheetName = sheetNamesByIndex[sheetNum]?.lowercase().orEmpty()
            val parsedRows = parseWorksheetXml(entries[key].orEmpty(), sharedStrings)
            when {
                sheetName == "subjects" -> subjectRows = parsedRows
                sheetName == "categoryfields" -> categoryRows = parsedRows
                sheetName == "students" || studentRows.isEmpty() -> {
                    if (studentRows.isEmpty() || sheetName == "students") {
                        studentRows = parsedRows
                    }
                }
            }
        }

        return buildResultFromTables(
            studentRows = studentRows,
            subjectRows = subjectRows,
            categoryRows = categoryRows,
            defaultAcademicYear = defaultAcademicYear,
            existingSubjects = existingSubjects,
            existingCustomFields = existingCustomFields,
            formatLabel = "Excel Workbook (.xlsx)"
        )
    }

    private fun buildResultFromTables(
        studentRows: List<List<String>>,
        subjectRows: List<List<String>>,
        categoryRows: List<List<String>>,
        defaultAcademicYear: Int,
        existingSubjects: List<SubjectDefinition>,
        existingCustomFields: List<CustomFieldDef>,
        formatLabel: String
    ): BackupParseResult {
        if (studentRows.size < 2) {
            throw IllegalStateException("Backup spreadsheet contains no student data rows.")
        }

        // 1. Merge subjects from Subjects sheet (if present)
        val workingSubjects = existingSubjects.toMutableList()
        val newSubjectsToUpsert = mutableMapOf<String, SubjectDefinition>()

        if (subjectRows.size >= 2) {
            val subHeaders = subjectRows.first().map { it.trim().lowercase() }
            val nameIdx = subHeaders.indexOfFirst { it.contains("subject name") || it == "name" }.takeIf { it >= 0 } ?: 0
            val scopeIdx = subHeaders.indexOfFirst { it.contains("scope") || it.contains("grade") || it.contains("class") }.takeIf { it >= 0 } ?: 1
            val fidIdx = subHeaders.indexOfFirst { it.contains("field id") }.takeIf { it >= 0 } ?: 2
            val flabelIdx = subHeaders.indexOfFirst { it.contains("field label") || it == "label" }.takeIf { it >= 0 } ?: 3
            val ftypeIdx = subHeaders.indexOfFirst { it.contains("field type") || it == "type" }.takeIf { it >= 0 } ?: 4
            val maxIdx = subHeaders.indexOfFirst { it.contains("max") }.takeIf { it >= 0 } ?: 5

            val grouped = subjectRows.drop(1)
                .filter { it.getOrNull(nameIdx)?.isNotBlank() == true }
                .groupBy { (it.getOrNull(nameIdx) ?: "").trim() to (it.getOrNull(scopeIdx) ?: GradeScopes.ALL_CLASSES).trim() }

            grouped.forEach { (key, rowsForSub) ->
                val (subName, scope) = key
                val importedFields = rowsForSub.mapNotNull { r ->
                    val lbl = r.getOrNull(flabelIdx)?.trim().orEmpty()
                    if (lbl.isBlank()) null
                    else SubjectFieldDef(
                        fieldId = r.getOrNull(fidIdx)?.trim()?.ifBlank { generateShortId("sf") } ?: generateShortId("sf"),
                        label = lbl,
                        fieldType = r.getOrNull(ftypeIdx)?.trim()?.takeIf { it in CustomFieldTypes.SUBJECT_TYPES }
                            ?: CustomFieldTypes.NUMBER,
                        maxMarks = r.getOrNull(maxIdx)?.trim()?.toLongOrNull() ?: 100L
                    )
                }
                val match = workingSubjects.firstOrNull {
                    it.name.equals(subName, ignoreCase = true) && it.gradeScope.equals(scope, ignoreCase = true)
                }
                if (match == null) {
                    val created = SubjectDefinition(name = subName, gradeScope = scope, fields = importedFields)
                    workingSubjects += created
                    newSubjectsToUpsert["$subName|$scope"] = created
                } else {
                    val missing = importedFields.filter { imp ->
                        match.fields.none { it.label.equals(imp.label, ignoreCase = true) }
                    }
                    if (missing.isNotEmpty()) {
                        val updated = match.copy(fields = match.fields + missing)
                        workingSubjects.remove(match)
                        workingSubjects += updated
                        newSubjectsToUpsert[match.id.ifBlank { "$subName|$scope" }] = updated
                    }
                }
            }
        }

        // 2. Merge custom category fields from CategoryFields sheet (if present)
        val workingCustomFields = existingCustomFields.toMutableList()
        val newCustomToUpsert = mutableListOf<CustomFieldDef>()
        if (categoryRows.size >= 2) {
            categoryRows.drop(1).forEach { r ->
                val cat = r.getOrNull(1)?.trim().orEmpty().ifBlank { "General" }
                val lbl = r.getOrNull(2)?.trim().orEmpty()
                if (lbl.isNotBlank()) {
                    val exists = workingCustomFields.any {
                        it.category.equals(cat, ignoreCase = true) && it.label.equals(lbl, ignoreCase = true)
                    }
                    if (!exists) {
                        val cf = CustomFieldDef(
                            category = cat,
                            label = lbl,
                            fieldType = r.getOrNull(3)?.trim()?.takeIf { it in CustomFieldTypes.ALL }
                                ?: CustomFieldTypes.TEXT,
                            resetsYearly = r.getOrNull(4)?.trim()?.equals("true", ignoreCase = true) ?: false
                        )
                        workingCustomFields += cf
                        newCustomToUpsert += cf
                    }
                }
            }
        }

        // 3. Map Student table headers flexibly
        val headers = studentRows.first().map { it.trim() }
        val normalizedHeaders = headers.map { it.lowercase() }

        fun findCol(vararg candidates: String): Int =
            normalizedHeaders.indexOfFirst { h -> candidates.any { c -> h == c || h.contains(c) } }

        val yearCol = findCol("academic year", "year")
        val classCol = findCol("class name", "class level", "class")
        val rollCol = findCol("roll no", "roll number", "roll")
        val sectionCol = findCol("section")
        val nameEnCol = findCol("student name (en)", "student name", "full name", "name")
        val nameBnCol = findCol("student name (bn)", "bangla")
        val fatherCol = findCol("father")
        val motherCol = findCol("mother")
        val dobCol = findCol("date of birth", "dob")
        val phoneCol = findCol("phone", "mobile", "guardian phone")
        val addressCol = findCol("address")
        val workingDaysCol = findCol("working days", "total days")
        val presentDaysCol = findCol("days present", "present days")
        val promoCol = findCol("promotion")
        val examsJsonCol = findCol("exam marks json")
        val notesJsonCol = findCol("notes json")
        val notesTextCol = findCol("field comments")
        val photoCol = findCol("photo base64", "photobase64")

        val fixedColIndices = setOf(
            yearCol, classCol, rollCol, sectionCol, nameEnCol, nameBnCol,
            fatherCol, motherCol, dobCol, phoneCol, addressCol, workingDaysCol,
            presentDaysCol, promoCol, examsJsonCol, notesJsonCol, notesTextCol, photoCol,
            findCol("attendance %"), findCol("overall performance %")
        ).filter { it >= 0 }.toSet()

        // Match extra columns to Subject Fields or Custom Category Fields

        val extraMappings = mutableMapOf<Int, ExtraColumnMapping>()
        headers.forEachIndexed { idx, rawHeader ->
            if (idx in fixedColIndices || rawHeader.isBlank()) return@forEachIndexed

            // Match "[Sub: English | Reading]" or "English: Reading"
            val subPipeMatch = Regex("""^\[Sub:\s*(.+?)\s*\|\s*(.+?)]$""", RegexOption.IGNORE_CASE).find(rawHeader)
            val catPipeMatch = Regex("""^\[Cat:\s*(.+?)\s*\|\s*(.+?)]$""", RegexOption.IGNORE_CASE).find(rawHeader)

            if (subPipeMatch != null) {
                val sName = subPipeMatch.groupValues[1].trim()
                val fLabel = subPipeMatch.groupValues[2].trim()
                val sub = workingSubjects.firstOrNull { it.name.equals(sName, ignoreCase = true) }
                val f = sub?.fields?.firstOrNull { it.label.equals(fLabel, ignoreCase = true) }
                if (sub != null && f != null) {
                    extraMappings[idx] = ExtraColumnMapping.SubjectCol(sub.id.ifBlank { sName }, f.fieldId)
                }
            } else if (catPipeMatch != null) {
                val cat = catPipeMatch.groupValues[1].trim()
                val lbl = catPipeMatch.groupValues[2].trim()
                val cf = workingCustomFields.firstOrNull {
                    it.category.equals(cat, ignoreCase = true) && it.label.equals(lbl, ignoreCase = true)
                }
                if (cf != null) {
                    extraMappings[idx] = ExtraColumnMapping.CustomCol(cf.id.ifBlank { "$cat|$lbl" })
                }
            } else if (rawHeader.contains(":")) {
                val parts = rawHeader.split(":", limit = 2)
                val left = parts[0].trim()
                val right = parts[1].trim()
                val sub = workingSubjects.firstOrNull { it.name.equals(left, ignoreCase = true) }
                val sf = sub?.fields?.firstOrNull { it.label.equals(right, ignoreCase = true) }
                if (sub != null && sf != null) {
                    extraMappings[idx] = ExtraColumnMapping.SubjectCol(sub.id, sf.fieldId)
                } else {
                    val cf = workingCustomFields.firstOrNull {
                        it.category.equals(left, ignoreCase = true) && it.label.equals(right, ignoreCase = true)
                    }
                    if (cf != null) {
                        extraMappings[idx] = ExtraColumnMapping.CustomCol(cf.id)
                    }
                }
            }
        }

        val parsedStudents = studentRows.drop(1).mapNotNull { row ->
            fun cell(colIdx: Int): String = if (colIdx >= 0) row.getOrNull(colIdx)?.trim().orEmpty() else ""

            val nameEn = cell(nameEnCol)
            if (nameEn.isBlank()) return@mapNotNull null

            val rawClass = cell(classCol).ifBlank { "Class 1" }
            val resolvedClass = ClassLevels.ALL.firstOrNull { it.equals(rawClass, ignoreCase = true) } ?: rawClass
            val roll = cell(rollCol).ifBlank { "1" }
            val year = cell(yearCol).toLongOrNull()?.coerceIn(2020L, 2050L) ?: defaultAcademicYear.toLong()

            val subVals = mutableMapOf<String, String>()
            val cfVals = mutableMapOf<String, String>()
            extraMappings.forEach { (colIdx, mapping) ->
                val v = row.getOrNull(colIdx)?.trim().orEmpty()
                if (v.isNotBlank()) {
                    when (mapping) {
                        is ExtraColumnMapping.SubjectCol -> {
                            subVals["${mapping.subjectIdOrKey}__${mapping.fieldId}"] = v.take(200)
                        }
                        is ExtraColumnMapping.CustomCol -> {
                            cfVals[mapping.fieldIdOrKey] = v.take(300)
                        }
                    }
                }
            }

            val notes = mutableMapOf<String, String>()
            val rawNotesJson = cell(notesJsonCol)
            if (rawNotesJson.startsWith("{")) {
                try {
                    val obj = JSONObject(rawNotesJson)
                    obj.keys().forEach { k ->
                        val v = obj.optString(k, "").trim()
                        if (v.isNotBlank()) notes[k] = v.take(300)
                    }
                } catch (_: Exception) {
                    // Ignore malformed notes JSON
                }
            } else {
                val rawNotesText = cell(notesTextCol)
                if (rawNotesText.isNotBlank()) {
                    rawNotesText.split("|").forEach { segment ->
                        val parts = segment.split(":", limit = 2)
                        if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                            notes[parts[0].trim()] = parts[1].trim().take(300)
                        }
                    }
                }
            }

            val examResults = mutableMapOf<String, String>()
            val rawExamsJson = cell(examsJsonCol)
            if (rawExamsJson.startsWith("{")) {
                try {
                    val obj = JSONObject(rawExamsJson)
                    obj.keys().forEach { k ->
                        val v = obj.optString(k, "").trim()
                        if (v.isNotBlank()) examResults[k] = v.take(20)
                    }
                } catch (_: Exception) {
                }
            }

            StudentRecord(
                studentNameEn = nameEn.take(120),
                studentNameBn = cell(nameBnCol).take(120),
                fatherNameEn = cell(fatherCol).take(120),
                motherNameEn = cell(motherCol).take(120),
                dateOfBirth = cell(dobCol).take(30),
                guardianPhone = cell(phoneCol).take(30),
                address = cell(addressCol).take(250),
                photoBase64 = cell(photoCol).take(140000),
                rollNumber = roll.take(20),
                classLevel = resolvedClass.take(40),
                section = cell(sectionCol).ifBlank { "A" }.take(30),
                academicYear = year,
                totalSchoolDays = cell(workingDaysCol).filter { it.isDigit() }.toLongOrNull()?.coerceIn(0L, 366L) ?: 0L,
                daysPresent = cell(presentDaysCol).filter { it.isDigit() }.toLongOrNull()?.coerceIn(0L, 366L) ?: 0L,
                promotionStatus = cell(promoCol).ifBlank { "Studying" }.take(40),
                customValues = cfVals,
                subjectValues = subVals,
                examResults = examResults,
                sectorNotes = notes
            )
        }

        if (parsedStudents.isEmpty()) {
            throw IllegalStateException("No valid student rows found. Ensure a 'Student Name (EN)' or 'Name' column exists.")
        }

        return BackupParseResult(
            students = parsedStudents,
            subjectsToUpsert = newSubjectsToUpsert.values.toList(),
            customFieldsToUpsert = newCustomToUpsert,
            formatLabel = formatLabel
        )
    }

    // ─────────────────────────── XML & CSV HELPERS ───────────────────────────

    private fun parseWorkbookSheetNames(workbookXml: String?): Map<Int, String> {
        if (workbookXml.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<Int, String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(workbookXml))
            var event = parser.eventType
            var idx = 1
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name").orEmpty()
                    val sheetId = parser.getAttributeValue(null, "sheetId")?.toIntOrNull() ?: idx
                    result[idx] = name.ifBlank { "Sheet$sheetId" }
                    idx++
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun parseSharedStrings(xml: String): List<String> {
        val list = mutableListOf<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var insideSi = false
            val current = StringBuilder()
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "si") {
                            insideSi = true
                            current.setLength(0)
                        } else if (insideSi && parser.name == "t") {
                            current.append(parser.nextText())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "si") {
                            list.add(current.toString())
                            insideSi = false
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return list
    }

    private fun parseWorksheetXml(xml: String, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xml))
            var event = parser.eventType
            var currentRow = mutableListOf<String>()
            var currentCellIdx = -1
            var currentType = ""
            var currentVal = StringBuilder()
            var inCell = false

            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            inCell = true
                            val ref = parser.getAttributeValue(null, "r").orEmpty()
                            currentCellIdx = if (ref.isNotBlank()) colRefToIndex(ref) else currentRow.size
                            currentType = parser.getAttributeValue(null, "t").orEmpty()
                            currentVal.setLength(0)
                        }
                        "v", "t" -> {
                            if (inCell) {
                                currentVal.append(parser.nextText())
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> when (parser.name) {
                        "c" -> {
                            val resolved = if (currentType == "s") {
                                val idx = currentVal.toString().trim().toIntOrNull()
                                if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
                            } else {
                                currentVal.toString()
                            }
                            while (currentRow.size <= currentCellIdx) {
                                currentRow.add("")
                            }
                            if (currentCellIdx >= 0) {
                                currentRow[currentCellIdx] = resolved
                            }
                            inCell = false
                        }
                        "row" -> {
                            if (currentRow.any { it.isNotBlank() }) {
                                rows.add(currentRow)
                            }
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
        }
        return rows
    }

    fun parseCsvRows(csvText: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < csvText.length) {
            val ch = csvText[i]
            when {
                inQuotes -> {
                    if (ch == '"') {
                        if (i + 1 < csvText.length && csvText[i + 1] == '"') {
                            cell.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        cell.append(ch)
                    }
                }
                ch == '"' -> inQuotes = true
                ch == ',' -> {
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                }
                ch == '\r' -> {
                    // ignore CR
                }
                ch == '\n' -> {
                    currentRow.add(cell.toString())
                    cell.setLength(0)
                    if (currentRow.any { it.isNotBlank() }) {
                        rows.add(currentRow.toList())
                    }
                    currentRow.clear()
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(cell.toString())
            if (currentRow.any { it.isNotBlank() }) {
                rows.add(currentRow.toList())
            }
        }
        return rows
    }

    private fun xmlEscape(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
