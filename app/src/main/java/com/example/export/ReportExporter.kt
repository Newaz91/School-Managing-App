package com.example.export

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.CustomFieldDef
import com.example.data.model.StudentRecord
import com.example.data.model.StudentsCsvExporter
import com.example.data.model.SubjectDefinition
import com.example.data.model.TeacherProfile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportTargetFormat(
    val title: String,
    val subtitle: String,
    val mimeType: String,
    val extension: String
) {
    PDF(
        title = "PDF Document (.pdf)",
        subtitle = "Official printable A4 school report",
        mimeType = "application/pdf",
        extension = "pdf"
    ),
    GOOGLE_SHEETS(
        title = "Google Sheets / Excel (.csv)",
        subtitle = "Spreadsheet with all fixed, yearly, subject & custom fields",
        mimeType = "text/csv",
        extension = "csv"
    ),
    MICROSOFT_WORD(
        title = "Microsoft Word (.docx)",
        subtitle = "Editable Word document with formatted entries & comments",
        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        extension = "docx"
    ),
    WHATSAPP(
        title = "WhatsApp / Messaging Share",
        subtitle = "Instant formatted summary for parents or headteacher",
        mimeType = "text/plain",
        extension = "txt"
    )
}

data class DynamicFieldExportEntry(
    val group: String,
    val label: String,
    val value: String,
    val comment: String
)

object ReportExporter {

    fun extractStudentEntries(
        s: StudentRecord,
        subjects: List<SubjectDefinition>,
        customFields: List<CustomFieldDef>
    ): List<DynamicFieldExportEntry> {
        val entries = mutableListOf<DynamicFieldExportEntry>()

        // Fixed Core Fields
        entries += DynamicFieldExportEntry("Fixed", "Student Name (EN)", s.studentNameEn.ifBlank { "—" }, s.noteForKey("fixed_name_en"))
        entries += DynamicFieldExportEntry("Fixed", "Student Name (BN)", s.studentNameBn.ifBlank { "—" }, s.noteForKey("fixed_name_bn"))
        entries += DynamicFieldExportEntry("Fixed", "Father's Name", s.fatherNameEn.ifBlank { "—" }, s.noteForKey("fixed_father"))
        entries += DynamicFieldExportEntry("Fixed", "Mother's Name", s.motherNameEn.ifBlank { "—" }, s.noteForKey("fixed_mother"))
        entries += DynamicFieldExportEntry("Fixed", "Date of Birth", s.dateOfBirth.ifBlank { "—" }, s.noteForKey("fixed_dob"))
        entries += DynamicFieldExportEntry("Fixed", "Phone Number", s.guardianPhone.ifBlank { "—" }, s.noteForKey("fixed_phone"))
        entries += DynamicFieldExportEntry("Fixed", "Address", s.effectiveAddress().ifBlank { "—" }, s.noteForKey("fixed_address"))

        // Yearly Fields
        entries += DynamicFieldExportEntry("Yearly", "Academic Year", s.academicYear.toString(), s.noteForKey("yearly_year"))
        entries += DynamicFieldExportEntry("Yearly", "Class Name", s.classLevel.ifBlank { "—" }, s.noteForKey("yearly_class"))
        entries += DynamicFieldExportEntry("Yearly", "Roll Number", s.rollNumber.ifBlank { "—" }, s.noteForKey("yearly_roll"))
        entries += DynamicFieldExportEntry("Yearly", "Section", s.section.ifBlank { "—" }, s.noteForKey("yearly_section"))
        entries += DynamicFieldExportEntry(
            "Yearly",
            "Attendance",
            if (s.totalSchoolDays > 0) "${s.daysPresent}/${s.totalSchoolDays} (${s.attendancePercentage()}%)" else "—",
            s.noteForKey("yearly_attendance")
        )
        entries += DynamicFieldExportEntry("Yearly", "Promotion Status", s.promotionStatus.ifBlank { "—" }, s.noteForKey("yearly_promotion"))

        // Subject Fields (matching class or with entered values)
        subjects.forEach { subject ->
            val hasAnyValue = subject.fields.any { s.subjectValue(subject.id, it.fieldId).isNotBlank() }
            if (subject.appliesToClass(s.classLevel) || hasAnyValue) {
                subject.fields.forEach { field ->
                    val valStr = s.subjectValue(subject.id, field.fieldId).ifBlank { "—" }
                    val noteKey = "sf_${subject.id}__${field.fieldId}"
                    entries += DynamicFieldExportEntry(
                        group = subject.name,
                        label = field.label,
                        value = valStr,
                        comment = s.noteForKey(noteKey)
                    )
                }
            }
        }

        // Flexible Custom Category Fields
        customFields.forEach { cf ->
            val valStr = s.customValue(cf.id).ifBlank { "—" }
            entries += DynamicFieldExportEntry(
                group = cf.category,
                label = cf.label,
                value = valStr,
                comment = s.noteForKey("cf_${cf.id}")
            )
        }

        return entries
    }

