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
                canvas.drawText(s.primaryName().take(25), margin + 36f, y, bodyPaint)
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
        canvas.drawText("${student.primaryName()} — Student Profile Dossier", margin, y, titlePaint)
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
                canvas.drawText("${student.primaryName()} (Cont. Page $pageNum)", margin, y, titlePaint)
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
                        "Roll ${xmlEscape(s.rollNumber)} — ${xmlEscape(s.primaryName())} (${xmlEscape(s.classLevel)}) | Att: ${s.attendancePercentage()}% | Perf: ${s.overallPerformanceScore(subjects)}% | Filled: $filled/$total",
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
            append(docxParagraph("${xmlEscape(student.primaryName())} — Student Record", bold = true, sizeHalfPts = 32))
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
                    appendLine("${idx + 1}. ${s.primaryName()} (${s.classLevel}, Roll ${s.rollNumber}) — ${s.overallPerformanceScore(subjects)}%")
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
            appendLine("*Student:* ${student.combinedDisplayName()}")
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

    fun sharePlainText(context: Context, text: String, chooserTitle: String = "Share Result") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, chooserTitle))
    }

    // ─────────────────────────── EXAM MARKSHEET & CLASS RESULT EXPORTS ───────────────────────────

    private fun drawTextInCell(
        canvas: android.graphics.Canvas,
        text: String,
        x: Float,
        yTop: Float,
        width: Float,
        paint: android.text.TextPaint,
        alignment: android.text.Layout.Alignment = android.text.Layout.Alignment.ALIGN_NORMAL
    ) {
        val safeWidth = width.toInt().coerceAtLeast(10)
        val layout = android.text.StaticLayout.Builder
            .obtain(text, 0, text.length, paint, safeWidth)
            .setAlignment(alignment)
            .setMaxLines(2)
            .setEllipsize(android.text.TextUtils.TruncateAt.END)
            .build()
        canvas.save()
        canvas.translate(x, yTop)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun renderStudentExamCardOnCanvas(
        canvas: android.graphics.Canvas,
        pageWidth: Float,
        pageHeight: Float,
        student: StudentRecord,
        exam: com.example.data.model.ExamTermConfig,
        summary: com.example.data.model.StudentExamResultSummary,
        examConfigs: List<com.example.data.model.ExamTermConfig>,
        teacherProfile: TeacherProfile?,
        strings: com.example.ui.i18n.AppStrings
    ) {
        val scale = pageWidth / 842f
        val margin = 28f * scale
        val contentWidth = pageWidth - margin * 2f
        val normalizedExams = com.example.data.model.ExamDefaults.ensureThreeExams(examConfigs)
        val combinedTotals = com.example.data.model.ExamResultCalculator.computeCombinedThreeTermTotals(
            student = student,
            examConfigs = normalizedExams,
            activeExamId = exam.examId
        )

        val schoolPaint = android.text.TextPaint().apply {
            color = Color.rgb(0, 106, 78)
            textSize = 13.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val examTitlePaint = android.text.TextPaint().apply {
            color = Color.rgb(18, 22, 20)
            textSize = 15.5f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val metaPaint = android.text.TextPaint().apply {
            color = Color.rgb(55, 62, 58)
            textSize = 10f * scale
            isAntiAlias = true
        }
        val metaBoldPaint = android.text.TextPaint().apply {
            color = Color.rgb(20, 24, 22)
            textSize = 10f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val tableHeaderText = android.text.TextPaint().apply {
            color = Color.WHITE
            textSize = 8.4f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subHeaderText = android.text.TextPaint().apply {
            color = Color.rgb(12, 48, 36)
            textSize = 7.8f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val cellText = android.text.TextPaint().apply {
            color = Color.rgb(25, 28, 26)
            textSize = 8.4f * scale
            isAntiAlias = true
        }
        val cellBoldText = android.text.TextPaint().apply {
            color = Color.rgb(15, 20, 18)
            textSize = 8.6f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val passPaint = android.text.TextPaint().apply {
            color = Color.rgb(0, 106, 78)
            textSize = 8.4f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val failPaint = android.text.TextPaint().apply {
            color = Color.rgb(186, 26, 26)
            textSize = 8.4f * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val borderPaint = Paint().apply {
            color = Color.rgb(175, 185, 180)
            style = Paint.Style.STROKE
            strokeWidth = 0.9f * scale
        }
        val headerBgPaint = Paint().apply {
            color = Color.rgb(0, 106, 78)
            style = Paint.Style.FILL
        }
        val subHeaderBgPaint = Paint().apply {
            color = Color.rgb(222, 238, 231)
            style = Paint.Style.FILL
        }
        val infoBgPaint = Paint().apply {
            color = Color.rgb(241, 247, 244)
            style = Paint.Style.FILL
        }
        val zebraBgPaint = Paint().apply {
            color = Color.rgb(248, 250, 249)
            style = Paint.Style.FILL
        }
        val totalBgPaint = Paint().apply {
            color = Color.rgb(230, 242, 236)
            style = Paint.Style.FILL
        }

        canvas.drawColor(Color.WHITE)

        var y = 26f * scale
        val schoolName = teacherProfile?.schoolName?.ifBlank { strings.appName } ?: strings.appName

        // 1. TOP CENTER: School Name + 3-Term Marksheet Title
        drawTextInCell(
            canvas = canvas,
            text = "$schoolName  •  ${student.academicYear}",
            x = margin,
            yTop = y,
            width = contentWidth,
            paint = schoolPaint,
            alignment = android.text.Layout.Alignment.ALIGN_CENTER
        )
        y += 18f * scale

        drawTextInCell(
            canvas = canvas,
            text = "${strings.threeExamMarksheetTitle} (${strings.localizeExamTitle(exam)})",
            x = margin,
            yTop = y,
            width = contentWidth,
            paint = examTitlePaint,
            alignment = android.text.Layout.Alignment.ALIGN_CENTER
        )
        y += 22f * scale

        // 2. Student Info Strip
        val infoBoxHeight = 42f * scale
        val infoRect = android.graphics.RectF(margin, y, pageWidth - margin, y + infoBoxHeight)
        canvas.drawRoundRect(infoRect, 6f * scale, 6f * scale, infoBgPaint)
        canvas.drawRoundRect(infoRect, 6f * scale, 6f * scale, borderPaint)

        val displayName = student.combinedDisplayName()
        val classDisplay = strings.localizeClass(student.classLevel) +
            if (student.section.isNotBlank()) " (${student.section})" else ""
        drawTextInCell(
            canvas,
            "${strings.resultSheetStudentName}: $displayName",
            margin + 10f * scale,
            y + 6f * scale,
            contentWidth * 0.56f,
            metaBoldPaint
        )
        drawTextInCell(
            canvas,
            "${strings.resultSheetClassName}: $classDisplay",
            margin + 10f * scale,
            y + 23f * scale,
            contentWidth * 0.56f,
            metaPaint
        )
        drawTextInCell(
            canvas,
            "${strings.resultSheetRollNo}: ${student.rollNumber.ifBlank { "—" }}",
            margin + contentWidth * 0.58f,
            y + 13f * scale,
            contentWidth * 0.40f,
            metaBoldPaint,
            android.text.Layout.Alignment.ALIGN_OPPOSITE
        )
        y += infoBoxHeight + 12f * scale

        // 3. 2-TIER MARKSHEET TABLE (No Pass Column; 3 Cols per Term + 3-Term Average + Grade)
        val subjW = contentWidth * 0.150f
        val fullW = contentWidth * 0.055f
        val contW = contentWidth * 0.068f
        val sumW = contentWidth * 0.068f
        val totW = contentWidth * 0.074f
        val termW = contW + sumW + totW // 0.210f * 3 = 0.630f
        val avgW = contentWidth * 0.075f
        val gradeW = contentWidth * 0.090f

        val leafColWidths = floatArrayOf(
            subjW, fullW,
            contW, sumW, totW,
            contW, sumW, totW,
            contW, sumW, totW,
            avgW, gradeW
        )

        val tier1H = 24f * scale
        val tier2H = 22f * scale
        val rowHeight = 23f * scale

        // Tier 1 Header Row
        canvas.drawRect(margin, y, pageWidth - margin, y + tier1H, headerBgPaint)
        canvas.drawRect(margin, y, pageWidth - margin, y + tier1H, borderPaint)
        val tier1Specs = listOf(
            strings.resultSheetSubjectCol to subjW,
            strings.resultSheetFullCol to fullW,
            strings.localizeExamTitle(normalizedExams[0]) to termW,
            strings.localizeExamTitle(normalizedExams[1]) to termW,
            strings.localizeExamTitle(normalizedExams[2]) to termW,
            strings.threeTermAverageCol to avgW,
            strings.resultSheetGradeCol to gradeW
        )
        var t1x = margin
        tier1Specs.forEachIndexed { idx, (label, w) ->
            val align = if (idx == 0) android.text.Layout.Alignment.ALIGN_NORMAL else android.text.Layout.Alignment.ALIGN_CENTER
            drawTextInCell(canvas, label, t1x + 3f * scale, y + 6f * scale, w - 6f * scale, tableHeaderText, align)
            if (idx > 0) canvas.drawLine(t1x, y, t1x, y + tier1H, borderPaint)
            t1x += w
        }
        y += tier1H

        // Tier 2 Sub-Header Row
        canvas.drawRect(margin, y, pageWidth - margin, y + tier2H, subHeaderBgPaint)
        canvas.drawRect(margin, y, pageWidth - margin, y + tier2H, borderPaint)
        val tier2Labels = listOf(
            "", "",
            strings.continuousShortCol, strings.summativeShortCol, strings.totalShortCol,
            strings.continuousShortCol, strings.summativeShortCol, strings.totalShortCol,
            strings.continuousShortCol, strings.summativeShortCol, strings.totalShortCol,
            strings.threeTermAverageCol, strings.resultSheetOverallResult
        )
        var t2x = margin
        tier2Labels.forEachIndexed { idx, label ->
            if (label.isNotEmpty()) {
                drawTextInCell(
                    canvas,
                    label,
                    t2x + 2f * scale,
                    y + 5f * scale,
                    leafColWidths[idx] - 4f * scale,
                    subHeaderText,
                    android.text.Layout.Alignment.ALIGN_CENTER
                )
            }
            if (idx > 0) canvas.drawLine(t2x, y, t2x, y + tier2H, borderPaint)
            t2x += leafColWidths[idx]
        }
        y += tier2H

        fun fmt(v: Double?): String = v?.let { com.example.data.model.ExamResultCalculator.formatMark(it) } ?: "—"

        // Subject Rows
        if (combinedTotals.subjectRows.isEmpty()) {
            canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, borderPaint)
            drawTextInCell(
                canvas,
                "No subjects configured for ${exam.title}",
                margin + 8f * scale,
                y + 6f * scale,
                contentWidth - 16f * scale,
                cellText,
                android.text.Layout.Alignment.ALIGN_CENTER
            )
            y += rowHeight
        } else {
            combinedTotals.subjectRows.forEachIndexed { rowIdx, row ->
                if (rowIdx % 2 == 1) {
                    canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, zebraBgPaint)
                }
                canvas.drawRect(margin, y, pageWidth - margin, y + rowHeight, borderPaint)

                val stText = when {
                    !row.activeTermHasScore -> "—"
                    row.activeTermPassed -> "${row.activeTermGrade} (${strings.resultPass})"
                    else -> "${row.activeTermGrade} (${strings.resultFail})"
                }
                val stPaint = when {
                    !row.activeTermHasScore -> cellText
                    row.activeTermPassed -> passPaint
                    else -> failPaint
                }

                val rowCells = listOf(
                    row.subjectName,
                    row.fullMarks.toString(),
                    fmt(row.term1.continuous), fmt(row.term1.summative), fmt(row.term1.total),
                    fmt(row.term2.continuous), fmt(row.term2.summative), fmt(row.term2.total),
                    fmt(row.term3.continuous), fmt(row.term3.summative), fmt(row.term3.total),
                    fmt(row.threeTermAverage),
                    stText
                )
                var rx = margin
                rowCells.forEachIndexed { colIdx, textVal ->
                    val align = if (colIdx == 0) android.text.Layout.Alignment.ALIGN_NORMAL else android.text.Layout.Alignment.ALIGN_CENTER
                    val p = when (colIdx) {
                        0, 4, 7, 10, 11 -> cellBoldText
                        12 -> stPaint
                        else -> cellText
                    }
                    drawTextInCell(
                        canvas,
                        textVal,
                        rx + 3f * scale,
                        y + 5f * scale,
                        leafColWidths[colIdx] - 6f * scale,
                        p,
                        align
                    )
                    if (colIdx > 0) canvas.drawLine(rx, y, rx, y + rowHeight, borderPaint)
                    rx += leafColWidths[colIdx]
                }
                y += rowHeight
            }
        }

        // Total Summary Row
        canvas.drawRect(margin, y, pageWidth - margin, y + tier1H, totalBgPaint)
        canvas.drawRect(margin, y, pageWidth - margin, y + tier1H, borderPaint)
        val overallStatusText = when (summary.outcome) {
            com.example.data.model.ExamPassOutcome.PASS ->
                "${strings.resultPass} (${com.example.data.model.ExamResultCalculator.formatGpa(summary.gpa)})"
            com.example.data.model.ExamPassOutcome.FAIL -> strings.resultFail
            com.example.data.model.ExamPassOutcome.PENDING -> strings.resultPending
        }
        val overallPaint = when (summary.outcome) {
            com.example.data.model.ExamPassOutcome.PASS -> passPaint
            com.example.data.model.ExamPassOutcome.FAIL -> failPaint
            com.example.data.model.ExamPassOutcome.PENDING -> cellBoldText
        }

        val totalCells = listOf(
            strings.resultSheetTotalRow,
            combinedTotals.configuredFullMarks.toString(),
            if (combinedTotals.t1HasScore && combinedTotals.t1ContTotal > 0.0) fmt(combinedTotals.t1ContTotal) else "—",
            if (combinedTotals.t1HasScore && combinedTotals.t1SumTotal > 0.0) fmt(combinedTotals.t1SumTotal) else "—",
            if (combinedTotals.t1HasScore) fmt(combinedTotals.t1Total) else "—",
            if (combinedTotals.t2HasScore && combinedTotals.t2ContTotal > 0.0) fmt(combinedTotals.t2ContTotal) else "—",
            if (combinedTotals.t2HasScore && combinedTotals.t2SumTotal > 0.0) fmt(combinedTotals.t2SumTotal) else "—",
            if (combinedTotals.t2HasScore) fmt(combinedTotals.t2Total) else "—",
            if (combinedTotals.t3HasScore && combinedTotals.t3ContTotal > 0.0) fmt(combinedTotals.t3ContTotal) else "—",
            if (combinedTotals.t3HasScore && combinedTotals.t3SumTotal > 0.0) fmt(combinedTotals.t3SumTotal) else "—",
            if (combinedTotals.t3HasScore) fmt(combinedTotals.t3Total) else "—",
            fmt(combinedTotals.threeTermAvgTotal),
            overallStatusText
        )
        var tx = margin
        totalCells.forEachIndexed { colIdx, txt ->
            val align = if (colIdx == 0) android.text.Layout.Alignment.ALIGN_NORMAL else android.text.Layout.Alignment.ALIGN_CENTER
            val p = if (colIdx == 12) overallPaint else cellBoldText
            drawTextInCell(
                canvas,
                txt,
                tx + 3f * scale,
                y + 6f * scale,
                leafColWidths[colIdx] - 6f * scale,
                p,
                align
            )
            if (colIdx > 0) canvas.drawLine(tx, y, tx, y + tier1H, borderPaint)
            tx += leafColWidths[colIdx]
        }
        y += tier1H + 12f * scale

        // Final Exam Merit Position Box (STRICTLY ONLY for Final Exam)
        if (summary.isFinalExam && summary.meritPosition != null) {
            val meritH = 30f * scale
            val meritRect = android.graphics.RectF(margin, y, pageWidth - margin, y + meritH)
            canvas.drawRoundRect(meritRect, 6f * scale, 6f * scale, totalBgPaint)
            canvas.drawRoundRect(meritRect, 6f * scale, 6f * scale, borderPaint)
            val meritLabel = "${strings.meritPositionLabel}: ${com.example.data.model.ExamResultCalculator.ordinal(summary.meritPosition)}"
            drawTextInCell(
                canvas,
                meritLabel,
                margin + 12f * scale,
                y + 8f * scale,
                contentWidth - 24f * scale,
                passPaint,
                android.text.Layout.Alignment.ALIGN_CENTER
            )
            y += meritH + 10f * scale
        }

        // Signature Strip near bottom
        val sigY = maxOf(y + 20f * scale, pageHeight - 44f * scale)
        canvas.drawLine(margin + 16f * scale, sigY, margin + 176f * scale, sigY, borderPaint)
        canvas.drawLine(pageWidth - margin - 176f * scale, sigY, pageWidth - margin - 16f * scale, sigY, borderPaint)
        drawTextInCell(
            canvas,
            strings.resultSheetClassTeacherSig,
            margin + 16f * scale,
            sigY + 5f * scale,
            160f * scale,
            metaPaint,
            android.text.Layout.Alignment.ALIGN_CENTER
        )
        drawTextInCell(
            canvas,
            strings.resultSheetHeadTeacherSig,
            pageWidth - margin - 176f * scale,
            sigY + 5f * scale,
            160f * scale,
            metaPaint,
            android.text.Layout.Alignment.ALIGN_CENTER
        )
    }

    fun buildStudentExamResultPdfBytes(
        student: StudentRecord,
        exam: com.example.data.model.ExamTermConfig,
        summary: com.example.data.model.StudentExamResultSummary,
        examConfigs: List<com.example.data.model.ExamTermConfig> = com.example.data.model.ExamDefaults.defaultThreeExams(),
        teacherProfile: TeacherProfile?,
        strings: com.example.ui.i18n.AppStrings
    ): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 842
        val pageHeight = 595
        val page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create())
        renderStudentExamCardOnCanvas(
            canvas = page.canvas,
            pageWidth = pageWidth.toFloat(),
            pageHeight = pageHeight.toFloat(),
            student = student,
            exam = exam,
            summary = summary,
            examConfigs = examConfigs,
            teacherProfile = teacherProfile,
            strings = strings
        )
        doc.finishPage(page)
        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    fun buildStudentExamResultPngBytes(
        student: StudentRecord,
        exam: com.example.data.model.ExamTermConfig,
        summary: com.example.data.model.StudentExamResultSummary,
        examConfigs: List<com.example.data.model.ExamTermConfig> = com.example.data.model.ExamDefaults.defaultThreeExams(),
        teacherProfile: TeacherProfile?,
        strings: com.example.ui.i18n.AppStrings
    ): ByteArray {
        val width = 1400
        val combined = com.example.data.model.ExamResultCalculator.computeCombinedThreeTermTotals(
            student = student,
            examConfigs = examConfigs,
            activeExamId = exam.examId
        )
        val rowCount = maxOf(combined.subjectRows.size, 1)
        val estimatedHeight = (540 + rowCount * 46 + if (summary.isFinalExam && summary.meritPosition != null) 80 else 0)
            .coerceAtLeast(920)
        val bitmap = android.graphics.Bitmap.createBitmap(width, estimatedHeight, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        renderStudentExamCardOnCanvas(
            canvas = canvas,
            pageWidth = width.toFloat(),
            pageHeight = estimatedHeight.toFloat(),
            student = student,
            exam = exam,
            summary = summary,
            examConfigs = examConfigs,
            teacherProfile = teacherProfile,
            strings = strings
        )
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 95, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    fun buildStudentExamResultWhatsAppText(
        student: StudentRecord,
        exam: com.example.data.model.ExamTermConfig,
        summary: com.example.data.model.StudentExamResultSummary,
        examConfigs: List<com.example.data.model.ExamTermConfig> = com.example.data.model.ExamDefaults.defaultThreeExams(),
        teacherProfile: TeacherProfile?,
        strings: com.example.ui.i18n.AppStrings
    ): String {
        val school = teacherProfile?.schoolName?.ifBlank { strings.appName } ?: strings.appName
        val examTitle = strings.localizeExamTitle(exam)
        val classLabel = strings.localizeClass(student.classLevel)
        val combined = com.example.data.model.ExamResultCalculator.computeCombinedThreeTermTotals(
            student = student,
            examConfigs = examConfigs,
            activeExamId = exam.examId
        )
        fun fmt(v: Double?): String = v?.let { com.example.data.model.ExamResultCalculator.formatMark(it) } ?: "—"
        return buildString {
            appendLine("*$school (${student.academicYear})*")
            appendLine("*${strings.threeExamMarksheetTitle} ($examTitle)*")
            appendLine("──────────────────")
            appendLine("*${strings.resultSheetStudentName}:* ${student.combinedDisplayName()}")
            appendLine("*${strings.resultSheetClassName}:* $classLabel | *${strings.resultSheetRollNo}:* ${student.rollNumber}")
            appendLine()
            combined.subjectRows.forEach { row ->
                val activeTerm = row.breakdownForExam(exam.examId)
                val contStr = fmt(activeTerm.continuous)
                val sumStr = fmt(activeTerm.summative)
                val totStr = fmt(activeTerm.total)
                val avgStr = fmt(row.threeTermAverage)
                val st = if (!row.activeTermHasScore) "—" else "${row.activeTermGrade} (${if (row.activeTermPassed) strings.resultPass else strings.resultFail})"
                appendLine("• *${row.subjectName} (/${row.fullMarks}):* ${strings.continuousShortCol} $contStr + ${strings.summativeShortCol} $sumStr = *$totStr* | ${strings.threeTermAverageCol}: $avgStr → $st")
            }
            appendLine()
            val tot = if (summary.scoredCount > 0) {
                "${com.example.data.model.ExamResultCalculator.formatMark(summary.totalObtained)} / ${summary.configuredFullMarks} (${summary.percentage}%)"
            } else "—"
            appendLine("*${strings.resultSheetTotalRow}:* $tot | *${strings.threeTermAverageCol}:* ${fmt(combined.threeTermAvgTotal)}")
            appendLine("*${strings.resultSheetOverallResult}:* ${strings.localizeOutcome(summary.outcome.name)} | *${strings.resultSheetGpaLabel}:* ${com.example.data.model.ExamResultCalculator.formatGpa(summary.gpa)}")
            if (summary.isFinalExam && summary.meritPosition != null) {
                appendLine("*${strings.meritPositionLabel}:* ${com.example.data.model.ExamResultCalculator.ordinal(summary.meritPosition)}")
            }
        }
    }

    fun buildClassExamResultPdfBytes(
        sheet: com.example.data.model.ClassExamResultSheet,
        teacherProfile: TeacherProfile?,
        year: Int,
        strings: com.example.ui.i18n.AppStrings
    ): ByteArray {
        val doc = PdfDocument()
        val pageWidth = 842 // A4 Landscape for clean tabulation table
        val pageHeight = 595
        val margin = 32f

        val titlePaint = android.text.TextPaint().apply {
            color = Color.rgb(0, 106, 78)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val examPaint = android.text.TextPaint().apply {
            color = Color.rgb(20, 24, 22)
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subPaint = android.text.TextPaint().apply {
            color = Color.rgb(65, 72, 68)
            textSize = 9.5f
            isAntiAlias = true
        }
        val headerPaint = android.text.TextPaint().apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val cellPaint = android.text.TextPaint().apply {
            color = Color.rgb(25, 28, 26)
            textSize = 8.5f
            isAntiAlias = true
        }
        val borderPaint = Paint().apply {
            color = Color.rgb(185, 192, 188)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val headerBg = Paint().apply {
            color = Color.rgb(0, 106, 78)
            style = Paint.Style.FILL
        }

        val specs = sheet.applicableSpecs.take(8)
        val contentWidth = pageWidth - margin * 2f
        val rollW = 38f
        val nameW = 132f
        val tailColsCount = if (sheet.isFinalExam) 4 else 3 // Total, %, Status, [Merit]
        val tailW = if (sheet.isFinalExam) 200f else 152f
        val subjCount = specs.size.coerceAtLeast(1)
        val subjW = ((contentWidth - rollW - nameW - tailW) / subjCount).coerceAtLeast(42f)

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 30f

        fun drawHeader() {
            val school = teacherProfile?.schoolName?.ifBlank { strings.appName } ?: strings.appName
            drawTextInCell(
                canvas,
                "$school  •  ${strings.localizeClass(sheet.classLevel)}  •  $year",
                margin,
                y,
                contentWidth,
                titlePaint,
                android.text.Layout.Alignment.ALIGN_CENTER
            )
            y += 18f
            drawTextInCell(
                canvas,
                "${strings.localizeExamTitle(sheet.exam)} — ${strings.classResultSheetTitle}",
                margin,
                y,
                contentWidth,
                examPaint,
                android.text.Layout.Alignment.ALIGN_CENTER
            )
            y += 20f
            drawTextInCell(
                canvas,
                "${strings.appearedLabel}: ${sheet.appearedCount}  |  ${strings.passedLabel}: ${sheet.passedCount}  |  ${strings.failedLabel}: ${sheet.failedCount}  |  ${strings.passRateLabel}: ${sheet.passPercentage}%",
                margin,
                y,
                contentWidth,
                subPaint,
                android.text.Layout.Alignment.ALIGN_CENTER
            )
            y += 16f

            val hHeight = 24f
            canvas.drawRect(margin, y, pageWidth - margin, y + hHeight, headerBg)
            canvas.drawRect(margin, y, pageWidth - margin, y + hHeight, borderPaint)
            var x = margin
            drawTextInCell(canvas, strings.resultSheetRollNo, x + 3f, y + 6f, rollW - 6f, headerPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            x += rollW
            drawTextInCell(canvas, strings.resultSheetStudentName, x + 4f, y + 6f, nameW - 8f, headerPaint)
            x += nameW
            specs.forEach { sp ->
                drawTextInCell(canvas, "${sp.subjectName}\n(${sp.fullMarks})", x + 2f, y + 2f, subjW - 4f, headerPaint, android.text.Layout.Alignment.ALIGN_CENTER)
                x += subjW
            }
            val totalColW = 56f
            val pctColW = 42f
            val stColW = 54f
            drawTextInCell(canvas, strings.resultSheetTotalRow, x + 2f, y + 6f, totalColW - 4f, headerPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            x += totalColW
            drawTextInCell(canvas, "%", x + 2f, y + 6f, pctColW - 4f, headerPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            x += pctColW
            drawTextInCell(canvas, strings.resultSheetOverallResult, x + 2f, y + 6f, stColW - 4f, headerPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            x += stColW
            if (sheet.isFinalExam) {
                drawTextInCell(canvas, "Merit", x + 2f, y + 6f, 48f - 4f, headerPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            }
            y += hHeight
        }

        drawHeader()

        val sortedRows = if (sheet.isFinalExam) {
            sheet.studentSummaries.sortedWith(
                compareBy<com.example.data.model.StudentExamResultSummary> { it.meritPosition ?: Int.MAX_VALUE }
                    .thenBy { it.student.rollSortKey() }
            )
        } else {
            sheet.studentSummaries
        }

        sortedRows.forEach { s ->
            val rHeight = 22f
            if (y + rHeight > pageHeight - 30f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 30f
                drawHeader()
            }
            canvas.drawRect(margin, y, pageWidth - margin, y + rHeight, borderPaint)
            var x = margin
            drawTextInCell(canvas, s.student.rollNumber, x + 3f, y + 5f, rollW - 6f, cellPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            x += rollW
            drawTextInCell(canvas, s.student.primaryName(), x + 4f, y + 5f, nameW - 8f, cellPaint)
            x += nameW
            specs.forEach { sp ->
                val ev = s.subjectEvaluations.firstOrNull { it.spec.subjectId == sp.subjectId }
                val txt = ev?.obtained?.let { com.example.data.model.ExamResultCalculator.formatMark(it) } ?: "—"
                drawTextInCell(canvas, txt, x + 2f, y + 5f, subjW - 4f, cellPaint, android.text.Layout.Alignment.ALIGN_CENTER)
                x += subjW
            }
            val totalColW = 56f
            val pctColW = 42f
            val stColW = 54f
            val totStr = if (s.scoredCount > 0) com.example.data.model.ExamResultCalculator.formatMark(s.totalObtained) else "—"
            drawTextInCell(canvas, totStr, x + 2f, y + 5f, totalColW - 4f, cellPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            x += totalColW
            drawTextInCell(canvas, if (s.scoredCount > 0) "${s.percentage}%" else "—", x + 2f, y + 5f, pctColW - 4f, cellPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            x += pctColW
            drawTextInCell(canvas, strings.localizeOutcome(s.outcome.name), x + 2f, y + 5f, stColW - 4f, cellPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            x += stColW
            if (sheet.isFinalExam) {
                val meritStr = s.meritPosition?.let { "#$it" } ?: "—"
                drawTextInCell(canvas, meritStr, x + 2f, y + 5f, 44f, cellPaint, android.text.Layout.Alignment.ALIGN_CENTER)
            }
            y += rHeight
        }

        doc.finishPage(page)
        return ByteArrayOutputStream().use { out ->
            doc.writeTo(out)
            doc.close()
            out.toByteArray()
        }
    }

    fun buildClassExamResultCsvBytes(
        sheet: com.example.data.model.ClassExamResultSheet
    ): ByteArray {
        fun csvCell(raw: String): String {
            val escaped = raw.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        val sb = StringBuilder()
        sb.append('\uFEFF')
        val headers = buildList {
            add("Roll No")
            add("Student Name")
            add("Class")
            sheet.applicableSpecs.forEach { add("${it.subjectName} (/${it.fullMarks})") }
            add("Total Obtained")
            add("Full Marks")
            add("Percentage %")
            add("GPA")
            add("Result")
            if (sheet.isFinalExam) add("Final Merit Rank")
        }
        sb.appendLine(headers.joinToString(",") { csvCell(it) })

        val rows = if (sheet.isFinalExam) {
            sheet.studentSummaries.sortedWith(
                compareBy<com.example.data.model.StudentExamResultSummary> { it.meritPosition ?: Int.MAX_VALUE }
                    .thenBy { it.student.rollSortKey() }
            )
        } else {
            sheet.studentSummaries
        }

        rows.forEach { s ->
            val cols = buildList {
                add(s.student.rollNumber)
                add(s.student.primaryName())
                add(s.student.classLevel)
                sheet.applicableSpecs.forEach { sp ->
                    val ev = s.subjectEvaluations.firstOrNull { it.spec.subjectId == sp.subjectId }
                    add(ev?.obtained?.let { com.example.data.model.ExamResultCalculator.formatMark(it) }.orEmpty())
                }
                add(if (s.scoredCount > 0) com.example.data.model.ExamResultCalculator.formatMark(s.totalObtained) else "")
                add(s.configuredFullMarks.toString())
                add(if (s.scoredCount > 0) "${s.percentage}%" else "")
                add(if (s.scoredCount > 0) com.example.data.model.ExamResultCalculator.formatGpa(s.gpa) else "")
                add(s.outcome.name)
                if (sheet.isFinalExam) add(s.meritPosition?.toString().orEmpty())
            }
            sb.appendLine(cols.joinToString(",") { csvCell(it) })
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
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