    fun buildRegisterPdfBytes(
        students: List<StudentRecord>,
        teacherProfile: TeacherProfile?,
        year: Int,
        subjects: List<SubjectDefinition> = emptyList(),
        customFields: List<CustomFieldDef> = emptyList()
    ): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f

        val titlePaint = Paint().apply {
            color = Color.rgb(0, 106, 78)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subtitlePaint = Paint().apply {
            color = Color.rgb(70, 75, 72)
            textSize = 9.5f
            isAntiAlias = true
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 9.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 32, 30)
            textSize = 9f
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(200, 206, 202)
            strokeWidth = 0.8f
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 44f

        fun drawHeader() {
            val school = teacherProfile?.schoolName?.ifBlank { "Govt. Primary School Register" }
                ?: "Govt. Primary School Register"
            canvas.drawText("$school — $year Register", margin, y, titlePaint)
            y += 16f
            val teacherLine = buildString {
                append("Teacher: ${teacherProfile?.teacherName?.ifBlank { "—" } ?: "—"}")
                if (!teacherProfile?.emisCode.isNullOrBlank()) append("  |  EMIS: ${teacherProfile?.emisCode}")
                append("  |  Students: ${students.size}  |  Subjects: ${subjects.size}")
            }
            canvas.drawText(teacherLine, margin, y, subtitlePaint)
            y += 14f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 14f
            canvas.drawText("Roll", margin, y, headerPaint)
            canvas.drawText("Student Name", margin + 36f, y, headerPaint)
            canvas.drawText("Class", margin + 185f, y, headerPaint)
            canvas.drawText("Phone", margin + 255f, y, headerPaint)
            canvas.drawText("Att%", margin + 350f, y, headerPaint)
            canvas.drawText("Perf%", margin + 395f, y, headerPaint)
            canvas.drawText("Fields", margin + 455f, y, headerPaint)
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 14f
        }

        drawHeader()

        if (students.isEmpty()) {
            canvas.drawText("No student records available for $year.", margin, y, bodyPaint)
        } else {
            students.forEach { s ->
                if (y > pageHeight - 48f) {
                    doc.finishPage(page)
                    pageNum++
                    page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                    canvas = page.canvas
                    y = 44f
                    drawHeader()
                }
                val (filled, total) = s.filledFieldProgress(customFields, subjects)
                canvas.drawText(s.rollNumber.take(6), margin, y, bodyPaint)
                canvas.drawText(s.studentNameEn.take(25), margin + 36f, y, bodyPaint)
                canvas.drawText(s.classLevel.take(11), margin + 185f, y, bodyPaint)
                canvas.drawText(s.guardianPhone.ifBlank { "—" }.take(14), margin + 255f, y, bodyPaint)
                canvas.drawText("${s.attendancePercentage()}%", margin + 350f, y, bodyPaint)
                canvas.drawText("${s.overallPerformanceScore(subjects)}%", margin + 395f, y, bodyPaint)
                canvas.drawText("$filled/$total", margin + 455f, y, bodyPaint)
                y += 16f
            }
        }

        doc.finishPage(page)
        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    fun buildStudentPdfBytes(
        student: StudentRecord,
        teacherProfile: TeacherProfile?,
        subjects: List<SubjectDefinition> = emptyList(),
        customFields: List<CustomFieldDef> = emptyList()
    ): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 36f

        val titlePaint = Paint().apply {
            color = Color.rgb(0, 106, 78)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.rgb(75, 80, 78)
            textSize = 9.5f
            isAntiAlias = true
        }
        val labelPaint = Paint().apply {
            color = Color.rgb(25, 28, 26)
            textSize = 9f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val valuePaint = Paint().apply {
            color = Color.rgb(45, 48, 46)
            textSize = 9f
            isAntiAlias = true
        }
        val commentPaint = Paint().apply {
            color = Color.rgb(145, 100, 0)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
            isAntiAlias = true
        }
        val linePaint = Paint().apply {
            color = Color.rgb(215, 220, 217)
            strokeWidth = 0.7f
        }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 42f

        val school = teacherProfile?.schoolName?.ifBlank { "Govt. Primary School" } ?: "Govt. Primary School"
        canvas.drawText("${student.studentNameEn} — Student Profile Dossier", margin, y, titlePaint)
        y += 15f
        canvas.drawText(
            "$school  |  ${student.classLevel} (Roll ${student.rollNumber})  |  Year ${student.academicYear}  |  Performance: ${student.overallPerformanceScore(subjects)}%",
            margin,
            y,
            subPaint
        )
        y += 14f
        canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
        y += 14f

        val entries = extractStudentEntries(student, subjects, customFields)
        entries.forEach { item ->
            val neededHeight = if (item.comment.isNotBlank()) 26f else 15f
            if (y + neededHeight > pageHeight - 36f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 42f
                canvas.drawText("${student.studentNameEn} (Cont. Page $pageNum)", margin, y, titlePaint)
                y += 18f
            }
            canvas.drawText("[${item.group}] ${item.label}:", margin, y, labelPaint)
            canvas.drawText(item.value.take(52), margin + 195f, y, valuePaint)
            y += 13f
            if (item.comment.isNotBlank()) {
                canvas.drawText("Detail/Note: ${item.comment.take(80)}", margin + 14f, y, commentPaint)
                y += 12f
            }
        }

        doc.finishPage(page)
        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    fun buildRegisterDocxBytes(
        students: List<StudentRecord>,
        teacherProfile: TeacherProfile?,
        year: Int,
        subjects: List<SubjectDefinition> = emptyList(),
        customFields: List<CustomFieldDef> = emptyList()
    ): ByteArray {
        val bodyXml = buildString {
            val school = xmlEscape(teacherProfile?.schoolName?.ifBlank { "Govt. Primary School Register" } ?: "Govt. Primary School Register")
            val teacher = xmlEscape(teacherProfile?.teacherName?.ifBlank { "Teacher" } ?: "Teacher")
            append(docxParagraph("$school — $year Annual Student Register", bold = true, sizeHalfPts = 32))
            append(docxParagraph("Teacher: $teacher | Total Students: ${students.size} | Subjects: ${subjects.size} | Flexible Fields: ${customFields.size}", bold = false, sizeHalfPts = 20))
            append(docxParagraph("", bold = false, sizeHalfPts = 20))

            students.forEach { s ->
                val (filled, total) = s.filledFieldProgress(customFields, subjects)
                append(
                    docxParagraph(
                        "Roll ${xmlEscape(s.rollNumber)} — ${xmlEscape(s.studentNameEn)} (${xmlEscape(s.classLevel)}) | Att: ${s.attendancePercentage()}% | Perf: ${s.overallPerformanceScore(subjects)}% | Filled: $filled/$total",
                        bold = true,
                        sizeHalfPts = 22
                    )
                )
                val summaryLine = "Father: ${s.fatherNameEn.ifBlank { "—" }} | Mother: ${s.motherNameEn.ifBlank { "—" }} | Phone: ${s.guardianPhone.ifBlank { "—" }} | Address: ${s.effectiveAddress().ifBlank { "—" }}"
                append(docxParagraph(xmlEscape(summaryLine), bold = false, sizeHalfPts = 18))

                val comments = extractStudentEntries(s, subjects, customFields).filter { it.comment.isNotBlank() }
                if (comments.isNotEmpty()) {
                    val notes = comments.joinToString("; ") { "[${it.group}] ${it.label}: ${it.comment}" }
                    append(docxParagraph("Field Notes: ${xmlEscape(notes)}", bold = false, sizeHalfPts = 18))
                }
            }
        }
        return packageDocx(bodyXml)
    }

    fun buildStudentDocxBytes(
        student: StudentRecord,
        teacherProfile: TeacherProfile?,
        subjects: List<SubjectDefinition> = emptyList(),
        customFields: List<CustomFieldDef> = emptyList()
    ): ByteArray {
        val bodyXml = buildString {
            val school = xmlEscape(teacherProfile?.schoolName?.ifBlank { "Govt. Primary School" } ?: "Govt. Primary School")
            append(docxParagraph("${xmlEscape(student.studentNameEn)} — Student Record", bold = true, sizeHalfPts = 32))
            append(docxParagraph("$school | ${xmlEscape(student.classLevel)} | Roll ${xmlEscape(student.rollNumber)} | Year ${student.academicYear}", bold = false, sizeHalfPts = 20))
            append(docxParagraph("Overall Performance: ${student.overallPerformanceScore(subjects)}% | Attendance: ${student.attendancePercentage()}%", bold = true, sizeHalfPts = 20))
            append(docxParagraph("", bold = false, sizeHalfPts = 18))

            extractStudentEntries(student, subjects, customFields).forEach { item ->
                append(docxParagraph("[${xmlEscape(item.group)}] ${xmlEscape(item.label)}: ${xmlEscape(item.value)}", bold = true, sizeHalfPts = 20))
                if (item.comment.isNotBlank()) {
                    append(docxParagraph("    Comment/Detail: ${xmlEscape(item.comment)}", bold = false, sizeHalfPts = 18))
                }
            }
        }
        return packageDocx(bodyXml)
    }

    fun buildRegisterWhatsAppText(
        students: List<StudentRecord>,
        teacherProfile: TeacherProfile?,
        year: Int,
        subjects: List<SubjectDefinition> = emptyList()
    ): String {
        val school = teacherProfile?.schoolName?.ifBlank { "Govt. Primary School" } ?: "Govt. Primary School"
        val avgAtt = if (students.any { it.totalSchoolDays > 0 }) {
            students.filter { it.totalSchoolDays > 0 }.map { it.attendancePercentage() }.average().toInt()
        } else 0
        val avgPerf = if (students.isNotEmpty()) {
            students.map { it.overallPerformanceScore(subjects) }.average().toInt()
        } else 0
        val topStudents = students.sortedByDescending { it.overallPerformanceScore(subjects) }.take(5)

        return buildString {
            appendLine("*$school — $year Register Summary*")
            appendLine("Total Enrolled: ${students.size} students")
            appendLine("Average Attendance: $avgAtt%")
            appendLine("Overall Performance Avg: $avgPerf%")
            if (topStudents.isNotEmpty()) {
                appendLine()
                appendLine("*Top Performing Students:*")
                topStudents.forEachIndexed { idx, s ->
                    appendLine("${idx + 1}. ${s.studentNameEn} (${s.classLevel}, Roll ${s.rollNumber}) — ${s.overallPerformanceScore(subjects)}%")
                }
            }
        }
    }

    fun buildStudentWhatsAppText(
        student: StudentRecord,
        teacherProfile: TeacherProfile?,
        subjects: List<SubjectDefinition> = emptyList(),
        customFields: List<CustomFieldDef> = emptyList()
    ): String {
        val school = teacherProfile?.schoolName?.ifBlank { "Govt. Primary School" } ?: "Govt. Primary School"
        val entries = extractStudentEntries(student, subjects, customFields)
        val notes = entries.filter { it.comment.isNotBlank() }
        return buildString {
            appendLine("*$school (${student.academicYear})*")
            appendLine("*Student:* ${student.studentNameEn} ${if (student.studentNameBn.isNotBlank()) "(${student.studentNameBn})" else ""}")
            appendLine("*Class:* ${student.classLevel} | *Roll:* ${student.rollNumber}")
            appendLine("*Father:* ${student.fatherNameEn.ifBlank { "—" }} | *Mother:* ${student.motherNameEn.ifBlank { "—" }}")
            appendLine("*Attendance:* ${student.daysPresent}/${student.totalSchoolDays} (${student.attendancePercentage()}%)")
            appendLine("*Overall Performance:* ${student.overallPerformanceScore(subjects)}% | *Status:* ${student.promotionStatus}")
            val subjectEntries = entries.filter { it.group != "Fixed" && it.group != "Yearly" && it.value != "—" }
            if (subjectEntries.isNotEmpty()) {
                appendLine()
                appendLine("*Recorded Subject & Flexible Fields:*")
                subjectEntries.take(15).forEach {
                    appendLine("• [${it.group}] ${it.label}: ${it.value}")
                }
            }
            if (notes.isNotEmpty()) {
                appendLine()
                appendLine("*Field Comments (${notes.size}):*")
                notes.take(10).forEach {
                    appendLine("• ${it.label}: ${it.comment}")
                }
            }
        }
    }

    fun writeCsvBytes(
        students: List<StudentRecord>,
        subjects: List<SubjectDefinition> = emptyList(),
        customFields: List<CustomFieldDef> = emptyList()
    ): ByteArray = StudentsCsvExporter.exportToCsv(students, subjects, customFields).toByteArray(Charsets.UTF_8)

    fun writeBytesToUri(context: Context, uri: Uri, bytes: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(bytes)
        }
    }

    fun shareFile(
        context: Context,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        chooserTitle: String
    ) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, fileName)
        FileOutputStream(file).use { it.write(bytes) }
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, chooserTitle))
    }

    fun shareWhatsAppText(context: Context, text: String) {
        val waIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            setPackage("com.whatsapp")
        }
        try {
            context.startActivity(waIntent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(fallback, "Share Summary via WhatsApp / Messaging"))
        }
    }

    private fun docxParagraph(text: String, bold: Boolean, sizeHalfPts: Int): String {
        val rPr = buildString {
            append("<w:rPr>")
            if (bold) append("<w:b/>")
            append("<w:sz w:val=\"$sizeHalfPts\"/>")
            append("</w:rPr>")
        }
        return "<w:p><w:r>$rPr<w:t xml:space=\"preserve\">$text</w:t></w:r></w:p>"
    }

    private fun packageDocx(bodyParagraphsXml: String): ByteArray {
        val contentTypes = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>"""

        val rels = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        val documentXml = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $bodyParagraphsXml
    <w:sectPr/>
  </w:body>
</w:document>"""

        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(contentTypes.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(rels.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return baos.toByteArray()
    }

    private fun xmlEscape(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
